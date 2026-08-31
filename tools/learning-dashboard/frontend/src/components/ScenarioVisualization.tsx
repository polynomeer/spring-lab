import { useMemo } from "react";

import { reduceDispatcherFlow } from "../graph/dispatcherReducer";
import { reduceEventMulticast } from "../graph/eventMulticastReducer";
import { reduceExceptionResolution } from "../graph/exceptionResolutionReducer";
import { reduceAutoProxy, reduceBeanLifecycle } from "../graph/reducers";
import {
  AUTO_PROXY_STATUS_META,
  BEAN_LIFECYCLE_STATUS_META,
  EVENT_MULTICAST_MARKER_META,
  TX_MARKER_META,
} from "../graph/statusMeta";
import { reduceTxPropagation } from "../graph/txReducer";
import type { StatusMeta } from "../graph/types";
import { DISPATCHER_FLOW_PRESETS, MVC_EXCEPTION_PRESETS } from "../requestPresets";
import type { SemanticEvent } from "../types";
import { DiagramCanvas } from "./DiagramCanvas";
import { Pipeline } from "./Pipeline";
import { RequestPresets } from "./RequestPresets";
import { StatusGraph } from "./StatusGraph";
import { Swimlane } from "./Swimlane";

interface Props {
  scenarioKey: string;
  semanticEvents: SemanticEvent[];
  connected?: boolean;
  onSendRequest?: (method: string, path: string, body?: string) => void;
  // RAW LOG ↔ 다이어그램 양방향 강조(로그에서 선택한 히트 → 다이어그램 노드/마커,
  // 다이어그램에 마우스를 올리면 → RAW LOG의 관련 히트들).
  selectedHitId?: number | null;
  onHoverHitIds?: (hitIds: Set<number> | null) => void;
}

function Legend({ statusMeta }: { statusMeta: Record<string, StatusMeta> }) {
  return (
    <div className="legend">
      {Object.values(statusMeta).map((meta) => (
        <span key={meta.label}>
          <i style={{ background: meta.color }} />
          {meta.label}
        </span>
      ))}
    </div>
  );
}

export function ScenarioVisualization({
  scenarioKey,
  semanticEvents,
  connected,
  onSendRequest,
  selectedHitId,
  onHoverHitIds,
}: Props) {
  const beanGraph = useMemo(() => reduceBeanLifecycle(semanticEvents), [semanticEvents]);
  const proxyGraph = useMemo(() => reduceAutoProxy(semanticEvents), [semanticEvents]);
  const pipelineStages = useMemo(() => reduceDispatcherFlow(semanticEvents), [semanticEvents]);
  const exceptionStages = useMemo(() => reduceExceptionResolution(semanticEvents), [semanticEvents]);
  const txLanes = useMemo(() => reduceTxPropagation(semanticEvents), [semanticEvents]);
  const eventLanes = useMemo(() => reduceEventMulticast(semanticEvents), [semanticEvents]);

  // StatusGraph의 노드는 여러 히트가 쌓여 만들어진 빈 하나를 대표한다 - beanName 하나에
  // 관련된 히트가 여러 개일 수 있으므로 Set으로 모아 둔다.
  const hitIdsByBeanName = useMemo(() => {
    const map = new Map<string, Set<number>>();
    for (const event of semanticEvents) {
      const beanName = event.attributes.beanName;
      if (!beanName) {
        continue;
      }
      if (!map.has(beanName)) {
        map.set(beanName, new Set());
      }
      map.get(beanName)?.add(event.sourceHitId);
    }
    return map;
  }, [semanticEvents]);

  // RAW LOG에서 선택된 히트가 어느 빈에 관한 것인지 역으로 찾는다 - StatusGraph 쪽 강조에만
  // 필요하다(Swimlane/Pipeline은 이벤트/단계가 hitId를 직접 갖고 있어 이 조회가 필요 없다).
  const highlightedBeanName = useMemo(() => {
    if (selectedHitId == null) {
      return null;
    }
    return semanticEvents.find((event) => event.sourceHitId === selectedHitId && event.attributes.beanName)
      ?.attributes.beanName ?? null;
  }, [semanticEvents, selectedHitId]);

  const handleHoverNode = (nodeId: string | null) => {
    onHoverHitIds?.(nodeId ? hitIdsByBeanName.get(nodeId) ?? new Set() : null);
  };

  const handleHoverHitId = (hitId: number | null) => {
    onHoverHitIds?.(hitId != null ? new Set([hitId]) : null);
  };

  if (scenarioKey === "bean-lifecycle") {
    return (
      <div className="viz-frame">
        <DiagramCanvas exportFilename="bean-lifecycle">
          <StatusGraph
            nodes={beanGraph.nodes}
            edges={beanGraph.edges}
            statusMeta={BEAN_LIFECYCLE_STATUS_META}
            emptyHint="재생하면 빈이 여기 노드로 나타납니다."
            highlightedNodeId={highlightedBeanName}
            onHoverNode={handleHoverNode}
          />
        </DiagramCanvas>
        {beanGraph.nodes.length > 0 && <Legend statusMeta={BEAN_LIFECYCLE_STATUS_META} />}
      </div>
    );
  }

  if (scenarioKey === "aop-proxy") {
    return (
      <div className="viz-frame">
        <DiagramCanvas exportFilename="aop-proxy">
          <StatusGraph
            nodes={proxyGraph.nodes}
            edges={proxyGraph.edges}
            statusMeta={AUTO_PROXY_STATUS_META}
            emptyHint="재생하면 advisor와 대상 빈이 여기 노드로 나타납니다."
            highlightedNodeId={highlightedBeanName}
            onHoverNode={handleHoverNode}
          />
        </DiagramCanvas>
        {proxyGraph.nodes.length > 0 && <Legend statusMeta={AUTO_PROXY_STATUS_META} />}
      </div>
    );
  }

  if (scenarioKey === "tx-propagation") {
    return (
      <div className="viz-frame">
        <DiagramCanvas exportFilename="tx-propagation">
          <Swimlane
            lanes={txLanes}
            markerMeta={TX_MARKER_META}
            emptyHint="재생하면 @Transactional 메서드 경계마다 레인이 여기 나타납니다."
            selectedHitId={selectedHitId}
            onHoverHitId={handleHoverHitId}
          />
        </DiagramCanvas>
      </div>
    );
  }

  if (scenarioKey === "dispatcher-flow") {
    return (
      <div className="viz-frame">
        <RequestPresets
          presets={DISPATCHER_FLOW_PRESETS}
          disabled={!connected}
          onSend={(method, path, body) => onSendRequest?.(method, path, body)}
        />
        <DiagramCanvas exportFilename="dispatcher-flow">
          <Pipeline
            stages={pipelineStages}
            emptyHint="위에서 요청을 보내면 여기 파이프라인이 단계별로 채워집니다."
            selectedHitId={selectedHitId}
            onHoverHitId={handleHoverHitId}
          />
        </DiagramCanvas>
      </div>
    );
  }

  if (scenarioKey === "event-multicast") {
    return (
      <div className="viz-frame">
        <DiagramCanvas exportFilename="event-multicast">
          <Swimlane
            lanes={eventLanes}
            markerMeta={EVENT_MULTICAST_MARKER_META}
            emptyHint="재생하면 publishEvent/multicastEvent/리스너 호출이 여기 레인으로 나타납니다."
            selectedHitId={selectedHitId}
            onHoverHitId={handleHoverHitId}
          />
        </DiagramCanvas>
      </div>
    );
  }

  if (scenarioKey === "mvc-exception-priority") {
    return (
      <div className="viz-frame">
        <RequestPresets
          presets={MVC_EXCEPTION_PRESETS}
          disabled={!connected}
          onSend={(method, path, body) => onSendRequest?.(method, path, body)}
        />
        <DiagramCanvas exportFilename="mvc-exception-priority">
          <Pipeline
            stages={exceptionStages}
            emptyHint="위에서 요청을 보내면 어느 리졸버가 처리했는지 여기 파이프라인으로 나타납니다."
            selectedHitId={selectedHitId}
            onHoverHitId={handleHoverHitId}
          />
        </DiagramCanvas>
      </div>
    );
  }

  return (
    <div className="viz-frame placeholder">
      <p>이 시나리오는 아직 라이브로 실행할 수 없습니다.</p>
    </div>
  );
}
