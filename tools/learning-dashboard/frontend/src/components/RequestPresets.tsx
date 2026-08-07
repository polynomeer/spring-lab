import type { Preset } from "../requestPresets";

interface Props {
  presets: Preset[];
  disabled: boolean;
  onSend: (method: string, path: string, body?: string) => void;
}

export function RequestPresets({ presets, disabled, onSend }: Props) {
  return (
    <div className="request-presets">
      <div className="request-presets-label">요청 보내기</div>
      <div className="request-presets-buttons">
        {presets.map((preset) => (
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
