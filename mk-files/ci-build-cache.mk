# How many days cache entries can stay in the semaphore cache before they are considered stale
SEM_CACHE_DURATION_DAYS ?= 7
current_time := $(shell date +"%s")
# OS Name
os_name := $(shell uname -s)

# This target stores two specific caches: Gradle dependencies and SDKMAN! installed SDKs.
#
# Logic adapted from https://github.com/confluentinc/ide-sidecar/blob/7640c2e752da8c28ae9e10f356e94b7331ded0e3/mk-files/semaphore.mk#L21-L54.
#
# A new cache is only stored if the previous one is older than `SEM_CACHE_DURATION_DAYS` (default 7 days).
# Timestamp and the OS name in the cache key to prevent collisions and allow for fuzzy matching on restore.
# Only write to the cache from main builds because of security reasons.
.PHONY: ci-sem-cache-store-gradle
ci-sem-cache-store-gradle:
ifneq ($(SEMAPHORE_GIT_REF_TYPE),pull-request)
	@echo "Storing Gradle-specific semaphore caches"
	@stored_timestamp_gradle=$$(cache list | grep gradle-$(os_name)_ | awk '{print $$1}' | awk -F_ '{print $$NF}' | sort -r | awk 'NR==1'); \
	@stored_timestamp_sdkman=$$(cache list | grep sdkman-$(os_name)_ | awk '{print $$1}' | awk -F_ '{print $$NF}' | sort -r | awk 'NR==1'); \
	@threshold_timestamp=$$(date -d "$(SEM_CACHE_DURATION_DAYS) days ago" +%s); \
	if [ -z "$$stored_timestamp_gradle" ] || [ "$$stored_timestamp_gradle" -lt "$$threshold_timestamp" ]; then \
		echo "Gradle cache is too old or does not exist, storing it again..."; \
		cache store gradle-$(os_name)_$(current_time)_$(shell checksum gradle.properties build.gradle.kts) ~/.gradle; \
	else \
		echo "Gradle cache was updated recently, skipping..."; \
	fi; \
	if [ -z "$$stored_timestamp_sdkman" ] || [ "$$stored_timestamp_sdkman" -lt "$$threshold_timestamp" ]; then \
		echo "SDKMAN! cache is too old or does not exist, storing it again..."; \
		cache store sdkman-$(os_name)_$(current_time)_$(shell checksum .sdkmanrc) ~/.sdkman; \
	else \
		echo "SDKMAN! cache was updated recently, skipping..."; \
	fi
endif

# Restores Gradle and SDKMAN! caches using a checksum of your build files.
# The checksum ensures the cache is tied to project's exact dependency state.
# If a matching cache is not found, the build proceeds with a cold download.
.PHONY: ci-sem-cache-restore-gradle
ci-sem-cache-restore-gradle:
	@echo "Restoring Gradle-specific semaphore caches"
	cache restore gradle-$(os_name)_$(shell checksum gradle.properties build.gradle.kts)
	cache restore sdkman-$(os_name)_$(shell checksum .sdkmanrc)
	pwd
	ls
	cache store testing_cache_store_key $(HOME)/LICENSE.txt

# Override the store-test-results-to-semaphore target to handle Gradle test results
.PHONY: store-test-results-to-semaphore
store-test-results-to-semaphore:
	@for xml_file in $(HOME)/build/test-results/test/*TEST*.xml; do \
		if [ -f "$$xml_file" ]; then \
			test-results publish "$$xml_file" --name "$$(basename "$$xml_file")"; \
		fi; \
	done
