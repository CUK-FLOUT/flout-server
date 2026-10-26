#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "[FLOUT] .env 파일이 없습니다. 다음 명령으로 생성해 주세요."
    echo "cp .env.example .env"
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "[FLOUT] Docker를 찾을 수 없습니다. Docker Desktop을 설치하고 실행해 주세요."
    exit 1
fi

cd "${PROJECT_ROOT}"

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

echo "[FLOUT] MySQL 컨테이너를 시작합니다."
docker compose --env-file "${ENV_FILE}" up -d --wait mysql

echo "[FLOUT] Spring Boot 서버를 시작합니다."
echo "[FLOUT] Swagger UI: http://localhost:${SERVER_PORT:-8080}/swagger-ui/index.html"
exec ./gradlew bootRun
