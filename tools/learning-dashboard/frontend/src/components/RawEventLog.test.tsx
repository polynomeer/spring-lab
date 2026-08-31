import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { RawEventLog } from "./RawEventLog";
import type { ScenarioMessage, TraceEvent } from "../types";

function hit(hitId: number, methodName = "getBean", className = "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry"): ScenarioMessage {
  const event: TraceEvent = {
    hitId,
    timestampNanos: 0,
    thread: "main",
    location: { className, methodName, line: 184 },
    stack: [],
    locals: [],
    localsAvailable: false,
  };
  return { type: "hit", scenario: "bean-lifecycle", event };
}

describe("RawEventLog", () => {
  it("shows an empty hint and no filter box when there are no entries yet", () => {
    render(<RawEventLog entries={[]} />);
    expect(screen.getByText("시나리오를 선택하고 재생하면 여기 히트가 쌓입니다.")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/필터/)).not.toBeInTheDocument();
  });

  it("renders every entry type with its own row shape", () => {
    const entries: ScenarioMessage[] = [
      hit(1),
      { type: "stdout", scenario: "s", stream: "stdout", line: "hello" },
      { type: "exited", scenario: "s", totalHits: 3 },
      { type: "error", message: "boom" },
      { type: "httpResponse", scenario: "s", method: "GET", path: "/x", status: 200 },
      { type: "httpResponse", scenario: "s", method: "GET", path: "/y", error: "timeout" },
      { type: "semantic", scenario: "s", event: { type: "X", sourceHitId: 1, attributes: {} } },
    ];
    render(<RawEventLog entries={entries} />);

    expect(screen.getByText("EXITED")).toBeInTheDocument();
    expect(screen.getByText("total hits: 3")).toBeInTheDocument();
    expect(screen.getByText("ERROR")).toBeInTheDocument();
    expect(screen.getByText("boom")).toBeInTheDocument();
    expect(screen.getByText("[stdout] hello")).toBeInTheDocument();
    expect(screen.getByText("GET /x")).toBeInTheDocument();
    expect(screen.getByText("status 200")).toBeInTheDocument();
    expect(screen.getByText("GET /y")).toBeInTheDocument();
    expect(screen.getByText("timeout")).toBeInTheDocument();
    // semantic 이벤트는 이 패널이 아니라 SemanticEventLog가 보여준다 - 아무 행도 안 남긴다.
    expect(screen.queryByText("X")).not.toBeInTheDocument();
  });

  it("calls onSelectHit with the clicked hit's id", async () => {
    const user = userEvent.setup();
    const onSelectHit = vi.fn();
    render(<RawEventLog entries={[hit(7, "populateBean")]} onSelectHit={onSelectHit} />);

    await user.click(screen.getByText(/populateBean/));

    expect(onSelectHit).toHaveBeenCalledWith(7);
  });

  it("filters rows by class name, method name, thread, or message text", async () => {
    const user = userEvent.setup();
    render(
      <RawEventLog
        entries={[hit(1, "addSingletonFactory"), hit(2, "populateBean"), { type: "error", message: "classpath boom" }]}
      />,
    );

    await user.type(screen.getByPlaceholderText(/필터/), "populateBean");

    expect(screen.queryByText(/addSingletonFactory/)).not.toBeInTheDocument();
    expect(screen.getByText(/populateBean/)).toBeInTheDocument();
    expect(screen.queryByText("classpath boom")).not.toBeInTheDocument();
  });

  it("shows a distinct empty state when the filter matches nothing", async () => {
    const user = userEvent.setup();
    render(<RawEventLog entries={[hit(1)]} />);

    await user.type(screen.getByPlaceholderText(/필터/), "no such method anywhere");

    expect(screen.getByText("필터에 맞는 항목이 없습니다.")).toBeInTheDocument();
  });

  it("marks the selected hit's row and the diagram-hovered rows separately", () => {
    render(
      <RawEventLog
        entries={[hit(1, "methodA"), hit(2, "methodB"), hit(3, "methodC")]}
        selectedHitId={2}
        highlightedHitIds={new Set([1, 3])}
      />,
    );

    const rowFor = (label: string) => screen.getByText(new RegExp(label)).closest("button");
    expect(rowFor("methodA")).toHaveClass("highlighted");
    expect(rowFor("methodA")).not.toHaveClass("selected");
    expect(rowFor("methodB")).toHaveClass("selected");
    expect(rowFor("methodB")).not.toHaveClass("highlighted");
    expect(rowFor("methodC")).toHaveClass("highlighted");
  });
});
