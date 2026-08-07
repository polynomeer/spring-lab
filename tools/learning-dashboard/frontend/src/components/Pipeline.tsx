import type { PipelineStage } from "../graph/types";

interface Props {
  stages: PipelineStage[];
  emptyHint: string;
}

const BOX_WIDTH = 148;
const BOX_HEIGHT = 60;
const GAP = 36;
const MARGIN = 24;

const STATUS_COLOR: Record<PipelineStage["status"], string> = {
  pending: "var(--text-faint)",
  done: "var(--jade)",
  active: "var(--amber)",
};

const STATUS_LABEL: Record<PipelineStage["status"], string> = {
  pending: "PENDING",
  done: "DONE",
  active: "여기서 정지됨",
};

/**
 * 좌→우 단계 파이프라인 - dispatcher-flow(6.4절)와 mvc-exception-priority(22주차)가
 * 공유한다(StatusGraph가 statusMeta를 prop으로 받는 것과 같은 패턴). 단계 계산은
 * dispatcherReducer/exceptionResolutionReducer가 맡고, 이 컴포넌트는 이미 계산된
 * {@link PipelineStage}[]만 받는다.
 */
export function Pipeline({ stages, emptyHint }: Props) {
  const started = stages.some((stage) => stage.status !== "pending");
  const width = stages.length * BOX_WIDTH + (stages.length - 1) * GAP + MARGIN * 2;
  const height = BOX_HEIGHT + 64;
  const y = 20;

  return (
    <div>
      {!started && <div className="empty-hint">{emptyHint}</div>}
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxWidth: "100%", height: "auto", display: "block" }}>
        <defs>
          <marker id="pipeline-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
            <path d="M0,0 L6,3 L0,6 Z" className="graph-arrowhead" />
          </marker>
        </defs>
        {stages.slice(0, -1).map((_, index) => {
          const x1 = MARGIN + (index + 1) * BOX_WIDTH + index * GAP;
          const x2 = x1 + GAP;
          const cy = y + BOX_HEIGHT / 2;
          return <line key={index} className="graph-edge" x1={x1} y1={cy} x2={x2} y2={cy} markerEnd="url(#pipeline-arrow)" />;
        })}
        {stages.map((stage, index) => {
          const x = MARGIN + index * (BOX_WIDTH + GAP);
          const color = STATUS_COLOR[stage.status];
          return (
            <g key={stage.id} transform={`translate(${x}, ${y})`}>
              <rect
                width={BOX_WIDTH}
                height={BOX_HEIGHT}
                rx={9}
                fill={color}
                fillOpacity={stage.status === "pending" ? 0.05 : 0.16}
                stroke={color}
                strokeWidth={stage.status === "active" ? 2.4 : 1.6}
              />
              <text x={BOX_WIDTH / 2} y={26} textAnchor="middle" className="graph-node-label">
                {stage.label}
              </text>
              <text x={BOX_WIDTH / 2} y={42} textAnchor="middle" className="graph-node-status" fill={color}>
                {stage.hitId ? `#${stage.hitId} · ` : ""}
                {STATUS_LABEL[stage.status]}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
