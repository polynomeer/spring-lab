import { useEffect, useState } from "react";

import { deleteRun, fetchRunDetail, fetchRunHistory } from "../api/runHistoryApi";
import type { ScenarioRunDetail, ScenarioRunSummary } from "../types";
import { RawEventLog } from "./RawEventLog";
import { SemanticEventLog } from "./SemanticEventLog";

interface Props {
  scenarioName: string;
  scenarioTitle: string;
  onClose: () => void;
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR", { hour12: false });
}

/**
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "실행 히스토리 스냅샷" - 완료된 과거
 * 실행을 새 자식 JVM 없이 다시 본다. ConditionReportPanel과 같은 패턴으로 완전히
 * 자기 완결적이다(App.tsx의 라이브 log/semanticEvents 상태를 전혀 건드리지 않는다) -
 * 그래서 detail.events를 RawEventLog/SemanticEventLog에 그대로 넘기기만 하면 된다,
 * 두 컴포넌트 모두 실시간 스트림인지 과거 기록인지 구분할 필요가 없는 범용
 * ScenarioMessage[]/SemanticEvent[] 소비자이기 때문이다.
 */
export function RunHistoryModal({ scenarioName, scenarioTitle, onClose }: Props) {
  const [runs, setRuns] = useState<ScenarioRunSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<ScenarioRunDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadRuns = () => {
    setLoading(true);
    fetchRunHistory(scenarioName)
      .then(setRuns)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadRuns();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioName]);

  const selectRun = (id: number) => {
    setSelectedId(id);
    setDetailLoading(true);
    fetchRunDetail(id)
      .then(setDetail)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setDetailLoading(false));
  };

  const removeRun = async (id: number) => {
    try {
      await deleteRun(id);
      if (selectedId === id) {
        setSelectedId(null);
        setDetail(null);
      }
      loadRuns();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const semanticEntries = detail ? detail.events.filter((e) => e.type === "semantic").map((e) => e.event) : [];

  return (
    <div className="history-overlay" role="dialog" aria-modal="true">
      <div className="history-modal">
        <div className="history-modal-head">
          <h2>실행 기록 - {scenarioTitle}</h2>
          <button type="button" className="history-close" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>

        {error && (
          <div className="new-scenario-error" role="alert">
            {error}
          </div>
        )}

        <div className="history-body">
          <div className="history-run-list">
            {loading ? (
              <p className="new-scenario-hint">불러오는 중...</p>
            ) : runs.length === 0 ? (
              <p className="new-scenario-hint">
                아직 완료된 실행 기록이 없습니다 - 시나리오를 끝까지 재생하면 여기 쌓입니다.
              </p>
            ) : (
              <ul>
                {runs.map((run) => (
                  <li key={run.id} className="history-run-row">
                    <button
                      type="button"
                      className="history-run-item"
                      data-active={run.id === selectedId}
                      onClick={() => selectRun(run.id)}
                    >
                      <span className="history-run-time">{formatTimestamp(run.startedAt)}</span>
                      <span className="history-run-meta">
                        {run.timedOut ? "타임아웃" : `HIT ${run.totalHits ?? "?"}`}
                      </span>
                    </button>
                    <button
                      type="button"
                      className="history-run-delete"
                      onClick={() => void removeRun(run.id)}
                      aria-label="이 기록 삭제"
                    >
                      삭제
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="history-run-detail">
            {detailLoading ? (
              <p className="new-scenario-hint">불러오는 중...</p>
            ) : detail ? (
              <>
                <div className="rail-section-head">
                  semantic events <span className="count">{semanticEntries.length}</span>
                </div>
                <div className="history-detail-panel">
                  <SemanticEventLog entries={semanticEntries} />
                </div>
                <div className="rail-section-head">
                  raw log <span className="count">{detail.events.length}</span>
                </div>
                <div className="history-detail-panel">
                  <RawEventLog entries={detail.events} />
                </div>
              </>
            ) : (
              <p className="new-scenario-hint">왼쪽에서 기록을 골라 보세요.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
