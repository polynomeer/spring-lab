import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 브라우저에서 보면 프론트/백엔드가 서로 다른 포트라 별개 출처(origin)다 - STOMP
      // 클라이언트가 raw WebSocket으로 직접 8080에 붙는 대신, Vite 자신의 오리진(5173)
      // 아래 /ws로 프록시해서 같은 출처에서 붙는 것처럼 보이게 한다.
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
});
