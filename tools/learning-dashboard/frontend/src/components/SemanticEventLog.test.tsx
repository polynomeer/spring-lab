import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { SemanticEventLog } from "./SemanticEventLog";
import type { SemanticEvent } from "../types";

function event(type: string, sourceHitId: number, attributes: Record<string, string> = {}): SemanticEvent {
  return { type, sourceHitId, attributes };
}

describe("SemanticEventLog", () => {
  it("shows an empty hint and no filter box when there are no entries yet", () => {
    render(<SemanticEventLog entries={[]} />);
    expect(screen.getByText("아직 해석된 semantic 이벤트가 없습니다.")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/필터/)).not.toBeInTheDocument();
  });

  it("renders the event type, source hit, and attributes", () => {
    render(<SemanticEventLog entries={[event("SINGLETON_FACTORY_REGISTERED", 3, { beanName: "aopConfig" })]} />);

    expect(screen.getByText("SINGLETON_FACTORY_REGISTERED")).toBeInTheDocument();
    expect(screen.getByText("hit #3 · beanName=aopConfig")).toBeInTheDocument();
  });

  it("shows a 문서 marker only for event types with a known doc link", () => {
    render(
      <SemanticEventLog
        entries={[event("SINGLETON_FACTORY_REGISTERED", 1), event("SOME_UNDOCUMENTED_EVENT", 2)]}
      />,
    );

    const documented = screen.getByText("SINGLETON_FACTORY_REGISTERED").closest("button");
    const undocumented = screen.getByText("SOME_UNDOCUMENTED_EVENT").closest("button");
    expect(documented?.querySelector(".doc-marker")).toBeInTheDocument();
    expect(undocumented?.querySelector(".doc-marker")).not.toBeInTheDocument();
  });

  it("toggles the doc excerpt open and closed when a documented row is clicked", async () => {
    const user = userEvent.setup();
    render(<SemanticEventLog entries={[event("SINGLETON_FACTORY_REGISTERED", 1)]} />);

    expect(screen.queryByText(/3차 캐시는 그 결정을 나중으로 미루는/)).not.toBeInTheDocument();

    await user.click(screen.getByText("SINGLETON_FACTORY_REGISTERED"));
    expect(screen.getByText(/3차 캐시는 그 결정을 나중으로 미루는/)).toBeInTheDocument();

    await user.click(screen.getByText("SINGLETON_FACTORY_REGISTERED"));
    expect(screen.queryByText(/3차 캐시는 그 결정을 나중으로 미루는/)).not.toBeInTheDocument();
  });

  it("filters by event type or by any attribute key/value", async () => {
    const user = userEvent.setup();
    render(
      <SemanticEventLog
        entries={[
          event("SINGLETON_FACTORY_REGISTERED", 1, { beanName: "aopConfig" }),
          event("PROPERTY_INJECTION_STARTED", 2, { beanName: "proxiedCircularA" }),
        ]}
      />,
    );

    await user.type(screen.getByPlaceholderText(/필터/), "proxiedCircularA");

    expect(screen.queryByText("SINGLETON_FACTORY_REGISTERED")).not.toBeInTheDocument();
    expect(screen.getByText("PROPERTY_INJECTION_STARTED")).toBeInTheDocument();
  });

  it("shows a distinct empty state when the filter matches nothing", async () => {
    const user = userEvent.setup();
    render(<SemanticEventLog entries={[event("SINGLETON_FACTORY_REGISTERED", 1)]} />);

    await user.type(screen.getByPlaceholderText(/필터/), "no such event anywhere");

    expect(screen.getByText("필터에 맞는 이벤트가 없습니다.")).toBeInTheDocument();
  });
});
