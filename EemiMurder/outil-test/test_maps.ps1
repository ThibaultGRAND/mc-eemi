# Test hors serveur des maps construites Fete foraine / Grand Musee (rien de ce dossier n'entre dans le jar).
# Usage : powershell -File outil-test\test_maps.ps1 <fete|musee|paquebot|bibliotheque|hotel|chalet> [niveaux des pieds, ex. 65,70] [dossier de sortie]
param([string]$id = "fete", [string]$levels = "65", [string]$out = "$PSScriptRoot\out")
$jdk = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
$root = Split-Path $PSScriptRoot -Parent
$cls = "$PSScriptRoot\classes"
if (Test-Path $cls) { Remove-Item -Recurse -Force $cls }
New-Item -ItemType Directory -Force $cls | Out-Null
$m = "$root\src\main\java\fr\eemicraft\murder"
$src = @(Get-ChildItem "$m\build" -Filter *.java | ForEach-Object FullName)
$src += "$m\Manoir.java", "$m\MapKit.java", "$m\Fete.java", "$m\Musee.java", "$m\Paquebot.java", "$m\Hotel.java", "$m\Chalet.java", "$m\MapDef.java"
$src += "$PSScriptRoot\fr\eemicraft\murder\TestManoir.java", "$PSScriptRoot\fr\eemicraft\murder\TestMaps.java", "$PSScriptRoot\fr\eemicraft\murder\Controles.java"
& "$jdk\javac.exe" --release 21 -encoding UTF-8 -nowarn -d $cls $src
if ($LASTEXITCODE -ne 0) { exit 1 }
& "$jdk\java.exe" -Xmx3g -cp $cls fr.eemicraft.murder.TestMaps $id $out $levels
exit $LASTEXITCODE
