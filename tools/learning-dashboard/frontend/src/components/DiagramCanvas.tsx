import { useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent, ReactNode } from "react";

import { exportSvgAsPngFile, exportSvgAsSvgFile } from "../lib/exportDiagram";

const HEIGHT_STORAGE_KEY = "trace-dash.diagram-height";
const DEFAULT_HEIGHT = 380;
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.2;

function clampZoom(zoom: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
}

function readStoredHeight(): number {
  try {
    const raw = localStorage.getItem(HEIGHT_STORAGE_KEY);
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_HEIGHT;
  } catch {
    return DEFAULT_HEIGHT;
  }
}

interface Props {
  children: ReactNode;
  exportFilename: string;
}

/**
 * 다이어그램(StatusGraph/Pipeline/Swimlane) 하나를 감싸는 뷰포트 - 세 가지를 한곳에 묶는다:
 * (1) textarea 스타일 세로 리사이즈(그 높이는 localStorage에 남아 새로고침해도 유지),
 * (2) 휠/버튼 줌 + 드래그 팬, (3) SVG/PNG 내보내기. 어떤 구체적인 다이어그램이 안에 들어있는지
 * 전혀 몰라도 되게 만들었다 - children으로 받은 걸 그대로 감싸고, 내보낼 때는 DOM에서
 * 실제 <svg> 하나를 찾아 쓸 뿐이다.
 */
export function DiagramCanvas({ children, exportFilename }: Props) {
  const boxRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const draggingRef = useRef<{ startX: number; startY: number; originX: number; originY: number } | null>(null);

  useEffect(() => {
    const node = boxRef.current;
    if (!node) {
      return;
    }
    node.style.height = `${readStoredHeight()}px`;

    let frame = 0;
    const observer = new ResizeObserver((entries) => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const height = entries[0]?.contentRect.height;
        if (!height) {
          return;
        }
        try {
          localStorage.setItem(HEIGHT_STORAGE_KEY, String(Math.round(height)));
        } catch {
          // 프라이빗 모드 등에서 저장이 막혀도 리사이즈 자체는 계속 동작해야 한다.
        }
      });
    });
    observer.observe(node);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, []);

  useEffect(() => {
    const node = boxRef.current;
    if (!node) {
      return;
    }
    // React의 onWheel은 일부 브라우저에서 passive 리스너로 등록되어 preventDefault()가
    // 무시될 수 있다 - DOM에 직접 { passive: false }로 붙여서 확실히 막는다.
    function onWheel(event: WheelEvent) {
      event.preventDefault();
      setZoom((current) => clampZoom(current - Math.sign(event.deltaY) * ZOOM_STEP));
    }
    node.addEventListener("wheel", onWheel, { passive: false });
    return () => node.removeEventListener("wheel", onWheel);
  }, []);

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    draggingRef.current = { startX: event.clientX, startY: event.clientY, originX: pan.x, originY: pan.y };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = draggingRef.current;
    if (!drag) {
      return;
    }
    setPan({ x: drag.originX + (event.clientX - drag.startX), y: drag.originY + (event.clientY - drag.startY) });
  };

  const onPointerUp = () => {
    draggingRef.current = null;
  };

  const resetView = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  const findSvg = () => contentRef.current?.querySelector("svg") ?? null;

  return (
    <div className="diagram-canvas" ref={boxRef}>
      <div className="diagram-toolbar">
        <div className="diagram-toolbar-group">
          <button type="button" onClick={() => setZoom((z) => clampZoom(z - ZOOM_STEP))} title="축소">
            －
          </button>
          <span className="diagram-zoom-level">{Math.round(zoom * 100)}%</span>
          <button type="button" onClick={() => setZoom((z) => clampZoom(z + ZOOM_STEP))} title="확대">
            ＋
          </button>
          <button type="button" onClick={resetView} title="보기 초기화(줌/이동 리셋)">
            ⟲
          </button>
        </div>
        <div className="diagram-toolbar-group">
          <button
            type="button"
            title="SVG 파일로 내보내기"
            onClick={() => {
              const svg = findSvg();
              if (svg) {
                exportSvgAsSvgFile(svg, exportFilename);
              }
            }}
          >
            SVG
          </button>
          <button
            type="button"
            title="PNG 파일로 내보내기"
            onClick={() => {
              const svg = findSvg();
              if (svg) {
                exportSvgAsPngFile(svg, exportFilename);
              }
            }}
          >
            PNG
          </button>
        </div>
      </div>

      <div
        className="diagram-viewport"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
      >
        <div
          className="diagram-zoom-content"
          ref={contentRef}
          style={{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})` }}
        >
          {children}
        </div>
      </div>
    </div>
  );
}
