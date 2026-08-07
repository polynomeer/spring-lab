import type { SemanticEvent } from "../types";
import type { Lane } from "./types";

/**
 * lab.dashboard.interpret.EventMulticastInterpreter의 6개 이벤트 타입을 레인으로 옮긴다.
 * tx-propagation과 달리 레인을 가를 만한 엔티티 식별자가 없다(리스너 식별 불가 - 인터프리터
 * Javadoc 참고) - 그래서 레인 키를 이벤트 타입 자체로 쓴다. 같은 타입의 여러 히트(예: 리스너
 * 7개 각각의 invokeListener)는 한 레인 안에 순서대로 쌓인다 - hitId가 각 마커에 그대로
 * 찍히므로 어느 히트인지는 여전히 클릭해서 추적할 수 있다(공용 시간축의 order로 다른 레인과도
 * 나란히 비교된다).
 */
const LANE_ORDER: { type: string; id: string; label: string }[] = [
  { type: "EVENT_PUBLISHED", id: "publish", label: "publishEvent" },
  { type: "MULTICAST_STARTED", id: "multicast", label: "multicastEvent" },
  { type: "LISTENER_INVOKED", id: "invoke-listener", label: "invokeListener" },
  { type: "ASYNC_LISTENER_EXECUTING", id: "async-listener", label: "asyncListener (매번 새 스레드)" },
  { type: "TX_LISTENER_EVENT_RECEIVED", id: "tx-received", label: "TX 리스너 (수신)" },
  { type: "TX_LISTENER_INVOKED", id: "tx-invoked", label: "TX 리스너 (커밋 후 실행)" },
];

export function reduceEventMulticast(events: SemanticEvent[]): Lane[] {
  const lanes = new Map<string, Lane>();

  events.forEach((event, order) => {
    const laneSpec = LANE_ORDER.find((spec) => spec.type === event.type);
    if (!laneSpec) return;

    let lane = lanes.get(laneSpec.id);
    if (!lane) {
      lane = { id: laneSpec.id, label: laneSpec.label, events: [] };
      lanes.set(laneSpec.id, lane);
    }
    lane.events.push({ type: event.type, hitId: event.sourceHitId, order });
  });

  return LANE_ORDER.map((spec) => lanes.get(spec.id)).filter((lane): lane is Lane => lane !== undefined);
}
