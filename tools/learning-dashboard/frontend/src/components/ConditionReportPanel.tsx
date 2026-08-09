import { useCallback, useEffect, useState } from "react";

import { shortName } from "../graph/types";
import { useConditionReportSocket } from "../stomp/useConditionReportSocket";
import type { ConditionReportMessage } from "../types";

interface Preset {
  label: string;
  overrides: Record<string, string>;
}

// experiments/auto-configuration-lab의 4개 자동 설정이 대표하는 조건 4종을 각각 뒤집어 보는
// 조합이다 - docs/19-conditional-configuration.md 8번 절의 실험 표와 그대로 대응한다.
const PRESETS: Preset[] = [
  { label: "기본값", overrides: {} },
  { label: "greeting 끄기", overrides: { "greeting.enabled": "false" } },
  { label: "slow-mode 켜기", overrides: { "lab.slow-mode": "true" } },
  { label: "greeting 끄기 + slow-mode 켜기", overrides: { "greeting.enabled": "false", "lab.slow-mode": "true" } },
];

/**
 * condition-report 시나리오 패널 - 다른 시나리오들과 달리 재생 컨트롤이 없다(6.5절 설계).
 * 이 컴포넌트가 자기 몫의 STOMP 연결과 상태를 전부 갖는다 - App.tsx의 log/semanticEvents
 * 파이프라인은 이 시나리오에서 전혀 쓰이지 않는다(never publishes hit/semantic/stdout).
 */
export function ConditionReportPanel() {
  const [message, setMessage] = useState<ConditionReportMessage | null>(null);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const handleMessage = useCallback((next: ConditionReportMessage) => {
    setMessage(next);
    setLoading(false);
  }, []);

  const { connected, runReport } = useConditionReportSocket(handleMessage);

  const run = useCallback(
    (overrides: Record<string, string>) => {
      setLoading(true);
      runReport(overrides);
    },
    [runReport],
  );

  // 처음 열었을 때 기본값으로 한 번 자동 실행 - "다시 실행" 버튼을 누르기 전에도 뭔가 보이게.
  useEffect(() => {
    if (connected && message === null) {
      run({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connected]);

  const toggle = (key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const report = message?.report;
  const sourceEntries = report ? Object.entries(report.sources).sort(([a], [b]) => a.localeCompare(b)) : [];

  return (
    <div>
      <div className="request-presets">
        <div className="request-presets-label">다시 실행</div>
        <div className="request-presets-buttons">
          {PRESETS.map((preset) => (
            <button key={preset.label} type="button" disabled={!connected || loading} onClick={() => run(preset.overrides)}>
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {loading && <div className="empty-hint">실행 중…</div>}

      {!loading && message?.error && (
        <div className="event-row bad">
          <div className="tick">!</div>
          <div>
            <div className="name">ERROR</div>
            <div className="detail">{message.error}</div>
          </div>
        </div>
      )}

      {!loading && report && (
        <div className="condition-tree">
          {report.unconditional.length > 0 && (
            <div className="condition-source">
              <div className="condition-source-head unconditional">
                <span className="condition-badge unconditional">무조건 등록됨</span>
                <span className="condition-source-name">
                  {report.unconditional.map((name) => shortName(name)).join(", ")}
                </span>
              </div>
            </div>
          )}

          {sourceEntries.map(([sourceName, source]) => {
            const key = sourceName;
            const isExpanded = expanded.has(key);
            return (
              <div key={key} className="condition-source">
                <button type="button" className="condition-source-head as-button" onClick={() => toggle(key)}>
                  <span className={`condition-badge ${source.fullMatch ? "match" : "no-match"}`}>
                    {source.fullMatch ? "MATCH" : "NO MATCH"}
                  </span>
                  <span className="condition-source-name">{shortName(sourceName)}</span>
                </button>
                {isExpanded && (
                  <div className="condition-outcomes">
                    {source.outcomes.map((outcome, index) => (
                      <div key={index} className="condition-outcome">
                        <span className={`condition-badge small ${outcome.matched ? "match" : "no-match"}`}>
                          {outcome.matched ? "✓" : "✗"}
                        </span>
                        <span className="condition-outcome-body">
                          <span className="condition-outcome-condition">{outcome.condition}</span>
                          <span className="condition-outcome-message">{outcome.message}</span>
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
