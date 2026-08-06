import { useMemo } from "react";

import { reduceAutoProxy, reduceBeanLifecycle } from "../graph/reducers";
import { AUTO_PROXY_STATUS_META, BEAN_LIFECYCLE_STATUS_META } from "../graph/statusMeta";
import type { StatusMeta } from "../graph/types";
import type { SemanticEvent } from "../types";
import { StatusGraph } from "./StatusGraph";
import { TxSwimlane } from "./TxSwimlane";

interface Props {
  scenarioKey: string;
  semanticEvents: SemanticEvent[];
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

export function ScenarioVisualization({ scenarioKey, semanticEvents }: Props) {
  const beanGraph = useMemo(() => reduceBeanLifecycle(semanticEvents), [semanticEvents]);
  const proxyGraph = useMemo(() => reduceAutoProxy(semanticEvents), [semanticEvents]);

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
        <TxSwimlane events={semanticEvents} />
      </div>
    );
  }

  return (
    <div className="viz-frame placeholder">
      <p>이 시나리오는 아직 라이브로 실행할 수 없습니다(설계 문서 4단계 예정).</p>
    </div>
  );
}
