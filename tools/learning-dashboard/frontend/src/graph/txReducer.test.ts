import { describe, expect, it } from "vitest";

import { reduceTxPropagation } from "./txReducer";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number, attributes: Record<string, string> = {}): SemanticEvent {
  return { type, sourceHitId, attributes };
}

describe("reduceTxPropagation", () => {
  it("ignores events without a joinpointIdentification attribute", () => {
    expect(reduceTxPropagation([event("TX_STARTED", 1)])).toEqual([]);
  });

  it("creates one lane per joinpoint, labeled Class#method (the last two dot-separated segments)", () => {
    const lanes = reduceTxPropagation([
      event("TX_STARTED", 1, { joinpointIdentification: "com.example.OrderService.placeOrder" }),
    ]);

    expect(lanes).toHaveLength(1);
    expect(lanes[0]).toMatchObject({ id: "com.example.OrderService.placeOrder", label: "OrderService#placeOrder" });
  });

  it("falls back to the raw identification string when it has no dot to split on", () => {
    const lanes = reduceTxPropagation([event("TX_STARTED", 1, { joinpointIdentification: "placeOrder" })]);
    expect(lanes[0].label).toBe("placeOrder");
  });

  it("keeps separate lanes for separate joinpoints, in first-seen order", () => {
    const lanes = reduceTxPropagation([
      event("TX_STARTED", 1, { joinpointIdentification: "a.Service.methodB" }),
      event("TX_STARTED", 2, { joinpointIdentification: "a.Service.methodA" }),
    ]);
    expect(lanes.map((l) => l.id)).toEqual(["a.Service.methodB", "a.Service.methodA"]);
  });

  it("records each event's order as its index across the whole stream, not per-lane, so lanes stay time-aligned", () => {
    const lanes = reduceTxPropagation([
      event("TX_STARTED", 1, { joinpointIdentification: "a.Service.outer" }),
      event("TX_STARTED", 2, { joinpointIdentification: "a.Service.inner" }),
      event("TX_SUSPENDED", 3, { joinpointIdentification: "a.Service.outer" }),
    ]);

    const outerLane = lanes.find((l) => l.id === "a.Service.outer")!;
    expect(outerLane.events.map((e) => e.order)).toEqual([0, 2]);
  });

  it("passes the unexpected attribute through onto the lane event", () => {
    const lanes = reduceTxPropagation([
      event("TX_ROLLED_BACK", 1, { joinpointIdentification: "a.Service.m", unexpected: "true" }),
    ]);
    expect(lanes[0].events[0].unexpected).toBe("true");
  });
});
