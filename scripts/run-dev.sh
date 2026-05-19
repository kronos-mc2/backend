#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  export JAVA_HOME
  JAVA_HOME=$(/usr/libexec/java_home)
fi

if [ -f .env.dev ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.dev
  set +a
elif [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

exec ./mvnw spring-boot:run
