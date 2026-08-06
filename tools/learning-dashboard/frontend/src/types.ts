// lab.tools.jdi.TraceEvent / lab.dashboard.interpret.SemanticEvent (백엔드)와 1:1로
// 대응한다. ScenarioWebSocketController가 /topic/scenario로 보내는 봉투 모양도 그대로다.

export interface TraceLocation {
  className: string;
  methodName: string;
  line: number;
}

export interface TraceFrame {
  className: string;
  methodName: string;
}

export interface TraceLocal {
  name: string;
  type: string;
  value: string;
}

export interface TraceEvent {
  hitId: number;
  timestampNanos: number;
  thread: string;
  location: TraceLocation;
  stack: TraceFrame[];
  locals: TraceLocal[];
  localsAvailable: boolean;
}

export interface SemanticEvent {
  type: string;
  sourceHitId: number;
  attributes: Record<string, string>;
}

export type ScenarioMessage =
  | { type: "hit"; scenario: string; event: TraceEvent }
  | { type: "semantic"; scenario: string; event: SemanticEvent }
  | { type: "stdout"; scenario: string; stream: string; line: string }
  | { type: "exited"; scenario: string; totalHits: number }
  | { type: "error"; message: string };

export interface ScenarioMeta {
  key: string;
  title: string;
  description: string;
  live: boolean;
}
