import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { HitInspector } from "./HitInspector";
import type { TraceEvent } from "../types";

function hitWith(overrides: Partial<TraceEvent>): TraceEvent {
  return {
    hitId: 1,
    timestampNanos: 0,
    thread: "main",
    location: { className: "com.example.BeanFactory", methodName: "getBean", line: 184 },
    stack: [],
    locals: [],
    localsAvailable: true,
    ...overrides,
  };
}

describe("HitInspector", () => {
  it("shows a hint when no hit is selected", () => {
    render(<HitInspector hit={null} />);
    expect(screen.getByText("히트를 선택하면 스택과 지역 변수가 여기 보입니다.")).toBeInTheDocument();
  });

  it("renders the location, stack frames, and local variables", () => {
    const hit = hitWith({
      stack: [
        { className: "com.example.BeanFactory", methodName: "getBean" },
        { className: "com.example.Caller", methodName: "run" },
      ],
      locals: [{ name: "beanName", type: "String", value: "\"aopConfig\"" }],
    });
    render(<HitInspector hit={hit} />);

    expect(screen.getByText("com.example.BeanFactory#getBean (line 184)")).toBeInTheDocument();
    expect(screen.getByText("[0] BeanFactory#getBean")).toBeInTheDocument();
    expect(screen.getByText("[1] Caller#run")).toBeInTheDocument();
    expect(screen.getByText("beanName")).toBeInTheDocument();
    expect(screen.getByText(/"aopConfig"/)).toBeInTheDocument();
    expect(screen.getByText("(String)")).toBeInTheDocument();
  });

  it("explains missing debug info when locals aren't available", () => {
    render(<HitInspector hit={hitWith({ localsAvailable: false })} />);
    expect(screen.getByText("(no local variable debug info in this jar)")).toBeInTheDocument();
  });

  it("shows a distinct message when locals are available but empty at this line", () => {
    render(<HitInspector hit={hitWith({ localsAvailable: true, locals: [] })} />);
    expect(screen.getByText("(no visible local variables at this line)")).toBeInTheDocument();
  });
});
