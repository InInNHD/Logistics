[CmdletBinding()]
param(
    [ValidateSet('Empty', 'Demo')]
    [string]$DataMode = 'Empty',
    [switch]$AllowDevDefaults,
    [switch]$SkipBuild,
    [switch]$NoBrowser,
    [switch]$NonInteractive,
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 150
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$script:ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:RuntimeRoot = Join-Path $script:ProjectRoot '.firefly\runtime'
$script:StatePath = Join-Path $script:RuntimeRoot 'state.json'
$script:LockPath = Join-Path $script:RuntimeRoot 'start.lock'
$script:RunId = (Get-Date).ToString('yyyyMMdd-HHmmss-fff')
$script:RunLogDir = Join-Path $script:RuntimeRoot (Join-Path 'logs' $script:RunId)
$script:Started = @()
$script:StateRecords = @()
$script:OriginalEnvironment = @{}
$script:LockStream = $null
$script:DbSslMode = 'DISABLED'
$script:LocalMySqlStarted = $false

$script:AllowedEnvironment = @{
    MYSQL_HOST = $true; MYSQL_PORT = $true; MYSQL_DATABASE = $true; MYSQL_SSL_MODE = $true
    MYSQL_CLIENT_PATH = $true; MYSQL_SERVER_PATH = $true; MYSQL_CONFIG_FILE = $true
    MYSQL_USER = $true; MYSQL_PASSWORD = $true
    DB_URL = $true; DB_USERNAME = $true; DB_PASSWORD = $true
    JWT_SECRET = $true; JWT_TTL_HOURS = $true; JWT_ISSUER = $true; JWT_AUDIENCE = $true
    WAREHOUSE_SECURITY_ENABLED = $true
    ADMIN_BOOTSTRAP_ENABLED = $true; ADMIN_USERNAME = $true; ADMIN_PASSWORD = $true
    LOGIN_MAX_FAILURES = $true; LOGIN_LOCK_MINUTES = $true
    GATEWAY_PORT = $true; AUTH_SERVICE_PORT = $true; WAREHOUSE_SERVICE_PORT = $true
    AUTH_SERVICE_URL = $true; WAREHOUSE_SERVICE_URL = $true
    TOKEN_STATUS_CHECK_ENABLED = $true; SPRINGDOC_ENABLED = $true
    CORS_ALLOWED_ORIGINS = $true; FRONTEND_PORT = $true; VITE_API_BASE_URL = $true
}

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Protect-PrivatePath([string]$Path, [switch]$Directory) {
    try {
        $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
        $systemSid = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')
        $administratorsSid = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-32-544')
        $expectedSids = @($currentSid.Value, $systemSid.Value, $administratorsSid.Value)
        $security = Get-Acl -LiteralPath $Path
        $ownerSid = (New-Object System.Security.Principal.NTAccount($security.Owner)).Translate([System.Security.Principal.SecurityIdentifier])
        $rules = @($security.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))
        $validRules = @($rules | Where-Object {
                -not $_.IsInherited -and $_.AccessControlType -eq [System.Security.AccessControl.AccessControlType]::Allow -and
                $expectedSids -contains $_.IdentityReference.Value -and
                ($_.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::FullControl) -eq [System.Security.AccessControl.FileSystemRights]::FullControl -and
                ((-not $Directory -and $_.InheritanceFlags -eq [System.Security.AccessControl.InheritanceFlags]::None) -or
                    ($Directory -and ($_.InheritanceFlags -band [System.Security.AccessControl.InheritanceFlags]::ContainerInherit) -ne 0 -and
                        ($_.InheritanceFlags -band [System.Security.AccessControl.InheritanceFlags]::ObjectInherit) -ne 0))
            })
        $validIdentities = @($validRules | ForEach-Object { $_.IdentityReference.Value } | Select-Object -Unique)
        if ($security.AreAccessRulesProtected -and $ownerSid.Value -eq $currentSid.Value -and
                $rules.Count -eq $validRules.Count -and $validIdentities.Count -eq 3) {
            return
        }

        $security.SetAccessRuleProtection($true, $false)
        foreach ($rule in @($security.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))) {
            $security.RemoveAccessRuleSpecific($rule)
        }
        foreach ($sid in @($currentSid, $systemSid, $administratorsSid)) {
            $rule = if ($Directory) {
                New-Object System.Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            } else {
                New-Object System.Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow')
            }
            $security.AddAccessRule($rule) | Out-Null
        }
        if ($ownerSid.Value -ne $currentSid.Value) { $security.SetOwner($currentSid) }
        Set-Acl -LiteralPath $Path -AclObject $security
    } catch {
        throw "无法将本地敏感路径权限收紧到当前用户、SYSTEM 和 Administrators：$Path。$($_.Exception.Message)"
    }
}

function Get-EnvValue([string]$Name, [string]$DefaultValue = '') {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultValue }
    return $value
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        $example = Join-Path $script:ProjectRoot '.env.example'
        if (-not (Test-Path -LiteralPath $example -PathType Leaf)) {
            throw '缺少 .env 和 .env.example。'
        }
        Copy-Item -LiteralPath $example -Destination $Path
        Write-Host '已从 .env.example 创建本地 .env（该文件已被 Git 忽略）。' -ForegroundColor Yellow
    }

    foreach ($rawLine in [System.IO.File]::ReadAllLines($Path)) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) { continue }
        $separatorIndex = $line.IndexOf('=')
        $name = $line.Substring(0, $separatorIndex).Trim()
        if (-not $script:AllowedEnvironment.ContainsKey($name)) { continue }
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { continue }

        $value = $line.Substring($separatorIndex + 1).Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $current = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ([string]::IsNullOrEmpty($current)) {
            if (-not $script:OriginalEnvironment.ContainsKey($name)) {
                $script:OriginalEnvironment[$name] = $null
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Save-EnvironmentValue([string]$Name, [AllowNull()][string]$Value) {
    if (-not $script:OriginalEnvironment.ContainsKey($Name)) {
        $script:OriginalEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Restore-Environment {
    foreach ($entry in $script:OriginalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
    }
}

function Invoke-WithoutApplicationSecrets([scriptblock]$Action) {
    $secretNames = @('DB_PASSWORD', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'MYSQL_PWD', 'JWT_SECRET', 'ADMIN_PASSWORD')
    $original = @{}
    try {
        foreach ($name in $secretNames) {
            $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        & $Action
    } finally {
        foreach ($entry in $original.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
        }
    }
}

function Resolve-Tool([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) { throw "未找到 $Name，请先安装并加入 PATH。" }
    return $command.Source
}

function New-SanitizedJavaEnvironment {
    $environment = @{}
    foreach ($entry in Get-ChildItem Env:) {
        $name = [string]$entry.Name
        if ($name -match '^(SPRING_|FIREFLY_|SERVER_|MANAGEMENT_)' -or
                $name -in @('PORT', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')) {
            $environment[$name] = $null
        }
    }
    foreach ($name in @('PORT', 'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION',
            'SPRING_CONFIG_IMPORT', 'SPRING_PROFILES_ACTIVE', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME',
            'SPRING_DATASOURCE_PASSWORD', 'SPRING_FLYWAY_LOCATIONS', 'SPRING_FLYWAY_OUT_OF_ORDER', 'SPRING_FLYWAY_ENABLED',
            'SPRING_FLYWAY_TABLE', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MYSQL_ROOT_PASSWORD', 'MYSQL_PWD')) {
        $environment[$name] = $null
    }
    return $environment
}

function New-SanitizedFrontendEnvironment([string]$ApiBaseUrl) {
    $environment = @{}
    foreach ($entry in Get-ChildItem Env:) {
        if ([string]$entry.Name -match '^VITE_') { $environment[[string]$entry.Name] = $null }
    }
    foreach ($name in @($script:AllowedEnvironment.Keys) + @('NODE_OPTIONS', 'MYSQL_ROOT_PASSWORD', 'MYSQL_PWD')) {
        $environment[$name] = $null
    }
    $environment['VITE_API_BASE_URL'] = $ApiBaseUrl
    return $environment
}

function Assert-RuntimeVersions([string]$JavaExe, [string]$MavenExe, [string]$NodeExe, [string]$NpmExe) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $javaText = ((& $JavaExe -version 2>&1) | ForEach-Object { $_.ToString() }) -join "`n" }
    finally { $ErrorActionPreference = $previousErrorPreference }
    if ($javaText -notmatch 'version\s+"(?:1\.)?(\d+)') { throw '无法识别 Java 版本。' }
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 17) { throw '需要 JDK 17 或更高版本。' }

    $nodeText = (& $NodeExe --version 2>&1 | Out-String).Trim()
    if ($nodeText -notmatch '^v(\d+)\.(\d+)\.') { throw '无法识别 Node.js 版本。' }
    $nodeMajor = [int]$Matches[1]
    $nodeMinor = [int]$Matches[2]
    $nodeSupported = ($nodeMajor -eq 20 -and $nodeMinor -ge 19) -or ($nodeMajor -eq 22 -and $nodeMinor -ge 12) -or ($nodeMajor -gt 22)
    if (-not $nodeSupported) { throw '需要 Node.js 20.19+ 或 22.12+。' }

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $mavenText = ((& $MavenExe --version 2>&1) | ForEach-Object { $_.ToString() }) -join "`n" }
    finally { $ErrorActionPreference = $previousErrorPreference }
    if ($LASTEXITCODE -ne 0 -or $mavenText -notmatch 'Apache Maven\s+(\d+)\.(\d+)') { throw '无法识别 Maven 版本。' }
    $mavenVersion = New-Object Version([int]$Matches[1], [int]$Matches[2])
    if ($mavenVersion -lt [Version]'3.8') { throw '需要 Maven 3.8 或更高版本。' }
    $null = & $NpmExe --version 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'npm 无法正常运行。' }
    Write-Ok "Java $javaMajor / Node $nodeText / Maven $mavenVersion / npm 检查通过"
}

function Get-PortOwner([int]$Port) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) { return $null }
    $pidValue = $listeners[0].OwningProcess
    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    $name = if ($null -eq $process) { '未知进程' } else { $process.ProcessName }
    return [pscustomobject]@{ Pid = $pidValue; Name = $name }
}

function Assert-PortsAvailable([int[]]$Ports) {
    foreach ($port in $Ports) {
        $owner = Get-PortOwner $port
        if ($null -ne $owner) {
            throw "端口 $port 已被 $($owner.Name)（PID $($owner.Pid)）占用；脚本不会自动终止该进程。"
        }
    }
}

function Get-MySqlHandshakeVersion([string]$DbHost, [int]$DbPort) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect($DbHost, $DbPort, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(5000)) { throw "连接 MySQL 超时：${DbHost}:$DbPort" }
        $client.EndConnect($pending)
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $header = New-Object byte[] 4
        $read = 0
        while ($read -lt 4) {
            $count = $stream.Read($header, $read, 4 - $read)
            if ($count -le 0) { throw 'MySQL 未返回握手数据。' }
            $read += $count
        }
        $length = [int]$header[0] + ([int]$header[1] -shl 8) + ([int]$header[2] -shl 16)
        if ($length -lt 3 -or $length -gt 65535) { throw 'MySQL 握手数据长度异常。' }
        $payload = New-Object byte[] $length
        $read = 0
        while ($read -lt $length) {
            $count = $stream.Read($payload, $read, $length - $read)
            if ($count -le 0) { throw 'MySQL 握手数据不完整。' }
            $read += $count
        }
        if ($payload[0] -ne 10) { throw "不支持的 MySQL 握手协议：$($payload[0])" }
        $end = 1
        while ($end -lt $payload.Length -and $payload[$end] -ne 0) { $end++ }
        if ($end -le 1) { throw '无法识别 MySQL 服务版本。' }
        return [System.Text.Encoding]::ASCII.GetString($payload, 1, $end - 1)
    } finally {
        $client.Close()
    }
}

function Ensure-LocalMySqlRunning([string]$DbHost, [int]$DbPort) {
    try { return Get-MySqlHandshakeVersion $DbHost $DbPort }
    catch { $initialError = $_.Exception.Message }

    $localHosts = @('127.0.0.1', 'localhost', '::1')
    if ($localHosts -notcontains $DbHost.ToLowerInvariant()) { throw $initialError }
    $owner = Get-PortOwner $DbPort
    if ($null -ne $owner) {
        throw "本地端口 $DbPort 已被 $($owner.Name)（PID $($owner.Pid)）占用，但没有返回有效 MySQL 握手。"
    }

    $serverPath = Get-EnvValue 'MYSQL_SERVER_PATH'
    $configPath = Get-EnvValue 'MYSQL_CONFIG_FILE'
    if (-not $serverPath -or -not $configPath) { throw $initialError }
    if (-not [System.IO.Path]::IsPathRooted($serverPath) -or -not (Test-Path -LiteralPath $serverPath -PathType Leaf) -or
            [System.IO.Path]::GetFileName($serverPath) -ne 'mysqld.exe') {
        throw 'MYSQL_SERVER_PATH 必须指向存在的 mysqld.exe 绝对路径。'
    }
    if (-not [System.IO.Path]::IsPathRooted($configPath) -or -not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        throw 'MYSQL_CONFIG_FILE 必须指向存在的 my.ini 绝对路径。'
    }
    $serverPath = [System.IO.Path]::GetFullPath($serverPath)
    $configPath = [System.IO.Path]::GetFullPath($configPath)

    Write-Host "本地 MySQL 尚未运行，正在启动独立实例：127.0.0.1:$DbPort" -ForegroundColor Yellow
    $original = @{}
    try {
        foreach ($name in @($script:AllowedEnvironment.Keys) + @('MYSQL_ROOT_PASSWORD', 'MYSQL_PWD', 'SPRING_APPLICATION_JSON')) {
            $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        $stdout = Join-Path $script:RuntimeRoot 'mysql-server.out.log'
        $stderr = Join-Path $script:RuntimeRoot 'mysql-server.err.log'
        $argumentLine = Quote-NativeArgument "--defaults-file=$configPath"
        $null = Start-Process -FilePath $serverPath -ArgumentList $argumentLine `
            -WorkingDirectory ([System.IO.Path]::GetDirectoryName($serverPath)) `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    } finally {
        foreach ($entry in $original.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
        }
    }

    $deadline = (Get-Date).AddSeconds(30)
    do {
        try {
            $version = Get-MySqlHandshakeVersion $DbHost $DbPort
            $script:LocalMySqlStarted = $true
            Write-Ok "本地 MySQL $version 已启动"
            return $version
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "本地 MySQL 在 30 秒内未就绪，请检查 $stderr。脚本不会按进程名终止任何 mysqld。"
}

function Find-MySqlClient {
    $configured = Get-EnvValue 'MYSQL_CLIENT_PATH'
    if ($configured) {
        if (-not [System.IO.Path]::IsPathRooted($configured) -or -not (Test-Path -LiteralPath $configured -PathType Leaf) -or
                [System.IO.Path]::GetFileName($configured) -ne 'mysql.exe') {
            throw 'MYSQL_CLIENT_PATH 必须指向存在的 mysql.exe 绝对路径。'
        }
        return [System.IO.Path]::GetFullPath($configured)
    }

    $candidatePaths = @()
    $command = Get-Command mysql -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $command) { $candidatePaths += $command.Source }
    $roots = @()
    if ($env:ProgramFiles) { $roots += (Join-Path $env:ProgramFiles 'MySQL') }
    if (${env:ProgramFiles(x86)}) { $roots += (Join-Path ${env:ProgramFiles(x86)} 'MySQL') }
    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'FireflyLogistics') }
    foreach ($root in $roots) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $candidatePaths += @(Get-ChildItem -LiteralPath $root -Filter mysql.exe -File -Recurse -ErrorAction SilentlyContinue |
                ForEach-Object { $_.FullName })
        }
    }
    foreach ($candidate in @($candidatePaths | Where-Object { $_ } | Sort-Object -Unique -Descending)) {
        try {
            Assert-MySqlClientVersion $candidate
            return [System.IO.Path]::GetFullPath($candidate)
        } catch { }
    }
    return $null
}

function Assert-MySqlClientVersion([string]$MySqlExe) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $versionText = ((& $MySqlExe --version 2>&1) | ForEach-Object { $_.ToString() }) -join ' ' }
    finally { $ErrorActionPreference = $previousErrorPreference }
    $clientMajor = $null
    if ($versionText -match 'Distrib\s+(\d+)\.') { $clientMajor = [int]$Matches[1] }
    elseif ($versionText -match '\bVer\s+(\d+)\.\d+\.\d+') { $clientMajor = [int]$Matches[1] }
    if ($null -eq $clientMajor) { throw '无法识别 mysql.exe 客户端版本。' }
    if ($clientMajor -lt 8) { throw "检测到旧版 MySQL Client：$versionText。请安装 MySQL 8 Client 并加入 PATH。" }
}

function Quote-NativeArgument([string]$Value) {
    if ($Value -notmatch '[\s"]') { return $Value }
    return '"' + ($Value -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

function Invoke-MySql(
    [string]$MySqlExe,
    [string]$DbHost,
    [int]$DbPort,
    [string]$Username,
    [string]$Password,
    [AllowEmptyString()][string]$Database,
    [string]$Sql
) {
    $arguments = @('--protocol=TCP', '--connect-timeout=5', "--ssl-mode=$script:DbSslMode", "--host=$DbHost", "--port=$DbPort", "--user=$Username", '--batch', '--skip-column-names')
    if ($script:DbSslMode -eq 'DISABLED' -and @('127.0.0.1', 'localhost', '::1') -contains $DbHost.ToLowerInvariant()) {
        $arguments += '--get-server-public-key'
    }
    if ($Database) { $arguments += "--database=$Database" }

    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $MySqlExe
    $info.Arguments = (($arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($secretName in @('DB_PASSWORD', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'JWT_SECRET', 'ADMIN_PASSWORD')) {
        $info.EnvironmentVariables.Remove($secretName)
    }
    $info.EnvironmentVariables['MYSQL_PWD'] = $Password

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    if (-not $process.Start()) { throw '无法启动 MySQL 客户端。' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.WriteLine($Sql)
    $process.StandardInput.Close()
    $process.WaitForExit()
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $stdoutTask.Result.Trim()
        Error = $stderrTask.Result.Trim()
    }
}

function ConvertTo-MySqlString([string]$Value) {
    return "'" + $Value.Replace('\', '\\').Replace("'", "''") + "'"
}

function Convert-SecureStringToPlain([Security.SecureString]$SecureValue) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Assert-AuthenticatedMySqlVersion([string]$VersionText) {
    if ($VersionText -match '(?i)MariaDB') { throw "检测到 MariaDB（$VersionText）；本项目要求 Oracle MySQL 8.0.16+。" }
    if ($VersionText -notmatch '(\d+)\.(\d+)\.(\d+)') { throw "无法识别认证后的 MySQL 版本：$VersionText" }
    $version = New-Object Version([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
    if ($version -lt [Version]'8.0.16') { throw "认证后的数据库版本为 $VersionText；项目要求 MySQL 8.0.16+。" }
}

function Ensure-Database(
    [string]$MySqlExe,
    [string]$DbHost,
    [int]$DbPort,
    [string]$Database,
    [string]$Username,
    [string]$Password
) {
    $probe = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database 'SELECT VERSION();'
    if ($probe.ExitCode -eq 0) {
        Assert-AuthenticatedMySqlVersion $probe.Output
        return
    }

    $localHosts = @('127.0.0.1', 'localhost', '::1')
    if ($localHosts -notcontains $DbHost.ToLowerInvariant()) {
        throw '远程 MySQL 业务账号无法连接；请在 .env 中配置已创建数据库的有效 DB_URL/DB_USERNAME/DB_PASSWORD。'
    }
    if ($NonInteractive) {
        throw '数据库或业务用户尚不可用。请交互运行 start.cmd，或预先创建 MySQL 8 数据库与业务用户。'
    }
    if ($Password -notmatch '^[A-Za-z0-9._!@#%+=:-]{12,128}$') {
        throw '自动建库仅接受 12-128 位的安全业务密码字符集（字母、数字和 ._!@#%+=:-）；请修改 .env 或手工建库。'
    }

    Write-Host '首次启动需要创建数据库和最小权限业务用户。管理员密码只用于本次初始化，不会写入日志或状态文件。' -ForegroundColor Yellow
    $adminUser = Read-Host 'MySQL 管理员用户名（默认 root）'
    if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = 'root' }
    if ($adminUser -notmatch '^[A-Za-z0-9_.-]+$') { throw 'MySQL 管理员用户名格式不安全。' }
    $securePassword = Read-Host 'MySQL 管理员密码' -AsSecureString
    $adminPassword = Convert-SecureStringToPlain $securePassword
    try {
        $adminProbe = Invoke-MySql $MySqlExe $DbHost $DbPort $adminUser $adminPassword '' 'SELECT VERSION();'
        if ($adminProbe.ExitCode -ne 0) { throw 'MySQL 管理员认证失败，未修改数据库。' }
        Assert-AuthenticatedMySqlVersion $adminProbe.Output

        $databaseExists = Invoke-MySql $MySqlExe $DbHost $DbPort $adminUser $adminPassword '' "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$Database';"
        if ($databaseExists.ExitCode -ne 0) { throw '无法只读检查目标数据库，未进行初始化。' }
        if ($databaseExists.Output.Trim() -ne '0') {
            throw '目标数据库已存在，但当前业务账号无法连接；为避免修改既有账号或数据，脚本不会自动重置密码或授权。请修正 .env 或手工处理账号。'
        }
        $accountExists = Invoke-MySql $MySqlExe $DbHost $DbPort $adminUser $adminPassword '' "SELECT COUNT(*) FROM mysql.user WHERE User = '$Username' AND Host IN ('localhost', '127.0.0.1', '::1');"
        if ($accountExists.ExitCode -ne 0) { throw '无法只读检查现有 MySQL 账号，未进行初始化。' }
        if ($accountExists.Output.Trim() -ne '0') {
            throw '数据库尚未创建，但同名业务账号已存在；脚本不会修改其密码或权限，请手工确认后再启动。'
        }

        $dbLiteral = '`' + $Database + '`'
        $userLiteral = "'$Username'"
        $passwordLiteral = "'$Password'"
        $sql = @"
CREATE DATABASE IF NOT EXISTS $dbLiteral CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER $userLiteral@'localhost' IDENTIFIED BY $passwordLiteral;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON $dbLiteral.* TO $userLiteral@'localhost';
CREATE USER $userLiteral@'127.0.0.1' IDENTIFIED BY $passwordLiteral;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON $dbLiteral.* TO $userLiteral@'127.0.0.1';
CREATE USER $userLiteral@'::1' IDENTIFIED BY $passwordLiteral;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON $dbLiteral.* TO $userLiteral@'::1';
"@
        $provision = Invoke-MySql $MySqlExe $DbHost $DbPort $adminUser $adminPassword '' $sql
        if ($provision.ExitCode -ne 0) { throw '数据库初始化失败。MySQL DDL/授权不是原子操作，可能已创建部分对象；脚本不会自动删除、repair 或覆盖，请由管理员检查后重试。' }
    } finally {
        $adminPassword = $null
        $securePassword.Dispose()
    }

    $probe = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database 'SELECT VERSION();'
    if ($probe.ExitCode -ne 0) { throw '数据库已初始化，但业务账号仍无法连接。' }
    Assert-AuthenticatedMySqlVersion $probe.Output
    Write-Ok '数据库和本地业务用户已准备完成'
}

function Assert-FlywayHistory(
    [string]$MySqlExe,
    [string]$DbHost,
    [int]$DbPort,
    [string]$Database,
    [string]$Username,
    [string]$Password,
    [string]$HistoryTable,
    [string]$DomainPrefix,
    [string[]]$AllowedVersionKeys,
    [string[]]$AllowedRepeatableDescriptions = @()
) {
    if ($HistoryTable -notmatch '^[a-z_]+$' -or $DomainPrefix -notmatch '^[a-z_]+$') { throw '内部 Flyway 校验参数无效。' }
    $tableCheck = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '$Database' AND TABLE_NAME = '$HistoryTable';"
    if ($tableCheck.ExitCode -ne 0) { throw "无法检查 $HistoryTable。" }

    if ($tableCheck.Output.Trim() -eq '0') {
        $domainTables = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '$Database' AND LEFT(TABLE_NAME, $($DomainPrefix.Length)) = '$DomainPrefix';"
        if ($domainTables.ExitCode -ne 0) { throw '无法检查既有业务表。' }
        if ([int]$domainTables.Output.Trim() -gt 0) { throw "$HistoryTable 不存在，但数据库已有 $DomainPrefix 业务表；拒绝将其当作新库迁移。" }
        return
    }

    $history = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT COALESCE(version, '<repeatable>'), description, success FROM $HistoryTable ORDER BY installed_rank;"
    if ($history.ExitCode -ne 0) { throw "无法读取 $HistoryTable。" }
    $versions = @()
    foreach ($line in @($history.Output -split "`r?`n" | Where-Object { $_ })) {
        $columns = @($line -split "`t")
        if ($columns.Count -ne 3 -or $columns[2] -ne '1') { throw "$HistoryTable 包含失败或格式异常的迁移记录。" }
        if ($columns[0] -eq '<repeatable>') {
            if ($AllowedRepeatableDescriptions -notcontains $columns[1]) { throw "$HistoryTable 包含当前脚本不支持的可重复迁移。" }
            continue
        }
        if ($columns[0] -notmatch '^\d+$') { throw "$HistoryTable 包含当前脚本不支持的迁移版本。" }
        $versions += $columns[0]
    }
    $versionKey = $versions -join ','
    if ($AllowedVersionKeys -notcontains $versionKey) {
        throw "$HistoryTable 版本序列 [$versionKey] 不属于当前版本允许的迁移路径。"
    }
    if (-not $versionKey) {
        $domainTables = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '$Database' AND LEFT(TABLE_NAME, $($DomainPrefix.Length)) = '$DomainPrefix';"
        if ($domainTables.ExitCode -ne 0 -or [int]$domainTables.Output.Trim() -gt 0) { throw "$HistoryTable 为空但业务表已存在，拒绝迁移。" }
    }
}

function Assert-DatabaseMode(
    [string]$MySqlExe,
    [string]$DbHost,
    [int]$DbPort,
    [string]$Database,
    [string]$Username,
    [string]$Password
) {
    $authenticatedVersion = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database 'SELECT VERSION();'
    if ($authenticatedVersion.ExitCode -ne 0) { throw '业务账号无法执行版本复核。' }
    Assert-AuthenticatedMySqlVersion $authenticatedVersion.Output

    $schema = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$Database';"
    $schemaColumns = @($schema.Output.Trim() -split "`t")
    if ($schema.ExitCode -ne 0 -or $schemaColumns.Count -ne 2 -or $schemaColumns[0].ToLowerInvariant() -ne 'utf8mb4' -or $schemaColumns[1].ToLowerInvariant() -notmatch '^utf8mb4_.*_ci$') {
        throw '目标数据库必须使用 utf8mb4 且采用大小写不敏感的 utf8mb4 排序规则；脚本不会自动改造已有数据库。'
    }
    $tableCollations = Invoke-MySql $MySqlExe $DbHost $DbPort $Username $Password $Database "SELECT DISTINCT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '$Database' AND (LEFT(TABLE_NAME, 4) = 'wms_' OR LEFT(TABLE_NAME, 4) = 'sys_') AND TABLE_COLLATION IS NOT NULL;"
    if ($tableCollations.ExitCode -ne 0) { throw '无法检查既有业务表排序规则。' }
    foreach ($collation in @($tableCollations.Output -split "`r?`n" | Where-Object { $_ })) {
        if ($collation.ToLowerInvariant() -notmatch '^utf8mb4_.*_ci$') { throw "既有业务表排序规则 $collation 与应用的大小写唯一性语义不兼容。" }
    }

    Assert-FlywayHistory $MySqlExe $DbHost $DbPort $Database $Username $Password 'flyway_auth_schema_history' 'sys_' @('', '1', '1,2', '1,2,3', '1,2,3,4', '1,2,3,4,5', '0,1', '0,1,2', '0,1,2,3', '0,1,2,3,4', '0,1,2,3,4,5')
    $warehouseAllowed = if ($DataMode -eq 'Demo') { @('', '1', '1,2', '1,2,3', '0,1', '0,1,2', '0,1,2,3') } else { @('', '1', '1,3', '0,1', '0,1,3') }
    $warehouseRepeatables = if ($DataMode -eq 'Demo') { @('seed public orders') } else { @() }
    Assert-FlywayHistory $MySqlExe $DbHost $DbPort $Database $Username $Password 'flyway_warehouse_schema_history' 'wms_' $warehouseAllowed $warehouseRepeatables
}

function Get-ApplicationJar([string]$Module) {
    $target = Join-Path $script:ProjectRoot "backend\$Module\target"
    $jar = Get-ChildItem -LiteralPath $target -Filter "$Module-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($null -eq $jar) { throw "未找到 $Module 可执行 JAR。" }
    return $jar.FullName
}

function Write-State {
    $state = [ordered]@{
        schemaVersion = 1
        runId = $script:RunId
        projectRoot = $script:ProjectRoot
        createdAt = [DateTime]::UtcNow.ToString('o')
        services = @($script:StateRecords)
    }
    $json = $state | ConvertTo-Json -Depth 6
    $tempPath = $script:StatePath + '.tmp'
    [System.IO.File]::WriteAllText($tempPath, $json, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tempPath -Destination $script:StatePath -Force
}

function Test-LiteralContains([AllowEmptyString()][string]$Text, [AllowEmptyString()][string]$Value) {
    return -not [string]::IsNullOrEmpty($Text) -and -not [string]::IsNullOrEmpty($Value) -and
        $Text.IndexOf($Value, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Test-ExpectedServiceMarker([string]$Name, [string]$Marker) {
    if ([string]::IsNullOrWhiteSpace($Marker) -or -not [System.IO.Path]::IsPathRooted($Marker)) { return $false }
    try { $fullMarker = [System.IO.Path]::GetFullPath($Marker) }
    catch { return $false }

    if ($Name -eq 'frontend') {
        $expected = [System.IO.Path]::GetFullPath((Join-Path $script:ProjectRoot 'frontend\node_modules\vite\bin\vite.js'))
        return $fullMarker.Equals($expected, [StringComparison]::OrdinalIgnoreCase)
    }
    if (@('auth-service', 'warehouse-service', 'gateway') -notcontains $Name) { return $false }
    $target = [System.IO.Path]::GetFullPath((Join-Path $script:ProjectRoot "backend\$Name\target"))
    $directory = [System.IO.Path]::GetDirectoryName($fullMarker)
    $fileName = [System.IO.Path]::GetFileName($fullMarker)
    return $directory.Equals($target, [StringComparison]::OrdinalIgnoreCase) -and
        $fileName -match ('^' + [Regex]::Escape($Name) + '-[A-Za-z0-9_.-]+\.jar$')
}

function Assert-NoExistingRun {
    if (-not (Test-Path -LiteralPath $script:StatePath -PathType Leaf)) { return }
    try { $state = Get-Content -Raw -Encoding UTF8 -LiteralPath $script:StatePath | ConvertFrom-Json }
    catch { throw '运行状态文件损坏；请检查 .firefly/runtime/state.json，脚本不会据此终止任何进程。' }
    if ([string]$state.projectRoot -ne $script:ProjectRoot -or [int]$state.schemaVersion -ne 1) {
        throw '运行状态文件不属于当前工作区或格式无效。'
    }

    $verified = 0
    foreach ($record in @($state.services)) {
        if ($null -eq $record.pid -or $null -eq $record.startTimeUtcTicks -or $null -eq $record.executable -or $null -eq $record.marker -or
                -not (Test-ExpectedServiceMarker ([string]$record.name) ([string]$record.marker))) {
            throw '运行状态文件包含无效的服务记录；为避免误判，脚本拒绝自动清理。'
        }
        $process = Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
        if ($null -eq $process) { continue }
        try {
            $ticksMatch = $process.StartTime.ToUniversalTime().Ticks -eq [long]$record.startTimeUtcTicks
            $cim = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$record.pid)" -ErrorAction SilentlyContinue
            $pathMatch = $null -ne $cim -and [string]$cim.ExecutablePath -eq [string]$record.executable
            $markerMatch = $null -ne $cim -and (Test-LiteralContains ([string]$cim.CommandLine) ([string]$record.marker))
            if ($ticksMatch -and $pathMatch -and $markerMatch) { $verified++ }
        } catch { }
    }
    if ($verified -gt 0) { throw 'Firefly Logistics 已有进程在运行；请先双击 stop.cmd。' }
    Remove-Item -LiteralPath $script:StatePath -Force
}

function Start-ManagedProcess(
    [string]$Name,
    [string]$Executable,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [hashtable]$Environment,
    [string]$Marker
) {
    $original = @{}
    try {
        foreach ($entry in $Environment.GetEnumerator()) {
            $original[$entry.Key] = [Environment]::GetEnvironmentVariable([string]$entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
        }
        $stdout = Join-Path $script:RunLogDir "$Name.out.log"
        $stderr = Join-Path $script:RunLogDir "$Name.err.log"
        $argumentLine = (($Arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
        $process = Start-Process -FilePath $Executable -ArgumentList $argumentLine -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    } finally {
        foreach ($entry in $original.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
        }
    }

    Start-Sleep -Milliseconds 400
    if ($process.HasExited) { throw "$Name 启动后立即退出，请查看 $stderr" }
    $record = [ordered]@{
        name = $Name
        pid = $process.Id
        startTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
        executable = [System.IO.Path]::GetFullPath($Executable)
        marker = $Marker
        stdout = $stdout
        stderr = $stderr
    }
    $script:Started += [pscustomobject]@{ Process = $process; Record = $record }
    $script:StateRecords += $record
    Write-State
    return $process
}

function Wait-Health([string]$Name, [string]$Url, [System.Diagnostics.Process]$Process, [switch]$Frontend) {
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        if ($Process.HasExited) { throw "$Name 已退出，请查看本次日志目录：$script:RunLogDir" }
        try {
            if ($Frontend) {
                $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
                if ($response.StatusCode -eq 200) { Write-Ok "$Name 已就绪"; return }
            } else {
                $response = Invoke-RestMethod -Uri $Url -TimeoutSec 3
                if ([string]$response.status -eq 'UP') { Write-Ok "$Name 已就绪"; return }
            }
        } catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "$Name 在 $StartupTimeoutSeconds 秒内未就绪，请查看本次日志目录：$script:RunLogDir"
}

function Rollback-CurrentRun {
    $rollbackEntries = @($script:Started)
    [array]::Reverse($rollbackEntries)
    foreach ($entry in $rollbackEntries) {
        try {
            $process = Get-Process -Id $entry.Process.Id -ErrorAction SilentlyContinue
            $cim = if ($null -eq $process) { $null } else { Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)" -ErrorAction SilentlyContinue }
            $matches = $null -ne $process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$entry.Record.startTimeUtcTicks -and
                $null -ne $cim -and [string]$cim.ExecutablePath -eq [string]$entry.Record.executable -and
                (Test-LiteralContains ([string]$cim.CommandLine) ([string]$entry.Record.marker))
            if ($matches) {
                Stop-Process -InputObject $process -Force -ErrorAction SilentlyContinue
                $process.WaitForExit(5000) | Out-Null
            }
        } catch { }
    }
    if (Test-Path -LiteralPath $script:StatePath -PathType Leaf) { Remove-Item -LiteralPath $script:StatePath -Force }
}

try {
    New-Item -ItemType Directory -Path $script:RuntimeRoot -Force | Out-Null
    Protect-PrivatePath $script:RuntimeRoot -Directory
    try {
        $script:LockStream = New-Object System.IO.FileStream($script:LockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw '另一个启动或停止操作正在执行，请稍后重试。'
    }
    Assert-NoExistingRun

    Write-Step '读取本地配置并检查运行环境'
    $javaExe = Resolve-Tool 'java'
    $mavenExe = Resolve-Tool 'mvn'
    $nodeExe = Resolve-Tool 'node'
    $npmExe = Resolve-Tool 'npm'
    Assert-RuntimeVersions $javaExe $mavenExe $nodeExe $npmExe
    $envPath = Join-Path $script:ProjectRoot '.env'
    Import-DotEnv $envPath
    Protect-PrivatePath $envPath

    $gatewayPort = [int](Get-EnvValue 'GATEWAY_PORT' '8080')
    $authPort = [int](Get-EnvValue 'AUTH_SERVICE_PORT' '8081')
    $warehousePort = [int](Get-EnvValue 'WAREHOUSE_SERVICE_PORT' '8082')
    $frontendPort = [int](Get-EnvValue 'FRONTEND_PORT' '5173')
    foreach ($port in @($gatewayPort, $authPort, $warehousePort, $frontendPort)) {
        if ($port -lt 1 -or $port -gt 65535) { throw "端口超出范围：$port" }
    }
    if (@(@($gatewayPort, $authPort, $warehousePort, $frontendPort) | Select-Object -Unique).Count -ne 4) {
        throw '四个应用端口必须互不相同。'
    }
    Assert-PortsAvailable @($gatewayPort, $authPort, $warehousePort, $frontendPort)

    $jwtSecret = Get-EnvValue 'JWT_SECRET'
    $adminPassword = Get-EnvValue 'ADMIN_PASSWORD'
    $knownJwt = @('change-me-to-at-least-32-random-characters', 'firefly-logistics-change-this-jwt-secret-in-production')
    $usesDevDefaults = ($knownJwt -contains $jwtSecret) -or $adminPassword -eq 'Firefly@123'
    if ($usesDevDefaults -and (-not $AllowDevDefaults -or $DataMode -ne 'Demo')) {
        throw '检测到示例 JWT 或管理员密码。仅可显式使用 -DataMode Demo -AllowDevDefaults，并且脚本只绑定回环地址。'
    }
    if ($jwtSecret.Length -lt 32) { throw 'JWT_SECRET 至少需要 32 个字符。' }

    Save-EnvironmentValue 'SERVER_ADDRESS' '127.0.0.1'
    Save-EnvironmentValue 'CORS_ALLOWED_ORIGINS' "http://127.0.0.1:$frontendPort"
    Save-EnvironmentValue 'WAREHOUSE_SECURITY_ENABLED' 'true'
    Save-EnvironmentValue 'AUTH_SERVICE_URL' "http://127.0.0.1:$authPort"
    Save-EnvironmentValue 'WAREHOUSE_SERVICE_URL' "http://127.0.0.1:$warehousePort"
    Save-EnvironmentValue 'VITE_API_BASE_URL' "http://127.0.0.1:$gatewayPort/api"

    Write-Step '检查 MySQL 8 和数据库模式'
    $dbUrl = Get-EnvValue 'DB_URL'
    $mysqlHost = Get-EnvValue 'MYSQL_HOST' '127.0.0.1'
    $mysqlPort = [int](Get-EnvValue 'MYSQL_PORT' '3306')
    $mysqlDatabase = Get-EnvValue 'MYSQL_DATABASE' 'firefly_logistics'
    $dbHost = $mysqlHost
    $dbPort = $mysqlPort
    $database = $mysqlDatabase
    if ($dbUrl) {
        if ($dbUrl -notmatch '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') { throw 'DB_URL 必须是明确的 jdbc:mysql://host:port/database 格式。' }
        $dbHost = $Matches[1].Trim()
        if ($Matches[2]) { $dbPort = [int]$Matches[2] }
        $database = $Matches[3]
        if (-not $dbHost.Equals($mysqlHost, [StringComparison]::OrdinalIgnoreCase) -or $dbPort -ne $mysqlPort -or
                -not $database.Equals($mysqlDatabase, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'DB_URL 与 MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE 不一致；请统一配置，脚本不会猜测应连接哪个数据库。'
        }
    }
    if ($database -notmatch '^[A-Za-z0-9_]+$') { throw '数据库名只能包含字母、数字和下划线。' }
    if ($dbPort -lt 1 -or $dbPort -gt 65535) { throw 'MySQL 端口无效。' }
    $mysqlUser = Get-EnvValue 'MYSQL_USER' 'firefly'
    $mysqlPassword = Get-EnvValue 'MYSQL_PASSWORD'
    $dbUser = Get-EnvValue 'DB_USERNAME' $mysqlUser
    $dbPassword = Get-EnvValue 'DB_PASSWORD' $mysqlPassword
    if (-not $dbUser.Equals($mysqlUser, [StringComparison]::Ordinal) -or $dbPassword -cne $mysqlPassword) {
        throw 'DB_USERNAME/DB_PASSWORD 与 MYSQL_USER/MYSQL_PASSWORD 不一致；请统一配置，脚本不会猜测应使用哪个账号。'
    }
    if ($dbUser -notmatch '^[A-Za-z0-9_]+$') { throw '数据库业务用户名只能包含字母、数字和下划线。' }
    if ([string]::IsNullOrEmpty($dbPassword)) { throw 'DB_PASSWORD 不能为空。' }

    $localDatabaseHosts = @('127.0.0.1', 'localhost', '::1')
    $isLocalDatabase = $localDatabaseHosts -contains $dbHost.ToLowerInvariant()
    $defaultSslMode = if ($isLocalDatabase) { 'DISABLED' } else { 'REQUIRED' }
    $script:DbSslMode = (Get-EnvValue 'MYSQL_SSL_MODE' $defaultSslMode).ToUpperInvariant()
    $allowedSslModes = @('DISABLED', 'REQUIRED', 'VERIFY_CA', 'VERIFY_IDENTITY')
    if ($allowedSslModes -notcontains $script:DbSslMode) { throw 'MYSQL_SSL_MODE 只允许 DISABLED、REQUIRED、VERIFY_CA 或 VERIFY_IDENTITY。' }
    if (-not $isLocalDatabase -and $script:DbSslMode -eq 'DISABLED') { throw '远程 MySQL 必须启用 TLS，MYSQL_SSL_MODE 不能为 DISABLED。' }

    if (-not $dbUrl) {
        $dbUrl = "jdbc:mysql://${dbHost}:$dbPort/$database?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    }
    if (-not $isLocalDatabase -and $dbUrl -match '(?i)(?:^|[?&])(?:useSSL=false|sslMode=(?:DISABLED|PREFERRED))(?:&|$)') {
        throw '远程 DB_URL 禁止关闭 TLS 或使用 PREFERRED 回退模式。'
    }
    if ($dbUrl -match '(?i)(?:^|[?&])sslMode=([^&]+)') {
        if ($Matches[1].ToUpperInvariant() -ne $script:DbSslMode) {
            throw 'DB_URL 的 sslMode 与 MYSQL_SSL_MODE 不一致。'
        }
    } else {
        $separator = if ($dbUrl.Contains('?')) { '&' } else { '?' }
        $dbUrl += "${separator}sslMode=$script:DbSslMode"
    }
    Save-EnvironmentValue 'DB_URL' $dbUrl
    Save-EnvironmentValue 'DB_USERNAME' $dbUser
    Save-EnvironmentValue 'DB_PASSWORD' $dbPassword

    $serverVersionText = Ensure-LocalMySqlRunning $dbHost $dbPort
    if ($serverVersionText -match '(?i)MariaDB') { throw "检测到 MariaDB（$serverVersionText）；本项目要求 Oracle MySQL 8.0.16+。" }
    if ($serverVersionText -notmatch '(\d+)\.(\d+)\.(\d+)') { throw "无法识别 MySQL 版本：$serverVersionText" }
    $serverVersion = New-Object Version([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
    if ($serverVersion -lt [Version]'8.0.16') {
        throw "检测到 MySQL $serverVersionText；项目要求 MySQL 8.0.16+。脚本未修改现有数据库。建议保留 MySQL 5.7，并另装 MySQL 8（可用 3307）或配置远程 MySQL 8。"
    }
    Write-Ok "MySQL $serverVersionText 版本检查通过"

    $mysqlExe = Find-MySqlClient
    if ([string]::IsNullOrWhiteSpace($mysqlExe)) { throw '未找到 mysql.exe；请安装 MySQL 8 Client 并加入 PATH。' }
    Assert-MySqlClientVersion $mysqlExe
    Ensure-Database $mysqlExe $dbHost $dbPort $database $dbUser $dbPassword
    Assert-DatabaseMode $mysqlExe $dbHost $dbPort $database $dbUser $dbPassword
    Write-Ok "数据库连接和 $DataMode 模式检查通过"

    if (-not $SkipBuild) {
        Write-Step '构建后端可执行 JAR'
        Invoke-WithoutApplicationSecrets {
            Push-Location (Join-Path $script:ProjectRoot 'backend')
            try { & $mavenExe '-q' '-DskipTests' 'package'; if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' } }
            finally { Pop-Location }
        }
        Write-Ok '后端构建完成'
    }

    $authJar = Get-ApplicationJar 'auth-service'
    $warehouseJar = Get-ApplicationJar 'warehouse-service'
    $gatewayJar = Get-ApplicationJar 'gateway'

    $viteScript = Join-Path $script:ProjectRoot 'frontend\node_modules\vite\bin\vite.js'
    $installedLock = Join-Path $script:ProjectRoot 'frontend\node_modules\.package-lock.json'
    $packageLock = Join-Path $script:ProjectRoot 'frontend\package-lock.json'
    if (-not (Test-Path -LiteralPath $viteScript -PathType Leaf) -or -not (Test-Path -LiteralPath $installedLock -PathType Leaf) -or
            (Get-Item -LiteralPath $packageLock).LastWriteTimeUtc -gt (Get-Item -LiteralPath $installedLock).LastWriteTimeUtc) {
        Write-Step '按 package-lock 安装前端依赖'
        Invoke-WithoutApplicationSecrets {
            Push-Location (Join-Path $script:ProjectRoot 'frontend')
            try { & $npmExe 'ci' '--no-audit' '--no-fund'; if ($LASTEXITCODE -ne 0) { throw 'npm ci 失败。' } }
            finally { Pop-Location }
        }
    }

    New-Item -ItemType Directory -Path $script:RunLogDir -Force | Out-Null
    $javaEnvironment = New-SanitizedJavaEnvironment

    Write-Step '启动认证服务'
    $authProcess = Start-ManagedProcess 'auth-service' $javaExe @('-Duser.timezone=Asia/Shanghai', '-jar', $authJar, "--server.address=127.0.0.1", "--server.port=$authPort", '--spring.profiles.active=local', '--spring.flyway.enabled=true', '--spring.flyway.out-of-order=false', '--spring.flyway.baseline-on-migrate=true', '--spring.flyway.baseline-version=0', '--spring.flyway.locations=classpath:db/migration') (Join-Path $script:ProjectRoot 'backend') $javaEnvironment $authJar
    Wait-Health '认证服务' "http://127.0.0.1:$authPort/actuator/health" $authProcess

    Write-Step '启动仓储服务'
    $warehouseLocations = if ($DataMode -eq 'Demo') { 'classpath:db/migration,classpath:db/demo' } else { 'classpath:db/migration' }
    $warehouseArguments = @('-Duser.timezone=Asia/Shanghai', '-jar', $warehouseJar, '--server.address=127.0.0.1', "--server.port=$warehousePort", '--spring.flyway.enabled=true', '--spring.flyway.out-of-order=false', '--spring.flyway.baseline-on-migrate=true', '--spring.flyway.baseline-version=0', "--spring.flyway.locations=$warehouseLocations")
    $warehouseProfile = if ($DataMode -eq 'Demo') { 'local,demo' } else { 'local' }
    $warehouseArguments += "--spring.profiles.active=$warehouseProfile"
    $warehouseArguments += '--firefly.security.enabled=true'
    $warehouseEnvironment = $javaEnvironment.Clone()
    $warehouseEnvironment['ADMIN_PASSWORD'] = $null
    $warehouseProcess = Start-ManagedProcess 'warehouse-service' $javaExe $warehouseArguments (Join-Path $script:ProjectRoot 'backend') $warehouseEnvironment $warehouseJar
    Wait-Health '仓储服务' "http://127.0.0.1:$warehousePort/actuator/health" $warehouseProcess

    Write-Step '启动 API 网关'
    $gatewayEnvironment = $javaEnvironment.Clone()
    foreach ($secretName in @('DB_PASSWORD', 'MYSQL_PASSWORD', 'ADMIN_PASSWORD')) { $gatewayEnvironment[$secretName] = $null }
    $gatewayProcess = Start-ManagedProcess 'gateway' $javaExe @('-Duser.timezone=Asia/Shanghai', '-jar', $gatewayJar, '--server.address=127.0.0.1', "--server.port=$gatewayPort", '--spring.profiles.active=local') (Join-Path $script:ProjectRoot 'backend') $gatewayEnvironment $gatewayJar
    Wait-Health 'API 网关' "http://127.0.0.1:$gatewayPort/actuator/health" $gatewayProcess

    Write-Step '启动 Vue 前端'
    $frontendEnvironment = New-SanitizedFrontendEnvironment "http://127.0.0.1:$gatewayPort/api"
    $frontendProcess = Start-ManagedProcess 'frontend' $nodeExe @($viteScript, '--host', '127.0.0.1', '--port', [string]$frontendPort, '--strictPort') (Join-Path $script:ProjectRoot 'frontend') $frontendEnvironment $viteScript
    Wait-Health 'Vue 前端' "http://127.0.0.1:$frontendPort/" $frontendProcess -Frontend

    Write-Host "`nFirefly Logistics 已启动：http://127.0.0.1:$frontendPort" -ForegroundColor Green
    Write-Host "运行日志：$script:RunLogDir"
    if ($usesDevDefaults) {
        Write-Host '本地 Demo 默认账号：admin / Firefly@123（仅绑定 127.0.0.1，请勿用于生产）' -ForegroundColor Yellow
    } else {
        Write-Host '管理员引导密码只在首次创建账号时生效；修改 .env 不会重置已有管理员。' -ForegroundColor Yellow
    }
    Write-Host '停止服务：双击 stop.cmd'
    Restore-Environment
    if (-not $NoBrowser) {
        try { Start-Process "http://127.0.0.1:$frontendPort" | Out-Null }
        catch { Write-Host '服务已启动，但未能自动打开浏览器；请手动访问上方地址。' -ForegroundColor Yellow }
    }
} catch {
    $message = $_.Exception.Message
    Write-Host "`n启动失败：$message" -ForegroundColor Red
    if ($script:Started.Count -gt 0) {
        Write-Host '正在停止本次启动的进程；不会影响其他 Java、Node 或 MySQL 进程。' -ForegroundColor Yellow
        Rollback-CurrentRun
    }
    exit 1
} finally {
    Restore-Environment
    if ($null -ne $script:LockStream) { $script:LockStream.Dispose() }
}
