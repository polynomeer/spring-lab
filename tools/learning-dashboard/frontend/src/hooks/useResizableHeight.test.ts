import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useResizableHeight } from "./useResizableHeight";

const STORAGE_KEY = "trace-dash.test-section-height";

function fakePointerEvent(clientY: number) {
  return { preventDefault: vi.fn(), clientY } as unknown as React.PointerEvent;
}

describe("useResizableHeight", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("starts at the given default when nothing is stored", () => {
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));
    expect(result.current.height).toBe(200);
  });

  it("reads a previously stored height, clamped to the given range", () => {
    localStorage.setItem(STORAGE_KEY, "999");
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));
    expect(result.current.height).toBe(640);
  });

  it("grows/shrinks from the drag start point, relative to the origin height", () => {
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));

    act(() => {
      result.current.startDrag(fakePointerEvent(100));
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 180 }));
    });
    expect(result.current.height).toBe(280);

    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 40 }));
    });
    expect(result.current.height).toBe(140);
  });

  it("clamps to the min/max even while actively dragging", () => {
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));

    act(() => {
      result.current.startDrag(fakePointerEvent(100));
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 5000 }));
    });
    expect(result.current.height).toBe(640);

    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: -5000 }));
    });
    expect(result.current.height).toBe(90);
  });

  it("ignores pointermove before startDrag", () => {
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 500 }));
    });
    expect(result.current.height).toBe(200);
  });

  it("persists the final height to localStorage on pointerup", () => {
    const { result } = renderHook(() => useResizableHeight(STORAGE_KEY, 200, 90, 640));
    act(() => {
      result.current.startDrag(fakePointerEvent(100));
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 180 }));
    });
    act(() => {
      window.dispatchEvent(new PointerEvent("pointerup"));
    });

    expect(localStorage.getItem(STORAGE_KEY)).toBe("280");

    act(() => {
      window.dispatchEvent(new PointerEvent("pointermove", { clientY: 10 }));
    });
    expect(result.current.height).toBe(280);
  });
});
