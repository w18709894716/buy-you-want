# Generate / inspect jasypt ENC(...) values for config secrets.
#
# Usage:
#   .\encrypt.ps1 -Text 'root'                 # -> ENC(...)
#   .\encrypt.ps1 -Text 'ENC(...)' -Mode decrypt
#
# Master key: taken from env var JASYPT_ENCRYPTOR_PASSWORD; if unset, falls back to
#   %USERPROFILE%\.byw-jasypt.key   (keep that file OUT of the repo)

param(
    [Parameter(Mandatory = $true)][string]$Text,
    [ValidateSet('encrypt', 'decrypt')][string]$Mode = 'encrypt'
)

$ErrorActionPreference = 'Stop'

if (-not $env:JASYPT_ENCRYPTOR_PASSWORD) {
    $keyFile = Join-Path $env:USERPROFILE '.byw-jasypt.key'
    if (Test-Path $keyFile) {
        $env:JASYPT_ENCRYPTOR_PASSWORD = (Get-Content $keyFile -Raw).Trim()
        Write-Host "master key: loaded from $keyFile"
    } else {
        throw "JASYPT_ENCRYPTOR_PASSWORD is not set and $keyFile does not exist"
    }
}

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$module = 'byw-common/byw-common-core'
Push-Location $root
try {
    # 注意：-Dmdep.outputFile=... 必须整体加引号，否则 PowerShell 5.1 会在 '.' 处把参数拆开
    mvn -q "-pl" $module "-DskipTests" compile dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
    if ($LASTEXITCODE -ne 0) { throw 'maven build failed' }
    $classes = Join-Path $root "$module/target/classes"
    $deps = (Get-Content (Join-Path $root "$module/target/cp.txt") -Raw).Trim()
    $cp = "$classes;$deps"
    java -cp $cp com.byw.common.core.crypto.JasyptEncryptTool $Mode $Text
} finally {
    Pop-Location
}
