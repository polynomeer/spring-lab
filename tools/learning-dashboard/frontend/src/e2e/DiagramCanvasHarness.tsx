import { DiagramCanvas } from "../components/DiagramCanvas";

// Playwright 전용 마운트 지점(main.tsx가 ?e2e=diagram-canvas일 때만 이걸 띄운다) - 실제
// StatusGraph/Pipeline/Swimlane 대신 고정된 정적 SVG를 감싸서, 백엔드나 WebSocket 없이도
// DiagramCanvas 자신의 리사이즈/줌·팬/내보내기 상호작용만 겨냥해서 확인한다. jsdom은 실제
// ResizeObserver 관찰 동작이나 <a download> 파일 저장을 재현하지 못해 유닛 테스트에서는
// 모킹으로 우회해 왔다(src/lib/exportDiagram.test.ts) - 여기서는 진짜 브라우저로 그 자체를
// 검증한다.
export function DiagramCanvasHarness() {
  return (
    <div style={{ width: 500, padding: 24 }}>
      <DiagramCanvas exportFilename="e2e-harness">
        <svg viewBox="0 0 200 100" data-testid="harness-svg">
          <rect x="10" y="10" width="80" height="60" fill="steelblue" />
          <circle cx="150" cy="50" r="30" fill="tomato" />
        </svg>
      </DiagramCanvas>
    </div>
  );
}
