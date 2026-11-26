# Recopie _commun\build\outils\...\Audit.java dans l'outil-audit de chaque plugin (package adapte).
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$audit = Get-Content "$PSScriptRoot\fr\eemicraft\build\Audit.java" -Raw
$enc = New-Object Text.UTF8Encoding $false
foreach ($d in Get-ChildItem $root -Directory -Filter Eemi*) {
  foreach ($f in Get-ChildItem "$($d.FullName)\outil-audit\fr\eemicraft\*\build\Audit.java" -ErrorAction SilentlyContinue) {
    $j = $f.Directory.Parent.Name
    [IO.File]::WriteAllText($f.FullName, $audit.Replace('package fr.eemicraft.build;', "package fr.eemicraft.$j.build;"), $enc)
    "sync $($f.FullName)"
  }
}