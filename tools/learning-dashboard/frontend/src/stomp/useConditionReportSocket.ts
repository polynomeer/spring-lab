import { useCallback, useEffect, useRef, useState } from "react";
import { Client, type IMessage } from "@stomp/stompjs";

import type { ConditionReportMessage } from "../types";

const BROKER_URL = `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws`;

/**
 * lab.dashboard.conditionreport.ConditionReportWebSocketController 전용 - useDashboardSocket과
 * 별도의 STOMP 연결을 쓴다(6.5절 설계 그대로, 이 시나리오는 ScenarioSession의 재생 모델을
 * 타지 않으므로 /topic/scenario와 섞지 않는다).
 */
export function useConditionReportSocket(onMessage: (message: ConditionReportMessage) => void) {
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
        client.subscribe("/topic/condition-report", (message: IMessage) => {
          try {
            onMessageRef.current(JSON.parse(message.body) as ConditionReportMessage);
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

  const runReport = useCallback((overrides: Record<string, string>) => {
    clientRef.current?.publish({
      destination: "/app/condition-report/run",
      body: JSON.stringify({ overrides }),
    });
  }, []);

  return { connected, runReport };
}
