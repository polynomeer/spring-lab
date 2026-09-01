import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ScenarioTabs } from "./ScenarioTabs";
import type { ScenarioMeta } from "../types";

function meta(key: string, title: string, live = true): ScenarioMeta {
  return { key, title, description: "", live };
}

describe("ScenarioTabs", () => {
  it("renders a tab per scenario and marks the active one", () => {
    render(<ScenarioTabs scenarios={[meta("a", "Bean Lifecycle"), meta("b", "AOP Proxy")]} active="b" onSelect={vi.fn()} />);

    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(2);
    expect(screen.getByText("Bean Lifecycle").closest("button")).toHaveAttribute("data-active", "false");
    expect(screen.getByText("AOP Proxy").closest("button")).toHaveAttribute("data-active", "true");
  });

  it("disables and badges a tab whose scenario isn't live yet", () => {
    render(<ScenarioTabs scenarios={[meta("c", "Coming Soon", false)]} active="c" onSelect={vi.fn()} />);

    expect(screen.getByRole("tab")).toBeDisabled();
    expect(screen.getByText("SOON")).toBeInTheDocument();
  });

  it("calls onSelect with the clicked scenario's key", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<ScenarioTabs scenarios={[meta("a", "Bean Lifecycle"), meta("b", "AOP Proxy")]} active="a" onSelect={onSelect} />);

    await user.click(screen.getByText("AOP Proxy"));

    expect(onSelect).toHaveBeenCalledWith("b");
  });
});
