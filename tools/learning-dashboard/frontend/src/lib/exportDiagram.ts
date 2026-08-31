// StatusGraph/Pipeline/Swimlane는 색상을 CSS 커스텀 프로퍼티(var(--jade) 등)로, 라벨
// 스타일은 styles.css의 클래스(.graph-node-label 등)로 넣는다 - 화면에 붙어 있을 때는
// 문제없지만, SVG를 파일로 내보내면 그 스타일시트가 함께 따라가지 않아 색도 글꼴도 사라진다.
// 그래서 내보내기 직전에 "지금 실제로 화면에 렌더링된" computed style을 각 노드에 그대로
// 구워 넣어 완전히 독립적인 SVG로 만든다.
const INLINE_PROPERTIES = [
  "fill",
  "stroke",
  "stroke-width",
  "stroke-dasharray",
  "stroke-opacity",
  "fill-opacity",
  "opacity",
  "font-family",
  "font-size",
  "font-weight",
  "letter-spacing",
  "text-anchor",
] as const;

function inlineComputedStyles(source: Element, target: Element) {
  const computed = getComputedStyle(source);
  const declarations = INLINE_PROPERTIES.map((prop) => `${prop}:${computed.getPropertyValue(prop)}`).join(";");
  target.setAttribute("style", declarations);

  const sourceChildren = Array.from(source.children);
  const targetChildren = Array.from(target.children);
  sourceChildren.forEach((child, index) => {
    const targetChild = targetChildren[index];
    if (targetChild) {
      inlineComputedStyles(child, targetChild);
    }
  });
}

function buildStandaloneSvg(svg: SVGSVGElement): SVGSVGElement {
  const clone = svg.cloneNode(true) as SVGSVGElement;
  inlineComputedStyles(svg, clone);
  clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");

  const background = getComputedStyle(document.documentElement).getPropertyValue("--ink-900").trim() || "#191d24";
  const [, , viewBoxWidth, viewBoxHeight] = (clone.getAttribute("viewBox") ?? "0 0 100 100").split(/\s+/);
  const backgroundRect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  backgroundRect.setAttribute("x", "0");
  backgroundRect.setAttribute("y", "0");
  backgroundRect.setAttribute("width", viewBoxWidth);
  backgroundRect.setAttribute("height", viewBoxHeight);
  backgroundRect.setAttribute("fill", background);
  clone.insertBefore(backgroundRect, clone.firstChild);

  return clone;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function exportSvgAsSvgFile(svg: SVGSVGElement, filenameBase: string) {
  const standalone = buildStandaloneSvg(svg);
  const source = new XMLSerializer().serializeToString(standalone);
  downloadBlob(new Blob([source], { type: "image/svg+xml" }), `${filenameBase}.svg`);
}

export function exportSvgAsPngFile(svg: SVGSVGElement, filenameBase: string, scale = 2) {
  const standalone = buildStandaloneSvg(svg);
  const [, , viewBoxWidth, viewBoxHeight] = (standalone.getAttribute("viewBox") ?? "0 0 800 600").split(/\s+/).map(Number);
  const source = new XMLSerializer().serializeToString(standalone);
  const svgBlob = new Blob([source], { type: "image/svg+xml" });
  const url = URL.createObjectURL(svgBlob);

  const image = new Image();
  image.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(viewBoxWidth * scale));
    canvas.height = Math.max(1, Math.round(viewBoxHeight * scale));
    const ctx = canvas.getContext("2d");
    if (ctx) {
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      canvas.toBlob((blob) => {
        if (blob) {
          downloadBlob(blob, `${filenameBase}.png`);
        }
        URL.revokeObjectURL(url);
      }, "image/png");
    } else {
      URL.revokeObjectURL(url);
    }
  };
  image.onerror = () => URL.revokeObjectURL(url);
  image.src = url;
}
