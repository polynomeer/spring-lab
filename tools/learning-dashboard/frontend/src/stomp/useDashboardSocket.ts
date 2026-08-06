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

  return { connected, startScenario, sendCommand };
}
