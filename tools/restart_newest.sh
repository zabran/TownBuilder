#!/bin/bash
# Waits until nobody is on the test server, stops it, puts in the NEWEST Falloutak and TownBuilder jars and starts it again
# (this script becomes the server's process). RCON via TownBuilder's tools/rcon.py (its password from ~/.secrets, never printed).
SRV=/d/LollyWood-work/mc-test-server
RCON="python -I /c/Users/Marek/IdeaProjects/TownBuilder/tools/rcon.py"
while true; do
  out=$($RCON list 2>&1 | tail -1)
  echo "$(date +%H:%M:%S) $out"
  case "$out" in
    *"There are 0 of"*|*"Connection refused"*|*"ConnectionRefused"*|*"closed"*) break ;;
  esac
  sleep 30
done
$RCON stop >/dev/null 2>&1
for i in $(seq 1 60); do
  if ! $RCON list >/dev/null 2>&1; then break; fi
  sleep 2
done
sleep 5
cd "$SRV" || exit 1
mkdir -p old-plugins
newest() { ls -t "$1"/build/libs/"$2"-*.jar 2>/dev/null | grep -v -- '-plain' | head -1; }
FA=$(newest /c/Users/Marek/IdeaProjects/Falloutak Falloutak)
TB=$(newest /c/Users/Marek/IdeaProjects/TownBuilder TownBuilder)
mv plugins/Falloutak-*.jar plugins/TownBuilder-*.jar old-plugins/ 2>/dev/null
rm -rf plugins/update
cp "$FA" "$TB" plugins/
echo "$(date +%H:%M:%S) swapped: $(ls plugins | tr '\n' ' ')"
"C:/Users/Marek/.jdks/jdk-21.0.13+9/bin/java" -Xms1G -Xmx3G -jar paper-1.21.11-132.jar --nogui > server-run.log 2>&1
