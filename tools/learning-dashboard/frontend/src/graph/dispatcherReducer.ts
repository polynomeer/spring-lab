import type { SemanticEvent } from "../types";
import { reducePipeline, type PipelineStageSpec } from "./pipelineReducer";
import type { PipelineStage } from "./types";

const STAGE_ORDER: PipelineStageSpec[] = [
  { type: "REQUEST_RECEIVED", id: "dispatch", label: "DispatcherServlet" },
  { type: "HANDLER_LOOKUP_STARTED", id: "handler-mapping", label: "HandlerMapping" },
  { type: "INTERCEPTOR_CHAIN_STARTED", id: "interceptor", label: "Interceptor" },
  { type: "CONTROLLER_INVOKED", id: "controller", label: "Controller" },
  { type: "EXCEPTION_RESOLUTION_STARTED", id: "exception-resolver", label: "ExceptionResolver" },
];

/**
 * lab.dashboard.interpret.DispatcherFlowInterpreter의 5개 이벤트 타입을 좌→우 파이프라인
 * 단계로 옮긴다.
 *
 * 설계 문서(6.4절)가 그리는 파이프라인은 Servlet/FrameworkServlet 단계도 포함하지만,
 * 여기서는 뺐다 - doDispatch 진입이 이미 DispatcherServlet 내부라 그 앞 두 단계를 구분할
 * 브레이크포인트 증거가 없고, 이 프로젝트 전반의 원칙대로 관찰되지 않은 걸 지어내지 않는다.
 */
export function reduceDispatcherFlow(events: SemanticEvent[]): PipelineStage[] {
  return reducePipeline(events, STAGE_ORDER, "REQUEST_RECEIVED");
}
