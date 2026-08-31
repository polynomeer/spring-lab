import { useCallback, useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";

const STORAGE_KEY = "trace-dash.rail-width";
const DEFAULT_WIDTH = 340;
const MIN_WIDTH = 260;
const MAX_WIDTH = 640;

function clamp(width: number): number {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width));
}

function readStoredWidth(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? clamp(parsed) : DEFAULT_WIDTH;
  } catch {
    return DEFAULT_WIDTH;
  }
}

/**
 * 우측 레일(semantic events / raw log / hit inspector)의 너비를 드래그로 조절한다.
 * .stage의 grid-template-columns 세 번째 트랙을 CSS 커스텀 프로퍼티(--rail-width)로 넘겨서
 * 900px 이하의 반응형 미디어쿼리(단일 컬럼으로 접힘)와 충돌하지 않게 한다 - 인라인
 * grid-template-columns를 직접 덮어쓰면 그 미디어쿼리보다 항상 우선해 버려서 좁은 화면에서도
 * 3컬럼 레이아웃이 강제로 유지되는 문제가 있었다. 고른 너비는 localStorage에 남겨서
 * 새로고침해도 유지된다.
 */
export function useResizableRail() {
  const [width, setWidth] = useState(readStoredWidth);
  const draggingRef = useRef(false);
  const stageRef = useRef<HTMLDivElement | null>(null);

  const startDrag = useCallback((event: ReactPointerEvent) => {
    event.preventDefault();
    draggingRef.current = true;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, []);

  useEffect(() => {
    function onPointerMove(event: PointerEvent) {
      if (!draggingRef.current || !stageRef.current) {
        return;
      }
      const rect = stageRef.current.getBoundingClientRect();
      setWidth(clamp(rect.right - event.clientX));
    }

    function onPointerUp() {
      if (!draggingRef.current) {
        return;
      }
      draggingRef.current = false;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      setWidth((current) => {
        try {
          localStorage.setItem(STORAGE_KEY, String(current));
        } catch {
          // 프라이빗 모드 등에서 저장이 막혀도 조절 자체는 계속 동작해야 한다.
        }
        return current;
      });
    }

    window.addEventListener("pointermove", onPointerMove);
    window.addEventListener("pointerup", onPointerUp);
    return () => {
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerup", onPointerUp);
    };
  }, []);

  return { width, startDrag, stageRef };
}
