import type { ScenarioRunDetail, ScenarioRunSummary } from "../types";

// lab.dashboard.web.ScenarioRunController(백엔드)를 그대로 감싼 얇은 fetch 래퍼 -
// docs/plan/04-dynamic-scenario-design.md 7번 절 "실행 히스토리 스냅샷". 쓰기(POST/PUT)가
// 없다 - 기록 자체는 백엔드가 이벤트를 구독해서 자동으로 한다.

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new Error(message || `요청이 실패했습니다 (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function fetchRunHistory(scenarioName: string): Promise<ScenarioRunSummary[]> {
  return fetch(`/api/scenario-runs?scenarioName=${encodeURIComponent(scenarioName)}`).then((response) =>
    handleJson<ScenarioRunSummary[]>(response),
  );
}

export function fetchRunDetail(id: number): Promise<ScenarioRunDetail> {
  return fetch(`/api/scenario-runs/${id}`).then((response) => handleJson<ScenarioRunDetail>(response));
}

export function deleteRun(id: number): Promise<void> {
  return fetch(`/api/scenario-runs/${id}`, { method: "DELETE" }).then((response) => {
    if (!response.ok) {
      throw new Error(`삭제가 실패했습니다 (${response.status})`);
    }
  });
}
