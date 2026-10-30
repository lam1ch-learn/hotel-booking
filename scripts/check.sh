#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export DEBUG=false TRACE=false
exec mvn -B -Dmaven.repo.local="$PWD/.work/m2" verify "$@"
