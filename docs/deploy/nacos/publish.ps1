# Publish configs in this folder to Nacos (Windows / PowerShell).
#
# Usage:
#   .\publish.ps1                                # default http://127.0.0.1:8848
#   .\publish.ps1 -Server http://nacos:8848      # custom address
#   .\publish.ps1 -DataId byw-common.yml         # publish one file only
#
# Why curl.exe for publishing:
#   PowerShell 5.1 encodes -Body @{} with the system code page, which corrupts non-ASCII
#   content; the Nacos client then fails to parse it SILENTLY and the property source ends
#   up empty (the config looks loaded but never applies). curl sends the raw file bytes.
#
# Rule: keep Nacos config content ASCII-only (see README.md).

param(
    [string]$Server = 'http://127.0.0.1:8848',
    [string]$Group = 'DEFAULT_GROUP',
    [string]$DataId = ''
)

$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
# Recursive: byw-common.yml lives in this folder, per-service byw-<name>.yml live in services/.
# NOTE: keep this script ASCII-only - PowerShell 5.1 reads .ps1 as ANSI and mangles non-ASCII.
if ($DataId) {
    $files = @(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter $DataId)
} else {
    $files = @(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter '*.yml')
}
if ($files.Count -eq 0) { throw "no yml found in $dir" }

foreach ($f in $files) {
    $name = $f.Name
    $raw = [System.IO.File]::ReadAllText($f.FullName)
    if ([regex]::IsMatch($raw, '[^\x00-\x7F]')) {
        Write-Warning "$name contains non-ASCII chars; the nacos client may parse it to EMPTY"
    }
    $curlArgs = @('-s', '-X', 'POST', "$Server/nacos/v1/cs/configs",
                  '--data-urlencode', "dataId=$name",
                  '--data-urlencode', "group=$Group",
                  '--data-urlencode', 'type=yaml',
                  '--data-urlencode', "content@$($f.FullName)")
    $resp = & curl.exe @curlArgs
    if ($LASTEXITCODE -ne 0 -or "$resp" -notmatch 'true') { throw "publish failed: $name -> $resp" }
    Write-Output "published: $name"
}

foreach ($f in $files) {
    $name = $f.Name
    $uri = "$Server/nacos/v1/cs/configs?dataId=$name&group=$Group"
    $back = (Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 15).Content
    if ($back -is [byte[]]) { $back = [System.Text.Encoding]::UTF8.GetString($back) }
    $local = [System.IO.File]::ReadAllText($f.FullName)
    $same = (($back -replace '\s', '') -eq ($local -replace '\s', ''))
    Write-Output "verify: $name chars=$($back.Length) identical=$same"
}
