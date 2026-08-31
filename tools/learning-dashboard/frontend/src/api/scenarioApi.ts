import type { SavedScenario, ScenarioSaveRequest } from "../types";

// lab.dashboard.web.ScenarioController(백엔드)를 그대로 감싼 얇은 fetch 래퍼 - 시나리오
// "정의"의 CRUD만 다룬다. 시나리오를 "실행"하는 건 여전히 useDashboardSocket(STOMP)의 몫이다.

async function errorMessageFrom(response: Response): Promise<string> {
  const text = await response.text().catch(() => response.statusText);
  if (!text) {
    return `요청이 실패했습니다 (${response.status})`;
  }
  // ScenarioController#handleCompilationFailed는 문자열 하나가 아니라 진단 메시지
  // 배열(JSON array)을 돌려준다(2단계 컴파일 실패) - 배열이면 줄바꿈으로 합쳐서
  // 여러 줄 에러 메시지로 보여준다. 그 외(400/404/409)는 평범한 문자열 그대로다.
  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) {
      return parsed.join("\n");
    }
  } catch {
    // 배열이 아니면(평범한 문자열 응답) 그대로 사용한다.
  }
  return text;
}

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response));
  }
  return response.json() as Promise<T>;
}

export function fetchScenarios(): Promise<SavedScenario[]> {
  return fetch("/api/scenarios").then((response) => handleJson<SavedScenario[]>(response));
}

export function fetchAvailableModulePaths(): Promise<string[]> {
  return fetch("/api/scenarios/modules").then((response) => handleJson<string[]>(response));
}

export function fetchCompilerStatus(): Promise<{ available: boolean }> {
  return fetch("/api/scenarios/compiler-status").then((response) => handleJson(response));
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
