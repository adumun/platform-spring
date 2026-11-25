.PHONY: deps validate onboarding test package

MVNW := ./mvnw
VALIDATION_DIR := .validation
VALIDATION_LOG := $(VALIDATION_DIR)/validate.log

JAVA_MAJOR := $(shell java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')

deps:
	@command -v java >/dev/null 2>&1 || { echo "[deps] FAIL — java is not installed or not on PATH"; exit 1; }
	@test "$(JAVA_MAJOR)" = "25" || { echo "[deps] FAIL — Java 25 is required; found $$(java -version 2>&1 | head -1)"; exit 1; }
	@test -x $(MVNW) || { echo "[deps] FAIL — Maven Wrapper ./mvnw is missing or not executable"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "[deps] FAIL — docker CLI is not installed or not on PATH"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "[deps] FAIL — Docker daemon is not available. Start Docker before running validation."; exit 1; }
	@echo "[deps] PASS — Java 25, Maven Wrapper, Docker daemon"

validate: deps
	@VERBOSE=$(VERBOSE) MVNW=$(MVNW) VALIDATION_DIR=$(VALIDATION_DIR) VALIDATION_LOG=$(VALIDATION_LOG) bash scripts/validate.sh

onboarding:
	@VERBOSE=$(VERBOSE) PUBLISHED=$(PUBLISHED) VERSION=$(VERSION) MVNW=$(MVNW) VALIDATION_DIR=$(VALIDATION_DIR) bash scripts/onboarding.sh

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
