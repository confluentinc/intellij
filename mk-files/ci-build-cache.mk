# How many days cache entries can stay in the semaphore cache before they are considered stale
SEM_CACHE_DURATION_DAYS ?= 7
current_time := $(shell date +"%s")

# This target stores two specific caches: Gradle dependencies and SDKMAN! installed SDKs.
#
# Logic adapted from https://github.com/confluentinc/ide-sidecar/blob/7640c2e752da8c28ae9e10f356e94b7331ded0e3/mk-files/semaphore.mk#L21-L54.
#
# A new cache is only stored if the previous one is older than `SEM_CACHE_DURATION_DAYS` (default 7 days).
# Timestamp and the OS name in the cache key to prevent collisions and allow for fuzzy matching on restore.
# Only write to the cache from main builds because of security reasons.
.PHONY: ci-sem-cache-store
ci-sem-cache-store: ci-sem-cache-store-gradle ci-sem-cache-store-sdkman

.PHONY: ci-sem-cache-restore
ci-sem-cache-restore: ci-sem-cache-restore-gradle ci-sem-cache-restore-sdkman

# This target stores the Gradle-specific caches: dependencies and wrapper. Caching only the ~/.gradle/caches and ~/.gradle/wrapper directories.
# See https://docs.gradle.org/current/userguide/gradle_directories_intermediate.html#gradle_user_home
.PHONY: ci-sem-cache-store-gradle
ci-sem-cache-store-gradle:
# ifneq ($(SEMAPHORE_GIT_REF_TYPE),pull-request)
	@echo "Storing Gradle-specific semaphore caches"
	@set -e; \
	current_checksum=$$(checksum gradle.properties build.gradle.kts); \
	stored_key=$$(cache list | grep "gradle_$$current_checksum" | awk '{print $$1}' | sort -r | awk 'NR==1'); \
	stored_timestamp=$$(echo "$$stored_key" | awk -F_ '{print $$NF}'); \
	threshold_timestamp=$$(date -d "$(SEM_CACHE_DURATION_DAYS) days ago" +%s); \
	if [ -z "$$stored_timestamp" ] || [ "$$stored_timestamp" -lt "$$threshold_timestamp" ]; then \
		echo "Gradle cache is too old or does not exist, storing it again..."; \
		cache store "gradle_$$current_checksum_$(current_time)" ~/.gradle/caches ~/.gradle/wrapper; \
	else \
		echo "Gradle cache for this checksum was updated recently, skipping..."; \
	fi
# endif

# This target stores the SDKMAN! installed SDKs.
.PHONY: ci-sem-cache-store-sdkman
ci-sem-cache-store-sdkman:
# ifneq ($(SEMAPHORE_GIT_REF_TYPE),pull-request)
	@echo "Storing SDKMAN! semaphore cache"
	@set -e; \
	current_checksum=$$(checksum .sdkmanrc); \
	stored_key=$$(cache list | grep "sdkman_$$current_checksum" | awk '{print $$1}' | sort -r | awk 'NR==1'); \
	stored_timestamp=$$(echo "$$stored_key" | awk -F_ '{print $$NF}'); \
	threshold_timestamp=$$(date -d "$(SEM_CACHE_DURATION_DAYS) days ago" +%s); \
	if [ -z "$$stored_timestamp" ] || [ "$$stored_timestamp" -lt "$$threshold_timestamp" ]; then \
		echo "SDKMAN! cache is too old or does not exist, storing it again..."; \
		cache store "sdkman_$$current_checksum_$(current_time)" ~/.sdkman; \
	else \
		echo "SDKMAN! cache for this checksum was updated recently, skipping..."; \
	fi
# endif

# This target restores the Gradle-specific caches using a checksum of your build files.
.PHONY: ci-sem-cache-restore-gradle
ci-sem-cache-restore-gradle:
	@echo "Restoring Gradle-specific semaphore caches"
	cache restore "gradle_$(shell checksum gradle.properties build.gradle.kts)"

# This target restores the SDKMAN! installed SDKs.
.PHONY: ci-sem-cache-restore-sdkman
ci-sem-cache-restore-sdkman:
	@echo "Restoring SDKMAN! semaphore cache"
	cache restore "sdkman_$(shell checksum .sdkmanrc)"

# Override the store-test-results-to-semaphore target to handle Gradle test results
.PHONY: store-test-results-to-semaphore
store-test-results-to-semaphore:
	pwd
	ls
	@for xml_file in $(HOME)/intellij/build/test-results/test/*TEST*.xml; do \
		if [ -f "$$xml_file" ]; then \
			test-results publish "$$xml_file" --name "$$(basename "$$xml_file")"; \
		else \
			echo "No Gradle test results found in the current directory."; \
			exit 1; \
		fi; \
	done