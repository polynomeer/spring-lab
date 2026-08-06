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

export function shortName(fullyQualifiedName: string): string {
  const lastDot = fullyQualifiedName.lastIndexOf(".");
  return lastDot < 0 ? fullyQualifiedName : fullyQualifiedName.slice(lastDot + 1);
}
