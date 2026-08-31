/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// run.sh가 포트 충돌을 우회할 때 백엔드를 8080이 아닌 다른 포트로 띄울 수 있으므로, 하드코딩
// 대신 환경 변수로 실제 백엔드 포트를 받는다 - 직접 npm run dev로 띄울 때는 기본값(8080) 그대로.
const backendPort = process.env.DASHBOARD_BACKEND_PORT ?? "8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 브라우저에서 보면 프론트/백엔드가 서로 다른 포트라 별개 출처(origin)다 - STOMP
      // 클라이언트가 raw WebSocket으로 직접 백엔드 포트에 붙는 대신, Vite 자신의 오리진 아래
      // /ws로 프록시해서 같은 출처에서 붙는 것처럼 보이게 한다.
      "/ws": {
        target: `ws://localhost:${backendPort}`,
        ws: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
