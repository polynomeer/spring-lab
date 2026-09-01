import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { RequestPresets } from "./RequestPresets";
import type { Preset } from "../requestPresets";

const presets: Preset[] = [
  { label: "GET /health", method: "GET", path: "/health" },
  { label: "POST /orders", method: "POST", path: "/orders", body: '{"id":1}' },
];

describe("RequestPresets", () => {
  it("renders a button per preset, with a body hint in the title", () => {
    render(<RequestPresets presets={presets} disabled={false} onSend={vi.fn()} />);

    expect(screen.getByText("GET /health")).not.toHaveAttribute("title");
    expect(screen.getByText("POST /orders")).toHaveAttribute("title", 'body: {"id":1}');
  });

  it("calls onSend with the preset's method/path/body when clicked", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<RequestPresets presets={presets} disabled={false} onSend={onSend} />);

    await user.click(screen.getByText("POST /orders"));

    expect(onSend).toHaveBeenCalledWith("POST", "/orders", '{"id":1}');
  });

  it("disables every button when disabled is true", () => {
    render(<RequestPresets presets={presets} disabled onSend={vi.fn()} />);

    for (const button of screen.getAllByRole("button")) {
      expect(button).toBeDisabled();
    }
  });
});
