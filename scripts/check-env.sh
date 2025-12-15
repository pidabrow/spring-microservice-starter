#!/usr/bin/env bash

# Simple environment check for Git, Java, and Gradle.
# Can be run from anywhere; it resolves the project root based on this script's location.

set -u
set -o pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OVERALL_STATUS=0

echo "=== Checking Git ==="
if command -v git >/dev/null 2>&1; then
  echo "GIT: OK"
  git --version
else
  echo "GIT: ERROR - 'git' command not found on PATH."
  echo "      Please install Git and make sure it is available on PATH."
  OVERALL_STATUS=1
fi

echo
echo "=== Checking Java ==="
JAVA_OK=0
if command -v java >/dev/null 2>&1; then
  if java -version >/dev/null 2>&1; then
    echo "JAVA: OK (działa 'java -version')"
    java -version
    JAVA_OK=1
  fi
fi

if [ "$JAVA_OK" -ne 1 ]; then
  echo "JAVA: Not working correctly or not configured."
  echo "Trying to detect installed JDKs and set the newest one..."

  # Show available JDKs
  if /usr/libexec/java_home -V >/tmp/java_home_list.$$ 2>&1; then
    echo
    echo "Available JDKs (reported by the system):"
    cat /tmp/java_home_list.$$
    rm -f /tmp/java_home_list.$$
  else
    echo "JAVA: ERROR - could not read JDK list using /usr/libexec/java_home -V."
    echo "      Please check your JDK installation manually."
    OVERALL_STATUS=1
  fi

  # Try to pick the default (usually newest) JDK
  DEFAULT_JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  if [ -n "${DEFAULT_JAVA_HOME}" ]; then
    export JAVA_HOME="${DEFAULT_JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    echo
    echo "JAVA: JAVA_HOME set to:"
    echo "  ${JAVA_HOME}"
    echo "Current 'java -version':"
    java -version || {
      echo "JAVA: ERROR - even after setting JAVA_HOME, 'java -version' failed."
      OVERALL_STATUS=1
    }
  else
    echo "JAVA: ERROR - could not find any JDK using /usr/libexec/java_home."
    OVERALL_STATUS=1
  fi
fi

echo
echo "=== Checking Gradle ==="

GRADLE_CMD=""
if [ -x "${PROJECT_ROOT}/gradlew" ]; then
  GRADLE_CMD="${PROJECT_ROOT}/gradlew"
  echo "Gradle: using wrapper: ${GRADLE_CMD}"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
  echo "Gradle: using global installation: gradle"
else
  echo "GRADLE: ERROR - neither './gradlew' in the project nor 'gradle' on PATH was found."
  OVERALL_STATUS=1
fi

if [ -n "${GRADLE_CMD}" ]; then
  echo "Checking 'gradle --version'..."
  if [ "${GRADLE_CMD}" = "gradle" ]; then
    if ! gradle --version; then
      echo "GRADLE: ERROR - 'gradle --version' finished with an error."
      OVERALL_STATUS=1
    else
      echo "GRADLE: OK"
    fi
  else
    # Run the wrapper from the project root directory
    if (cd "${PROJECT_ROOT}" && "${GRADLE_CMD}" --version); then
      echo "GRADLE: OK (wrapper works correctly)"
    else
      STATUS=$?
      echo "GRADLE: ERROR - './gradlew --version' finished with an error (exit code: ${STATUS})."
      OVERALL_STATUS=1
    fi
  fi
fi

echo
if [ "${OVERALL_STATUS}" -eq 0 ]; then
  echo "=== Podsumowanie: wszystkie narzędzia wyglądają na sprawne. ==="
else
  echo "=== Podsumowanie: wykryto problemy (szczegóły powyżej). Kod wyjścia: ${OVERALL_STATUS} ==="
fi

exit "${OVERALL_STATUS}"


