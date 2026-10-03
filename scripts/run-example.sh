#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <domain> <subscriber-email> <asset-url>" >&2
  exit 2
fi

classes="${TMPDIR:-/tmp}/creator-domain-java-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" learnship.CourseDeliveryExample "$1" "$2" "$3"
