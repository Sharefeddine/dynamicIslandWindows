$ErrorActionPreference = 'Stop'
$source = $PSScriptRoot
$build = Join-Path $source 'build'

if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
    throw 'CMake and the Visual Studio C++ build tools with the Windows SDK are required.'
}

cmake -S $source -B $build -A x64
if ($LASTEXITCODE -ne 0) { throw 'CMake configuration failed.' }
cmake --build $build --config Release
if ($LASTEXITCODE -ne 0) { throw 'Windows build failed.' }
ctest --test-dir $build -C Release --output-on-failure
if ($LASTEXITCODE -ne 0) { throw 'Native tests failed.' }
Write-Host "`nBuilt: $build\Release\Island.exe"
