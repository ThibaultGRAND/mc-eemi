# Test hors serveur du decor du De a coudre : .\test.ps1 <avant|apres> [rayon] [hauteur]
param([string]$mode = "apres", [string]$r = "", [string]$h = "")
$jdk = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $PSScriptRoot "out"
$cls = Join-Path $out "classes"
New-Item -ItemType Directory -Force $cls | Out-Null
$src = @()
$src += (Get-ChildItem -Recurse "$root\src\main\java\fr\eemicraft\deacoudre\build" -Filter *.java).FullName
$src += "$root\src\main\java\fr\eemicraft\deacoudre\Builder.java"
$src += (Get-ChildItem -Recurse "$PSScriptRoot\fr" -Filter *.java).FullName
& "$jdk\javac.exe" --release 21 -encoding UTF-8 -d $cls $src
if ($LASTEXITCODE -ne 0) { exit 1 }
$args2 = @($mode, $out)
if ($r -ne "") { $args2 += $r; $args2 += $h }
& "$jdk\java.exe" -cp $cls fr.eemicraft.deacoudre.test.TestDecor @args2
