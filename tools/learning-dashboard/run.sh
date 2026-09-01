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
BACKEND_TAIL_PID=""
FRONTEND_PID=""
BACKEND_LOG="$(mktemp)"

# 지금 떠 있는 백엔드 시도(성공했든 실패했든)와 그 로그를 화면에 따라 찍어 주던 tail을 함께
# 정리한다 - 재시도할 때(다음 포트로 새로 띄우기 전)와 스크립트 종료 시 둘 다 이걸 쓴다.
stop_backend_attempt() {
    [[ -n "$BACKEND_TAIL_PID" ]] && kill "$BACKEND_TAIL_PID" 2>/dev/null || true
    [[ -n "$BACKEND_PID" ]] && kill -- "-$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_TAIL_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
    BACKEND_TAIL_PID=""
    BACKEND_PID=""
}

cleanup() {
    echo ""
    echo "대시보드를 종료합니다..."
    stop_backend_attempt
    [[ -n "$FRONTEND_PID" ]] && kill -- "-$FRONTEND_PID" 2>/dev/null || true
    wait 2>/dev/null || true
    rm -f "$BACKEND_LOG"
}
trap cleanup EXIT INT TERM

# find_free_port로 빈 포트를 확인한 시점과 실제로 Spring Boot가 그 포트에 bind하는 시점
# 사이에는 항상 레이스 윈도우가 있다 - 그 몇 초 사이(gradlew가 컴파일/기동하는 동안) 다른
# 프로세스가 그 포트를 먼저 차지할 수 있다. 그래서 그냥 띄우고 끝내는 대신, 로그를
# 파일로도 받아서(터미널에는 tail -f로 그대로 흘려보내면서) Spring Boot 자신의 "포트 이미
# 사용 중" 실패 메시지가 보이는지 지켜보다가, 보이면 다음 빈 포트로 다시 시도한다 - 파일
# 맨 위 설명의 "자동 우회"가 이 경우에도 실제로 지켜지도록.
start_backend() {
    : > "$BACKEND_LOG"
    (cd "$REPO_ROOT" && ./gradlew --no-daemon :tools:learning-dashboard:backend:run \
        --args="--server.port=$BACKEND_PORT") > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    tail -n +1 -f "$BACKEND_LOG" &
    BACKEND_TAIL_PID=$!
}

# 성공/포트 충돌/그 외 조기 종료 셋 중 하나로 결론 날 때까지 로그를 지켜본다.
wait_for_backend() {
    local attempts=0
    while (( attempts < 120 )); do
        if grep -q "Started DashboardApplication" "$BACKEND_LOG" 2>/dev/null; then
            return 0
        fi
        # Spring Boot의 PortInUseFailureAnalyzer가 남기는 실제 문구 그대로.
        if grep -qE "Port [0-9]+ was already in use" "$BACKEND_LOG" 2>/dev/null; then
            return 1
        fi
        if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
            echo "백엔드 프로세스가 예상치 못하게 종료됐습니다. 로그:" >&2
            cat "$BACKEND_LOG" >&2
            exit 1
        fi
        sleep 0.5
        attempts=$((attempts + 1))
    done
    echo "백엔드가 60초 안에 기동하지 못했습니다. 로그:" >&2
    cat "$BACKEND_LOG" >&2
    exit 1
}

echo "백엔드 기동 중 (Spring Boot, http://localhost:$BACKEND_PORT)..."
BACKEND_RETRIES=0
while true; do
    start_backend
    if wait_for_backend; then
        break
    fi
    stop_backend_attempt
    BACKEND_RETRIES=$((BACKEND_RETRIES + 1))
    if (( BACKEND_RETRIES >= 5 )); then
        echo "백엔드를 5번 재시도했지만 포트를 잡지 못했습니다." >&2
        exit 1
    fi
    BACKEND_PORT="$(find_free_port $((BACKEND_PORT + 1)))"
    echo "포트가 그사이 다시 사용 중이 되어 $BACKEND_PORT 포트로 재시도합니다..."
done

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    echo "프론트엔드 의존성 설치 중..."
    (cd "$FRONTEND_DIR" && npm install)
fi

echo "프론트엔드 기동 중 (Vite, http://localhost:$FRONTEND_PORT)..."
# DASHBOARD_BACKEND_PORT: vite.config.ts가 /ws 프록시 대상을 정할 때 읽는다 - 백엔드가
# 8080이 아닌 포트로 올라갔으면(재시도로 바뀐 경우 포함) 프록시도 그 포트를 바라봐야 한다.
# --strictPort: 방금 찾아 둔 빈 포트가 그사이 다시 쓰이게 됐을 때, Vite가 조용히 또 다른
# 포트로 넘어가는 대신 명확히 실패하게 한다 - 화면에 찍어 준 포트가 항상 진실이도록.
(cd "$FRONTEND_DIR" && DASHBOARD_BACKEND_PORT="$BACKEND_PORT" \
    npm run dev -- --port "$FRONTEND_PORT" --strictPort) &
FRONTEND_PID=$!

echo ""
echo "백엔드가 기동했고, 프론트엔드도 기동을 시작했습니다 - 몇 초 정도 걸릴 수 있습니다."
echo "  backend:  http://localhost:$BACKEND_PORT"
echo "  frontend: http://localhost:$FRONTEND_PORT"
echo "Ctrl+C로 둘 다 종료합니다."

wait
