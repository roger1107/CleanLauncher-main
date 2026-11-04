#!/bin/sh
# Runs the pure-Java checks (no Android SDK needed):  sh test.sh
set -e
cd "$(dirname "$0")"
if [ -n "$JAVA_HOME" ]; then JAVAC=$JAVA_HOME/bin/javac; JAVA=$JAVA_HOME/bin/java; else JAVAC=javac; JAVA=java; fi
command -v "$JAVAC" >/dev/null 2>&1 || { echo "error: JDK 17+ not found. Install one or set JAVA_HOME." >&2; exit 1; }
rm -rf build/test && mkdir -p build/test
"$JAVAC" -encoding UTF-8 -d build/test \
    src/com/simple/carlauncher/PicoPlaca.java \
    src/com/simple/carlauncher/DayParts.java \
    src/com/simple/carlauncher/TripStats.java \
    src/com/simple/carlauncher/DocDates.java \
    test/com/simple/carlauncher/*.java
for t in PicoPlacaTest DayPartsTest TripStatsTest DocDatesTest; do
    "$JAVA" -cp build/test com.simple.carlauncher.$t
done
