param([string[]]$Ids)
$t = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
if (-not $env:BLOCKS_JSON) { $env:BLOCKS_JSON = "C:\Users\esteb\AppData\Local\Temp\claude\ac16b798c15ad83e\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\gen\reports\blocks.json" }
Set-Location $PSScriptRoot
if (Test-Path classes) { Remove-Item -Recurse -Force classes }
& "$t\javac.exe" --release 21 -encoding UTF-8 -nowarn -d classes (Get-ChildItem -Recurse src -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { exit 1 }
$o = & "$t\java.exe" -Xmx3g -cp classes fr.eemicraft.sheepwars.gen.SwGen ..\src\main\resources\maps out @Ids
$code = $LASTEXITCODE
$o | Where-Object { $_ -notmatch '^PARAMS ' }
foreach ($l in $o) { if ($l -match '^PARAMS (\w+) (.*)$') { [IO.File]::WriteAllText("$PSScriptRoot\out\params_$($Matches[1]).json", $Matches[2], (New-Object Text.UTF8Encoding $false)) } }
exit $code
