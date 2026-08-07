import type { SemanticEvent } from "../types";
import type { PipelineStage } from "./types";

export interface PipelineStageSpec {
  type: string;
  id: string;
  label: string;
}

/**
 * 좌→우 파이프라인 리듀서의 공용 알고리즘 - dispatcherReducer(6.4절)와
 * exceptionResolutionReducer(22주차)가 공유한다. resetEventType과 일치하는 가장 최근
 * 이벤트 이후만 본다(이전 요청/예외의 흔적과 섞이지 않게 매번 다시 그린다) - 도달한 마지막
 * 단계가 "active"(여기서 정지됨), 그 앞은 "done", 도달하지 못한 단계는 "pending"이다.
 */
export function reducePipeline(
  events: SemanticEvent[],
  stageOrder: PipelineStageSpec[],
  resetEventType: string,
): PipelineStage[] {
  const stages: PipelineStage[] = stageOrder.map((stage) => ({
    id: stage.id,
    label: stage.label,
    status: "pending",
  }));

  const lastResetIndex = events.reduce(
    (found, event, index) => (event.type === resetEventType ? index : found),
    -1,
  );
  if (lastResetIndex === -1) {
    return stages;
  }

  let lastReachedIndex = -1;
  for (let i = lastResetIndex; i < events.length; i++) {
    const event = events[i];
    const stageIndex = stageOrder.findIndex((stage) => stage.type === event.type);
    if (stageIndex === -1) continue;
    stages[stageIndex] = { ...stages[stageIndex], status: "done", hitId: event.sourceHitId };
    lastReachedIndex = Math.max(lastReachedIndex, stageIndex);
  }
  if (lastReachedIndex >= 0) {
    stages[lastReachedIndex] = { ...stages[lastReachedIndex], status: "active" };
  }
  return stages;
}
