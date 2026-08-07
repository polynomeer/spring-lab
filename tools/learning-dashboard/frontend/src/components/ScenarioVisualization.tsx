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
import { Pipeline } from "./Pipeline";
import { RequestPresets } from "./RequestPresets";
import { StatusGraph } from "./StatusGraph";
import { Swimlane } from "./Swimlane";

interface Props {
  scenarioKey: string;
  semanticEvents: SemanticEvent[];
  connected?: boolean;
  onSendRequest?: (method: string, path: string, body?: string) => void;
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

export function ScenarioVisualization({ scenarioKey, semanticEvents, connected, onSendRequest }: Props) {
  const beanGraph = useMemo(() => reduceBeanLifecycle(semanticEvents), [semanticEvents]);
  const proxyGraph = useMemo(() => reduceAutoProxy(semanticEvents), [semanticEvents]);
  const pipelineStages = useMemo(() => reduceDispatcherFlow(semanticEvents), [semanticEvents]);
  const exceptionStages = useMemo(() => reduceExceptionResolution(semanticEvents), [semanticEvents]);
  const txLanes = useMemo(() => reduceTxPropagation(semanticEvents), [semanticEvents]);
  const eventLanes = useMemo(() => reduceEventMulticast(semanticEvents), [semanticEvents]);

  if (scenarioKey === "bean-lifecycle") {
    return (
      <div className="viz-frame">
        <StatusGraph
          nodes={beanGraph.nodes}
          edges={beanGraph.edges}
          statusMeta={BEAN_LIFECYCLE_STATUS_META}
          emptyHint="재생하면 빈이 여기 노드로 나타납니다."
        />
        {beanGraph.nodes.length > 0 && <Legend statusMeta={BEAN_LIFECYCLE_STATUS_META} />}
      </div>
    );
  }

  if (scenarioKey === "aop-proxy") {
    return (
      <div className="viz-frame">
        <StatusGraph
          nodes={proxyGraph.nodes}
          edges={proxyGraph.edges}
          statusMeta={AUTO_PROXY_STATUS_META}
          emptyHint="재생하면 advisor와 대상 빈이 여기 노드로 나타납니다."
        />
        {proxyGraph.nodes.length > 0 && <Legend statusMeta={AUTO_PROXY_STATUS_META} />}
      </div>
    );
  }

  if (scenarioKey === "tx-propagation") {
    return (
      <div className="viz-frame">
        <Swimlane
          lanes={txLanes}
          markerMeta={TX_MARKER_META}
          emptyHint="재생하면 @Transactional 메서드 경계마다 레인이 여기 나타납니다."
        />
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
        <Pipeline stages={pipelineStages} emptyHint="위에서 요청을 보내면 여기 파이프라인이 단계별로 채워집니다." />
      </div>
    );
  }

  if (scenarioKey === "event-multicast") {
    return (
      <div className="viz-frame">
        <Swimlane
          lanes={eventLanes}
          markerMeta={EVENT_MULTICAST_MARKER_META}
          emptyHint="재생하면 publishEvent/multicastEvent/리스너 호출이 여기 레인으로 나타납니다."
        />
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
        <Pipeline stages={exceptionStages} emptyHint="위에서 요청을 보내면 어느 리졸버가 처리했는지 여기 파이프라인으로 나타납니다." />
      </div>
    );
  }

  return (
    <div className="viz-frame placeholder">
      <p>이 시나리오는 아직 라이브로 실행할 수 없습니다.</p>
    </div>
  );
}
