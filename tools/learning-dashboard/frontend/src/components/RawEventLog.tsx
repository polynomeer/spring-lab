import { useMemo, useState } from "react";

import { useAutoScroll } from "../hooks/useAutoScroll";
import type { ScenarioMessage } from "../types";

interface Props {
  entries: ScenarioMessage[];
  onSelectHit?: (hitId: number) => void;
  // RAW LOG ↔ 다이어그램 양방향 강조. selectedHitId는 지금 선택된(HitInspector에 뜬) 히트,
  // highlightedHitIds는 다이어그램의 노드/마커에 마우스를 올렸을 때 그와 관련된 히트들이다.
  selectedHitId?: number | null;
  highlightedHitIds?: Set<number> | null;
}

function searchableText(entry: ScenarioMessage): string {
  switch (entry.type) {
    case "hit":
      return `${entry.event.location.className} ${entry.event.location.methodName} ${entry.event.thread}`;
    case "stdout":
      return `${entry.stream} ${entry.line}`;
    case "exited":
      return `exited total hits ${entry.totalHits}`;
    case "error":
      return `error ${entry.message}`;
    case "httpResponse":
      return `${entry.method} ${entry.path} ${entry.status ?? ""} ${entry.error ?? ""}`;
    default:
      return "";
  }
}

export function RawEventLog({ entries, onSelectHit, selectedHitId, highlightedHitIds }: Props) {
  const [filter, setFilter] = useState("");
  const logRef = useAutoScroll<HTMLDivElement>(entries.length);

  const needle = filter.trim().toLowerCase();
  const visible = useMemo(
    () => (needle ? entries.filter((entry) => searchableText(entry).toLowerCase().includes(needle)) : entries),
    [entries, needle],
  );

  return (
    <div className="log-panel">
      {entries.length > 0 && (
        <div className="log-filter-row">
          <input
            className="log-filter"
            type="text"
            placeholder="필터 - 클래스명, 메서드, 메시지..."
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        </div>
      )}
      {visible.length === 0 ? (
        <div className="event-log">
          <div className="empty-hint">
            {entries.length === 0 ? "시나리오를 선택하고 재생하면 여기 히트가 쌓입니다." : "필터에 맞는 항목이 없습니다."}
          </div>
        </div>
      ) : (
        <div className="event-log" ref={logRef}>
          {visible.map((entry, index) => {
            if (entry.type === "hit") {
              const loc = entry.event.location;
              const shortClass = loc.className.split(".").pop();
              const isSelected = entry.event.hitId === selectedHitId;
              const isHighlighted = highlightedHitIds?.has(entry.event.hitId) ?? false;
              const rowClass = ["event-row", "accent", "as-button", isSelected && "selected", isHighlighted && "highlighted"]
                .filter(Boolean)
                .join(" ");
              return (
                <button
                  key={index}
                  type="button"
                  className={rowClass}
                  onClick={() => onSelectHit?.(entry.event.hitId)}
                >
                  <div className="tick">{String(entry.event.hitId).padStart(3, "0")}</div>
                  <div>
                    <div className="name">
                      {shortClass}#{loc.methodName}
                    </div>
                    <div className="detail">
                      line {loc.line} · {entry.event.thread}
                    </div>
                  </div>
                </button>
              );
            }
            if (entry.type === "stdout") {
              return (
                <div key={index} className="event-row">
                  <div className="tick">·</div>
                  <div>
                    <div className="detail">
                      [{entry.stream}] {entry.line}
                    </div>
                  </div>
                </div>
              );
            }
            if (entry.type === "exited") {
              return (
                <div key={index} className="event-row good">
                  <div className="tick">■</div>
                  <div>
                    <div className="name">EXITED</div>
                    <div className="detail">total hits: {entry.totalHits}</div>
                  </div>
                </div>
              );
            }
            if (entry.type === "error") {
              return (
                <div key={index} className="event-row bad">
                  <div className="tick">!</div>
                  <div>
                    <div className="name">ERROR</div>
                    <div className="detail">{entry.message}</div>
                  </div>
                </div>
              );
            }
            if (entry.type === "httpResponse") {
              return (
                <div key={index} className={`event-row ${entry.error ? "bad" : "good"}`}>
                  <div className="tick">↦</div>
                  <div>
                    <div className="name">
                      {entry.method} {entry.path}
                    </div>
                    <div className="detail">{entry.error ? entry.error : `status ${entry.status}`}</div>
                  </div>
                </div>
              );
            }
            // semantic 이벤트는 이 원본 로그가 아니라 옆의 SemanticEventLog 패널이 보여준다.
            return null;
          })}
        </div>
      )}
    </div>
  );
}
