import { describe, expect, it } from "vitest";

import { reduceEventMulticast } from "./eventMulticastReducer";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number): SemanticEvent {
  return { type, sourceHitId, attributes: {} };
}

describe("reduceEventMulticast", () => {
  it("returns no lanes when there are no matching events", () => {
    expect(reduceEventMulticast([event("NOISE", 1)])).toEqual([]);
  });

  it("only returns lanes that had at least one matching event, in the fixed LANE_ORDER (not first-seen order)", () => {
    const lanes = reduceEventMulticast([
      event("LISTENER_INVOKED", 1),
      event("EVENT_PUBLISHED", 2),
    ]);

    // LISTENER_INVOKED가 배열에서는 먼저 나오지만, 고정된 LANE_ORDER상 publish가 앞선다.
    expect(lanes.map((l) => l.id)).toEqual(["publish", "invoke-listener"]);
  });

  it("accumulates repeated hits of the same type into one lane, in arrival order", () => {
    const lanes = reduceEventMulticast([
      event("LISTENER_INVOKED", 1),
      event("LISTENER_INVOKED", 2),
      event("LISTENER_INVOKED", 3),
    ]);

    expect(lanes).toHaveLength(1);
    expect(lanes[0].events.map((e) => e.hitId)).toEqual([1, 2, 3]);
  });

  it("records each event's order as its index across the whole stream", () => {
    const lanes = reduceEventMulticast([
      event("EVENT_PUBLISHED", 1),
      event("MULTICAST_STARTED", 2),
      event("LISTENER_INVOKED", 3),
    ]);

    const invokeLane = lanes.find((l) => l.id === "invoke-listener")!;
    expect(invokeLane.events[0].order).toBe(2);
  });

  it("assigns the documented label to each lane", () => {
    const lanes = reduceEventMulticast([event("ASYNC_LISTENER_EXECUTING", 1)]);
    expect(lanes[0]).toMatchObject({ id: "async-listener", label: "asyncListener (매번 새 스레드)" });
  });
});
