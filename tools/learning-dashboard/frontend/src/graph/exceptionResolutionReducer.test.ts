import { describe, expect, it } from "vitest";

import { reduceExceptionResolution } from "./exceptionResolutionReducer";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number): SemanticEvent {
  return { type, sourceHitId, attributes: {} };
}

// 공용 알고리즘은 pipelineReducer.test.ts가 검증한다 - 여기서는 이 리듀서가
// ExceptionResolutionInterpreter의 5개 이벤트 타입을 올바른 순서/라벨로, EXCEPTION_HANDLING_STARTED를
// 리셋 기준으로 정확히 연결했는지만 확인한다.
describe("reduceExceptionResolution", () => {
  it("defines the 5 resolver stages in the documented order", () => {
    const stages = reduceExceptionResolution([]);
    expect(stages.map((s) => s.id)).toEqual(["dispatch", "composite", "exception-handler", "response-status", "default"]);
  });

  it("resets on EXCEPTION_HANDLING_STARTED and marks the resolver that actually handled it as active", () => {
    const stages = reduceExceptionResolution([
      event("EXCEPTION_HANDLING_STARTED", 1),
      event("RESOLVER_CHAIN_ENTERED", 2),
      event("EXCEPTION_HANDLER_LOOKUP_STARTED", 3),
    ]);

    expect(stages.map((s) => s.status)).toEqual(["done", "done", "active", "pending", "pending"]);
    expect(stages[2].hitId).toBe(3);
  });

  it("keeps only the last hit's id when ResponseStatusResolver is hit more than once via a cause chain", () => {
    const stages = reduceExceptionResolution([
      event("EXCEPTION_HANDLING_STARTED", 1),
      event("RESPONSE_STATUS_RESOLUTION_STARTED", 2),
      event("RESPONSE_STATUS_RESOLUTION_STARTED", 3),
    ]);

    expect(stages[3]).toMatchObject({ id: "response-status", status: "active", hitId: 3 });
  });
});
