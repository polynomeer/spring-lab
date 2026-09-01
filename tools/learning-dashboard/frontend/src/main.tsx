import React from "react";
import ReactDOM from "react-dom/client";

import App from "./App";
import "./styles.css";

const root = ReactDOM.createRoot(document.getElementById("root")!);

// ?e2e=diagram-canvas로 들어오면 실제 대시보드 대신 Playwright 전용 테스트 하네스를
// 띄운다(src/e2e/DiagramCanvasHarness.tsx) - 일반 사용자 흐름(파라미터 없이 접속)에는
// 전혀 영향이 없고, 프로덕션 빌드에는 포함되지만 동적 import라 초기 번들에는 안 실린다.
const params = new URLSearchParams(location.search);
if (params.get("e2e") === "diagram-canvas") {
  void import("./e2e/DiagramCanvasHarness").then(({ DiagramCanvasHarness }) => {
    root.render(
      <React.StrictMode>
        <DiagramCanvasHarness />
      </React.StrictMode>,
    );
  });
} else {
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}
