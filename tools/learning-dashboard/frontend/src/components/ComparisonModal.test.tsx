import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ComparisonModal } from "./ComparisonModal";
import type { SavedScenario, ScenarioMessage } from "../types";

const fetchScenarios = vi.fn();
vi.mock("../api/scenarioApi", () => ({
  fetchScenarios: (...args: unknown[]) => fetchScenarios(...args),
}));

const startComparison = vi.fn();
const stopComparison = vi.fn();
const sendComparisonHttpRequest = vi.fn();
let connected = true;
let capturedOnMessage: ((message: ScenarioMessage) => void) | null = null;

vi.mock("../stomp/useDashboardSocket", () => ({
  useDashboardSocket: (onMessage: (message: ScenarioMessage) => void) => {
    capturedOnMessage = onMessage;
    return {
      connected,
      startScenario: vi.fn(),
      sendCommand: vi.fn(),
      sendHttpRequest: vi.fn(),
      startComparison,
      stopComparison,
      sendComparisonHttpRequest,
    };
  },
}));

function scenario(name: string, title: string): SavedScenario {
  return {
    id: name.length,
    name,
    title,
    description: "",
    gradleModulePaths: [],
    mainClass: "",
    breakpointSpec: "",
    sourceCode: null,
    interpreterKind: "raw",
    createdAt: "2026-01-01T00:00:00Z",
  };
}

const scenarios = [scenario("bean-lifecycle", "Bean Lifecycle"), scenario("dispatcher-flow", "Dispatcher Flow")];

function deliver(message: ScenarioMessage) {
  act(() => capturedOnMessage?.(message));
}

function fieldSelects() {
  return screen.getAllByRole("combobox").filter((el) => el.closest(".field"));
}

async function waitForScenariosLoaded() {
  await waitFor(() => expect(fieldSelects()[0]).toHaveValue("bean-lifecycle"));
}

beforeEach(() => {
  connected = true;
  capturedOnMessage = null;
  startComparison.mockClear();
  stopComparison.mockClear();
  sendComparisonHttpRequest.mockClear();
  fetchScenarios.mockReset().mockResolvedValue(scenarios);
});

describe("ComparisonModal", () => {
  it("pre-selects the first two scenarios once loaded", async () => {
    render(<ComparisonModal onClose={vi.fn()} />);

    await waitForScenariosLoaded();
    expect(fieldSelects()[1]).toHaveValue("dispatcher-flow");
  });

  it("starts a comparison at the default 1x speed and disables setup controls", async () => {
    const user = userEvent.setup();
    render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();

    await user.click(screen.getByText("▶ 비교 시작"));

    expect(startComparison).toHaveBeenCalledWith("bean-lifecycle", "dispatcher-flow", 500);
    expect(screen.getByText("■ 비교 종료")).toBeInTheDocument();
    for (const select of fieldSelects()) {
      expect(select).toBeDisabled();
    }
  });

  it("disables the start button while disconnected", async () => {
    connected = false;
    render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();

    expect(screen.getByText("▶ 비교 시작")).toBeDisabled();
  });

  it("routes incoming messages to the matching side by scenario name", async () => {
    const user = userEvent.setup();
    render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();
    await user.click(screen.getByText("▶ 비교 시작"));

    deliver({ type: "hit", scenario: "bean-lifecycle", event: { hitId: 1, timestampNanos: 0, thread: "main", location: { className: "C", methodName: "m", line: 1 }, stack: [], locals: [], localsAvailable: false } });
    deliver({ type: "hit", scenario: "dispatcher-flow", event: { hitId: 2, timestampNanos: 0, thread: "main", location: { className: "C", methodName: "n", line: 2 }, stack: [], locals: [], localsAvailable: false } });
    deliver({ type: "hit", scenario: "unrelated-scenario", event: { hitId: 3, timestampNanos: 0, thread: "main", location: { className: "C", methodName: "o", line: 3 }, stack: [], locals: [], localsAvailable: false } });

    const hitCounts = screen.getAllByText(/^HIT \d+$/);
    expect(hitCounts[0]).toHaveTextContent("HIT 1");
    expect(hitCounts[1]).toHaveTextContent("HIT 1");
  });

  it("shows a banner for error messages instead of routing them to a side", async () => {
    render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();

    deliver({ type: "error", message: "classpath boom" });

    expect(screen.getByRole("alert")).toHaveTextContent("classpath boom");
  });

  it("shows request presets only for the side whose scenario has known presets, once started", async () => {
    const user = userEvent.setup();
    render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();

    expect(screen.queryByText("요청 보내기")).not.toBeInTheDocument();
    await user.click(screen.getByText("▶ 비교 시작"));

    // dispatcher-flow(B)는 프리셋이 있고 bean-lifecycle(A)은 없다.
    expect(screen.getByText("요청 보내기")).toBeInTheDocument();
  });

  it("stops both sides and re-enables setup on 비교 종료, without duplicating the stop on unmount", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();
    await user.click(screen.getByText("▶ 비교 시작"));

    await user.click(screen.getByText("■ 비교 종료"));

    expect(stopComparison).toHaveBeenCalledWith("bean-lifecycle");
    expect(stopComparison).toHaveBeenCalledWith("dispatcher-flow");
    expect(screen.getByText("▶ 비교 시작")).toBeInTheDocument();

    stopComparison.mockClear();
    unmount();
    expect(stopComparison).not.toHaveBeenCalled();
  });

  it("stops any still-running comparison on unmount", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<ComparisonModal onClose={vi.fn()} />);
    await waitForScenariosLoaded();
    await user.click(screen.getByText("▶ 비교 시작"));
    stopComparison.mockClear();

    unmount();

    expect(stopComparison).toHaveBeenCalledWith("bean-lifecycle");
    expect(stopComparison).toHaveBeenCalledWith("dispatcher-flow");
  });

  it("calls onClose when the close button is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ComparisonModal onClose={onClose} />);
    await waitForScenariosLoaded();

    await user.click(screen.getByLabelText("닫기"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
