import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useResizableRail } from "./useResizableRail";

const STORAGE_KEY = "trace-dash.rail-width";

function fakePointerEvent(clientY = 0) {
  return { preventDefault: vi.fn(), clientY } as unknown as React.PointerEvent;
}

function stageWithRightEdge(right: number): HTMLDivElement {
  const element = document.createElement("div");
  element.getBoundingClientRect = () => ({ right }) as DOMRect;
  return element;
}

describe("useResizableRail", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("defaults to 340px when nothing is stored", () => {
    const { result } = renderHook(() => useResizableRail());
    expect(result.current.width).toBe(340);
  });

  it("reads a previously stored width, clamped to [260, 640]", () => {
    localStorage.setItem(STORAGE_KEY, "999");
    const { result } = renderHook(() => useResizableRail());
    expect(result.current.width).toBe(640);
  });

  it("falls back to the default for garbage stored values", () => {
    localStorage.setItem(STORAGE_KEY, "not-a-number");
    const { result } = renderHook(() => useResizableRail());
    expect(result.current.width).toBe(340);
  });

  it("ignores pointermove before startDrag is called", () => {
    const { result } = renderHook(() => useResizableRail());
    act(() => {
      result.current.stageRef.current = stageWithRightEdge(1000);
    });

    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 700 }));
    });

    expect(result.current.width).toBe(340);
  });

  it("computes width from the stage's right edge while dragging, clamped to the min/max", () => {
    const { result } = renderHook(() => useResizableRail());
    act(() => {
      result.current.stageRef.current = stageWithRightEdge(1000);
    });

    act(() => {
      result.current.startDrag(fakePointerEvent());
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 700 }));
    });
    expect(result.current.width).toBe(300);

    // 화면 가장자리까지 끌어도 최소/최대 폭을 벗어나지 않는다.
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 0 }));
    });
    expect(result.current.width).toBe(640);

    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 990 }));
    });
    expect(result.current.width).toBe(260);
  });

  it("persists the final width to localStorage on pointerup, and ignores further moves", () => {
    const { result } = renderHook(() => useResizableRail());
    act(() => {
      result.current.stageRef.current = stageWithRightEdge(1000);
      result.current.startDrag(fakePointerEvent());
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 700 }));
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointerup"));
    });

    expect(localStorage.getItem(STORAGE_KEY)).toBe("300");

    // 드래그가 끝난 뒤의 pointermove는 더 이상 폭에 영향을 주지 않는다.
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 100 }));
    });
    expect(result.current.width).toBe(300);
  });
});
