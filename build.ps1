param([switch]$SkipTests, [string]$MavenHome = $env:SPRING_BOARD_MAVEN_HOME, [string]$Repository = $env:SPRING_BOARD_MAVEN_REPOSITORY)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
. (Join-Path $projectRoot 'scripts/java-runtime.ps1')
$compiler = Get-SpringBoardJava -RequireCompiler
$java = Get-SpringBoardJava
$toolRoot = Join-Path $projectRoot '.tools'
$mavenVersion = '3.9.16'
$expectedHash = 'ED41650D42485CFC243FAD22158CAF9CBB5DC408CE7A09DDB94DD42A019DE929CA43065BFA450612CF12BF78B5CAFA3884B96C090DE326FF590448C933454AF3'
if (-not $MavenHome) { $MavenHome = Join-Path $toolRoot "apache-maven-$mavenVersion" }
if (-not $Repository) { $Repository = Join-Path $toolRoot 'repository' }
$mavenCommand = Join-Path $MavenHome 'bin/mvn.cmd'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath $mavenCommand)) {
    $defaultHome = Join-Path $toolRoot "apache-maven-$mavenVersion"
    if ([IO.Path]::GetFullPath($MavenHome) -ne [IO.Path]::GetFullPath($defaultHome)) { throw 'SPRING_BOARD_MAVEN_HOME does not contain bin/mvn.cmd.' }
    $archivePath = Join-Path $toolRoot "apache-maven-$mavenVersion-bin.zip"
    Write-Host 'Preparing Maven (first build requires an internet connection)...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip" -OutFile $archivePath
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA512).Hash -ne $expectedHash) { throw 'Maven distribution checksum mismatch.' }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolRoot -Force
}
$originalOptions = $env:MAVEN_OPTS
$originalJavaHome = $env:JAVA_HOME
Push-Location $projectRoot
try {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $compiler)
    $env:MAVEN_OPTS = "$originalOptions -Dfile.encoding=UTF-8"
    $arguments = @('-B', '-ntp', "-Dmaven.repo.local=$Repository", 'clean', 'verify', 'dependency:copy-dependencies', '-DincludeScope=test', '-DoutputDirectory=target/local-lib')
    if ($SkipTests) { $arguments += '-DskipTests' }
    & $mavenCommand @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; inspect the Maven output above.' }
    $localRoot = Join-Path $projectRoot 'local'
    $libraryRoot = Join-Path $localRoot 'lib'
    $classRoot = Join-Path $localRoot 'classes/local'
    New-Item -ItemType Directory -Path $libraryRoot, $classRoot -Force | Out-Null
    Get-ChildItem -LiteralPath $libraryRoot -File -Filter '*.jar' | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Get-ChildItem -LiteralPath $classRoot -File -Filter '*.class' | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Copy-Item -LiteralPath (Join-Path $projectRoot 'target/spring-board.war') -Destination (Join-Path $localRoot 'spring-board.war') -Force
    # The WAR contains application libraries; the launcher needs only the container and JSP compiler.
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target/local-lib') -File -Filter '*.jar' |
        Where-Object { $_.Name -like 'tomcat-*.jar' -or $_.Name -like 'ecj-*.jar' -or $_.Name -like 'jakarta.servlet-api-*.jar' -or $_.Name -like 'jakarta.servlet.jsp-api-*.jar' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $libraryRoot -Force }
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target/test-classes/local') -File |
        Where-Object { $_.Name -like 'BoardServer*.class' -or $_.Name -like 'DevServer*.class' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $classRoot -Force }
    Write-Host 'Build complete: target/spring-board.war and the local launcher have been updated.'
} finally {
    Pop-Location
    $env:MAVEN_OPTS = $originalOptions
    $env:JAVA_HOME = $originalJavaHome
}
