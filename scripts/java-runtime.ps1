$script:springBoardRuntimeProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Get-SpringBoardJavaMajor {
    param([string]$Command)
    if (-not $Command -or -not (Test-Path -LiteralPath $Command)) { return 0 }
    try {
        $versionText = (& $Command -version 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0 -or $versionText -notmatch '(?:version\s+"|javac\s+)(\d+)(?:\.(\d+))?') { return 0 }
        $major = [int]$Matches[1]
        if ($major -eq 1) { $major = [int]$Matches[2] }
        return $major
    } catch { return 0 }
}

function Get-SpringBoardJava {
    param([switch]$RequireCompiler)
    $commandName = if ($RequireCompiler) { 'javac' } else { 'java' }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME "bin/$commandName.exe" }
    $installed = Get-Command $commandName -ErrorAction SilentlyContinue
    if ($installed) { $candidates += $installed.Source }
    $portableRoot = Join-Path $script:springBoardRuntimeProjectRoot '.tools/jdk17'
    $jdkDirectory = Join-Path $portableRoot 'jdk-17.0.20.1+1'
    $portableCommand = Join-Path $jdkDirectory "bin/$commandName.exe"
    $candidates += $portableCommand
    foreach ($candidate in $candidates) {
        if ((Get-SpringBoardJavaMajor $candidate) -lt 17) { continue }
        if ($RequireCompiler) {
            $matchingJava = Join-Path (Split-Path -Parent $candidate) 'java.exe'
            if ((Get-SpringBoardJavaMajor $matchingJava) -lt 17) { continue }
        }
        return $candidate
    }

    # This lookup intentionally prepares a portable runtime if Java 17+ is unavailable.
    # It writes only under this project's .tools folder and never installs system-wide.
    $archivePath = Join-Path $portableRoot 'OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip'
    $expectedHash = 'E53A79C3C3D86865BD7E787903884331068E71321714FFD44F145785AFFC7CB0'
    $downloadUri = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip'
    Write-Host 'Java 17+ was not found. Preparing a portable Temurin JDK inside .tools/jdk17.'
    New-Item -ItemType Directory -Path $portableRoot -Force | Out-Null
    $verifiedArchive = (Test-Path -LiteralPath $archivePath) -and
        ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -eq $expectedHash)
    if (-not $verifiedArchive) {
        Write-Host 'Downloading the official Temurin 17.0.20.1+1 archive (about 191 MB; first run requires internet)...'
        $originalProtocol = [Net.ServicePointManager]::SecurityProtocol
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -UseBasicParsing -Uri $downloadUri -OutFile $archivePath
        } finally { [Net.ServicePointManager]::SecurityProtocol = $originalProtocol }
    }
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw 'Temurin archive SHA256 checksum mismatch. The archive will not be extracted or executed.'
    }
    Write-Host 'Temurin archive SHA256 verified. Extracting the portable JDK...'
    Expand-Archive -LiteralPath $archivePath -DestinationPath $portableRoot -Force
    if ((Get-SpringBoardJavaMajor $portableCommand) -lt 17) { throw 'The portable JDK could not be started.' }
    Write-Host 'Portable JDK is ready. Windows Java settings have not been changed.'
    return $portableCommand
}
