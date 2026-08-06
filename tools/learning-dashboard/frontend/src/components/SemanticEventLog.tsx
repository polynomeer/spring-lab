import { useState } from "react";

import { DOC_LINKS } from "../docLinks";
import { useAutoScroll } from "../hooks/useAutoScroll";
import type { SemanticEvent } from "../types";

interface Props {
  entries: SemanticEvent[];
}

export function SemanticEventLog({ entries }: Props) {
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);
  // 문서 발췌를 펼쳐 읽는 중에는 새 이벤트가 도착해도 스크롤이 튀지 않게 한다.
  const logRef = useAutoScroll<HTMLDivElement>(expandedIndex === null ? entries.length : expandedIndex);

  if (entries.length === 0) {
    return (
      <div className="event-log">
        <div className="empty-hint">아직 해석된 semantic 이벤트가 없습니다.</div>
      </div>
    );
  }

  return (
    <div className="event-log" ref={logRef}>
      {entries.map((entry, index) => {
        const docLink = DOC_LINKS[entry.type];
        const expanded = expandedIndex === index;
        return (
          <div key={index}>
            <button
              type="button"
              className="event-row accent as-button"
              onClick={() => setExpandedIndex(expanded ? null : index)}
            >
              <div className="tick">{String(index + 1).padStart(2, "0")}</div>
              <div>
                <div className="name">
                  {entry.type}
                  {docLink && <span className="doc-marker">문서</span>}
                </div>
                <div className="detail">
                  hit #{entry.sourceHitId}
                  {Object.entries(entry.attributes)
                    .map(([key, value]) => ` · ${key}=${value}`)
                    .join("")}
                </div>
              </div>
            </button>
            {expanded && docLink && (
              <div className="doc-excerpt">
                <div className="doc-excerpt-path">
                  {docLink.path} · {docLink.section}
                </div>
                <p>{docLink.excerpt}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
