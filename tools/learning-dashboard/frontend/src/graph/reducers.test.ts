import { describe, expect, it } from "vitest";

import { reduceAutoProxy, reduceBeanLifecycle } from "./reducers";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number, attributes: Record<string, string> = {}): SemanticEvent {
  return { type, sourceHitId, attributes };
}

describe("reduceBeanLifecycle", () => {
  it("ignores events without a beanName attribute", () => {
    const result = reduceBeanLifecycle([event("BEAN_CREATION_STARTED", 1)]);
    expect(result.nodes).toEqual([]);
    expect(result.edges).toEqual([]);
  });

  it("creates a node per bean, labeled with its short name, defaulting to registered", () => {
    const result = reduceBeanLifecycle([event("SOME_UNMAPPED_TYPE", 1, { beanName: "com.example.AopConfig" })]);
    expect(result.nodes).toEqual([{ id: "com.example.AopConfig", label: "AopConfig", status: "registered" }]);
  });

  it("moves a bean's status through the known lifecycle event types", () => {
    const beanName = "aopConfig";
    const events = [
      event("BEAN_CREATION_STARTED", 1, { beanName }),
      event("SINGLETON_FACTORY_REGISTERED", 2, { beanName }),
      event("PROPERTY_INJECTION_STARTED", 3, { beanName }),
      event("EARLY_REFERENCE_REQUESTED", 4, { beanName }),
      event("EARLY_REFERENCE_PROXIED", 5, { beanName }),
      event("BEAN_INITIALIZATION_STARTED", 6, { beanName }),
    ];
    const expectedStatuses = ["creating", "registered", "populating", "early-ref-requested", "early-ref-proxied", "initializing"];

    for (let i = 0; i < events.length; i++) {
      const result = reduceBeanLifecycle(events.slice(0, i + 1));
      expect(result.nodes[0].status).toBe(expectedStatuses[i]);
    }
  });

  it("draws a deduplicated edge from requestedBy to the bean, and ensures the requester also has a node", () => {
    const events = [
      event("PROPERTY_INJECTION_STARTED", 1, { beanName: "child", requestedBy: "parent" }),
      event("PROPERTY_INJECTION_STARTED", 2, { beanName: "child", requestedBy: "parent" }),
    ];

    const result = reduceBeanLifecycle(events);

    expect(result.edges).toEqual([{ from: "parent", to: "child" }]);
    expect(result.nodes.map((n) => n.id).sort()).toEqual(["child", "parent"]);
  });
});

describe("reduceAutoProxy", () => {
  it("ignores events without a beanName attribute", () => {
    const result = reduceAutoProxy([event("PROXY_CREATED", 1)]);
    expect(result.nodes).toEqual([]);
  });

  it("links the advisors hub to a bean under evaluation, and marks it evaluating", () => {
    const result = reduceAutoProxy([event("ADVISOR_LOOKUP_STARTED", 1, { beanName: "svc" })]);

    expect(result.edges).toEqual([{ from: "advisors", to: "svc" }]);
    const svcNode = result.nodes.find((n) => n.id === "svc");
    expect(svcNode).toMatchObject({ label: "svc", status: "evaluating" });
    const advisorsNode = result.nodes.find((n) => n.id === "advisors");
    expect(advisorsNode).toMatchObject({ label: "advisors", status: "advisor-pool" });
  });

  it("does not duplicate the advisors edge across repeated lookups of the same bean", () => {
    const result = reduceAutoProxy([
      event("ADVISOR_LOOKUP_STARTED", 1, { beanName: "svc" }),
      event("ADVISOR_LOOKUP_STARTED", 2, { beanName: "svc" }),
    ]);
    expect(result.edges).toHaveLength(1);
  });

  it("moves a bean's status to proxied or skipped without needing a prior lookup event", () => {
    const proxied = reduceAutoProxy([event("PROXY_CREATED", 1, { beanName: "svc" })]);
    expect(proxied.nodes[0].status).toBe("proxied");

    const skipped = reduceAutoProxy([event("PROXY_SKIPPED", 1, { beanName: "svc" })]);
    expect(skipped.nodes[0].status).toBe("skipped");
  });

  it("marks eagerly-instantiated advisors distinctly, without linking them from the advisors hub", () => {
    const result = reduceAutoProxy([event("ADVISOR_EAGERLY_INSTANTIATED", 1, { beanName: "someAdvisor" })]);

    expect(result.nodes[0].status).toBe("advisor");
    expect(result.edges).toEqual([]);
  });
});
