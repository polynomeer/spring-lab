import type { GraphEdge, GraphNode, StatusMeta } from "../graph/types";

interface Props {
  nodes: GraphNode[];
  edges: GraphEdge[];
  statusMeta: Record<string, StatusMeta>;
  emptyHint: string;
  // RAW LOG에서 선택한 히트가 이 빈에 관한 것이면 그 노드에 링을 그린다(로그 → 다이어그램).
  highlightedNodeId?: string | null;
  // 노드에 마우스를 올리면 그 빈과 관련된 히트들을 RAW LOG에서 강조할 수 있게 알려준다
  // (다이어그램 → 로그, 반대 방향).
  onHoverNode?: (nodeId: string | null) => void;
}

const COLUMNS = 3;
const COLUMN_WIDTH = 190;
const ROW_HEIGHT = 104;
const NODE_RADIUS = 32;
const MARGIN = 95;

export function StatusGraph({ nodes, edges, statusMeta, emptyHint, highlightedNodeId, onHoverNode }: Props) {
  if (nodes.length === 0) {
    return <div className="empty-hint">{emptyHint}</div>;
  }

  const positions = new Map<string, { x: number; y: number }>();
  nodes.forEach((node, index) => {
    const col = index % COLUMNS;
    const row = Math.floor(index / COLUMNS);
    positions.set(node.id, { x: MARGIN + col * COLUMN_WIDTH, y: 50 + row * ROW_HEIGHT });
  });

  const width = (COLUMNS - 1) * COLUMN_WIDTH + MARGIN * 2;
  const height = (Math.floor((nodes.length - 1) / COLUMNS) + 1) * ROW_HEIGHT + 40;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" height="100%" style={{ display: "block" }}>
      <defs>
        <marker id="graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" className="graph-arrowhead" />
        </marker>
      </defs>
      {edges.map((edge, index) => {
        const from = positions.get(edge.from);
        const to = positions.get(edge.to);
        if (!from || !to) {
          return null;
        }
        return (
          <line
            key={index}
            className="graph-edge"
            x1={from.x}
            y1={from.y}
            x2={to.x}
            y2={to.y}
            markerEnd="url(#graph-arrow)"
          />
        );
      })}
      {nodes.map((node) => {
        const pos = positions.get(node.id);
        if (!pos) {
          return null;
        }
        const meta = statusMeta[node.status] ?? { label: node.status, color: "var(--text-faint)" };
        const highlighted = node.id === highlightedNodeId;
        return (
          <g
            key={node.id}
            transform={`translate(${pos.x}, ${pos.y})`}
            onMouseEnter={() => onHoverNode?.(node.id)}
            onMouseLeave={() => onHoverNode?.(null)}
            style={{ cursor: onHoverNode ? "pointer" : undefined }}
          >
            {highlighted && (
              <circle r={NODE_RADIUS + 7} fill="none" stroke="var(--amber)" strokeWidth={2} strokeDasharray="3 3" />
            )}
            <circle r={NODE_RADIUS} fill={meta.color} fillOpacity={0.16} stroke={meta.color} strokeWidth={2.2} />
            <text textAnchor="middle" y={NODE_RADIUS + 17} className="graph-node-label">
              {node.label}
            </text>
            <text textAnchor="middle" y={NODE_RADIUS + 30} className="graph-node-status" fill={meta.color}>
              {meta.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
