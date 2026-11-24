# Banc de test hors serveur des maps du cache-cache : .\test.ps1 [id de map]
# Construit les maps en memoire, audite les etats (blocks.json 26.3), mesure l'accessibilite et la part des
# blocs de deguisement, ecrit hs_<id>.schem puis lance analyze.py (rendus dans .claude/skills/builder-minecraft/out).
param([string]$only = "")
$tools = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
$json = "C:\Users\esteb\AppData\Local\Temp\claude\ac16b798c15ad83e\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\gen\reports\blocks.json"
if ($env:BLOCKS_JSON) { $json = $env:BLOCKS_JSON }
$paper = "$env:USERPROFILE\.m2\repository\io\papermc\paper\paper-api\26.3.build.218-stable\paper-api-26.3.build.218-stable.jar"
$m2 = "$env:USERPROFILE\.m2\repository"
$paper = "$paper;$m2\org\jetbrains\annotations\26.1.0\annotations-26.1.0.jar;$m2\net\kyori\adventure-api\5.2.0\adventure-api-5.2.0.jar;$m2\net\kyori\adventure-key\5.2.0\adventure-key-5.2.0.jar;$m2\net\kyori\examination-api\1.3.0\examination-api-1.3.0.jar"
$root = Split-Path -Parent $PSScriptRoot
$cls = Join-Path $env:TEMP "test_EemiHideSeek"
$out = Join-Path $env:TEMP "test_EemiHideSeek_out"
if (Test-Path $cls) { Remove-Item -Recurse -Force $cls }
New-Item -ItemType Directory -Force $cls | Out-Null
& "$tools\javac.exe" --release 21 -encoding UTF-8 -nowarn -proc:none -implicit:class -sourcepath "$PSScriptRoot;$root\src\main\java" -d $cls "$PSScriptRoot\fr\eemicraft\hideseek\TestMaps.java"
if ($LASTEXITCODE -ne 0) { exit 1 }
& "$tools\java.exe" -Xmx3g -cp "$cls" fr.eemicraft.hideseek.TestMaps $json $out $only
exit $LASTEXITCODE
