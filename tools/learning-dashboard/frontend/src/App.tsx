import { useCallback, useMemo, useState } from "react";

import { HitInspector } from "./components/HitInspector";
import { RawEventLog } from "./components/RawEventLog";
import { ScenarioTabs } from "./components/ScenarioTabs";
import { ScenarioVisualization } from "./components/ScenarioVisualization";
import { SemanticEventLog } from "./components/SemanticEventLog";
import { TransportControls } from "./components/TransportControls";
import { useDashboardSocket } from "./stomp/useDashboardSocket";
import type { ScenarioMessage, ScenarioMeta, SemanticEvent, TraceEvent } from "./types";

// lab.dashboard.session.ScenarioCatalog(백엔드)에 실제 등록된 이름과 정확히 일치해야 한다 -
// dispatcher-flow는 아직 라이브 Lab이 없어(4단계 예정) live: false로 비활성 처리한다.
const SCENARIOS: ScenarioMeta[] = [
  {
    key: "bean-lifecycle",
    title: "빈 생명주기 + 순환 참조",
    description: "3단계 캐시가 조기 참조를 노출하는 시점과, AOP 프록시 대상 빈의 순환 참조가 실제로 풀리는 과정.",
    live: true,
  },
  {
    key: "aop-proxy",
    title: "AOP 자동 프록시 생성",
    description: "advisor 빈이 재귀적으로 인스턴스화되는 순간과 JDK/CGLIB 프록시 선택 경로.",
    live: true,
  },
  {
    key: "tx-propagation",
    title: "트랜잭션 전파",
    description: "REQUIRES_NEW의 suspend/resume, 그리고 참여자 실패가 커밋 시점의 UnexpectedRollbackException으로 이어지는 경로.",
    live: true,
  },
  {
    key: "dispatcher-flow",
    title: "DispatcherServlet 요청 흐름",
    description: "임베디드 서버 + 요청 주입 인프라가 아직 없어 라이브로 실행할 수 없다(설계 문서 4단계 예정).",
    live: false,
  },
];

export default function App() {
  const [activeScenario, setActiveScenario] = useState(SCENARIOS[0].key);
  const [log, setLog] = useState<ScenarioMessage[]>([]);
  const [semanticEvents, setSemanticEvents] = useState<SemanticEvent[]>([]);
  const [selectedHit, setSelectedHit] = useState<TraceEvent | null>(null);
  const [running, setRunning] = useState(false);

  const handleMessage = useCallback((message: ScenarioMessage) => {
    setLog((prev) => [...prev, message]);
    if (message.type === "hit") {
      setSelectedHit(message.event);
    } else if (message.type === "semantic") {
      setSemanticEvents((prev) => [...prev, message.event]);
    } else if (message.type === "exited") {
      setRunning(false);
    }
  }, []);

  const { connected, startScenario, sendCommand } = useDashboardSocket(handleMessage);

  const hits = useMemo(() => log.filter((entry) => entry.type === "hit"), [log]);
  const hitCount = hits.length;

  const selectScenario = (key: string) => {
    setActiveScenario(key);
    setLog([]);
    setSemanticEvents([]);
    setSelectedHit(null);
    setRunning(false);
    startScenario(key);
  };

  const handleSelectHit = (hitId: number) => {
    const found = hits.find((entry) => entry.type === "hit" && entry.event.hitId === hitId);
    if (found && found.type === "hit") {
      setSelectedHit(found.event);
    }
  };

  const activeMeta = SCENARIOS.find((scenario) => scenario.key === activeScenario) ?? SCENARIOS[0];

  return (
    <div className="app">
      <div className="topbar">
        <div className="wordmark">
          <span className="glyph">◆</span>
          trace<span className="slash">/</span>dash
          <span className="sub">— jdi-tracer 라이브 실행 시각화</span>
        </div>
        <ScenarioTabs scenarios={SCENARIOS} active={activeScenario} onSelect={selectScenario} />
      </div>

      <div className="stage">
        <div className="canvas-col">
          <div className="scenario-head">
            <div className="scenario-title-group">
              <div className="eyebrow">SCENARIO</div>
              <h1>{activeMeta.title}</h1>
              <p>{activeMeta.description}</p>
            </div>
            <div className="hitcounter">
              HIT <b>{hitCount}</b>
            </div>
          </div>

          <TransportControls
            connected={connected && activeMeta.live}
            running={running}
            onStep={() => sendCommand("step")}
            onPlay={(intervalMs) => {
              setRunning(true);
              sendCommand("play", intervalMs);
            }}
            onPause={() => {
              setRunning(false);
              sendCommand("pause");
            }}
            onReset={() => selectScenario(activeScenario)}
          />

          <div className="viewport">
            <ScenarioVisualization scenarioKey={activeScenario} semanticEvents={semanticEvents} />
          </div>
        </div>

        <div className="rail">
          <div className="rail-section">
            <div className="rail-section-head">
              semantic events <span className="count">{semanticEvents.length}</span>
            </div>
            <SemanticEventLog entries={semanticEvents} />
          </div>
          <div className="rail-section">
            <div className="rail-section-head">
              raw log <span className="count">{log.length}</span>
            </div>
            <div className="scrollable-log">
              <RawEventLog entries={log} onSelectHit={handleSelectHit} />
            </div>
          </div>
          <div className="rail-section grow">
            <div className="rail-section-head">hit inspector</div>
            <HitInspector hit={selectedHit} />
          </div>
        </div>
      </div>
    </div>
  );
}
