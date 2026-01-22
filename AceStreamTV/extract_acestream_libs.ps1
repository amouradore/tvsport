<# 
.SYNOPSIS
    AceStream APK Library Extractor for Android Project Integration
    
.DESCRIPTION
    This script extracts native .so libraries from an AceStream APK file
    and copies them to the correct jniLibs folder structure for Android Studio.
    
.PARAMETER ApkPath
    Path to the AceStream APK file
    
.PARAMETER ProjectPath
    Path to the Android project root (containing app folder)
    
.EXAMPLE
    .\extract_acestream_libs.ps1 -ApkPath "C:\Downloads\acestream.apk" -ProjectPath "C:\Projects\AceStreamTV"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$ApkPath,
    
    [Parameter(Mandatory=$false)]
    [string]$ProjectPath = "."
)

# Colors for output
function Write-Success { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Info { param($msg) Write-Host $msg -ForegroundColor Cyan }
function Write-Warning { param($msg) Write-Host $msg -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host $msg -ForegroundColor Red }

Write-Info "============================================"
Write-Info "  AceStream APK Library Extractor"
Write-Info "============================================"
Write-Host ""

# Check if APK exists
if (-not (Test-Path $ApkPath)) {
    Write-Error "APK file not found: $ApkPath"
    Write-Info ""
    Write-Info "Please download AceStream Engine APK from:"
    Write-Info "  1. https://apkmirror.com (search 'Ace Stream Engine')"
    Write-Info "  2. https://apkpure.com (search 'Ace Stream Media')"
    Write-Info "  3. Official site: https://acestream.org/products/android"
    exit 1
}

# Create temp extraction folder
$TempDir = Join-Path $env:TEMP "acestream_extract_$(Get-Random)"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

Write-Info "Extracting APK to temporary folder..."

try {
    # APK is just a ZIP file, extract it
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ApkPath, $TempDir)
    Write-Success "APK extracted successfully!"
} catch {
    Write-Error "Failed to extract APK: $_"
    Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}

# Find lib folder in extracted APK
$LibFolder = Join-Path $TempDir "lib"
if (-not (Test-Path $LibFolder)) {
    Write-Error "No 'lib' folder found in APK. This APK may not contain native libraries."
    Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}

# Get available architectures
$Archs = Get-ChildItem -Path $LibFolder -Directory | Select-Object -ExpandProperty Name
Write-Info "Found architectures: $($Archs -join ', ')"

# Target jniLibs folder
$JniLibsPath = Join-Path $ProjectPath "app\src\main\jniLibs"
New-Item -ItemType Directory -Path $JniLibsPath -Force | Out-Null

# Copy libraries for each architecture
$CopiedCount = 0
foreach ($Arch in $Archs) {
    $SourceArch = Join-Path $LibFolder $Arch
    $DestArch = Join-Path $JniLibsPath $Arch
    
    New-Item -ItemType Directory -Path $DestArch -Force | Out-Null
    
    $SoFiles = Get-ChildItem -Path $SourceArch -Filter "*.so"
    Write-Info "Copying $($SoFiles.Count) libraries for $Arch..."
    
    foreach ($SoFile in $SoFiles) {
        Copy-Item -Path $SoFile.FullName -Destination $DestArch -Force
        $CopiedCount++
        Write-Host "  + $($SoFile.Name)"
    }
}

# Find important AceStream libraries
$ImportantLibs = @(
    "libacestream*.so",
    "libpython*.so",
    "libtorrent*.so",
    "libssl*.so",
    "libcrypto*.so"
)

Write-Host ""
Write-Info "Scanning for key AceStream libraries..."
$FoundLibs = @()
foreach ($Pattern in $ImportantLibs) {
    $Found = Get-ChildItem -Path $JniLibsPath -Recurse -Filter $Pattern -ErrorAction SilentlyContinue
    if ($Found) {
        $FoundLibs += $Found[0].Name
        Write-Success "  Found: $($Found[0].Name)"
    }
}

# Copy any additional assets (python files, configs)
$AssetsSource = Join-Path $TempDir "assets"
if (Test-Path $AssetsSource) {
    $AssetsDestBase = Join-Path $ProjectPath "app\src\main\assets"
    $AceStreamAssets = Join-Path $AssetsDestBase "acestream"
    New-Item -ItemType Directory -Path $AceStreamAssets -Force | Out-Null
    
    Write-Info "Copying AceStream assets..."
    
    # Copy engine-related assets
    $AssetPatterns = @("python*", "*.py", "engine*", "config*", "bootstrap*")
    foreach ($Pattern in $AssetPatterns) {
        $AssetFiles = Get-ChildItem -Path $AssetsSource -Filter $Pattern -Recurse -ErrorAction SilentlyContinue
        foreach ($Asset in $AssetFiles) {
            $RelPath = $Asset.FullName.Substring($AssetsSource.Length)
            $DestPath = Join-Path $AceStreamAssets $RelPath
            $DestDir = Split-Path $DestPath -Parent
            New-Item -ItemType Directory -Path $DestDir -Force -ErrorAction SilentlyContinue | Out-Null
            Copy-Item -Path $Asset.FullName -Destination $DestPath -Force
            Write-Host "  + $($Asset.Name)"
        }
    }
}

# Cleanup
Write-Info "Cleaning up temporary files..."
Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue

# Summary
Write-Host ""
Write-Success "============================================"
Write-Success "  Extraction Complete!"
Write-Success "============================================"
Write-Host ""
Write-Info "Libraries copied: $CopiedCount"
Write-Info "Architectures: $($Archs -join ', ')"
Write-Info "Output folder: $JniLibsPath"
Write-Host ""
Write-Warning "NEXT STEPS:"
Write-Host "  1. Open Android Studio and sync Gradle"
Write-Host "  2. The native libraries are now in jniLibs/"
Write-Host "  3. Update AceStreamService.kt to load native libraries"
Write-Host ""
Write-Info "To load the libraries in Kotlin, add this to AceStreamService:"
Write-Host @"
    companion object {
        init {
            System.loadLibrary("acestream")
            // Add other required libraries
        }
    }
"@
