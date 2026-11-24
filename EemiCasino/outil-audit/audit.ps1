# Controle automatique hors serveur des maps du plugin : .\audit.ps1 [id de map]
param([string]$only = "")
$tools = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools\jdk\jdk-25.0.5+8\bin"
$json = "C:\Users\esteb\AppData\Local\Temp\claude\ac16b798c15ad83e\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\gen\reports\blocks.json"
if ($env:BLOCKS_JSON) { $json = $env:BLOCKS_JSON }
$paper = "$env:USERPROFILE\.m2\repository\io\papermc\paper\paper-api\26.3.build.218-stable\paper-api-26.3.build.218-stable.jar"
$root = Split-Path -Parent $PSScriptRoot
$cls = Join-Path $env:TEMP ("audit_" + (Split-Path -Leaf $root))
if (Test-Path $cls) { Remove-Item -Recurse -Force $cls }
New-Item -ItemType Directory -Force $cls | Out-Null
$main = Get-ChildItem -Recurse "$PSScriptRoot\fr" -Filter TestAudit.java | Select-Object -First 1
& "$tools\javac.exe" --release 21 -encoding UTF-8 -nowarn -proc:none -implicit:class -sourcepath "$PSScriptRoot;$root\src\main\java" -cp $paper -d $cls $main.FullName
if ($LASTEXITCODE -ne 0) { exit 1 }
$pkg = ($main.FullName.Substring(("$PSScriptRoot\").Length) -replace '\.java$','' -replace '\\','.')
& "$tools\java.exe" -Xmx3g -cp "$cls;$paper" $pkg $json $only
exit $LASTEXITCODE