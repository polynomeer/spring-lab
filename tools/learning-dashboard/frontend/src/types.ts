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
  | { type: "httpResponse"; scenario: string; method: string; path: string; status?: number; error?: string }
  | { type: "error"; message: string };

export interface ScenarioMeta {
  key: string;
  title: string;
  description: string;
  live: boolean;
  // "stepped"(기본값 취급) - jdi-tracer로 한 걸음씩 재생하는 나머지 시나리오들.
  // "snapshot" - condition-report처럼 재생 개념이 없는, "다시 실행해서 결과 하나를 받는" 시나리오.
  // "create" - 실제 시나리오가 아니라 "+ 새 시나리오" 탭 자신 - NewScenarioForm을 보여준다.
  interactionMode?: "stepped" | "snapshot" | "create";
}

// lab.dashboard.web.ScenarioResponse(백엔드)와 1:1 대응 - DB에 저장된 시나리오 정의 하나.
// docs/plan/04-dynamic-scenario-design.md 3번 절.
export interface SavedScenario {
  id: number;
  name: string;
  title: string;
  description: string;
  gradleModulePaths: string[];
  mainClass: string;
  breakpointSpec: string;
  // null/빈 문자열이면 1단계(기존 모듈 기반) 시나리오, 값이 있으면 2단계(즉석 코드 작성).
  sourceCode: string | null;
  interpreterKind: string;
  createdAt: string;
}

export interface ScenarioSaveRequest {
  name: string;
  title: string;
  description: string;
  gradleModulePaths: string[];
  mainClass: string;
  breakpointSpec: string;
  sourceCode?: string;
}

// lab.dashboard.conditionreport.ConditionReportWebSocketController가 /topic/condition-report로
// 보내는 봉투 모양 - ScenarioMessage 유니언과는 별개다(6.5절 설계 그대로, 데이터 모양이
// 이질적이라 같은 토픽/유니언에 섞지 않는다).
export interface ConditionOutcome {
  condition: string;
  matched: boolean;
  message: string;
}

export interface ConditionSource {
  fullMatch: boolean;
  outcomes: ConditionOutcome[];
}

export interface ConditionReportData {
  sources: Record<string, ConditionSource>;
  unconditional: string[];
}

export interface ConditionReportMessage {
  overrides: Record<string, string>;
  report?: ConditionReportData;
  error?: string;
}
