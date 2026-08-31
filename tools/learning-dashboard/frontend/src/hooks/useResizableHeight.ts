import { useCallback, useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function readStored(key: string, fallback: number, min: number, max: number): number {
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? clamp(parsed, min, max) : fallback;
  } catch {
    return fallback;
  }
}

/**
 * 세로 방향 드래그 리사이저 하나를 관리한다 - useResizableRail(가로, 우측 레일 너비)과 같은
 * 패턴(포인터 델타 → 크기, localStorage에 저장)을 세로 축에 맞춰 쓴 것이다. 레일 안 세
 * 섹션(semantic events/raw log/hit inspector) 사이의 두 경계선이 각각 독립된 인스턴스를
 * 하나씩 쓴다.
 */
export function useResizableHeight(storageKey: string, defaultHeight: number, min: number, max: number) {
  const [height, setHeight] = useState(() => readStored(storageKey, defaultHeight, min, max));
  const draggingRef = useRef<{ startY: number; originHeight: number } | null>(null);

  const startDrag = useCallback(
    (event: ReactPointerEvent) => {
      event.preventDefault();
      draggingRef.current = { startY: event.clientY, originHeight: height };
      document.body.style.cursor = "row-resize";
      document.body.style.userSelect = "none";
    },
    [height],
  );

  useEffect(() => {
    function onPointerMove(event: PointerEvent) {
      const drag = draggingRef.current;
      if (!drag) {
        return;
      }
      setHeight(clamp(drag.originHeight + (event.clientY - drag.startY), min, max));
    }

    function onPointerUp() {
      if (!draggingRef.current) {
        return;
      }
      draggingRef.current = null;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      setHeight((current) => {
        try {
          localStorage.setItem(storageKey, String(current));
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
  }, [storageKey, min, max]);

  return { height, startDrag };
}
