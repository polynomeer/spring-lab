import type { Lane, StatusMeta } from "../graph/types";

interface Props {
  lanes: Lane[];
  markerMeta: Record<string, StatusMeta>;
  emptyHint: string;
}

const STEP = 96;
const LANE_HEIGHT = 78;

/**
 * "공유 시간축 위의 여러 레인, 레인마다 마커" 모양을 쓰는 시각화가 둘이 되면서(tx-propagation,
 * event-multicast) 일반화했다 - 레인 계산은 시나리오별 reducer(txReducer.ts,
 * eventMulticastReducer.ts)가 맡고, 이 컴포넌트는 이미 계산된 {@link Lane}[]과 마커 색상/라벨
 * 맵만 받는다(StatusGraph가 statusMeta를 prop으로 받는 것과 같은 패턴).
 */
export function Swimlane({ lanes, markerMeta, emptyHint }: Props) {
  if (lanes.length === 0) {
    return <div className="empty-hint">{emptyHint}</div>;
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
              const meta = markerMeta[event.type] ?? { label: event.type, color: "var(--text-faint)" };
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
