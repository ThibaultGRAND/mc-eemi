# Test hors serveur de la map Manoir-bibliotheque (rien de ce dossier n'entre dans le jar).
# Usage : powershell -File outil-test\test.ps1 [dossier de sortie]
param([string]$out = "$PSScriptRoot\out")
$jdk = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
$root = Split-Path $PSScriptRoot -Parent
$cls = "$PSScriptRoot\classes"
if (Test-Path $cls) { Remove-Item -Recurse -Force $cls }
New-Item -ItemType Directory -Force $cls | Out-Null
$src = @(Get-ChildItem "$root\src\main\java\fr\eemicraft\murder\build" -Filter *.java | ForEach-Object FullName)
$src += "$root\src\main\java\fr\eemicraft\murder\Manoir.java"
$src += "$PSScriptRoot\fr\eemicraft\murder\TestManoir.java"
& "$jdk\javac.exe" --release 21 -encoding UTF-8 -nowarn -d $cls $src
if ($LASTEXITCODE -ne 0) { exit 1 }
& "$jdk\java.exe" -cp $cls fr.eemicraft.murder.TestManoir $out
exit $LASTEXITCODE
