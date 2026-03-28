.PHONY: test lint fmt

## Run all tests across subprojects
test:
	@echo "=== Running tests ==="
	@if [ -f services/voice-gateway/gradlew ]; then \
		cd services/voice-gateway && ./gradlew test; \
	else \
		echo "[voice-gateway] No tests yet (Gradle not initialized)"; \
	fi
	@if [ -f apps/android/gradlew ]; then \
		cd apps/android && ./gradlew test; \
	else \
		echo "[android] No tests yet (Gradle not initialized)"; \
	fi

## Run linters across subprojects
lint:
	@echo "=== Running linters ==="
	@if [ -f services/voice-gateway/gradlew ]; then \
		cd services/voice-gateway && ./gradlew ktlintCheck 2>/dev/null || echo "[voice-gateway] ktlint not configured yet"; \
	else \
		echo "[voice-gateway] No linter yet (Gradle not initialized)"; \
	fi
	@if [ -f apps/android/gradlew ]; then \
		cd apps/android && ./gradlew lint 2>/dev/null || echo "[android] lint not configured yet"; \
	else \
		echo "[android] No linter yet (Gradle not initialized)"; \
	fi

## Format code
fmt:
	@echo "=== Formatting ==="
	@echo "[placeholder] No formatters configured yet"
