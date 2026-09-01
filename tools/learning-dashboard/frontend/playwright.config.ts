import { defineConfig, devices } from "@playwright/test";

// DiagramCanvas의 리사이즈(ResizeObserver)/줌·팬/SVG·PNG 내보내기는 jsdom이 재현하지 못해
// src/lib/exportDiagram.test.ts 등에서 모킹으로 우회해 왔다 - 이 설정은 그 부분만 실제
// Chromium으로 검증하는 별도의 E2E 스위트다. Vite dev 서버가 대상이고(백엔드/WebSocket은
// 필요 없다 - src/e2e/DiagramCanvasHarness.tsx가 정적 SVG만 감싼다), Vitest 스위트와
// 완전히 분리돼 있다(testDir가 src/ 밖의 e2e/, vite.config.ts test.exclude도 참고).
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:4173",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev -- --port 4173 --strictPort",
    url: "http://localhost:4173",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
