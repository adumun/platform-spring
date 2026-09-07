#!/usr/bin/env bash
set -u
set -o pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

MVNW=${MVNW:-./mvnw}
VALIDATION_DIR=${VALIDATION_DIR:-.validation}
VERBOSE=${VERBOSE:-0}
PUBLISHED=${PUBLISHED:-0}
VERSION=${VERSION:-0.1.0-alpha.0}
M2_REPO="$ROOT_DIR/$VALIDATION_DIR/onboarding-m2"
LOG_DIR="$ROOT_DIR/$VALIDATION_DIR/onboarding"

CASES=(
  rest-minimal
  security-custom-decoder
  security-oidc
  observability-minimal
  resilience-dependency
  full-stack-service
)

command -v java >/dev/null 2>&1 || { echo "[onboarding] FAIL — java is not installed or not on PATH"; exit 1; }
test -x "$MVNW" || { echo "[onboarding] FAIL — Maven Wrapper ./mvnw is missing or not executable"; exit 1; }

java_major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
[[ "$java_major" == "25" ]] || { echo "[onboarding] FAIL — Java 25 is required"; exit 1; }

mkdir -p "$M2_REPO" "$LOG_DIR"
rm -rf "$M2_REPO/io/adumun/platform"
rm -rf "$LOG_DIR"/*

started_at=$(date +%s)
step=0
total=${#CASES[@]}
if [[ "$PUBLISHED" != "1" ]]; then
  total=$((total + 1))
fi

run_maven() {
  local label=$1
  local log_file=$2
  shift 2

  if [[ "$VERBOSE" == "1" ]]; then
    echo "[onboarding] $label"
    "$MVNW" -B -Dmaven.repo.local="$M2_REPO" "$@" 2>&1 | tee "$log_file"
    return ${PIPESTATUS[0]}
  fi

  "$MVNW" -B -Dmaven.repo.local="$M2_REPO" "$@" >"$log_file" 2>&1
}

show_failure() {
  local log_file=$1
  echo "[onboarding] Diagnostic:"
  grep -E "APPLICATION FAILED TO START|Description:|Action:|Caused by:|\[ERROR\]|Tests run:|BUILD FAILURE|COMPILATION ERROR" "$log_file" | tail -40 || true
  echo "[onboarding] Full log: $log_file"
  echo "[onboarding] Re-run with full output: make onboarding VERBOSE=1"
}

if [[ "$PUBLISHED" != "1" ]]; then
  step=$((step + 1))
  echo "[onboarding] [$step/$total] candidate artifacts → isolated Maven repository"
  install_log="$LOG_DIR/00-install-candidate.log"
  if ! run_maven "installing candidate artifacts" "$install_log" -DskipTests install; then
    echo "[onboarding] FAIL — candidate artifact installation"
    show_failure "$install_log"
    exit 1
  fi
  echo "[onboarding] [$step/$total] candidate artifacts — PASS"
else
  echo "[onboarding] Published-artifact mode — version $VERSION"
  echo "[onboarding] ADÜMÜN artifacts will not be installed from this checkout"
fi

case_index=0
for case_name in "${CASES[@]}"; do
  case_index=$((case_index + 1))
  step=$((step + 1))
  case_dir="$ROOT_DIR/onboarding/$case_name"
  log_file="$LOG_DIR/$(printf '%02d' "$case_index")-$case_name.log"

  echo "[onboarding] [$step/$total] $case_name — RUNNING"
  if run_maven "$case_name" "$log_file" -f "$case_dir/pom.xml" -Dadumun.platform.version="$VERSION" test; then
    echo "[onboarding] [$step/$total] $case_name — PASS"
  else
    echo "[onboarding] [$step/$total] $case_name — FAIL"
    show_failure "$log_file"
    exit 1
  fi
done

finished_at=$(date +%s)
elapsed=$((finished_at - started_at))
echo "[onboarding] PASS — ${#CASES[@]} isolated consumer projects, ${elapsed}s"
echo "[onboarding] Logs: $LOG_DIR"
