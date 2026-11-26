#!/usr/bin/env bash
# Outils hors serveur de la fête (à lancer après la compilation Maven : target/classes doit être à jour).
#   bash outil-test/essais.sh show             vérifie les transformations des BlockDisplay / ItemDisplay
#   bash outil-test/essais.sh econ [attente croissance]
#   bash outil-test/essais.sh pack             écrit pack/FetePack.zip
#   bash outil-test/essais.sh render <dossier> [pack]   vidages pour render_iso.py
# Variable JDK : dossier bin d'un JDK 21+ (par défaut le JDK des outils de la session).
set -e
cd "$(dirname "$0")/.."
JDK="${JDK:-$(ls -d "$LOCALAPPDATA"/Temp/claude/*/*/scratchpad/tools/jdk-25*/bin 2>/dev/null | head -1)}"
M2="$USERPROFILE/.m2/repository"
CP="$(cygpath -w target/classes);$(cygpath -w "$M2/io/papermc/paper/paper-api/26.3.build.218-stable/paper-api-26.3.build.218-stable.jar")"
for f in $(find "$M2/org/joml" "$M2/net/kyori" "$M2/org/jetbrains" "$M2/com/google/guava" "$M2/net/md-5" -name '*.jar' 2>/dev/null | grep -v sources); do CP="$CP;$(cygpath -w "$f")"; done
OUT="${TMPDIR:-/tmp}/fete_outils"
rm -rf "$OUT"; mkdir -p "$OUT"
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$(cygpath -w "$OUT")" outil-test/fr/eemicraft/fete/*.java
case "$1" in
  show) "$JDK/java" -cp "$(cygpath -w "$OUT");$CP" fr.eemicraft.fete.TestShow ;;
  seats) "$JDK/java" -cp "$(cygpath -w "$OUT");$CP" fr.eemicraft.fete.TestSeats ;;
  econ) "$JDK/java" -cp "$(cygpath -w "$OUT");$CP" fr.eemicraft.fete.TestEcon ${2:+"$2"} ${3:+"$3"} ;;
  pack) mkdir -p pack; "$JDK/java" -cp "$(cygpath -w "$OUT");$CP" fr.eemicraft.fete.GenPack "$(cygpath -w pack/FetePack.zip)" ;;
  render) "$JDK/java" -Xmx3g -cp "$(cygpath -w "$OUT");$CP" fr.eemicraft.fete.TestRender "$(cygpath -w "$2")" ${3:+"$3"} ;;
  *) echo "usage : essais.sh show|econ|pack|render" ;;
esac
