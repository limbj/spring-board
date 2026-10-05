param([ValidateRange(1, 65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'scripts/java-runtime.ps1')
$javaCommand = Get-SpringBoardJava
$warPath = Join-Path $projectRoot 'local/spring-board.war'
$classPath = (Join-Path $projectRoot 'local/classes') + ';' + (Join-Path $projectRoot 'local/lib/*')
if (-not (Test-Path -LiteralPath $warPath)) {
    Write-Host 'The local bundle is missing. Building it first...'
    & (Join-Path $projectRoot 'build.ps1')
}
Push-Location $projectRoot
try {
    & $javaCommand '-Dfile.encoding=UTF-8' -cp $classPath local.DevServer $projectRoot "$Port"
    if ($LASTEXITCODE -ne 0) { throw 'The server stopped with an error. Check its console output.' }
} finally { Pop-Location }
