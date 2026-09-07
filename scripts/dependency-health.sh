#!/usr/bin/env bash
set -u
set -o pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

PROGRESS_PREFIX=dependency-health
source scripts/progress.sh

MODE=${1:-local}
MVNW=${MVNW:-./mvnw}
VALIDATION_DIR=${VALIDATION_DIR:-.validation}
VERBOSE=${VERBOSE:-0}
REPORT_DIR="$VALIDATION_DIR/dependency-health"
POLICY_FILE="governance/dependency-health-policy.json"
PYTHON=${PYTHON:-python3}

case "$MODE" in
  local|full) ;;
  *) echo "[dependency-health] FAIL — mode must be 'local' or 'full'"; exit 2 ;;
esac

command -v java >/dev/null 2>&1 || { echo "[dependency-health] FAIL — java is required"; exit 1; }
java_major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
[[ "$java_major" == "25" ]] || { echo "[dependency-health] FAIL — Java 25 is required"; exit 1; }
[[ -x "$MVNW" ]] || { echo "[dependency-health] FAIL — Maven Wrapper is missing or not executable"; exit 1; }
[[ -f "$POLICY_FILE" ]] || { echo "[dependency-health] FAIL — missing $POLICY_FILE"; exit 1; }
if [[ "$MODE" == "full" ]]; then
  command -v "$PYTHON" >/dev/null 2>&1 || {
    echo "[dependency-health] FAIL — python3 is required for external-intelligence analysis"
    exit 1
  }
fi

rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR/effective-pom"

started_at=$(date +%s)
if [[ "$MODE" == "full" ]]; then
  total=10
else
  total=7
fi
current=0
warnings=0

run_stage() {
  local label=$1
  local log=$2
  shift 2
  current=$((current + 1))
  progress_render "$current" "$total" "$label" RUNNING
  if [[ "$VERBOSE" == "1" ]]; then
    "$@" 2>&1 | tee "$log"
    local status=${PIPESTATUS[0]}
  else
    "$@" >"$log" 2>&1
    local status=$?
  fi
  if (( status != 0 )); then
    progress_render "$current" "$total" "$label" FAIL
    progress_finish_line
    echo "[dependency-health] FAIL — $label"
    grep -E "\[ERROR\]|BUILD FAILURE|Caused by:|Rule [0-9]+:|Dependency convergence error" "$log" | tail -40 || true
    echo "[dependency-health] Full log: $log"
    return "$status"
  fi
  progress_render "$current" "$total" "$label" PASS
  return 0
}

run_advisory_stage() {
  local label=$1
  local log=$2
  shift 2
  current=$((current + 1))
  progress_render "$current" "$total" "$label" RUNNING
  if [[ "$VERBOSE" == "1" ]]; then
    "$@" 2>&1 | tee "$log"
    local status=${PIPESTATUS[0]}
  else
    "$@" >"$log" 2>&1
    local status=$?
  fi
  if (( status != 0 )); then
    warnings=$((warnings + 1))
    progress_render "$current" "$total" "$label" WARN
    printf '%s\n' "$status" >"$log.status"
    return 0
  fi
  progress_render "$current" "$total" "$label" PASS
  printf '0\n' >"$log.status"
  return 0
}

run_stage "Deprecated API usage" "$REPORT_DIR/deprecations.log" \
  "$MVNW" -B -DskipTests clean compile || exit $?
if grep -Eq '\[deprecation\]|deprecated and marked for removal|has been deprecated' "$REPORT_DIR/deprecations.log"; then
  progress_finish_line
  echo "[dependency-health] FAIL — deprecated API usage detected"
  grep -E '\[deprecation\]|deprecated and marked for removal|has been deprecated' "$REPORT_DIR/deprecations.log" | tail -40 || true
  echo "[dependency-health] Full log: $REPORT_DIR/deprecations.log"
  exit 1
fi

run_stage "Resolved dependency graph" "$REPORT_DIR/dependency-tree.txt" \
  "$MVNW" -B org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree -Dverbose || exit $?

current=$((current + 1))
progress_render "$current" "$total" "Effective POMs" RUNNING
EFFECTIVE_LOG="$REPORT_DIR/effective-pom.log"
: >"$EFFECTIVE_LOG"
abs_effective_dir=$(cd "$REPORT_DIR/effective-pom" && pwd)
if [[ "$VERBOSE" == "1" ]]; then effective_quiet=(); else effective_quiet=(-q); fi
write_effective_pom() {
  local pom=$1
  local name=$2
  "$MVNW" "${effective_quiet[@]}" -f "$pom" \
    org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom \
    -Doutput="$abs_effective_dir/$name.xml" >>"$EFFECTIVE_LOG" 2>&1
}
if ! write_effective_pom pom.xml root; then
  progress_render "$current" "$total" "Effective POMs" FAIL
  progress_finish_line
  echo "[dependency-health] FAIL — effective POM generation"
  tail -40 "$EFFECTIVE_LOG" || true
  exit 1
fi
while IFS= read -r module; do
  [[ -z "$module" ]] && continue
  safe_name=${module//\//__}
  if ! write_effective_pom "$module/pom.xml" "$safe_name"; then
    progress_render "$current" "$total" "Effective POMs" FAIL
    progress_finish_line
    echo "[dependency-health] FAIL — effective POM generation for $module"
    tail -40 "$EFFECTIVE_LOG" || true
    exit 1
  fi
done < <(sed -n 's:.*<module>\([^<]*\)</module>.*:\1:p' pom.xml)
progress_render "$current" "$total" "Effective POMs" PASS

run_stage "Plugin dependency graph" "$REPORT_DIR/plugin-dependencies.txt" \
  "$MVNW" -B org.apache.maven.plugins:maven-dependency-plugin:3.11.0:resolve-plugins \
  -DexcludeTransitive=false -DexcludeReactor=true || exit $?

run_stage "Dependency convergence" "$REPORT_DIR/convergence.log" \
  "$MVNW" -B org.apache.maven.plugins:maven-enforcer-plugin:3.6.3:enforce \
  -Drules=dependencyConvergence -Denforcer.fail=true || exit $?

run_advisory_stage "Upper-bound drift" "$REPORT_DIR/upper-bound.log" \
  "$MVNW" -B org.apache.maven.plugins:maven-enforcer-plugin:3.6.3:enforce \
  -Drules=requireUpperBoundDeps -Denforcer.fail=true

run_stage "CycloneDX SBOM" "$REPORT_DIR/sbom.log" \
  "$MVNW" -B org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
  -DoutputFormat=json -DoutputName=platform-spring-sbom -DoutputDirectory="$REPORT_DIR" || exit $?

SBOM="$REPORT_DIR/platform-spring-sbom.json"
if [[ ! -s "$SBOM" ]]; then
  progress_finish_line
  echo "[dependency-health] FAIL — CycloneDX SBOM was not produced at $SBOM"
  exit 1
fi

if [[ "$MODE" == "full" ]]; then
  run_advisory_stage "Dependency updates" "$REPORT_DIR/dependency-updates.log" \
    "$MVNW" -B org.codehaus.mojo:versions-maven-plugin:2.21.0:display-dependency-updates \
    -DprocessDependencyManagement=true -DallowSnapshots=false

  run_advisory_stage "Plugin updates" "$REPORT_DIR/plugin-updates.log" \
    "$MVNW" -B org.codehaus.mojo:versions-maven-plugin:2.21.0:display-plugin-updates \
    -DallowSnapshots=false

  current=$((current + 1))
  progress_render "$current" "$total" "External intelligence" RUNNING
  if [[ "$VERBOSE" == "1" ]]; then
    "$PYTHON" scripts/dependency_health.py external \
      --sbom "$SBOM" --plugin-dependencies "$REPORT_DIR/plugin-dependencies.txt" \
      --policy "$POLICY_FILE" --report-dir "$REPORT_DIR" 2>&1 | tee "$REPORT_DIR/external-intelligence.log"
    intel_status=${PIPESTATUS[0]}
  else
    "$PYTHON" scripts/dependency_health.py external \
      --sbom "$SBOM" --plugin-dependencies "$REPORT_DIR/plugin-dependencies.txt" \
      --policy "$POLICY_FILE" --report-dir "$REPORT_DIR" >"$REPORT_DIR/external-intelligence.log" 2>&1
    intel_status=$?
  fi
  if (( intel_status != 0 )); then
    progress_render "$current" "$total" "External intelligence" FAIL
    progress_finish_line
    echo "[dependency-health] FAIL — external intelligence"
    tail -60 "$REPORT_DIR/external-intelligence.log" || true
    echo "[dependency-health] Report: $REPORT_DIR/report.json"
    exit "$intel_status"
  fi
  progress_render "$current" "$total" "External intelligence" PASS
else
  if command -v "$PYTHON" >/dev/null 2>&1; then
    "$PYTHON" scripts/dependency_health.py local \
      --sbom "$SBOM" --plugin-dependencies "$REPORT_DIR/plugin-dependencies.txt" \
      --policy "$POLICY_FILE" --report-dir "$REPORT_DIR" >"$REPORT_DIR/local-analysis.log" 2>&1 || {
        progress_finish_line
        echo "[dependency-health] FAIL — local SBOM policy analysis"
        tail -40 "$REPORT_DIR/local-analysis.log" || true
        exit 1
      }
  else
    cat >"$REPORT_DIR/report.json" <<EOF
{"schema_version":1,"mode":"local","status":"PASS","note":"python3 unavailable; SBOM generated but enriched local policy summary not produced"}
EOF
  fi
fi

progress_finish_line
finished_at=$(date +%s)
elapsed=$((finished_at - started_at))
if (( warnings > 0 )); then
  echo "[dependency-health] WARN — $warnings advisory stage(s); inspect $REPORT_DIR"
fi
echo "[dependency-health] PASS — $(progress_duration "$elapsed")"
echo "[dependency-health] Mode: $MODE"
echo "[dependency-health] Report: $REPORT_DIR/report.json"
echo "[dependency-health] SBOM: $SBOM"
