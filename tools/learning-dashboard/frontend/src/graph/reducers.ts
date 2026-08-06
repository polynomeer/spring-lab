import type { SemanticEvent } from "../types";
import { shortName, type GraphEdge, type GraphNode } from "./types";

/**
 * lab.dashboard.interpret.BeanLifecycleInterpreter가 내보내는 semantic 이벤트를 그래프
 * 데이터로 옮긴다 - requestedBy 속성(백엔드가 populateBean 중이던 빈을 기억해 뒀다가 붙여
 * 준다)이 엣지의 유일한 근거다. 그 밖의 인과 관계는 지어내지 않는다.
 */
export function reduceBeanLifecycle(events: SemanticEvent[]): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const nodes = new Map<string, GraphNode>();
  const edgeKeys = new Set<string>();
  const edges: GraphEdge[] = [];

  const ensure = (id: string): GraphNode => {
    let node = nodes.get(id);
    if (!node) {
      node = { id, label: shortName(id), status: "registered" };
      nodes.set(id, node);
    }
    return node;
  };

  for (const event of events) {
    const beanName = event.attributes.beanName;
    if (!beanName) continue;
    const node = ensure(beanName);

    switch (event.type) {
      case "BEAN_CREATION_STARTED":
        node.status = "creating";
        break;
      case "SINGLETON_FACTORY_REGISTERED":
        node.status = "registered";
        break;
      case "PROPERTY_INJECTION_STARTED":
        node.status = "populating";
        break;
      case "EARLY_REFERENCE_REQUESTED":
        node.status = "early-ref-requested";
        break;
      case "EARLY_REFERENCE_PROXIED":
        node.status = "early-ref-proxied";
        break;
      case "BEAN_INITIALIZATION_STARTED":
        node.status = "initializing";
        break;
      default:
        break;
    }

    const requestedBy = event.attributes.requestedBy;
    if (requestedBy) {
      ensure(requestedBy);
      const key = `${requestedBy}->${beanName}`;
      if (!edgeKeys.has(key)) {
        edgeKeys.add(key);
        edges.push({ from: requestedBy, to: beanName });
      }
    }
  }

  return { nodes: [...nodes.values()], edges };
}

const ADVISORS_NODE = "advisors";

/**
 * lab.dashboard.interpret.AutoProxyInterpreter의 이벤트를 그래프로 옮긴다. canApply의
 * 개별 결과는 해석기가 애초에 만들어내지 않으므로(진입 브레이크포인트만으로는 반환값을
 * 알 수 없다) 여기서도 지어내지 않는다 - "advisors"라는 고정 허브 노드에서, 지금 평가
 * 중이거나 재귀적으로 인스턴스화된 빈으로만 엣지를 긋는다.
 */
export function reduceAutoProxy(events: SemanticEvent[]): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const nodes = new Map<string, GraphNode>();
  const edgeKeys = new Set<string>();
  const edges: GraphEdge[] = [];

  const ensure = (id: string, label: string, defaultStatus: string): GraphNode => {
    let node = nodes.get(id);
    if (!node) {
      node = { id, label, status: defaultStatus };
      nodes.set(id, node);
    }
    return node;
  };

  const linkFromAdvisors = (beanName: string) => {
    ensure(ADVISORS_NODE, "advisors", "advisor-pool");
    const key = `${ADVISORS_NODE}->${beanName}`;
    if (!edgeKeys.has(key)) {
      edgeKeys.add(key);
      edges.push({ from: ADVISORS_NODE, to: beanName });
    }
  };

  for (const event of events) {
    const beanName = event.attributes.beanName;
    if (!beanName) continue;

    switch (event.type) {
      case "ADVISOR_LOOKUP_STARTED":
        ensure(beanName, shortName(beanName), "evaluating").status = "evaluating";
        linkFromAdvisors(beanName);
        break;
      case "ADVISOR_EAGERLY_INSTANTIATED":
        ensure(beanName, shortName(beanName), "advisor").status = "advisor";
        break;
      case "PROXY_CREATED":
        ensure(beanName, shortName(beanName), "evaluating").status = "proxied";
        break;
      case "PROXY_SKIPPED":
        ensure(beanName, shortName(beanName), "evaluating").status = "skipped";
        break;
      default:
        break;
    }
  }

  return { nodes: [...nodes.values()], edges };
}
