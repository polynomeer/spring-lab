import type { ScenarioMessage } from "../types";

interface Props {
  entries: ScenarioMessage[];
  onSelectHit?: (hitId: number) => void;
}

export function RawEventLog({ entries, onSelectHit }: Props) {
  if (entries.length === 0) {
    return <div className="event-log"><div className="empty-hint">시나리오를 선택하고 재생하면 여기 히트가 쌓입니다.</div></div>;
  }

  return (
    <div className="event-log">
      {entries.map((entry, index) => {
        if (entry.type === "hit") {
          const loc = entry.event.location;
          const shortClass = loc.className.split(".").pop();
          return (
            <button
              key={index}
              type="button"
              className="event-row accent as-button"
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
        // semantic 이벤트는 이 원본 로그가 아니라 옆의 SemanticEventLog 패널이 보여준다.
        return null;
      })}
    </div>
  );
}
