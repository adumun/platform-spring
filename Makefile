.PHONY: validate test package

validate:
	mvn -B clean verify

test:
	mvn -B test

package:
	mvn -B -DskipTests package
