import { describe, expect, it } from "vitest";

import { reducePipeline, type PipelineStageSpec } from "./pipelineReducer";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number): SemanticEvent {
  return { type, sourceHitId, attributes: {} };
}

const STAGES: PipelineStageSpec[] = [
  { type: "A", id: "a", label: "Stage A" },
  { type: "B", id: "b", label: "Stage B" },
  { type: "C", id: "c", label: "Stage C" },
];

describe("reducePipeline", () => {
  it("returns every stage as pending when the reset event never happened", () => {
    const stages = reducePipeline([event("B", 1)], STAGES, "A");
    expect(stages).toEqual([
      { id: "a", label: "Stage A", status: "pending" },
      { id: "b", label: "Stage B", status: "pending" },
      { id: "c", label: "Stage C", status: "pending" },
    ]);
  });

  it("marks the reset stage itself active when nothing has progressed past it yet", () => {
    const stages = reducePipeline([event("A", 1)], STAGES, "A");
    expect(stages[0]).toEqual({ id: "a", label: "Stage A", status: "active", hitId: 1 });
    expect(stages[1].status).toBe("pending");
  });

  it("marks stages up to the furthest reached as done, the furthest as active, and the rest pending", () => {
    const stages = reducePipeline([event("A", 1), event("B", 2)], STAGES, "A");

    expect(stages[0]).toEqual({ id: "a", label: "Stage A", status: "done", hitId: 1 });
    expect(stages[1]).toEqual({ id: "b", label: "Stage B", status: "active", hitId: 2 });
    expect(stages[2]).toEqual({ id: "c", label: "Stage C", status: "pending" });
  });

  it("only looks at events from the most recent reset onward, discarding an earlier run's progress", () => {
    const events = [event("A", 1), event("B", 2), event("C", 3), event("A", 4)];

    const stages = reducePipeline(events, STAGES, "A");

    expect(stages[0]).toEqual({ id: "a", label: "Stage A", status: "active", hitId: 4 });
    expect(stages[1].status).toBe("pending");
    expect(stages[2].status).toBe("pending");
  });

  it("ignores event types that aren't part of the stage order", () => {
    const stages = reducePipeline([event("A", 1), event("NOISE", 99), event("B", 2)], STAGES, "A");
    expect(stages[1]).toEqual({ id: "b", label: "Stage B", status: "active", hitId: 2 });
  });

  it("keeps the hitId of the last hit when a stage is reached more than once after reset", () => {
    const stages = reducePipeline([event("A", 1), event("B", 2), event("B", 3)], STAGES, "A");
    expect(stages[1]).toEqual({ id: "b", label: "Stage B", status: "active", hitId: 3 });
  });

  it("marks the furthest-index stage active even if events arrive out of stageOrder's order", () => {
    const stages = reducePipeline([event("A", 1), event("C", 2), event("B", 3)], STAGES, "A");

    expect(stages[0].status).toBe("done");
    expect(stages[2]).toEqual({ id: "c", label: "Stage C", status: "active", hitId: 2 });
    expect(stages[1].status).toBe("done");
  });
});
