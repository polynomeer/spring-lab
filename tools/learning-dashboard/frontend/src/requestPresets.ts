export interface Preset {
  label: string;
  method: string;
  path: string;
  body?: string;
}

// UserController/GreetingViewController/MvcTraceConfig(experiments/dispatcher-servlet-trace)가
// 실제로 처리하는 경로들이다 - 각각 8번 절의 서로 다른 결정 지점(리터럴 vs 변수 경로, 요청
// 본문 역직렬화, 인터셉터 차단, HandlerMapping 우선순위, 처리되지 않는 예외, 404)을 만든다.
export const DISPATCHER_FLOW_PRESETS: Preset[] = [
  { label: "GET /users/42", method: "GET", path: "/users/42?detail=true" },
  { label: "GET /users/me", method: "GET", path: "/users/me" },
  { label: "POST /users", method: "POST", path: "/users", body: '{"name":"Ada"}' },
  { label: "GET /users/boom", method: "GET", path: "/users/boom" },
  { label: "GET /users/blocked", method: "GET", path: "/users/blocked" },
  { label: "GET /users/priority-test", method: "GET", path: "/users/priority-test" },
  { label: "GET /greeting", method: "GET", path: "/greeting" },
  { label: "GET /does-not-exist", method: "GET", path: "/does-not-exist" },
];

// DemoController/CommonAdvice/HighPriorityAdvice/LowPriorityAdvice(experiments/mvc-exception-pipeline)가
// docs/22-mvc-exception-handling.md 8번 절에서 표로 정리한 10가지 경우 그대로다.
export const MVC_EXCEPTION_PRESETS: Preset[] = [
  { label: "GET boom-local (418)", method: "GET", path: "/widgets/boom-local" },
  { label: "GET boom-advice-only (502)", method: "GET", path: "/widgets/boom-advice-only" },
  { label: "GET boom-shared (409)", method: "GET", path: "/widgets/boom-shared" },
  { label: "GET boom-response-status-exception (402)", method: "GET", path: "/widgets/boom-response-status-exception" },
  { label: "GET boom-annotated-exception (404)", method: "GET", path: "/widgets/boom-annotated-exception" },
  { label: "GET widgets/abc (400, 타입 불일치)", method: "GET", path: "/widgets/abc" },
  { label: "POST widgets 깨진 JSON (400)", method: "POST", path: "/widgets", body: "{broken" },
  { label: "POST widgets 빈 name (400, @Valid)", method: "POST", path: "/widgets", body: '{"name":""}' },
  { label: "GET does-not-exist (404)", method: "GET", path: "/does-not-exist" },
  { label: "PUT widgets/1 (405)", method: "PUT", path: "/widgets/1" },
];
