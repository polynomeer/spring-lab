import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TransportControls } from "./TransportControls";

function setup(overrides: Partial<React.ComponentProps<typeof TransportControls>> = {}) {
  const onStep = vi.fn();
  const onPlay = vi.fn();
  const onPause = vi.fn();
  const onReset = vi.fn();
  const props = {
    connected: true,
    running: false,
    onStep,
    onPlay,
    onPause,
    onReset,
    ...overrides,
  };
  const view = render(<TransportControls {...props} />);
  return { ...view, onStep, onPlay, onPause, onReset };
}

describe("TransportControls", () => {
  it("shows Play when not running and Pause when running", () => {
    const { rerender } = setup();
    expect(screen.getByText("▶ Play")).toBeInTheDocument();

    rerender(
      <TransportControls
        connected
        running
        onStep={vi.fn()}
        onPlay={vi.fn()}
        onPause={vi.fn()}
        onReset={vi.fn()}
      />,
    );
    expect(screen.getByText("❚❚ Pause")).toBeInTheDocument();
  });

  it("disables all transport buttons when disconnected", () => {
    setup({ connected: false });
    expect(screen.getByText("⏭ Step")).toBeDisabled();
    expect(screen.getByText("▶ Play")).toBeDisabled();
    expect(screen.getByText("↺ Reset")).toBeDisabled();
  });

  it("calls onStep/onPlay/onReset from button clicks, defaulting to the 1× speed", async () => {
    const user = userEvent.setup();
    const { onStep, onPlay, onReset } = setup();

    await user.click(screen.getByText("⏭ Step"));
    expect(onStep).toHaveBeenCalledTimes(1);

    await user.click(screen.getByText("▶ Play"));
    expect(onPlay).toHaveBeenCalledWith(500);

    await user.click(screen.getByText("↺ Reset"));
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it("Space toggles play/pause via keyboard", async () => {
    const user = userEvent.setup();
    const { onPlay } = setup({ running: false });
    await user.keyboard(" ");
    expect(onPlay).toHaveBeenCalledWith(500);
  });

  it("ArrowRight steps and R resets via keyboard", async () => {
    const user = userEvent.setup();
    const { onStep, onReset } = setup();

    await user.keyboard("{ArrowRight}");
    expect(onStep).toHaveBeenCalledTimes(1);

    await user.keyboard("r");
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it("ignores shortcuts while disconnected", async () => {
    const user = userEvent.setup();
    const { onStep, onPlay, onReset } = setup({ connected: false });

    await user.keyboard(" {ArrowRight}r");

    expect(onStep).not.toHaveBeenCalled();
    expect(onPlay).not.toHaveBeenCalled();
    expect(onReset).not.toHaveBeenCalled();
  });

  it("ignores shortcuts typed into a text input so they don't collide with typing", async () => {
    const user = userEvent.setup();
    const { onReset } = setup();

    const input = document.createElement("input");
    document.body.appendChild(input);
    input.focus();

    await user.keyboard("r");

    expect(onReset).not.toHaveBeenCalled();
    document.body.removeChild(input);
  });

  it("ignores shortcuts with a modifier key held", async () => {
    const user = userEvent.setup();
    const { onReset } = setup();

    await user.keyboard("{Control>}r{/Control}");

    expect(onReset).not.toHaveBeenCalled();
  });

  it("changes the play speed via the select, and replays immediately if already running", async () => {
    const user = userEvent.setup();
    const { onPlay } = setup({ running: true });

    await user.selectOptions(screen.getByRole("combobox"), "2×");

    expect(onPlay).toHaveBeenCalledWith(250);
  });
});
