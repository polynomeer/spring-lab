import type { TraceEvent } from "../types";

interface Props {
  hit: TraceEvent | null;
}

export function HitInspector({ hit }: Props) {
  if (!hit) {
    return (
      <div className="hit-inspector">
        <div className="empty-hint">히트를 선택하면 스택과 지역 변수가 여기 보입니다.</div>
      </div>
    );
  }

  return (
    <div className="hit-inspector">
      <div className="loc">
        {hit.location.className}#{hit.location.methodName} (line {hit.location.line})
      </div>
      {hit.stack.map((frame, index) => (
        <div key={index} className={`stack-row ${index === 0 ? "top" : ""}`}>
          [{index}] {frame.className.split(".").pop()}#{frame.methodName}
        </div>
      ))}
      <div className="locals">
        {!hit.localsAvailable && <div className="detail">(no local variable debug info in this jar)</div>}
        {hit.localsAvailable && hit.locals.length === 0 && (
          <div className="detail">(no visible local variables at this line)</div>
        )}
        {hit.locals.map((local) => (
          <div key={local.name} className="local-row">
            <span className="k">{local.name}</span> = {local.value}
            <span className="local-type"> ({local.type})</span>
          </div>
        ))}
      </div>
    </div>
  );
}
