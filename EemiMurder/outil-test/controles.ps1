# Controles de details des 4 maps construites (escaliers, trappes, portes, supports) + accessibilite.
# Usage : powershell -File outil-test\controles.ps1 [ids separes par des virgules]
param([string]$ids = "bibliotheque,fete,musee,paquebot,hotel,chalet", [string]$out = "$PSScriptRoot\out")
$lv = @{ bibliotheque = "65,72"; fete = "65,66,69,70,77"; musee = "65,66,72"; paquebot = "65"; hotel = "65,71"; chalet = "59,65,71" }
$code = 0
foreach ($id in $ids.Split(",")) {
    Write-Output "=== $id"
    & powershell -File "$PSScriptRoot\test_maps.ps1" $id $lv[$id] $out | Select-String -Pattern "apparitions|Controles de|PROBLEME |Problemes de details|Passe Details|Distances|\(info\)"
    if ($LASTEXITCODE -ne 0) { $code = $LASTEXITCODE }
}
exit $code
