.PHONY: deps validate test package

MVNW := ./mvnw

JAVA_MAJOR := $(shell java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')

deps:
	@command -v java >/dev/null 2>&1 || { echo "ERROR: java is not installed or not on PATH"; exit 1; }
	@test "$(JAVA_MAJOR)" = "25" || { echo "ERROR: Java 25 is required; found $$(java -version 2>&1 | head -1)"; exit 1; }
	@test -x $(MVNW) || { echo "ERROR: Maven Wrapper ./mvnw is missing or not executable"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "ERROR: docker CLI is not installed or not on PATH"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon is not available. Start Docker before running validation."; exit 1; }
	@echo "Toolchain OK: Java 25, Maven Wrapper and Docker daemon are available."

validate: deps
	$(MVNW) -B clean verify

test: deps
	$(MVNW) -B test

package:
	$(MVNW) -B -DskipTests package
