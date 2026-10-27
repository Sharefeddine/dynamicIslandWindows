param([switch] $Package)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command java -ErrorAction SilentlyContinue) -or -not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'Install JDK 17 or newer and Maven 3.8+, and make sure java and mvn are on PATH.'
}
Push-Location $PSScriptRoot
try {
    & mvn -B clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven build or tests failed.' }
    if ($Package) {
        if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
            throw 'jpackage was not found. Use a full JDK 17 or newer, not a JRE.'
        }
        $inputDir = Join-Path $PSScriptRoot 'target\package-input'
        $outputDir = Join-Path $PSScriptRoot 'target\package'
        New-Item -ItemType Directory -Force -Path $inputDir, $outputDir | Out-Null
        & mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$inputDir"
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy runtime dependencies.' }
        Copy-Item 'target\island-windows-1.0.0-SNAPSHOT.jar' (Join-Path $inputDir 'island-windows.jar')
        $icon = Join-Path $PSScriptRoot 'src\main\resources\com\islandfx\island.ico'
        & jpackage --type app-image --name Island --dest $outputDir --input $inputDir `
            --main-jar island-windows.jar --main-class com.islandfx.Launcher --icon $icon `
            --app-version 1.0.0 --vendor 'Island for Windows' `
            --add-modules 'java.desktop,java.logging,java.xml,java.scripting,java.prefs,java.naming,java.management,jdk.unsupported,jdk.zipfs'
        if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
        Write-Host "`nPortable Windows app: $outputDir\Island\Island.exe"
    } else {
        Write-Host "`nBuild passed. Run: mvn javafx:run"
        Write-Host 'To create a Windows app image, run: .\build-windows.ps1 -Package'
    }
} finally {
    Pop-Location
}
