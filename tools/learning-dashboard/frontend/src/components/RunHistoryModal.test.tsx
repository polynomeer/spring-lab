import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RunHistoryModal } from "./RunHistoryModal";
import type { ScenarioRunDetail, ScenarioRunSummary } from "../types";

const fetchRunHistory = vi.fn();
const fetchRunDetail = vi.fn();
const deleteRun = vi.fn();
const exportScenarioDoc = vi.fn();

vi.mock("../api/runHistoryApi", () => ({
  fetchRunHistory: (...args: unknown[]) => fetchRunHistory(...args),
  fetchRunDetail: (...args: unknown[]) => fetchRunDetail(...args),
  deleteRun: (...args: unknown[]) => deleteRun(...args),
  exportScenarioDoc: (...args: unknown[]) => exportScenarioDoc(...args),
}));

const runs: ScenarioRunSummary[] = [
  { id: 1, scenarioName: "bean-lifecycle", startedAt: "2026-01-01T09:00:00Z", finishedAt: "2026-01-01T09:01:00Z", totalHits: 12, timedOut: false },
  { id: 2, scenarioName: "bean-lifecycle", startedAt: "2026-01-02T09:00:00Z", finishedAt: "2026-01-02T09:05:00Z", totalHits: null, timedOut: true },
];

const detail: ScenarioRunDetail = {
  ...runs[0],
  events: [
    { type: "semantic", scenario: "bean-lifecycle", event: { type: "SINGLETON_FACTORY_REGISTERED", sourceHitId: 1, attributes: {} } },
    { type: "exited", scenario: "bean-lifecycle", totalHits: 12 },
  ],
};

beforeEach(() => {
  fetchRunHistory.mockReset().mockResolvedValue(runs);
  fetchRunDetail.mockReset().mockResolvedValue(detail);
  deleteRun.mockReset().mockResolvedValue(undefined);
  exportScenarioDoc.mockReset().mockResolvedValue("# doc skeleton");
});

describe("RunHistoryModal", () => {
  it("loads and lists past runs, distinguishing timed-out runs from hit counts", async () => {
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={vi.fn()} />);

    await waitFor(() => expect(fetchRunHistory).toHaveBeenCalledWith("bean-lifecycle"));
    expect(await screen.findByText("HIT 12")).toBeInTheDocument();
    expect(screen.getByText("타임아웃")).toBeInTheDocument();
  });

  it("shows an empty hint when there are no runs yet", async () => {
    fetchRunHistory.mockResolvedValue([]);
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={vi.fn()} />);

    expect(await screen.findByText(/아직 완료된 실행 기록이 없습니다/)).toBeInTheDocument();
  });

  it("loads and renders the detail of a selected run", async () => {
    const user = userEvent.setup();
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={vi.fn()} />);
    await screen.findByText("HIT 12");

    await user.click(screen.getByText("HIT 12"));

    await waitFor(() => expect(fetchRunDetail).toHaveBeenCalledWith(1));
    expect(await screen.findByText("SINGLETON_FACTORY_REGISTERED")).toBeInTheDocument();
    expect(screen.getByText("EXITED")).toBeInTheDocument();
  });

  it("exports the selected run to a doc skeleton and copies it to the clipboard", async () => {
    const user = userEvent.setup();
    // userEvent.setup()이 자기만의 clipboard 폴리필로 navigator.clipboard를 덮어써 버리므로,
    // 우리 스텁은 그 뒤에 심어야 한다 - beforeEach에 두면 user.setup()이 나중에 또 덮어쓴다.
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={vi.fn()} />);
    await user.click(await screen.findByText("HIT 12"));
    await screen.findByText("SINGLETON_FACTORY_REGISTERED");

    await user.click(screen.getByText("이 실행으로 문서 뼈대 만들기"));

    await waitFor(() => expect(exportScenarioDoc).toHaveBeenCalledWith("bean-lifecycle", 1));
    expect(await screen.findByDisplayValue("# doc skeleton")).toBeInTheDocument();

    await user.click(screen.getByText("복사"));
    expect(writeText).toHaveBeenCalledWith("# doc skeleton");
    expect(await screen.findByText("복사됨")).toBeInTheDocument();
  });

  it("deletes a run and refreshes the list, clearing the detail if it was selected", async () => {
    const user = userEvent.setup();
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={vi.fn()} />);
    await user.click(await screen.findByText("HIT 12"));
    await screen.findByText("SINGLETON_FACTORY_REGISTERED");
    fetchRunHistory.mockResolvedValue([runs[1]]);

    await user.click(screen.getAllByLabelText("이 기록 삭제")[0]);

    await waitFor(() => expect(deleteRun).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.getByText("왼쪽에서 기록을 골라 보세요.")).toBeInTheDocument());
  });

  it("calls onClose when the close button is clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<RunHistoryModal scenarioName="bean-lifecycle" scenarioTitle="Bean Lifecycle" onClose={onClose} />);
    await screen.findByText("HIT 12");

    await user.click(screen.getByLabelText("닫기"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
