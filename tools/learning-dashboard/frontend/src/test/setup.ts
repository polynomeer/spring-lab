import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

import "@testing-library/jest-dom/vitest";

// vitest globals(describe/it/expect가 전역으로 주입되는 것)를 안 켜 뒀으므로(각 테스트
// 파일에서 명시적으로 import한다), @testing-library/react가 자동으로 등록해 주는 afterEach
// cleanup도 감지되지 않는다 - 직접 등록해서 각 테스트 사이에 이전 렌더 결과가 DOM에 남아
// "여러 개 찾힘" 에러가 나는 걸 막는다.
afterEach(() => {
  cleanup();
});

// jsdom은 ResizeObserver를 구현하지 않는다 - DiagramCanvas가 마운트되자마자 무조건
// 하나를 만들기 때문에(리사이즈 높이를 localStorage에 남기기 위해), 그 컴포넌트를 렌더하는
// 테스트라면 전부 이게 없으면 즉시 ReferenceError로 죽는다. 실제 리사이즈 관찰 동작 자체는
// 브라우저가 아니면 검증할 수 없으므로(이미 앞선 세션에서 라이브로 확인했다), 여기서는
// "컴포넌트가 마운트는 된다"는 조건만 만족시키는 최소 스텁이다.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (!("ResizeObserver" in globalThis)) {
  Object.defineProperty(globalThis, "ResizeObserver", {
    writable: true,
    configurable: true,
    value: ResizeObserverStub,
  });
}
