import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";

import { fetchScenarios } from "./api/scenarioApi";
import { ConditionReportPanel } from "./components/ConditionReportPanel";
import { HitInspector } from "./components/HitInspector";
import { NewScenarioForm } from "./components/NewScenarioForm";
import { RawEventLog } from "./components/RawEventLog";
import { ScenarioTabs } from "./components/ScenarioTabs";
import { ScenarioVisualization } from "./components/ScenarioVisualization";
import { SemanticEventLog } from "./components/SemanticEventLog";
import { TransportControls } from "./components/TransportControls";
import { useResizableHeight } from "./hooks/useResizableHeight";
import { useResizableRail } from "./hooks/useResizableRail";
import { useDashboardSocket } from "./stomp/useDashboardSocket";
import type { SavedScenario, ScenarioMessage, ScenarioMeta, SemanticEvent, TraceEvent } from "./types";

// condition-report는 ScenarioCatalog(DB)에 등록돼 있지 않다 - ScenarioSession의 재생
// 모델을 아예 타지 않는 유일한 시나리오라서(docs/plan/03-learning-dashboard-design.md
// 6.5절) 여전히 프론트엔드에 정적으로 남아 있다.
const CONDITION_REPORT_META: ScenarioMeta = {
  key: "condition-report",
  title: "Boot 자동 설정 조건 평가 리포트",
  description:
    "ConditionEvaluationReport는 refresh()가 끝나는 순간 이미 완성돼 있어 '단계'가 없다 - 그래서 재생 대신 프로퍼티를 바꿔 다시 실행하고, 어느 자동 설정이 왜 매치/불일치했는지 트리로 본다.",
  live: true,
  interactionMode: "snapshot",
};

// 실제 시나리오가 아니라 "+ 새 시나리오" 탭 자신 - NewScenarioForm을 보여준다
// (docs/plan/04-dynamic-scenario-design.md).
const NEW_SCENARIO_META: ScenarioMeta = {
  key: "__new__",
  title: "+ 새 시나리오",
  description: "이미 있는 실험 모듈을 골라 새 시나리오를 등록합니다.",
  live: true,
  interactionMode: "create",
};

function toMeta(saved: SavedScenario): ScenarioMeta {
  return { key: saved.name, title: saved.title, description: saved.description, live: true };
}

function isPlayable(mode: ScenarioMeta["interactionMode"] | undefined): boolean {
  return mode !== "snapshot" && mode !== "create";
}

const SCENARIO_STORAGE_KEY = "trace-dash.active-scenario";

function readStoredScenario(): string {
  try {
    return localStorage.getItem(SCENARIO_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

export default function App() {
  // DB에서 불러오기 전까지는 정적 항목 두 개(condition-report, + 새 시나리오)만 보인다 -
  // 탭 자체는 항상 뭔가 보여줄 수 있어야 하므로 빈 배열로 시작하지 않는다.
  const [scenarios, setScenarios] = useState<ScenarioMeta[]>([CONDITION_REPORT_META, NEW_SCENARIO_META]);
  const [scenariosLoaded, setScenariosLoaded] = useState(false);
  const [activeScenario, setActiveScenario] = useState(readStoredScenario);
  const [log, setLog] = useState<ScenarioMessage[]>([]);
  const [semanticEvents, setSemanticEvents] = useState<SemanticEvent[]>([]);
  const [selectedHit, setSelectedHit] = useState<TraceEvent | null>(null);
  const [running, setRunning] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [hoveredHitIds, setHoveredHitIds] = useState<Set<number> | null>(null);

  // Promise를 그대로 반환한다 - 새 시나리오를 만든 직후(onCreated)에는 목록이 실제로
  // 갱신된 "다음"에 selectScenario를 불러야 한다. 순서를 안 지키면, 방금 저장된 시나리오가
  // 아직 scenarios 배열에 없는 찰나에 "존재하지 않는 activeScenario는 첫 항목으로 되돌린다"는
  // 아래 correction effect가 먼저 끼어들어 방금 만든 탭이 아니라 첫 번째 탭으로 되돌아가
  // 버린다(직접 겪은 경쟁 상태).
  const loadScenarios = useCallback(() => {
    return fetchScenarios()
      .then((saved) => {
        setScenarios([...saved.map(toMeta), CONDITION_REPORT_META, NEW_SCENARIO_META]);
      })
      .catch((e: unknown) => setErrorMessage(e instanceof Error ? e.message : String(e)))
      .finally(() => setScenariosLoaded(true));
  }, []);

  useEffect(() => {
    loadScenarios();
  }, [loadScenarios]);

  // localStorage에 저장돼 있던 시나리오가 그사이 삭제됐거나(또는 첫 방문이라 아직 아무것도
  // 저장돼 있지 않으면) 목록이 채워지는 대로 첫 번째 시나리오로 되돌린다.
  useEffect(() => {
    if (!scenariosLoaded) {
      return;
    }
    if (!scenarios.some((scenario) => scenario.key === activeScenario)) {
      setActiveScenario(scenarios[0]?.key ?? CONDITION_REPORT_META.key);
    }
  }, [scenariosLoaded, scenarios, activeScenario]);

  const handleMessage = useCallback((message: ScenarioMessage) => {
    // "+ 새 시나리오" 탭처럼 재생 대상이 없는 탭으로 옮겨 가도 백엔드의 이전 세션이
    // 계속 돌고 있을 수 있다(ScenarioSession은 새 start() 호출 전까지 스스로 멈추지
    // 않는다) - 지금 보고 있는 시나리오의 이벤트가 아니면 화면에 반영하지 않는다.
    // error는 scenario 필드가 없는 전역 신호라 항상 통과시킨다.
    if ("scenario" in message && message.scenario !== activeScenario) {
      return;
    }
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
  }, [activeScenario]);

  const { connected, startScenario, sendCommand, sendHttpRequest } = useDashboardSocket(handleMessage);

  // 새로고침 후 마지막으로 보던 시나리오가 activeScenario 초기값으로 복원되지만, 실제 라이브
  // 세션은 별도로 시작해 줘야 한다(탭을 다시 클릭하지 않아도 되게) - STOMP 연결이 처음
  // 붙는 순간, 그리고 시나리오 목록이 로드된 뒤 딱 한 번만 실행한다(재연결마다 세션을 다시
  // 시작해 버리면 안 되므로 ref로 막는다).
  const hasAutoStartedRef = useRef(false);
  useEffect(() => {
    if (!connected || !scenariosLoaded || hasAutoStartedRef.current) {
      return;
    }
    const meta = scenarios.find((scenario) => scenario.key === activeScenario);
    if (!meta) {
      return; // activeScenario가 아직 유효한 값으로 정리되기 전 - 위 effect가 곧 고쳐 준다.
    }
    hasAutoStartedRef.current = true;
    if (isPlayable(meta.interactionMode)) {
      startScenario(activeScenario);
    }
  }, [connected, scenariosLoaded, activeScenario, scenarios, startScenario]);

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
    // snapshot/create 시나리오는 ScenarioCatalog를 통해 실행하는 대상이 아니다 -
    // ScenarioSession의 재생 모델을 타지 않으므로 start()를 부를 대상이 없다.
    const meta = scenarios.find((scenario) => scenario.key === key);
    if (meta && isPlayable(meta.interactionMode)) {
      startScenario(key);
    }
  };

  const handleSelectHit = (hitId: number) => {
    const found = hits.find((entry) => entry.type === "hit" && entry.event.hitId === hitId);
    if (found && found.type === "hit") {
      setSelectedHit(found.event);
    }
  };

  const activeMeta = scenarios.find((scenario) => scenario.key === activeScenario) ?? scenarios[0];
  const isSnapshot = activeMeta.interactionMode === "snapshot";
  const isCreate = activeMeta.interactionMode === "create";
  const isInteractive = !isSnapshot && !isCreate;

  return (
    <div className="app">
      <div className="topbar">
        <div className="wordmark">
          <span className="glyph">◆</span>
          trace<span className="slash">/</span>dash
          <span className="sub">— jdi-tracer 라이브 실행 시각화</span>
        </div>
        <ScenarioTabs scenarios={scenarios} active={activeScenario} onSelect={selectScenario} />
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
            {isInteractive && (
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

          {isInteractive && (
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
            {isCreate ? (
              <div className="viz-frame">
                <NewScenarioForm
                  onCreated={(created) => {
                    void loadScenarios().then(() => selectScenario(created.name));
                  }}
                />
              </div>
            ) : isSnapshot ? (
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
