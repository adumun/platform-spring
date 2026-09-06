#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0.
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script, version 3.3.4
set -euf

trim() { printf "%s" "${1}" | tr -d '[:space:]'; }
die() { printf '%s\n' "$1" >&2; exit 1; }
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf '%x\n' "$h"
}

scriptDir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
while IFS="=" read -r key value; do
  case "${key-}" in
    distributionUrl) distributionUrl=$(trim "${value-}") ;;
    distributionSha256Sum) distributionSha256Sum=$(trim "${value-}") ;;
  esac
done < "$scriptDir/.mvn/wrapper/maven-wrapper.properties"

[ -n "${distributionUrl-}" ] || die "cannot read distributionUrl from .mvn/wrapper/maven-wrapper.properties"
case "${distributionUrl##*/}" in
  *-bin.zip) ;;
  *) die "distributionUrl must point to a Maven *-bin.zip distribution" ;;
esac

[ -z "${MVNW_REPOURL-}" ] || distributionUrl="$MVNW_REPOURL/org/apache/maven/${distributionUrl#*/org/apache/maven/}"
name="${distributionUrl##*/}"
base="${name%.zip}"
base="${base%-bin}"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$base/$(hash_string "$distributionUrl")"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  tmp="$(mktemp -d)" || die "cannot create temporary directory"
  trap 'rm -rf "$tmp"' EXIT HUP INT TERM
  archive="$tmp/$name"
  mkdir -p "${MAVEN_HOME%/*}"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$distributionUrl" -o "$archive"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$distributionUrl" -O "$archive"
  else
    die "curl or wget is required for the first Maven Wrapper run"
  fi
  if [ -n "${distributionSha256Sum-}" ]; then
    echo "$distributionSha256Sum  $archive" | sha256sum -c - >/dev/null
  fi
  unzip -q "$archive" -d "$tmp"
  extracted="$tmp/$base"
  [ -d "$extracted" ] || die "Maven distribution directory $base not found"
  rm -rf "$MAVEN_HOME"
  mv "$extracted" "$MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
