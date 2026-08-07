import type { SemanticEvent } from "../types";
import { reducePipeline, type PipelineStageSpec } from "./pipelineReducer";
import type { PipelineStage } from "./types";

const STAGE_ORDER: PipelineStageSpec[] = [
  { type: "EXCEPTION_HANDLING_STARTED", id: "dispatch", label: "processHandlerException" },
  { type: "RESOLVER_CHAIN_ENTERED", id: "composite", label: "ResolverComposite" },
  { type: "EXCEPTION_HANDLER_LOOKUP_STARTED", id: "exception-handler", label: "ExceptionHandlerResolver" },
  { type: "RESPONSE_STATUS_RESOLUTION_STARTED", id: "response-status", label: "ResponseStatusResolver" },
  { type: "DEFAULT_RESOLUTION_STARTED", id: "default", label: "DefaultResolver" },
];

/**
 * lab.dashboard.interpret.ExceptionResolutionInterpreter의 5개 이벤트 타입을 좌→우 파이프라인
 * 단계로 옮긴다 - HandlerExceptionResolverComposite가 등록된 리졸버를 순서대로 시도하다가
 * 하나가 처리하면 멈추므로, "마지막으로 도달한 단계"가 곧 실제로 처리한 리졸버다(dispatcher-flow와
 * 같은 원리). ResponseStatusResolver 단계는 원인 체인을 타고 재귀 호출되면 여러 번 히트할 수
 * 있는데(docs/22 7번 절), 이 리듀서는 마지막 히트의 hitId만 남긴다 - 그래도 "이 단계까지
 * 왔었다"는 사실 자체는 정확하다.
 */
export function reduceExceptionResolution(events: SemanticEvent[]): PipelineStage[] {
  return reducePipeline(events, STAGE_ORDER, "EXCEPTION_HANDLING_STARTED");
}
