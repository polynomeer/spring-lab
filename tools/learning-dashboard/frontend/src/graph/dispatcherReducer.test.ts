import { describe, expect, it } from "vitest";

import { reduceDispatcherFlow } from "./dispatcherReducer";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number): SemanticEvent {
  return { type, sourceHitId, attributes: {} };
}

// 공용 알고리즘 자체(순서 누적, 리셋, out-of-order 등)는 pipelineReducer.test.ts가 이미
// 검증한다 - 여기서는 이 리듀서가 DispatcherFlowInterpreter의 5개 이벤트 타입을 올바른
// 순서/라벨의 단계로, REQUEST_RECEIVED를 리셋 기준으로 정확히 연결했는지만 확인한다.
describe("reduceDispatcherFlow", () => {
  it("defines the 5 dispatcher stages in the documented order", () => {
    const stages = reduceDispatcherFlow([]);
    expect(stages.map((s) => s.id)).toEqual(["dispatch", "handler-mapping", "interceptor", "controller", "exception-resolver"]);
    expect(stages.map((s) => s.label)).toEqual([
      "DispatcherServlet",
      "HandlerMapping",
      "Interceptor",
      "Controller",
      "ExceptionResolver",
    ]);
  });

  it("resets on REQUEST_RECEIVED and advances through a full successful request", () => {
    const stages = reduceDispatcherFlow([
      event("REQUEST_RECEIVED", 1),
      event("HANDLER_LOOKUP_STARTED", 2),
      event("INTERCEPTOR_CHAIN_STARTED", 3),
      event("CONTROLLER_INVOKED", 4),
    ]);

    expect(stages.map((s) => s.status)).toEqual(["done", "done", "done", "active", "pending"]);
    expect(stages[3].hitId).toBe(4);
  });

  it("reaches the exception-resolver stage when the controller throws", () => {
    const stages = reduceDispatcherFlow([
      event("REQUEST_RECEIVED", 1),
      event("HANDLER_LOOKUP_STARTED", 2),
      event("CONTROLLER_INVOKED", 3),
      event("EXCEPTION_RESOLUTION_STARTED", 4),
    ]);

    expect(stages[4]).toMatchObject({ id: "exception-resolver", status: "active", hitId: 4 });
  });
});
