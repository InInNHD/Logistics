[CmdletBinding()]
param([switch]$Quiet)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeRoot = Join-Path $projectRoot '.firefly\runtime'
$statePath = Join-Path $runtimeRoot 'state.json'
$lockPath = Join-Path $runtimeRoot 'start.lock'
$lockStream = $null

function Test-LiteralContains([AllowEmptyString()][string]$Text, [AllowEmptyString()][string]$Value) {
    return -not [string]::IsNullOrEmpty($Text) -and -not [string]::IsNullOrEmpty($Value) -and
        $Text.IndexOf($Value, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Test-ExpectedServiceMarker([string]$Name, [string]$Marker) {
    if ([string]::IsNullOrWhiteSpace($Marker) -or -not [System.IO.Path]::IsPathRooted($Marker)) { return $false }
    try { $fullMarker = [System.IO.Path]::GetFullPath($Marker) }
    catch { return $false }

    if ($Name -eq 'frontend') {
        $expected = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js'))
        return $fullMarker.Equals($expected, [StringComparison]::OrdinalIgnoreCase)
    }
    if (@('auth-service', 'warehouse-service', 'gateway') -notcontains $Name) { return $false }
    $target = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "backend\$Name\target"))
    $directory = [System.IO.Path]::GetDirectoryName($fullMarker)
    $fileName = [System.IO.Path]::GetFileName($fullMarker)
    return $directory.Equals($target, [StringComparison]::OrdinalIgnoreCase) -and
        $fileName -match ('^' + [Regex]::Escape($Name) + '-[A-Za-z0-9_.-]+\.jar$')
}

try {
    if (-not (Test-Path -LiteralPath $runtimeRoot -PathType Container)) {
        if (-not $Quiet) { Write-Host 'Firefly Logistics 当前没有已记录的运行进程。' }
        exit 0
    }
    try {
        $lockStream = New-Object System.IO.FileStream($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw '另一个启动或停止操作正在执行，请稍后重试。'
    }
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        if (-not $Quiet) { Write-Host 'Firefly Logistics 当前没有已记录的运行进程。' }
        exit 0
    }

    try { $state = Get-Content -Raw -Encoding UTF8 -LiteralPath $statePath | ConvertFrom-Json }
    catch { throw '状态文件损坏。为避免误杀进程，脚本拒绝继续；请人工检查 .firefly/runtime/state.json。' }
    if ([string]$state.projectRoot -ne $projectRoot) { throw '状态文件不属于当前工作区，拒绝停止任何进程。' }
    if ([int]$state.schemaVersion -ne 1 -or [string]::IsNullOrWhiteSpace([string]$state.runId)) { throw '状态文件格式无效。' }

    $allowedNames = @('auth-service', 'warehouse-service', 'gateway', 'frontend')
    $records = @($state.services)
    if ($records.Count -gt 4 -or @($records | ForEach-Object { [string]$_.name } | Select-Object -Unique).Count -ne $records.Count) {
        throw '状态文件包含重复或过多的服务记录，拒绝停止。'
    }
    $verifiedProcesses = @()
    foreach ($record in $records) {
        if ($allowedNames -notcontains [string]$record.name) { throw '状态文件包含未知服务，拒绝停止。' }
        if ($null -eq $record.pid -or $null -eq $record.startTimeUtcTicks -or $null -eq $record.executable -or $null -eq $record.marker) {
            throw '状态文件缺少进程校验字段，拒绝停止。'
        }
        $pidValue = [int]$record.pid
        if ($pidValue -le 0) { throw '状态文件 PID 无效。' }
        $marker = [string]$record.marker
        if (-not (Test-ExpectedServiceMarker ([string]$record.name) $marker)) { throw '状态文件进程标记无效，拒绝停止。' }

        $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
        if ($null -eq $process) { continue }
        $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction SilentlyContinue
        $ticksMatch = $process.StartTime.ToUniversalTime().Ticks -eq [long]$record.startTimeUtcTicks
        $pathMatch = $null -ne $cim -and [string]$cim.ExecutablePath -eq [string]$record.executable
        $markerMatch = $null -ne $cim -and (Test-LiteralContains ([string]$cim.CommandLine) $marker)
        if (-not ($ticksMatch -and $pathMatch -and $markerMatch)) {
            throw "PID $pidValue 的身份校验失败，可能已被复用；拒绝停止任何未验证进程。"
        }

        $verifiedProcesses += [pscustomobject]@{ Process = $process; Record = $record }
    }

    [array]::Reverse($verifiedProcesses)
    $stopped = 0
    foreach ($entry in $verifiedProcesses) {
        $record = $entry.Record
        $pidValue = [int]$record.pid
        $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
        if ($null -eq $process) { continue }
        $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $pidValue" -ErrorAction SilentlyContinue
        $stillMatches = $process.StartTime.ToUniversalTime().Ticks -eq [long]$record.startTimeUtcTicks -and
            $null -ne $cim -and [string]$cim.ExecutablePath -eq [string]$record.executable -and
            (Test-LiteralContains ([string]$cim.CommandLine) ([string]$record.marker))
        if (-not $stillMatches) { throw "PID $pidValue 在停止前发生变化，拒绝终止。" }

        Stop-Process -InputObject $process -Force
        try { $process.WaitForExit(5000) | Out-Null } catch { }
        $stopped++
        if (-not $Quiet) { Write-Host "已停止 $($record.name)（PID $pidValue）" -ForegroundColor Green }
    }

    Remove-Item -LiteralPath $statePath -Force
    if (-not $Quiet) {
        if ($stopped -eq 0) { Write-Host '记录中的进程均已退出，已清理陈旧状态。' }
        else { Write-Host 'Firefly Logistics 已停止；MySQL 和业务数据保持不变。' -ForegroundColor Green }
    }
} catch {
    Write-Host "停止失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $lockStream) { $lockStream.Dispose() }
}
