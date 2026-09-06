.PHONY: validate test package

MVNW := ./mvnw

validate:
	$(MVNW) -B clean verify

test:
	$(MVNW) -B test

package:
	$(MVNW) -B -DskipTests package
