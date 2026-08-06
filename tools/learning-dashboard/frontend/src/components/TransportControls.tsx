import { useState } from "react";

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

  return (
    <div className="transport">
      <button type="button" onClick={onStep} disabled={!connected}>
        ⏭ Step
      </button>
      {running ? (
        <button type="button" className="primary" onClick={onPause} disabled={!connected}>
          ❚❚ Pause
        </button>
      ) : (
        <button
          type="button"
          className="primary"
          onClick={() => onPlay(SPEEDS[speedIndex].intervalMs)}
          disabled={!connected}
        >
          ▶ Play
        </button>
      )}
      <button type="button" onClick={onReset} disabled={!connected}>
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
