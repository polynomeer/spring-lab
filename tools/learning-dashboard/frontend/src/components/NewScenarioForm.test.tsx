import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NewScenarioForm } from "./NewScenarioForm";
import type { SavedScenario } from "../types";

const fetchAvailableModulePaths = vi.fn();
const fetchCompilerStatus = vi.fn();
const fetchScenarios = vi.fn();
const createScenario = vi.fn();

vi.mock("../api/scenarioApi", () => ({
  fetchAvailableModulePaths: (...args: unknown[]) => fetchAvailableModulePaths(...args),
  fetchCompilerStatus: (...args: unknown[]) => fetchCompilerStatus(...args),
  fetchScenarios: (...args: unknown[]) => fetchScenarios(...args),
  createScenario: (...args: unknown[]) => createScenario(...args),
}));

const savedScenario: SavedScenario = {
  id: 7,
  name: "custom-scope-lab",
  title: "커스텀 스코프",
  description: "설명",
  gradleModulePaths: [":experiments:custom-scope-lab"],
  mainClass: "lab.experiments.customscope.TenantScopeLab",
  breakpointSpec: "lab.Foo#bar",
  sourceCode: null,
  interpreterKind: "raw",
  createdAt: "2026-01-01T00:00:00Z",
};

beforeEach(() => {
  fetchAvailableModulePaths.mockReset().mockResolvedValue([":experiments:custom-scope-lab", ":experiments:aop-lab"]);
  fetchCompilerStatus.mockReset().mockResolvedValue({ available: true });
  fetchScenarios.mockReset().mockResolvedValue([]);
  createScenario.mockReset();
});

async function waitForModulesLoaded() {
  await waitFor(() => expect(screen.queryByText("모듈 목록을 불러오는 중...")).not.toBeInTheDocument());
}

describe("NewScenarioForm", () => {
  it("loads and renders available modules as checkboxes", async () => {
    render(<NewScenarioForm onCreated={vi.fn()} />);

    expect(screen.getByText("모듈 목록을 불러오는 중...")).toBeInTheDocument();
    await waitForModulesLoaded();

    expect(screen.getByText(":experiments:custom-scope-lab")).toBeInTheDocument();
    expect(screen.getByText(":experiments:aop-lab")).toBeInTheDocument();
  });

  it("disables the source-code toggle and explains why when no compiler is available", async () => {
    fetchCompilerStatus.mockResolvedValue({ available: false });
    render(<NewScenarioForm onCreated={vi.fn()} />);
    await waitForModulesLoaded();

    expect(screen.getByText(/이 서버에는 컴파일러가 없어 사용할 수 없습니다/)).toBeInTheDocument();
    const toggle = screen.getByRole("checkbox", { name: /직접 코드 작성/ });
    expect(toggle).toBeDisabled();
  });

  it("rejects submission when required fields are missing", async () => {
    const user = userEvent.setup();
    render(<NewScenarioForm onCreated={vi.fn()} />);
    await waitForModulesLoaded();

    await user.click(screen.getByText("저장하고 목록에 추가"));

    expect(screen.getByRole("alert")).toHaveTextContent("이름, 제목, 실행할 클래스, 브레이크포인트 스펙을 입력하고 모듈을 하나 이상 골라 주세요.");
    expect(createScenario).not.toHaveBeenCalled();
  });

  it("saves a valid module-based scenario and notifies the parent", async () => {
    const user = userEvent.setup();
    createScenario.mockResolvedValue(savedScenario);
    const onCreated = vi.fn();
    render(<NewScenarioForm onCreated={onCreated} />);
    await waitForModulesLoaded();

    await user.type(screen.getByPlaceholderText("예: custom-scope-lab"), "custom-scope-lab");
    await user.type(screen.getByPlaceholderText("예: 커스텀 스코프"), "커스텀 스코프");
    await user.click(screen.getByText(":experiments:custom-scope-lab"));
    await user.type(screen.getByPlaceholderText(/TenantScopeLab/), "lab.experiments.customscope.TenantScopeLab");
    await user.type(screen.getByPlaceholderText(/DefaultListableBeanFactory#getBean/), "lab.Foo#bar");

    await user.click(screen.getByText("저장하고 목록에 추가"));

    await waitFor(() => expect(createScenario).toHaveBeenCalledTimes(1));
    expect(createScenario).toHaveBeenCalledWith({
      name: "custom-scope-lab",
      title: "커스텀 스코프",
      description: "",
      gradleModulePaths: [":experiments:custom-scope-lab"],
      mainClass: "lab.experiments.customscope.TenantScopeLab",
      breakpointSpec: "lab.Foo#bar",
      sourceCode: undefined,
    });
    expect(onCreated).toHaveBeenCalledWith(savedScenario);
    // 저장 후 폼이 리셋된다.
    expect(screen.getByPlaceholderText("예: custom-scope-lab")).toHaveValue("");
  });

  it("warns instead of saving when source code contains a risky API, then saves on 그래도 저장", async () => {
    const user = userEvent.setup();
    fetchScenarios.mockResolvedValue([]);
    createScenario.mockResolvedValue(savedScenario);
    render(<NewScenarioForm onCreated={vi.fn()} />);
    await waitForModulesLoaded();

    await user.type(screen.getByPlaceholderText("예: custom-scope-lab"), "risky-lab");
    await user.type(screen.getByPlaceholderText("예: 커스텀 스코프"), "위험한 랩");
    await user.click(screen.getByText(":experiments:custom-scope-lab"));
    await user.type(screen.getByPlaceholderText(/TenantScopeLab/), "lab.dynamic.RiskyLab");
    await user.type(screen.getByPlaceholderText(/DefaultListableBeanFactory#getBean/), "lab.Foo#bar");

    await user.click(screen.getByRole("checkbox", { name: /직접 코드 작성/ }));
    fireEvent.change(screen.getByPlaceholderText(/package lab.dynamic/), {
      target: { value: 'public class RiskyLab { void m() { Runtime.getRuntime().exec("ls"); } }' },
    });

    await user.click(screen.getByText("저장하고 목록에 추가"));

    expect(createScenario).not.toHaveBeenCalled();
    expect(screen.getByText(/Runtime.exec/)).toBeInTheDocument();

    await user.click(screen.getByText("그래도 저장"));
    await waitFor(() => expect(createScenario).toHaveBeenCalledTimes(1));
  });

  it("fills the form from an existing scenario when cloned", async () => {
    const user = userEvent.setup();
    fetchScenarios.mockResolvedValue([savedScenario]);
    render(<NewScenarioForm onCreated={vi.fn()} />);
    await waitForModulesLoaded();
    await waitFor(() => expect(screen.getByText("커스텀 스코프")).toBeInTheDocument());

    await user.selectOptions(screen.getByRole("combobox", { name: /기존 시나리오에서 복제/ }), String(savedScenario.id));

    expect(screen.getByPlaceholderText("예: custom-scope-lab")).toHaveValue("custom-scope-lab-copy");
    expect(screen.getByPlaceholderText("예: 커스텀 스코프")).toHaveValue("커스텀 스코프 (복제)");
  });
});
