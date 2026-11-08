#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ "$(uname -s)" != Darwin ]]; then
    echo 'This launcher requires macOS.' >&2
    exit 1
fi
if [[ "$(uname -m)" == arm64 ]] && ! /usr/bin/arch -x86_64 /usr/bin/true; then
    echo 'Install Rosetta: softwareupdate --install-rosetta --agree-to-license' >&2
    exit 1
fi

jdk="$PWD/runtime/zulu11.92.113-ca-jdk11.0.33-macosx_x64/Contents/Home"
if [[ ! -x "$jdk/bin/javac" || ! -d distribution/iresolver/lib ]]; then
    echo 'Run python3 scripts/setup.py first.' >&2
    exit 1
fi

main=ru.polytech.iresolver.Experiment
source_directory=src/main/java
log=output/run.log
if [[ $# -gt 1 ]]; then
    echo 'Usage: bash run.sh [--train|--test]' >&2
    exit 1
fi
case "${1:-}" in
    '') set -- ;;
    --train) ;;
    --test) main=ru.polytech.iresolver.ExperimentTest; source_directory=src; log=output/test.log; set -- ;;
    *) echo 'Usage: bash run.sh [--train|--test]' >&2; exit 1 ;;
esac

library_cache=$(mktemp -d /tmp/iresolver-lib.XXXXXX)
trap 'rm -rf "$library_cache"' EXIT
cp distribution/iresolver/lib/*.jar "$library_cache/"
mkdir -p build/classes output
classpath="$library_cache/*:config:build/classes"
sources=()
while IFS= read -r -d '' source; do
    sources+=("$source")
done < <(find "$source_directory" -name '*.java' -print0)
"$jdk/bin/javac" -J-Dfile.encoding=UTF-8 -encoding UTF-8 -Xlint:unchecked \
    -cp "$classpath" -d build/classes "${sources[@]}"
"$jdk/bin/java" -Xmx3g -Dfile.encoding=UTF-8 -Djava.awt.headless=true \
    -cp "$classpath" "$main" "$@" 2>&1 | tee "$log"
