#!/bin/bash

# This script extracts from all jars in the specified directory the NOTICE files.
# It then concatenates all NOTICE files into a single NOTICE-binary.txt file in the specified output directory.
# Be aware, that it does not deduplicate contents.

set -Eeuo pipefail

SRC=$(realpath ${1:-.})
DST=$(realpath ${2:-.})

PWD=$(pwd)
TMP="${DST}/tmp"
DIR=$(dirname "$0")

USAGE="collect-notices-binary <SOURCE_DIRECTORY:-.> <OUTPUT_DIRECTORY:-.>"

if [ "${SRC}" = "-h" ]; then
	echo "${USAGE}"
	exit 0
fi

# Find JARs in both build outputs and dependencies
jars=()
# Main plugin JARs
if [ -d "${SRC}/build/libs" ]; then
  while IFS= read -r -d '' jar; do
    jars+=("$jar")
  done < <(find -L "${SRC}/build/libs" -name "*.jar" -print0)
fi

# Dependency JARs (from IntelliJ plugin sandbox)
if [ -d "${SRC}/build/idea-sandbox" ]; then
  while IFS= read -r -d '' jar; do
    jars+=("$jar")
  done < <(find -L "${SRC}/build/idea-sandbox" -path "*/lib/*.jar" -print0)
fi

# Fallback: search the provided source directory
if [ ${#jars[@]} -eq 0 ]; then
  while IFS= read -r -d '' jar; do
    jars+=("$jar")
  done < <(find -L "${SRC}" -name "*.jar" -print0)
fi

n_jars="${#jars[@]}"

echo "Found ${n_jars} jars in ${SRC}"

for ((i=0; i<n_jars; i++))
do
  jar="${jars[$i]}"
	DIR="${TMP}/$(basename -- "$jar" .jar)"
	mkdir -p "${DIR}"
	echo "Extracting from: $(basename "$jar")"
	(cd "${DIR}" && jar xf "${jar}" META-INF/ 2>/dev/null || true)
	# Find and keep only NOTICE files
	(cd "${DIR}" && find META-INF -name "*NOTICE*" -type f 2>/dev/null || true)
done

NOTICE="${DST}/NOTICE.txt"
TMP_NOTICE_BINARY="${TMP}/NOTICE-binary.txt"
NOTICE_BINARY="${DST}/NOTICE-binary.txt"

append_notice() {
  local notice_file="${1}"
  local jar_name="${2}"
  echo "Appending NOTICE file: ${notice_file}"

  echo -e "\n============ ${jar_name} ============\n" >> "${TMP_NOTICE_BINARY}"
  cat "${notice_file}" >> "${TMP_NOTICE_BINARY}"
}

create_binary_notice() {
  [ -f "${TMP_NOTICE_BINARY}" ] && rm "${TMP_NOTICE_BINARY}"
  
  # Start with main NOTICE.txt if it exists, otherwise create header
  if [ -f "${NOTICE}" ]; then
    cp "${NOTICE}" "${TMP_NOTICE_BINARY}"
  else
    echo "# Third Party Notices" > "${TMP_NOTICE_BINARY}"
    echo "" >> "${TMP_NOTICE_BINARY}"
    echo "This file contains notices for third-party libraries included in this distribution." >> "${TMP_NOTICE_BINARY}"
  fi

  notices=( $(find . -type f -name "NOTICE*" -mindepth 2 | sort) )
  n="${#notices[@]}"
  echo "Found ${n} NOTICE files in extracted JARs"
  
  for ((i=0; i<n; i++))
  do
     notice_file="${notices[$i]}"
     jar_name=$(echo "${notice_file}" | cut -d'/' -f2)
     append_notice "${notice_file}" "${jar_name}"
  done
}

(cd "${TMP}" && create_binary_notice && cp "${TMP_NOTICE_BINARY}" "${NOTICE_BINARY}")

echo "Created ${NOTICE_BINARY} with notices from ${n_jars} JARs"

rm -r "${TMP}"
