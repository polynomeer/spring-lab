import type { SavedScenario, ScenarioSaveRequest } from "../types";

// lab.dashboard.web.ScenarioController(백엔드)를 그대로 감싼 얇은 fetch 래퍼 - 시나리오
// "정의"의 CRUD만 다룬다. 시나리오를 "실행"하는 건 여전히 useDashboardSocket(STOMP)의 몫이다.

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new Error(message || `요청이 실패했습니다 (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function fetchScenarios(): Promise<SavedScenario[]> {
  return fetch("/api/scenarios").then((response) => handleJson<SavedScenario[]>(response));
}

export function fetchAvailableModulePaths(): Promise<string[]> {
  return fetch("/api/scenarios/modules").then((response) => handleJson<string[]>(response));
}

export function createScenario(request: ScenarioSaveRequest): Promise<SavedScenario> {
  return fetch("/api/scenarios", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((response) => handleJson<SavedScenario>(response));
}

export function deleteScenario(id: number): Promise<void> {
  return fetch(`/api/scenarios/${id}`, { method: "DELETE" }).then((response) => {
    if (!response.ok) {
      throw new Error(`삭제가 실패했습니다 (${response.status})`);
    }
  });
}
