import { useEffect, useState } from "react";
import type { FormEvent } from "react";

import { createScenario, fetchAvailableModulePaths, fetchCompilerStatus } from "../api/scenarioApi";
import type { SavedScenario } from "../types";

interface Props {
  onCreated: (scenario: SavedScenario) => void;
}

// docs/plan/04-dynamic-scenario-design.md 5번 절 - 완전한 샌드박싱이 아니라 실수(fat-finger)
// 방지용 가벼운 정적 경고다. 저장을 막지는 않는다 - "그래도 저장" 버튼으로 넘어갈 수 있다.
const RISKY_PATTERNS: { pattern: RegExp; label: string }[] = [
  { pattern: /Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec/, label: "Runtime.exec" },
  { pattern: /new\s+ProcessBuilder/, label: "ProcessBuilder" },
  { pattern: /System\s*\.\s*exit/, label: "System.exit" },
  { pattern: /Files\s*\.\s*delete/, label: "Files.delete" },
];

function findRiskyApiUsages(source: string): string[] {
  return RISKY_PATTERNS.filter(({ pattern }) => pattern.test(source)).map(({ label }) => label);
}

/**
 * "이미 있는 모듈을 골라 새 시나리오로 등록"(1단계)과 "직접 코드를 작성해서 서버가
 * 컴파일·실행"(2단계, docs/plan/04-dynamic-scenario-design.md)을 함께 다루는 폼. 컴파일러가
 * 없는 환경(JRE로 실행 중 등)에서는 2단계 토글 자체를 비활성화한다 - 1단계 기능은 컴파일러
 * 없이도 그대로 동작해야 하므로 기능 저하가 아니라 부분 비활성화다.
 */
export function NewScenarioForm({ onCreated }: Props) {
  const [availableModules, setAvailableModules] = useState<string[]>([]);
  const [modulesLoading, setModulesLoading] = useState(true);
  const [compilerAvailable, setCompilerAvailable] = useState<boolean | null>(null);
  const [name, setName] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [mainClass, setMainClass] = useState("");
  const [breakpointSpec, setBreakpointSpec] = useState("");
  const [useSourceCode, setUseSourceCode] = useState(false);
  const [sourceCode, setSourceCode] = useState("");
  const [riskyApis, setRiskyApis] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAvailableModulePaths()
      .then(setAvailableModules)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setModulesLoading(false));
    fetchCompilerStatus()
      .then((status) => setCompilerAvailable(status.available))
      .catch(() => setCompilerAvailable(false));
  }, []);

  const toggleModule = (path: string) => {
    setSelectedModules((prev) => (prev.includes(path) ? prev.filter((p) => p !== path) : [...prev, path]));
  };

  const resetForm = () => {
    setName("");
    setTitle("");
    setDescription("");
    setSelectedModules([]);
    setMainClass("");
    setBreakpointSpec("");
    setUseSourceCode(false);
    setSourceCode("");
    setRiskyApis([]);
  };

  const doSave = async () => {
    setSaving(true);
    try {
      const created = await createScenario({
        name: name.trim(),
        title: title.trim(),
        description: description.trim(),
        gradleModulePaths: selectedModules,
        mainClass: mainClass.trim(),
        breakpointSpec,
        sourceCode: useSourceCode ? sourceCode : undefined,
      });
      onCreated(created);
      resetForm();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!name.trim() || !title.trim() || !mainClass.trim() || !breakpointSpec.trim() || selectedModules.length === 0) {
      setError("이름, 제목, 실행할 클래스, 브레이크포인트 스펙을 입력하고 모듈을 하나 이상 골라 주세요.");
      return;
    }
    if (useSourceCode && !sourceCode.trim()) {
      setError("직접 코드 작성을 켰으면 소스 코드를 입력해야 합니다.");
      return;
    }

    if (useSourceCode) {
      const risky = findRiskyApiUsages(sourceCode);
      if (risky.length > 0) {
        // 저장을 막지 않는다 - 경고만 띄우고, "그래도 저장" 버튼을 눌러야 실제로 진행된다.
        setRiskyApis(risky);
        return;
      }
    }

    await doSave();
  };

  const forceSave = async () => {
    setRiskyApis([]);
    await doSave();
  };

  return (
    <form className="new-scenario-form" onSubmit={(event) => void submit(event)}>
      <p className="new-scenario-intro">
        이미 이 저장소에 있는 실험 모듈을 골라 새 시나리오로 등록하거나, 아래 "직접 코드 작성"을
        켜서 즉석에서 작성한 Lab 클래스를 서버가 컴파일해 바로 실행합니다. 저장하면 목록에
        즉시 새 탭으로 나타납니다.
      </p>

      {error && (
        <div className="new-scenario-error" role="alert">
          {error}
        </div>
      )}

      {riskyApis.length > 0 && (
        <div className="new-scenario-warning" role="alert">
          <p>
            코드에 위험할 수 있는 API가 보입니다: <strong>{riskyApis.join(", ")}</strong> - 실수로
            넣은 게 아닌지 확인하세요.
          </p>
          <button type="button" onClick={() => void forceSave()} disabled={saving}>
            {saving ? "저장 중..." : "그래도 저장"}
          </button>
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
        <span>실행할 클래스 (FQCN, public static void main 필요{useSourceCode ? " - 아래 코드의 package/class와 정확히 일치해야 함" : ""})</span>
        <input
          value={mainClass}
          onChange={(e) => setMainClass(e.target.value)}
          placeholder="예: lab.experiments.customscope.TenantScopeLab"
        />
      </label>

      <label className="field new-scenario-toggle">
        <input
          type="checkbox"
          checked={useSourceCode}
          disabled={compilerAvailable !== true}
          onChange={(e) => {
            setUseSourceCode(e.target.checked);
            setRiskyApis([]);
          }}
        />
        <span>
          직접 코드 작성 (서버가 컴파일해서 실행)
          {compilerAvailable === false && " - 이 서버에는 컴파일러가 없어 사용할 수 없습니다(JDK로 실행해야 함)"}
          {compilerAvailable === null && " - 확인 중..."}
        </span>
      </label>

      {useSourceCode && (
        <label className="field">
          <span>Lab 클래스 소스 코드</span>
          <textarea
            className="mono"
            value={sourceCode}
            onChange={(e) => {
              setSourceCode(e.target.value);
              setRiskyApis([]);
            }}
            rows={16}
            placeholder={
              "package lab.dynamic;\n\npublic class MyLab {\n    public static void main(String[] args) {\n        // ...\n    }\n}"
            }
          />
        </label>
      )}

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
