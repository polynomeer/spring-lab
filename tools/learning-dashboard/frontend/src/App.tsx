import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";

import { ConditionReportPanel } from "./components/ConditionReportPanel";
import { HitInspector } from "./components/HitInspector";
import { RawEventLog } from "./components/RawEventLog";
import { ScenarioTabs } from "./components/ScenarioTabs";
import { ScenarioVisualization } from "./components/ScenarioVisualization";
import { SemanticEventLog } from "./components/SemanticEventLog";
import { TransportControls } from "./components/TransportControls";
import { useResizableHeight } from "./hooks/useResizableHeight";
import { useResizableRail } from "./hooks/useResizableRail";
import { useDashboardSocket } from "./stomp/useDashboardSocket";
import type { ScenarioMessage, ScenarioMeta, SemanticEvent, TraceEvent } from "./types";

// lab.dashboard.session.ScenarioCatalog(백엔드)에 실제 등록된 이름과 정확히 일치해야 한다.
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
    description: "임베디드 Tomcat에 실제 HTTP 요청을 쏴서, doDispatch → HandlerMapping → Interceptor → Controller(→ 예외 시 ExceptionResolver) 순서로 파이프라인이 채워지는 걸 지켜본다.",
    live: true,
  },
  {
    key: "event-multicast",
    title: "애플리케이션 이벤트 멀티캐스트",
    description: "동기 순서 리스너, condition 리스너, @Async 리스너(진짜 다른 스레드), 그리고 @TransactionalEventListener가 커밋 후에만 실행되는 것과 리스너 예외가 이후 리스너를 전부 막는 것까지.",
    live: true,
  },
  {
    key: "mvc-exception-priority",
    title: "MVC 예외 처리 우선순위",
    description: "컨트롤러 로컬 @ExceptionHandler가 @ControllerAdvice보다 항상 먼저 이기는 것, 두 advice가 겹치면 @Order가 정하는 것, 그리고 세 리졸버(ExceptionHandler → ResponseStatus → Default)가 어디서 멈추는지.",
    live: true,
  },
  {
    key: "condition-report",
    title: "Boot 자동 설정 조건 평가 리포트",
    description: "ConditionEvaluationReport는 refresh()가 끝나는 순간 이미 완성돼 있어 '단계'가 없다 - 그래서 재생 대신 프로퍼티를 바꿔 다시 실행하고, 어느 자동 설정이 왜 매치/불일치했는지 트리로 본다.",
    live: true,
    interactionMode: "snapshot",
  },
];

const SCENARIO_STORAGE_KEY = "trace-dash.active-scenario";

function readStoredScenario(): string {
  try {
    const raw = localStorage.getItem(SCENARIO_STORAGE_KEY);
    return raw && SCENARIOS.some((scenario) => scenario.key === raw) ? raw : SCENARIOS[0].key;
  } catch {
    return SCENARIOS[0].key;
  }
}

export default function App() {
  const [activeScenario, setActiveScenario] = useState(readStoredScenario);
  const [log, setLog] = useState<ScenarioMessage[]>([]);
  const [semanticEvents, setSemanticEvents] = useState<SemanticEvent[]>([]);
  const [selectedHit, setSelectedHit] = useState<TraceEvent | null>(null);
  const [running, setRunning] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [hoveredHitIds, setHoveredHitIds] = useState<Set<number> | null>(null);

  const handleMessage = useCallback((message: ScenarioMessage) => {
    setLog((prev) => [...prev, message]);
    if (message.type === "hit") {
      setSelectedHit(message.event);
    } else if (message.type === "semantic") {
      setSemanticEvents((prev) => [...prev, message.event]);
    } else if (message.type === "exited") {
      setRunning(false);
    } else if (message.type === "error") {
      // 예전에는 세션 시작이 실패해도(예: classpath 해석 실패) 서버 로그에만 남고 화면은
      // HIT 0에서 그냥 멈춰 있었다(직접 겪은 문제) - 이제 배너로 즉시 알린다.
      setErrorMessage(message.message);
      setRunning(false);
    }
  }, []);

  const { connected, startScenario, sendCommand, sendHttpRequest } = useDashboardSocket(handleMessage);

  // 새로고침 후 마지막으로 보던 시나리오가 activeScenario 초기값으로 복원되지만, 실제 라이브
  // 세션은 별도로 시작해 줘야 한다(탭을 다시 클릭하지 않아도 되게) - STOMP 연결이 처음
  // 붙는 순간 딱 한 번만 실행한다(재연결마다 세션을 다시 시작해 버리면 안 되므로 ref로 막는다).
  const hasAutoStartedRef = useRef(false);
  useEffect(() => {
    if (!connected || hasAutoStartedRef.current) {
      return;
    }
    hasAutoStartedRef.current = true;
    const meta = SCENARIOS.find((scenario) => scenario.key === activeScenario);
    if (meta?.interactionMode !== "snapshot") {
      startScenario(activeScenario);
    }
  }, [connected, activeScenario, startScenario]);

  const { width: railWidth, startDrag: startRailDrag, stageRef } = useResizableRail();
  const { height: semanticHeight, startDrag: startSemanticDrag } = useResizableHeight(
    "trace-dash.semantic-height", 200, 90, 640,
  );
  const { height: rawLogHeight, startDrag: startRawLogDrag } = useResizableHeight(
    "trace-dash.rawlog-height", 220, 90, 640,
  );

  const hits = useMemo(() => log.filter((entry) => entry.type === "hit"), [log]);
  const hitCount = hits.length;

  const selectScenario = (key: string) => {
    setActiveScenario(key);
    try {
      localStorage.setItem(SCENARIO_STORAGE_KEY, key);
    } catch {
      // 프라이빗 모드 등에서 저장이 막혀도 시나리오 선택 자체는 계속 동작해야 한다.
    }
    setLog([]);
    setSemanticEvents([]);
    setSelectedHit(null);
    setRunning(false);
    setErrorMessage(null);
    setHoveredHitIds(null);
    // snapshot 시나리오(condition-report)는 ScenarioCatalog에 등록돼 있지 않다 -
    // ScenarioSession의 재생 모델을 타지 않으므로 start()를 부를 대상이 없다.
    const meta = SCENARIOS.find((scenario) => scenario.key === key);
    if (meta?.interactionMode !== "snapshot") {
      startScenario(key);
    }
  };

  const handleSelectHit = (hitId: number) => {
    const found = hits.find((entry) => entry.type === "hit" && entry.event.hitId === hitId);
    if (found && found.type === "hit") {
      setSelectedHit(found.event);
    }
  };

  const activeMeta = SCENARIOS.find((scenario) => scenario.key === activeScenario) ?? SCENARIOS[0];
  const isSnapshot = activeMeta.interactionMode === "snapshot";

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

      <div
        className="stage"
        ref={stageRef}
        style={{ "--rail-width": `${railWidth}px` } as CSSProperties}
      >
        <div className="canvas-col">
          <div className="scenario-head">
            <div className="scenario-title-group">
              <div className="eyebrow">SCENARIO</div>
              <h1>{activeMeta.title}</h1>
              <p>{activeMeta.description}</p>
            </div>
            {!isSnapshot && (
              <div className="hitcounter">
                HIT <b>{hitCount}</b>
              </div>
            )}
          </div>

          {errorMessage && (
            <div className="error-banner" role="alert">
              <span className="error-banner-text">{errorMessage}</span>
              <button type="button" onClick={() => setErrorMessage(null)} aria-label="에러 배너 닫기">
                ✕
              </button>
            </div>
          )}

          {!isSnapshot && (
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
          )}

          <div className="viewport">
            {isSnapshot ? (
              <div className="viz-frame">
                <ConditionReportPanel />
              </div>
            ) : (
              <ScenarioVisualization
                scenarioKey={activeScenario}
                semanticEvents={semanticEvents}
                connected={connected}
                onSendRequest={sendHttpRequest}
                selectedHitId={selectedHit?.hitId ?? null}
                onHoverHitIds={setHoveredHitIds}
              />
            )}
          </div>
        </div>

        <div
          className="rail-resizer"
          onPointerDown={startRailDrag}
          role="separator"
          aria-orientation="vertical"
          aria-label="우측 패널 너비 조절"
        />

        <div className="rail">
          <div className="rail-section" style={{ flexBasis: `${semanticHeight}px`, flexGrow: 0 }}>
            <div className="rail-section-head">
              semantic events <span className="count">{semanticEvents.length}</span>
            </div>
            <SemanticEventLog entries={semanticEvents} />
          </div>
          <div
            className="row-resizer"
            onPointerDown={startSemanticDrag}
            role="separator"
            aria-orientation="horizontal"
            aria-label="semantic events 패널 높이 조절"
          />
          <div className="rail-section" style={{ flexBasis: `${rawLogHeight}px`, flexGrow: 0 }}>
            <div className="rail-section-head">
              raw log <span className="count">{log.length}</span>
            </div>
            <RawEventLog
              entries={log}
              onSelectHit={handleSelectHit}
              selectedHitId={selectedHit?.hitId ?? null}
              highlightedHitIds={hoveredHitIds}
            />
          </div>
          <div
            className="row-resizer"
            onPointerDown={startRawLogDrag}
            role="separator"
            aria-orientation="horizontal"
            aria-label="raw log 패널 높이 조절"
          />
          <div className="rail-section grow">
            <div className="rail-section-head">hit inspector</div>
            <HitInspector hit={selectedHit} />
          </div>
        </div>
      </div>
    </div>
  );
}
