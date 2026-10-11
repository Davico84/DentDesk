import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useElapsedWhile } from "./useElapsedWhile";
import { useOnlineStatus } from "./useOnlineStatus";

describe("useElapsedWhile", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("cuenta los segundos mientras está activo", async () => {
    const { result } = renderHook(() => useElapsedWhile(true));
    expect(result.current).toBe(0);
    await act(() => vi.advanceTimersByTimeAsync(4000));
    expect(result.current).toBe(4);
  });

  it("vuelve a 0 al pasar a inactivo y no cuenta mientras lo está", async () => {
    const { result, rerender } = renderHook(({ active }) => useElapsedWhile(active), {
      initialProps: { active: true },
    });
    await act(() => vi.advanceTimersByTimeAsync(5000));
    expect(result.current).toBe(5);

    rerender({ active: false });
    expect(result.current).toBe(0);
    await act(() => vi.advanceTimersByTimeAsync(5000));
    expect(result.current).toBe(0);

    rerender({ active: true });
    await act(() => vi.advanceTimersByTimeAsync(2000));
    expect(result.current).toBe(2);
  });

  it("limpia su intervalo al desmontar", async () => {
    const clear = vi.spyOn(globalThis, "clearInterval");
    const { unmount } = renderHook(() => useElapsedWhile(true));
    unmount();
    expect(clear).toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    clear.mockRestore();
  });
});

describe("useOnlineStatus", () => {
  it("sigue los eventos online/offline del navegador", () => {
    const onLine = vi.spyOn(navigator, "onLine", "get").mockReturnValue(true);
    const { result } = renderHook(() => useOnlineStatus());
    expect(result.current).toBe(true);

    onLine.mockReturnValue(false);
    act(() => {
      window.dispatchEvent(new Event("offline"));
    });
    expect(result.current).toBe(false);

    onLine.mockReturnValue(true);
    act(() => {
      window.dispatchEvent(new Event("online"));
    });
    expect(result.current).toBe(true);
    onLine.mockRestore();
  });
});
