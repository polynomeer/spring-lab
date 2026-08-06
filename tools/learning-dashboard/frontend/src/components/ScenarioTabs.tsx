import type { ScenarioMeta } from "../types";

interface Props {
  scenarios: ScenarioMeta[];
  active: string;
  onSelect: (key: string) => void;
}

export function ScenarioTabs({ scenarios, active, onSelect }: Props) {
  return (
    <div className="tabs" role="tablist">
      {scenarios.map((scenario) => (
        <button
          key={scenario.key}
          type="button"
          role="tab"
          className="tab"
          data-active={scenario.key === active}
          disabled={!scenario.live}
          title={scenario.live ? undefined : "아직 라이브 Lab이 없습니다 (4단계 예정)"}
          onClick={() => onSelect(scenario.key)}
        >
          <span className="dot" />
          {scenario.title}
          {!scenario.live && <span className="soon-badge">SOON</span>}
        </button>
      ))}
    </div>
  );
}
