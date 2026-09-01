import { useCallback, useEffect, useRef, useState } from "react";
import { Client, type IMessage } from "@stomp/stompjs";

import type { ScenarioMessage } from "../types";

// 페이지 자신의 오리진을 기준으로 상대 경로로 접속한다 - 개발 중에는 vite.config.ts의
// /ws 프록시를 거쳐 8080으로 전달되고(같은 출처로 보이게 하기 위함), 나중에 백엔드가
// 빌드된 프론트엔드를 직접 서빙하게 되면 그 설정 변경 없이도 그대로 동작한다.
const BROKER_URL = `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws`;

/**
 * lab.dashboard.web.WebSocketConfig가 등록한 순수 STOMP 엔드포인트(/ws, SockJS 폴백 없음)에
 * 연결한다. 연결이 끊기면 자동으로 재시도한다(reconnectDelay) - jdi-tracer-server가 시나리오
 * 하나를 끝까지 돌리는 동안 백엔드를 재시작해도 프론트엔드가 알아서 다시 붙는다.
 */
export function useDashboardSocket(onMessage: (message: ScenarioMessage) => void) {
  const clientRef = useRef<Client | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      brokerURL: BROKER_URL,
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/scenario", (message: IMessage) => {
          try {
            onMessageRef.current(JSON.parse(message.body) as ScenarioMessage);
          } catch {
            // 프로토콜 밖의 잡음 - 무시한다.
          }
        });
      },
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, []);

  const startScenario = useCallback((name: string) => {
    clientRef.current?.publish({ destination: `/app/scenario/${name}/start` });
  }, []);

  const sendCommand = useCallback((cmd: string, intervalMs?: number) => {
    clientRef.current?.publish({
      destination: "/app/scenario/command",
      body: JSON.stringify({ cmd, intervalMs: intervalMs ?? null }),
    });
  }, []);

  // dispatcher-flow(6.4절)의 "요청 보내기" - 백엔드가 지금 실행 중인 시나리오의 임베디드
  // 서버로 실제 HTTP 요청을 대신 쏴 준다(브라우저가 직접 쏘지 않는다 - 대상 포트는 매번
  // 랜덤이고 백엔드만 안다).
  const sendHttpRequest = useCallback((method: string, path: string, body?: string) => {
    clientRef.current?.publish({
      destination: "/app/scenario/http-request",
      body: JSON.stringify({ method, path, body: body ?? null }),
    });
  }, []);

  // docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행" - 두 시나리오를 서로
  // 건드리지 않고 동시에 띄운다. 일반 startScenario와 달리 시작하자마자 바로 재생되므로
  // (ComparisonPanel에는 개별 Step/Play 컨트롤이 없다) 속도(intervalMs)를 함께 보낸다.
  const startComparison = useCallback((nameA: string, nameB: string, intervalMs: number) => {
    clientRef.current?.publish({
      destination: "/app/scenario/comparison/start",
      body: JSON.stringify({ nameA, nameB, intervalMs }),
    });
  }, []);

  const stopComparison = useCallback((name: string) => {
    clientRef.current?.publish({
      destination: "/app/scenario/comparison/stop",
      body: JSON.stringify({ name }),
    });
  }, []);

  // 비교 화면 쪽의 "요청 보내기" - sendHttpRequest와 달리 대상이 A/B 둘 중 하나로 모호할
  // 수 있으므로 name으로 정확히 지정한다.
  const sendComparisonHttpRequest = useCallback((name: string, method: string, path: string, body?: string) => {
    clientRef.current?.publish({
      destination: "/app/scenario/comparison/http-request",
      body: JSON.stringify({ name, method, path, body: body ?? null }),
    });
  }, []);

  return {
    connected,
    startScenario,
    sendCommand,
    sendHttpRequest,
    startComparison,
    stopComparison,
    sendComparisonHttpRequest,
  };
}
