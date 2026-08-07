export interface GraphNode {
  id: string;
  label: string;
  status: string;
}

export interface GraphEdge {
  from: string;
  to: string;
}

export interface StatusMeta {
  label: string;
  color: string;
}

export interface LaneEvent {
  type: string;
  hitId: number;
  order: number;
  unexpected?: string;
}

export interface Lane {
  id: string;
  label: string;
  events: LaneEvent[];
}

export function shortName(fullyQualifiedName: string): string {
  const lastDot = fullyQualifiedName.lastIndexOf(".");
  return lastDot < 0 ? fullyQualifiedName : fullyQualifiedName.slice(lastDot + 1);
}
