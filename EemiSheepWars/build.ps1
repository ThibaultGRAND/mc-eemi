$t = "C:\Users\esteb\AppData\Local\Temp\claude\C--Users-esteb-EEMICRAFT\f4935c30-5a79-4234-981b-193c009bffe2\scratchpad\tools"
$env:JAVA_HOME = "$t\jdk\jdk-25.0.5+8"
$env:MAVEN_OPTS = "--enable-native-access=ALL-UNNAMED"
Set-Location $PSScriptRoot
if (Test-Path target) { Remove-Item -Recurse -Force target }
& "$t\mvn\apache-maven-3.9.9\bin\mvn.cmd" -B -q -o package
