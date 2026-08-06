import type { SemanticEvent } from "../types";

interface Props {
  entries: SemanticEvent[];
}

export function SemanticEventLog({ entries }: Props) {
  if (entries.length === 0) {
    return <div className="event-log"><div className="empty-hint">아직 해석된 semantic 이벤트가 없습니다.</div></div>;
  }

  return (
    <div className="event-log">
      {entries.map((entry, index) => (
        <div key={index} className="event-row accent">
          <div className="tick">{String(index + 1).padStart(2, "0")}</div>
          <div>
            <div className="name">{entry.type}</div>
            <div className="detail">
              hit #{entry.sourceHitId}
              {Object.entries(entry.attributes)
                .map(([key, value]) => ` · ${key}=${value}`)
                .join("")}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
