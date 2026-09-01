import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ConditionReportPanel } from "./ConditionReportPanel";
import type { ConditionReportMessage } from "../types";

const runReport = vi.fn();
let connected = true;
let capturedOnMessage: ((message: ConditionReportMessage) => void) | null = null;

// useConditionReportSocket은 실제 WebSocket(@stomp/stompjs)을 연다 - jsdom에는 그 전송
// 계층이 없으므로, 훅 자체를 모킹해서 이 컴포넌트의 로직(자동 실행/로딩/렌더링)만 검증한다.
vi.mock("../stomp/useConditionReportSocket", () => ({
  useConditionReportSocket: (onMessage: (message: ConditionReportMessage) => void) => {
    capturedOnMessage = onMessage;
    return { connected, runReport };
  },
}));

function deliver(message: ConditionReportMessage) {
  act(() => capturedOnMessage?.(message));
}

beforeEach(() => {
  runReport.mockClear();
  connected = true;
  capturedOnMessage = null;
});

describe("ConditionReportPanel", () => {
  it("auto-runs the default preset once connected, and shows a loading hint", () => {
    render(<ConditionReportPanel />);

    expect(runReport).toHaveBeenCalledTimes(1);
    expect(runReport).toHaveBeenCalledWith({});
    expect(screen.getByText("실행 중…")).toBeInTheDocument();
  });

  it("does not auto-run while disconnected, and disables preset buttons", () => {
    connected = false;
    render(<ConditionReportPanel />);

    expect(runReport).not.toHaveBeenCalled();
    for (const button of screen.getAllByRole("button")) {
      expect(button).toBeDisabled();
    }
  });

  it("renders unconditional beans and match/no-match sources once a report arrives", async () => {
    const user = userEvent.setup();
    render(<ConditionReportPanel />);

    deliver({
      overrides: {},
      report: {
        unconditional: ["lab.autoconfig.CoreAutoConfiguration"],
        sources: {
          "lab.autoconfig.GreetingAutoConfiguration": {
            fullMatch: true,
            outcomes: [{ condition: "@ConditionalOnProperty(greeting.enabled)", matched: true, message: "matched because greeting.enabled=true" }],
          },
          "lab.autoconfig.SlowModeAutoConfiguration": {
            fullMatch: false,
            outcomes: [{ condition: "@ConditionalOnProperty(lab.slow-mode)", matched: false, message: "did not match" }],
          },
        },
      },
    });

    expect(screen.queryByText("실행 중…")).not.toBeInTheDocument();
    expect(screen.getByText("무조건 등록됨")).toBeInTheDocument();
    expect(screen.getByText("CoreAutoConfiguration")).toBeInTheDocument();
    expect(screen.getByText("MATCH")).toBeInTheDocument();
    expect(screen.getByText("NO MATCH")).toBeInTheDocument();

    // outcome detail은 클릭해서 펼치기 전에는 안 보인다.
    expect(screen.queryByText("matched because greeting.enabled=true")).not.toBeInTheDocument();
    await user.click(screen.getByText("GreetingAutoConfiguration"));
    expect(screen.getByText("matched because greeting.enabled=true")).toBeInTheDocument();
  });

  it("shows an error row instead of a tree when the report fails", () => {
    render(<ConditionReportPanel />);
    deliver({ overrides: {}, error: "compile failed" });

    expect(screen.getByText("ERROR")).toBeInTheDocument();
    expect(screen.getByText("compile failed")).toBeInTheDocument();
  });

  it("re-runs with the clicked preset's overrides", async () => {
    const user = userEvent.setup();
    render(<ConditionReportPanel />);
    deliver({ overrides: {}, report: { unconditional: [], sources: {} } });
    runReport.mockClear();

    await user.click(screen.getByText("greeting 끄기"));

    expect(runReport).toHaveBeenCalledWith({ "greeting.enabled": "false" });
  });
});
