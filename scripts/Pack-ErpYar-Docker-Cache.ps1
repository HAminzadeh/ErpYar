#requires -Version 5.1
[CmdletBinding()]
param(
  [string]$RepoPath = (Get-Location).Path,
  [string]$OutputPath = (Join-Path $HOME 'Downloads\ErpYar-Docker-Cache.zip')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = (Resolve-Path -LiteralPath $RepoPath).ProviderPath
$dir = Join-Path $root '.local\docker-images'
$manifestPath = Join-Path $dir 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
  throw 'No image manifest. Run ErpYarLauncher --cache-export on this machine first.'
}
foreach ($path in @($dir, $manifestPath)) {
  $item = Get-Item -LiteralPath $path -Force
  if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Refusing reparse point: $path" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or @($manifest.images).Count -lt 1) { throw 'Invalid or empty cache manifest.' }
$archives = @()
foreach ($entry in @($manifest.images)) {
  $name = [string]$entry.tarFile
  if ($name -notmatch '^postgres-16-alpine-linux-(amd64|arm64)\.tar$') { throw "Unsafe archive name in manifest: $name" }
  $file = Join-Path $dir $name
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing cache archive: $name" }
  $item = Get-Item -LiteralPath $file -Force
  if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Refusing reparse point: $name" }
  if ($item.Length -ne [long]$entry.sizeBytes) { throw "Archive size mismatch: $name" }
  $sha = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
  if ($sha -ine [string]$entry.sha256) { throw "SHA256 mismatch; archive not packaged: $name" }
  $archives += [pscustomobject]@{ Path = $file; Name = $name }
}
if (Test-Path -LiteralPath $OutputPath) { throw "Output already exists (will not overwrite): $OutputPath" }
$outputDir = Split-Path -Path $OutputPath -Parent
if (-not (Test-Path -LiteralPath $outputDir -PathType Container)) { throw "Output directory does not exist: $outputDir" }
$archive = [IO.Compression.ZipFile]::Open($OutputPath, [IO.Compression.ZipArchiveMode]::Create)
try {
  [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $manifestPath, 'docker-images/manifest.json', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
  foreach ($entry in $archives) {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $entry.Path, ('docker-images/' + $entry.Name), [IO.Compression.CompressionLevel]::Optimal) | Out-Null
  }
} catch {
  $archive.Dispose()
  Remove-Item -LiteralPath $OutputPath -Force -ErrorAction SilentlyContinue
  throw
} finally { $archive.Dispose() }
Write-Host "Offline cache package created: $OutputPath" -ForegroundColor Green
Write-Host 'On destination: extract docker-images/ to .local/docker-images/ then run ErpYarLauncher --cache-import --offline.'
