interface Preset {
  label: string;
  method: string;
  path: string;
  body?: string;
}

// UserController/GreetingViewController/MvcTraceConfig(experiments/dispatcher-servlet-trace)가
// 실제로 처리하는 경로들이다 - 각각 8번 절의 서로 다른 결정 지점(리터럴 vs 변수 경로, 요청
// 본문 역직렬화, 인터셉터 차단, HandlerMapping 우선순위, 처리되지 않는 예외, 404)을 만든다.
const PRESETS: Preset[] = [
  { label: "GET /users/42", method: "GET", path: "/users/42?detail=true" },
  { label: "GET /users/me", method: "GET", path: "/users/me" },
  { label: "POST /users", method: "POST", path: "/users", body: '{"name":"Ada"}' },
  { label: "GET /users/boom", method: "GET", path: "/users/boom" },
  { label: "GET /users/blocked", method: "GET", path: "/users/blocked" },
  { label: "GET /users/priority-test", method: "GET", path: "/users/priority-test" },
  { label: "GET /greeting", method: "GET", path: "/greeting" },
  { label: "GET /does-not-exist", method: "GET", path: "/does-not-exist" },
];

interface Props {
  disabled: boolean;
  onSend: (method: string, path: string, body?: string) => void;
}

export function RequestPresets({ disabled, onSend }: Props) {
  return (
    <div className="request-presets">
      <div className="request-presets-label">요청 보내기</div>
      <div className="request-presets-buttons">
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            disabled={disabled}
            onClick={() => onSend(preset.method, preset.path, preset.body)}
            title={preset.body ? `body: ${preset.body}` : undefined}
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
}
