import type { SemanticEvent } from "../types";

export interface TxLaneEvent {
  type: string;
  hitId: number;
  order: number;
  unexpected?: string;
}

export interface TxLane {
  id: string;
  label: string;
  events: TxLaneEvent[];
}

/**
 * lab.dashboard.interpret.TransactionPropagationInterpreter가 붙여 주는
 * joinpointIdentification(LIFO 스택 기반 - 그 시점에 진행 중이던 @Transactional 메서드
 * 경계)을 레인 키로 그대로 쓴다. `order`는 전체 semantic 이벤트 스트림 안에서의 위치라,
 * 서로 다른 레인의 이벤트도 같은 시간축 위에서 나란히 비교할 수 있다.
 */
export function reduceTxPropagation(events: SemanticEvent[]): TxLane[] {
  const lanes = new Map<string, TxLane>();

  events.forEach((event, order) => {
    const id = event.attributes.joinpointIdentification;
    if (!id) return;

    let lane = lanes.get(id);
    if (!lane) {
      lane = { id, label: shortJoinpoint(id), events: [] };
      lanes.set(id, lane);
    }
    lane.events.push({ type: event.type, hitId: event.sourceHitId, order, unexpected: event.attributes.unexpected });
  });

  return [...lanes.values()];
}

function shortJoinpoint(joinpointIdentification: string): string {
  const parts = joinpointIdentification.split(".");
  return parts.length >= 2 ? `${parts[parts.length - 2]}#${parts[parts.length - 1]}` : joinpointIdentification;
}
