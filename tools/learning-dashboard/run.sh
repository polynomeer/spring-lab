#!/usr/bin/env bash
# tools/learning-dashboard의 백엔드(Spring Boot, 기본 8080)와 프론트엔드(Vite, 기본 5173)를
# 함께 띄우는 스크립트. 두 포트 중 하나라도 이미 쓰이고 있으면 다음 빈 포트로 자동 우회하고,
# 실제로 어느 포트를 썼는지 알려 준다. Ctrl+C 한 번으로 둘 다 종료된다.
set -euo pipefail
set -m # 백그라운드 잡마다 자기 프로세스 그룹을 갖게 해서, 종료 시 자식까지 함께 죽일 수 있게 한다.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

port_in_use() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

# 요청한 포트가 비어 있으면 그대로, 쓰이고 있으면 하나씩 올려 가며 빈 포트를 찾는다.
find_free_port() {
    local port="$1"
    local tries=0
    while port_in_use "$port"; do
        port=$((port + 1))
        tries=$((tries + 1))
        if [[ "$tries" -ge 50 ]]; then
            echo "빈 포트를 50개 넘게 찾지 못했습니다 (마지막 시도: $port)." >&2
            exit 1
        fi
    done
    echo "$port"
}

BACKEND_PORT="$(find_free_port 8080)"
if [[ "$BACKEND_PORT" != "8080" ]]; then
    echo "포트 8080이 이미 사용 중이라 백엔드를 $BACKEND_PORT 포트로 대신 띄웁니다."
fi

FRONTEND_PORT="$(find_free_port 5173)"
if [[ "$FRONTEND_PORT" != "5173" ]]; then
    echo "포트 5173이 이미 사용 중이라 프론트엔드를 $FRONTEND_PORT 포트로 대신 띄웁니다."
fi

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo ""
    echo "대시보드를 종료합니다..."
    [[ -n "$BACKEND_PID" ]] && kill -- "-$BACKEND_PID" 2>/dev/null || true
    [[ -n "$FRONTEND_PID" ]] && kill -- "-$FRONTEND_PID" 2>/dev/null || true
    wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "백엔드 기동 중 (Spring Boot, http://localhost:$BACKEND_PORT)..."
(cd "$REPO_ROOT" && ./gradlew --no-daemon :tools:learning-dashboard:backend:run \
    --args="--server.port=$BACKEND_PORT") &
BACKEND_PID=$!

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    echo "프론트엔드 의존성 설치 중..."
    (cd "$FRONTEND_DIR" && npm install)
fi

echo "프론트엔드 기동 중 (Vite, http://localhost:$FRONTEND_PORT)..."
# DASHBOARD_BACKEND_PORT: vite.config.ts가 /ws 프록시 대상을 정할 때 읽는다 - 백엔드가
# 8080이 아닌 포트로 올라갔으면 프록시도 그 포트를 바라봐야 한다.
# --strictPort: 방금 찾아 둔 빈 포트가 그사이 다시 쓰이게 됐을 때, Vite가 조용히 또 다른
# 포트로 넘어가는 대신 명확히 실패하게 한다 - 화면에 찍어 준 포트가 항상 진실이도록.
(cd "$FRONTEND_DIR" && DASHBOARD_BACKEND_PORT="$BACKEND_PORT" \
    npm run dev -- --port "$FRONTEND_PORT" --strictPort) &
FRONTEND_PID=$!

echo ""
echo "둘 다 기동을 시작했습니다 - 몇 초 정도 걸릴 수 있습니다."
echo "  backend:  http://localhost:$BACKEND_PORT"
echo "  frontend: http://localhost:$FRONTEND_PORT"
echo "Ctrl+C로 둘 다 종료합니다."

wait
