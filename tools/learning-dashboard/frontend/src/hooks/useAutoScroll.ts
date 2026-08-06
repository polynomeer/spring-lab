import { useEffect, useRef } from "react";

/** 로그 패널을 위한 것 - 의존값(보통 항목 개수)이 바뀔 때마다 컨테이너를 맨 아래로 스크롤한다. */
export function useAutoScroll<T extends HTMLElement>(dependency: unknown) {
  const ref = useRef<T>(null);

  useEffect(() => {
    const node = ref.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [dependency]);

  return ref;
}
