import type { SemanticEvent } from "../types";
import { reduceTxPropagation } from "../graph/txReducer";

interface Props {
  events: SemanticEvent[];
}

const MARKER_META: Record<string, { label: string; color: string }> = {
  TX_STARTED: { label: "START", color: "var(--blue)" },
  TX_SUSPENDED: { label: "SUSPEND", color: "var(--amber)" },
  TX_RESUMED: { label: "RESUME", color: "var(--violet)" },
  TX_COMMITTED: { label: "COMMIT", color: "var(--jade)" },
  TX_ROLLED_BACK: { label: "ROLLBACK", color: "var(--rose)" },
};

const STEP = 96;
const LANE_HEIGHT = 78;

export function TxSwimlane({ events }: Props) {
  const lanes = reduceTxPropagation(events);

  if (lanes.length === 0) {
    return <div className="empty-hint">재생하면 @Transactional 메서드 경계마다 레인이 여기 나타납니다.</div>;
  }

  const maxOrder = Math.max(...lanes.flatMap((lane) => lane.events.map((event) => event.order)));
  const width = Math.max(560, 100 + (maxOrder + 1) * STEP);
  const height = lanes.length * LANE_HEIGHT + 20;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxWidth: "100%", height: "auto", display: "block" }}>
      {lanes.map((lane, laneIndex) => {
        const y = 24 + laneIndex * LANE_HEIGHT;
        return (
          <g key={lane.id}>
            <rect x={8} y={y - 20} width={width - 16} height={LANE_HEIGHT - 14} rx={9} className="lane-box" />
            <text x={18} y={y - 6} className="lane-label">
              {lane.label}
            </text>
            <line x1={16} y1={y + 16} x2={width - 16} y2={y + 16} className="lane-baseline" />
            {lane.events.map((event, index) => {
              const meta = MARKER_META[event.type] ?? { label: event.type, color: "var(--text-faint)" };
              const x = 60 + event.order * STEP;
              const unexpected = event.unexpected === "true";
              return (
                <g key={index} transform={`translate(${x}, ${y + 16})`}>
                  <circle r={9} fill={meta.color} fillOpacity={0.22} stroke={meta.color} strokeWidth={2} />
                  {unexpected && <circle r={14} fill="none" stroke="var(--rose)" strokeWidth={1.5} strokeDasharray="3 2" />}
                  <text y={-16} textAnchor="middle" className="lane-marker-hit">
                    #{event.hitId}
                  </text>
                  <text y={26} textAnchor="middle" className="lane-marker-label" fill={meta.color}>
                    {meta.label}
                    {unexpected ? " !" : ""}
                  </text>
                </g>
              );
            })}
          </g>
        );
      })}
    </svg>
  );
}
