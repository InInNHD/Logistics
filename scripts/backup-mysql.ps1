param([string]$OutputDirectory = "")

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$projectRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
Get-Content (Join-Path $projectRoot ".env") | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2].Trim() }
}

$clientPath = $values["MYSQL_CLIENT_PATH"]
$dumpPath = if ($clientPath) { Join-Path (Split-Path $clientPath) "mysqldump.exe" } else { (Get-Command mysqldump.exe).Source }
if (-not (Test-Path -LiteralPath $dumpPath -PathType Leaf)) { throw "mysqldump.exe was not found; configure MYSQL_CLIENT_PATH." }

$backupRoot = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $projectRoot "backups" }
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$database = $values["MYSQL_DATABASE"]
$target = Join-Path $backupRoot ("{0}-{1}.sql" -f $database, (Get-Date -Format "yyyyMMdd-HHmmss"))
$defaultsFile = [System.IO.Path]::GetTempFileName()
$completed = $false
try {
    $content = "[client]`r`nhost=$($values['MYSQL_HOST'])`r`nport=$($values['MYSQL_PORT'])`r`nuser=$($values['MYSQL_USER'])`r`npassword=$($values['MYSQL_PASSWORD'])`r`n"
    [System.IO.File]::WriteAllText($defaultsFile, $content, (New-Object System.Text.UTF8Encoding($false)))
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().Name, "FullControl", "Allow")))
    Set-Acl -LiteralPath $defaultsFile -AclObject $acl
    & $dumpPath "--defaults-extra-file=$defaultsFile" --single-transaction --no-tablespaces --routines --triggers --set-gtid-purged=OFF --result-file=$target $database
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE." }
    Set-Acl -LiteralPath $target -AclObject $acl
    $completed = $true
    Write-Host "Backup completed: $target" -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $defaultsFile -Force -ErrorAction SilentlyContinue
    if (-not $completed -and (Test-Path -LiteralPath $target -PathType Leaf)) {
        Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    }
}
