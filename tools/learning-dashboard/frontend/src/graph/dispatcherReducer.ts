import type { SemanticEvent } from "../types";

export interface PipelineStage {
  id: string;
  label: string;
  status: "pending" | "done" | "active";
  hitId?: number;
}

const STAGE_ORDER: { type: string; id: string; label: string }[] = [
  { type: "REQUEST_RECEIVED", id: "dispatch", label: "DispatcherServlet" },
  { type: "HANDLER_LOOKUP_STARTED", id: "handler-mapping", label: "HandlerMapping" },
  { type: "INTERCEPTOR_CHAIN_STARTED", id: "interceptor", label: "Interceptor" },
  { type: "CONTROLLER_INVOKED", id: "controller", label: "Controller" },
  { type: "EXCEPTION_RESOLUTION_STARTED", id: "exception-resolver", label: "ExceptionResolver" },
];

/**
 * lab.dashboard.interpret.DispatcherFlowInterpreter의 5개 이벤트 타입을 좌→우 파이프라인
 * 단계로 옮긴다. 가장 최근 REQUEST_RECEIVED 이후의 이벤트만 본다 - 이전 요청의 흔적과
 * 섞이지 않게 매 요청마다 다시 그린다.
 *
 * 설계 문서(6.4절)가 그리는 파이프라인은 Servlet/FrameworkServlet 단계도 포함하지만,
 * 여기서는 뺐다 - doDispatch 진입이 이미 DispatcherServlet 내부라 그 앞 두 단계를 구분할
 * 브레이크포인트 증거가 없고, 이 프로젝트 전반의 원칙대로 관찰되지 않은 걸 지어내지 않는다.
 */
export function reduceDispatcherFlow(events: SemanticEvent[]): PipelineStage[] {
  const stages: PipelineStage[] = STAGE_ORDER.map((stage) => ({
    id: stage.id,
    label: stage.label,
    status: "pending",
  }));

  const lastRequestIndex = events.reduce(
    (found, event, index) => (event.type === "REQUEST_RECEIVED" ? index : found),
    -1,
  );
  if (lastRequestIndex === -1) {
    return stages;
  }

  let lastReachedIndex = -1;
  for (let i = lastRequestIndex; i < events.length; i++) {
    const event = events[i];
    const stageIndex = STAGE_ORDER.findIndex((stage) => stage.type === event.type);
    if (stageIndex === -1) continue;
    stages[stageIndex] = { ...stages[stageIndex], status: "done", hitId: event.sourceHitId };
    lastReachedIndex = Math.max(lastReachedIndex, stageIndex);
  }
  if (lastReachedIndex >= 0) {
    stages[lastReachedIndex] = { ...stages[lastReachedIndex], status: "active" };
  }
  return stages;
}
