import { useEffect, useState } from "react";
import type { FormEvent } from "react";

import { createScenario, fetchAvailableModulePaths } from "../api/scenarioApi";
import type { SavedScenario } from "../types";

interface Props {
  onCreated: (scenario: SavedScenario) => void;
}

/**
 * "이미 있는 모듈을 골라 새 시나리오로 등록"하는 1단계 폼(docs/plan/04-dynamic-scenario-design.md).
 * 새 코드를 작성하는 게 아니라, 이 저장소에 이미 컴파일돼 있는 실험 모듈의 main() 클래스를
 * 어떤 브레이크포인트로 관찰할지만 고른다 - 그래서 컴파일 단계도, 그에 따르는 위험(실행
 * 타임아웃 등)도 이 폼에는 없다. 2단계(즉석 코드 작성)는 별도 확장으로 남겨 둔다.
 */
export function NewScenarioForm({ onCreated }: Props) {
  const [availableModules, setAvailableModules] = useState<string[]>([]);
  const [modulesLoading, setModulesLoading] = useState(true);
  const [name, setName] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [mainClass, setMainClass] = useState("");
  const [breakpointSpec, setBreakpointSpec] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAvailableModulePaths()
      .then(setAvailableModules)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setModulesLoading(false));
  }, []);

  const toggleModule = (path: string) => {
    setSelectedModules((prev) => (prev.includes(path) ? prev.filter((p) => p !== path) : [...prev, path]));
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!name.trim() || !title.trim() || !mainClass.trim() || !breakpointSpec.trim() || selectedModules.length === 0) {
      setError("이름, 제목, 실행할 클래스, 브레이크포인트 스펙을 입력하고 모듈을 하나 이상 골라 주세요.");
      return;
    }

    setSaving(true);
    try {
      const created = await createScenario({
        name: name.trim(),
        title: title.trim(),
        description: description.trim(),
        gradleModulePaths: selectedModules,
        mainClass: mainClass.trim(),
        breakpointSpec,
      });
      onCreated(created);
      setName("");
      setTitle("");
      setDescription("");
      setSelectedModules([]);
      setMainClass("");
      setBreakpointSpec("");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="new-scenario-form" onSubmit={(event) => void submit(event)}>
      <p className="new-scenario-intro">
        이미 이 저장소에 있는 실험 모듈을 골라 새 시나리오로 등록합니다 - 새 코드를 짜는 게
        아니라, 어느 모듈의 어느 <code>main()</code> 클래스를 어떤 브레이크포인트로 관찰할지만
        정하면 됩니다. 저장하면 목록에 즉시 새 탭으로 나타납니다.
      </p>

      {error && (
        <div className="new-scenario-error" role="alert">
          {error}
        </div>
      )}

      <label className="field">
        <span>이름 (slug, 영문/숫자/하이픈 - STOMP 라우팅에 쓰이는 안정적인 키)</span>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="예: custom-scope-lab" />
      </label>

      <label className="field">
        <span>제목</span>
        <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 커스텀 스코프" />
      </label>

      <label className="field">
        <span>설명</span>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
      </label>

      <div className="field">
        <span>사용할 모듈 ({selectedModules.length}개 선택됨)</span>
        {modulesLoading ? (
          <p className="new-scenario-hint">모듈 목록을 불러오는 중...</p>
        ) : (
          <div className="module-checkbox-list">
            {availableModules.map((path) => (
              <label key={path} className="module-checkbox">
                <input type="checkbox" checked={selectedModules.includes(path)} onChange={() => toggleModule(path)} />
                <span>{path}</span>
              </label>
            ))}
          </div>
        )}
      </div>

      <label className="field">
        <span>실행할 클래스 (FQCN, public static void main 필요)</span>
        <input
          value={mainClass}
          onChange={(e) => setMainClass(e.target.value)}
          placeholder="예: lab.experiments.customscope.TenantScopeLab"
        />
      </label>

      <label className="field">
        <span>브레이크포인트 스펙 (한 줄에 하나, "Class#method1,method2" - # 주석/빈 줄 허용)</span>
        <textarea
          className="mono"
          value={breakpointSpec}
          onChange={(e) => setBreakpointSpec(e.target.value)}
          rows={6}
          placeholder="org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean"
        />
      </label>

      <button type="submit" className="primary" disabled={saving}>
        {saving ? "저장 중..." : "저장하고 목록에 추가"}
      </button>
    </form>
  );
}
