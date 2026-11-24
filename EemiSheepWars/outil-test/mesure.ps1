# Mesure complete des maps : sw_tool (parcours, vide, vues) + analyze.py du skill, puis tableau resume.
param([string[]]$Ids = @('pirates', 'ruines', 'spatiale'), [switch]$NoImg)
Set-Location $PSScriptRoot
$an = "$PSScriptRoot\..\..\.claude\skills\builder-minecraft\outils\analyze.py"
$a = @($Ids); if ($NoImg) { $a += '--noimg' }
python -I sw_tool.py @a | Out-Null
New-Item -ItemType Directory -Force out\analyse | Out-Null
foreach ($i in $Ids) {
  Copy-Item "out\sw_$i.schem" "out\analyse\swa_$i.schem" -Force
  python -I $an "out\analyse\swa_$i.schem" 24 | Out-Null
}
python -I resume.py rose vert vaisseau @Ids
