$ErrorActionPreference="Stop"
$repo=Split-Path $PSScriptRoot -Parent
foreach($path in Get-ChildItem -Path (Join-Path $repo "scripts"),(Join-Path $repo "tests") -Filter *.ps1) {
    $tokens=$null; $errors=$null
    [Management.Automation.Language.Parser]::ParseFile($path.FullName,[ref]$tokens,[ref]$errors) | Out-Null
    if($errors.Count) { throw ($errors | Out-String) }
}
& python -m unittest discover -s (Join-Path $repo "tests") -v
if($LASTEXITCODE -ne 0){throw "Manifest gates failed"}
Write-Host "PowerShell parse and manifest gates passed."
