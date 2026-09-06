.PHONY: deps validate test package

MVNW := ./mvnw
VALIDATION_DIR := .validation
VALIDATION_LOG := $(VALIDATION_DIR)/validate.log

JAVA_MAJOR := $(shell java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')

deps:
	@command -v java >/dev/null 2>&1 || { echo "ERROR: java is not installed or not on PATH"; exit 1; }
	@test "$(JAVA_MAJOR)" = "25" || { echo "ERROR: Java 25 is required; found $$(java -version 2>&1 | head -1)"; exit 1; }
	@test -x $(MVNW) || { echo "ERROR: Maven Wrapper ./mvnw is missing or not executable"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "ERROR: docker CLI is not installed or not on PATH"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon is not available. Start Docker before running validation."; exit 1; }
	@echo "[deps] OK — Java 25, Maven Wrapper, Docker daemon"

validate: deps
	@if [ "$(VERBOSE)" = "1" ]; then \
		echo "[validate] Running full Maven verification (verbose)"; \
		$(MVNW) -B clean verify; \
	else \
		mkdir -p $(VALIDATION_DIR); \
		echo "[validate] Running compile + tests + package"; \
		if $(MVNW) -B clean verify >$(VALIDATION_LOG) 2>&1; then \
			echo "[validate] PASS"; \
			echo "[validate] Full log: $(VALIDATION_LOG)"; \
		else \
			status=$$?; \
			echo "[validate] FAIL"; \
			echo "[validate] Diagnostic:"; \
			grep -E "APPLICATION FAILED TO START|Description:|Action:|Caused by:|\[ERROR\]|Tests run:|BUILD FAILURE" $(VALIDATION_LOG) | tail -40 || true; \
			echo "[validate] Full log: $(VALIDATION_LOG)"; \
			echo "[validate] Re-run with full output: make validate VERBOSE=1"; \
			exit $$status; \
		fi; \
	fi

test: deps
	@if [ "$(VERBOSE)" = "1" ]; then \
		$(MVNW) -B test; \
	else \
		mkdir -p $(VALIDATION_DIR); \
		if $(MVNW) -B test >$(VALIDATION_DIR)/test.log 2>&1; then \
			echo "[test] PASS"; \
		else \
			status=$$?; \
			echo "[test] FAIL"; \
			grep -E "Caused by:|\[ERROR\]|Tests run:|BUILD FAILURE" $(VALIDATION_DIR)/test.log | tail -40 || true; \
			echo "[test] Full log: $(VALIDATION_DIR)/test.log"; \
			echo "[test] Re-run with full output: make test VERBOSE=1"; \
			exit $$status; \
		fi; \
	fi

package:
	$(MVNW) -B -DskipTests package
