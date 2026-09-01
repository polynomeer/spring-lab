import { useCallback, useEffect, useRef, useState } from "react";

import { fetchScenarios } from "../api/scenarioApi";
import { DISPATCHER_FLOW_PRESETS, MVC_EXCEPTION_PRESETS, type Preset } from "../requestPresets";
import { useDashboardSocket } from "../stomp/useDashboardSocket";
import type { SavedScenario, ScenarioMessage, SemanticEvent } from "../types";
import { RawEventLog } from "./RawEventLog";
import { RequestPresets } from "./RequestPresets";
import { SemanticEventLog } from "./SemanticEventLog";

// dispatcher-flow/mvc-exception-priority는 사용자가 직접 HTTP 요청을 보내야 히트가
// 진행된다(ScenarioVisualization과 같은 프리셋을 재사용) - 나머지 시나리오는 재생만으로
// 끝까지 진행되므로 프리셋이 없다.
const REQUEST_PRESETS_BY_NAME: Record<string, Preset[]> = {
  "dispatcher-flow": DISPATCHER_FLOW_PRESETS,
  "mvc-exception-priority": MVC_EXCEPTION_PRESETS,
};

interface Props {
  onClose: () => void;
}

const SPEEDS = [
  { label: "0.5×", intervalMs: 1000 },
  { label: "1×", intervalMs: 500 },
  { label: "2×", intervalMs: 250 },
  { label: "4×", intervalMs: 100 },
];

interface Side {
  name: string;
  log: ScenarioMessage[];
  semanticEvents: SemanticEvent[];
}

function emptySide(name: string): Side {
  return { name, log: [], semanticEvents: [] };
}

function appendToSide(side: Side, message: ScenarioMessage): Side {
  return {
    ...side,
    log: [...side.log, message],
    semanticEvents: message.type === "semantic" ? [...side.semanticEvents, message.event] : side.semanticEvents,
  };
}

/**
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교 실행" - 서로 다른 두 시나리오를
 * 동시에 띄워 나란히 지켜본다. `03`번 문서가 명시적으로 전제한 "동시엔 시나리오 1개만"을
 * 이 화면에서만 예외적으로 깬다 - 그래서 메인 화면(App.tsx)의 STOMP 연결과는 별개로 자신만의
 * 연결을 새로 연다(ConditionReportPanel/RunHistoryModal과 같은 "완전히 자기 완결적" 패턴,
 * 두 시나리오 이름으로 들어오는 메시지를 여기서 직접 갈라 받는다).
 *
 * <p>Step/Play 같은 개별 트랜스포트 컨트롤은 없다 - 시작하자마자 선택한 속도로 바로
 * 재생되고(백엔드 {@code ScenarioSession#startComparison}), "나란히 지켜보는" 것 자체가
 * 목적이라 각 쪽을 따로 조작할 필요가 없다고 판단했다. 모달을 닫으면 떠 있는 두 세션을
 * 반드시 정리한다 - 일반 단일 세션 흐름과 달리 "다음 start()가 알아서 정리해 준다"는
 * 보장이 없기 때문이다(비교 세션은 메인 흐름의 세션 맵과 별개로 이름으로만 관리된다).
 */
export function ComparisonModal({ onClose }: Props) {
  const [available, setAvailable] = useState<SavedScenario[]>([]);
  const [nameA, setNameA] = useState("");
  const [nameB, setNameB] = useState("");
  const [speedIndex, setSpeedIndex] = useState(1);
  const [started, setStarted] = useState(false);
  const [sideA, setSideA] = useState<Side>(emptySide(""));
  const [sideB, setSideB] = useState<Side>(emptySide(""));
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    fetchScenarios()
      .then((scenarios) => {
        setAvailable(scenarios);
        if (scenarios.length > 0) {
          setNameA(scenarios[0].name);
        }
        setNameB(scenarios.length > 1 ? scenarios[1].name : (scenarios[0]?.name ?? ""));
      })
      .catch((e: unknown) => setErrorMessage(e instanceof Error ? e.message : String(e)));
  }, []);

  const handleMessage = useCallback(
    (message: ScenarioMessage) => {
      if (message.type === "error") {
        setErrorMessage(message.message);
        return;
      }
      if (!("scenario" in message)) {
        return;
      }
      if (message.scenario === nameA) {
        setSideA((prev) => appendToSide(prev, message));
      } else if (message.scenario === nameB) {
        setSideB((prev) => appendToSide(prev, message));
      }
    },
    [nameA, nameB],
  );

  const { connected, startComparison, stopComparison, sendComparisonHttpRequest } = useDashboardSocket(handleMessage);

  // 실제로 startComparison()에 넘긴 이름을 기억해 둔다(정지/언마운트 시점엔 select가 이미
  // 다른 값으로 바뀌었을 수 있으므로 state의 nameA/nameB를 그대로 믿을 수 없다).
  const runningNamesRef = useRef<{ a: string; b: string } | null>(null);

  useEffect(() => {
    return () => {
      if (runningNamesRef.current) {
        stopComparison(runningNamesRef.current.a);
        stopComparison(runningNamesRef.current.b);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const start = () => {
    if (!nameA || !nameB) {
      return;
    }
    setSideA(emptySide(nameA));
    setSideB(emptySide(nameB));
    setErrorMessage(null);
    startComparison(nameA, nameB, SPEEDS[speedIndex].intervalMs);
    runningNamesRef.current = { a: nameA, b: nameB };
    setStarted(true);
  };

  const stop = () => {
    if (runningNamesRef.current) {
      stopComparison(runningNamesRef.current.a);
      stopComparison(runningNamesRef.current.b);
      runningNamesRef.current = null;
    }
    setStarted(false);
  };

  const titleFor = (name: string) => available.find((s) => s.name === name)?.title ?? name;

  return (
    <div className="history-overlay" role="dialog" aria-modal="true">
      <div className="history-modal comparison-modal">
        <div className="history-modal-head">
          <h2>A/B 비교 실행</h2>
          <button type="button" className="history-close" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>

        {errorMessage && (
          <div className="new-scenario-error" role="alert">
            {errorMessage}
          </div>
        )}

        <div className="comparison-setup">
          <div className="field">
            <label>시나리오 A</label>
            <select value={nameA} onChange={(e) => setNameA(e.target.value)} disabled={started}>
              {available.map((s) => (
                <option key={s.name} value={s.name}>
                  {s.title}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>시나리오 B</label>
            <select value={nameB} onChange={(e) => setNameB(e.target.value)} disabled={started}>
              {available.map((s) => (
                <option key={s.name} value={s.name}>
                  {s.title}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>속도</label>
            <select
              value={speedIndex}
              onChange={(e) => setSpeedIndex(Number(e.target.value))}
              disabled={started}
            >
              {SPEEDS.map((speed, index) => (
                <option key={speed.label} value={index}>
                  {speed.label}
                </option>
              ))}
            </select>
          </div>
          {started ? (
            <button type="button" className="comparison-stop" onClick={stop}>
              ■ 비교 종료
            </button>
          ) : (
            <button
              type="button"
              className="comparison-start"
              onClick={start}
              disabled={!connected || !nameA || !nameB}
            >
              ▶ 비교 시작
            </button>
          )}
          <div className={`connection-pill ${connected ? "" : "offline"}`}>
            <span className="dot" />
            {connected ? "CONNECTED" : "DISCONNECTED"}
          </div>
        </div>

        <div className="comparison-panes">
          {[sideA, sideB].map((side, index) => (
            <div className="comparison-pane" key={index}>
              <div className="comparison-pane-head">
                <span className="comparison-pane-title">{side.name ? titleFor(side.name) : `시나리오 ${index === 0 ? "A" : "B"}`}</span>
                <span className="comparison-pane-hits">
                  HIT {side.log.filter((e) => e.type === "hit").length}
                </span>
              </div>
              {started && REQUEST_PRESETS_BY_NAME[side.name] && (
                <RequestPresets
                  presets={REQUEST_PRESETS_BY_NAME[side.name]}
                  disabled={!connected}
                  onSend={(method, path, body) => sendComparisonHttpRequest(side.name, method, path, body)}
                />
              )}
              <div className="rail-section-head">
                semantic events <span className="count">{side.semanticEvents.length}</span>
              </div>
              <div className="history-detail-panel">
                <SemanticEventLog entries={side.semanticEvents} />
              </div>
              <div className="rail-section-head">
                raw log <span className="count">{side.log.length}</span>
              </div>
              <div className="history-detail-panel">
                <RawEventLog entries={side.log} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
