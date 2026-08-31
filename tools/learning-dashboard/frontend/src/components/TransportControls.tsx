import { useEffect, useState } from "react";

interface Props {
  connected: boolean;
  running: boolean;
  onStep: () => void;
  onPlay: (intervalMs: number) => void;
  onPause: () => void;
  onReset: () => void;
}

const SPEEDS = [
  { label: "0.5×", intervalMs: 1000 },
  { label: "1×", intervalMs: 500 },
  { label: "2×", intervalMs: 250 },
  { label: "4×", intervalMs: 100 },
];

export function TransportControls({ connected, running, onStep, onPlay, onPause, onReset }: Props) {
  const [speedIndex, setSpeedIndex] = useState(1);

  // 키보드 단축키: Space(재생/일시정지), →(한 걸음), R(리셋). 필터 입력창 등 다른 곳에 타이핑
  // 중일 때는 무시한다 - 텍스트 입력과 충돌하면 안 되기 때문이다.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (!connected || event.ctrlKey || event.altKey || event.metaKey) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT")) {
        return;
      }
      if (event.key === " ") {
        event.preventDefault();
        if (running) {
          onPause();
        } else {
          onPlay(SPEEDS[speedIndex].intervalMs);
        }
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        onStep();
      } else if (event.key === "r" || event.key === "R") {
        event.preventDefault();
        onReset();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [connected, running, onStep, onPlay, onPause, onReset, speedIndex]);

  return (
    <div className="transport">
      <button type="button" onClick={onStep} disabled={!connected} title="한 걸음 (→)">
        ⏭ Step
      </button>
      {running ? (
        <button type="button" className="primary" onClick={onPause} disabled={!connected} title="일시정지 (Space)">
          ❚❚ Pause
        </button>
      ) : (
        <button
          type="button"
          className="primary"
          onClick={() => onPlay(SPEEDS[speedIndex].intervalMs)}
          disabled={!connected}
          title="재생 (Space)"
        >
          ▶ Play
        </button>
      )}
      <button type="button" onClick={onReset} disabled={!connected} title="리셋 (R)">
        ↺ Reset
      </button>
      <div className="speed">
        SPEED
        <select
          value={speedIndex}
          onChange={(event) => {
            const next = Number(event.target.value);
            setSpeedIndex(next);
            if (running) {
              onPlay(SPEEDS[next].intervalMs);
            }
          }}
        >
          {SPEEDS.map((speed, index) => (
            <option key={speed.label} value={index}>
              {speed.label}
            </option>
          ))}
        </select>
      </div>
      <div className={`connection-pill ${connected ? "" : "offline"}`}>
        <span className="dot" />
        {connected ? "CONNECTED" : "DISCONNECTED"}
      </div>
    </div>
  );
}
