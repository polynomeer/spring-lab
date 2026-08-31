import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { exportSvgAsPngFile, exportSvgAsSvgFile } from "./exportDiagram";

const SVG_NS = "http://www.w3.org/2000/svg";

// jsdom의 Blob은 slice()/size/type만 구현하고 표준 text()/arrayBuffer()는 없다 - FileReader는
// 있으므로 그걸로 대신 읽는다.
function readBlobAsText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

function buildSampleSvg(): SVGSVGElement {
  const svg = document.createElementNS(SVG_NS, "svg") as SVGSVGElement;
  svg.setAttribute("viewBox", "0 0 120 80");
  const group = document.createElementNS(SVG_NS, "g");
  const circle = document.createElementNS(SVG_NS, "circle");
  circle.setAttribute("r", "10");
  group.appendChild(circle);
  svg.appendChild(group);
  return svg;
}

// jsdom의 CSS 엔진은 SVG presentation attribute(fill="..." 등)를 실제 브라우저처럼
// getComputedStyle에 정확히 반영하지 않는다 - exportDiagram.ts 자신의 로직(computed style을
// 읽어서 style 속성으로 구워 넣는다)을 검증하는 게 목적이지 jsdom의 CSS 충실도를 검증하는 게
// 아니므로, getComputedStyle 자체를 결정론적인 값으로 모킹한다.
function mockComputedStyle() {
  return vi.spyOn(window, "getComputedStyle").mockImplementation((element: Element) => {
    if (element === document.documentElement) {
      return {
        getPropertyValue: (prop: string) => (prop === "--ink-900" ? "  #191d24  " : ""),
      } as CSSStyleDeclaration;
    }
    const tag = element.tagName;
    return {
      getPropertyValue: (prop: string) => `${tag}-${prop}`,
    } as CSSStyleDeclaration;
  });
}

describe("exportSvgAsSvgFile", () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockComputedStyle();
    createObjectURL = vi.fn(() => "blob:mock-url");
    revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", { ...URL, createObjectURL, revokeObjectURL });
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("downloads a self-contained SVG with the viewBox background rect inserted first", async () => {
    exportSvgAsSvgFile(buildSampleSvg(), "bean-lifecycle");

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    expect(blob.type).toBe("image/svg+xml");

    const source = await readBlobAsText(blob);
    expect(source).toContain('xmlns="http://www.w3.org/2000/svg"');

    // 배경 rect가 클론의 첫 자식으로, viewBox 크기 그대로, 트리밍된 --ink-900 값으로 들어갔다.
    const parsed = new DOMParser().parseFromString(source, "image/svg+xml");
    const root = parsed.documentElement;
    const firstChild = root.firstElementChild;
    expect(firstChild?.tagName).toBe("rect");
    expect(firstChild?.getAttribute("width")).toBe("120");
    expect(firstChild?.getAttribute("height")).toBe("80");
    expect(firstChild?.getAttribute("fill")).toBe("#191d24");

    // 원본 트리의 각 요소(svg 자신 포함)에 computed style이 그대로 style 속성으로 구워졌다.
    expect(root.getAttribute("style")).toBe("fill:svg-fill;stroke:svg-stroke;stroke-width:svg-stroke-width;" +
      "stroke-dasharray:svg-stroke-dasharray;stroke-opacity:svg-stroke-opacity;fill-opacity:svg-fill-opacity;" +
      "opacity:svg-opacity;font-family:svg-font-family;font-size:svg-font-size;font-weight:svg-font-weight;" +
      "letter-spacing:svg-letter-spacing;text-anchor:svg-text-anchor");
    const circle = root.querySelector("circle");
    expect(circle?.getAttribute("style")).toContain("fill:circle-fill");

    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:mock-url");
  });

  it("names the downloaded file after the given base with an .svg extension", () => {
    let downloadedName = "";
    clickSpy.mockImplementation(function (this: HTMLAnchorElement) {
      downloadedName = this.download;
    });

    exportSvgAsSvgFile(buildSampleSvg(), "aop-proxy");

    expect(downloadedName).toBe("aop-proxy.svg");
  });

  it("falls back to a solid default background color if --ink-900 isn't resolvable", async () => {
    vi.spyOn(window, "getComputedStyle").mockImplementation((element: Element) => {
      if (element === document.documentElement) {
        return { getPropertyValue: () => "" } as unknown as CSSStyleDeclaration;
      }
      return { getPropertyValue: () => "" } as unknown as CSSStyleDeclaration;
    });

    exportSvgAsSvgFile(buildSampleSvg(), "bean-lifecycle");
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const source = await readBlobAsText(blob);
    expect(source).toContain('fill="#191d24"');
  });
});

describe("exportSvgAsPngFile", () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let drawImageSpy: ReturnType<typeof vi.fn>;
  let toBlobSpy: ReturnType<typeof vi.fn>;
  let canvasSizes: { width: number; height: number }[];

  class FakeImage {
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    private _src = "";
    get src() {
      return this._src;
    }
    set src(value: string) {
      this._src = value;
      // 실제 Image는 비동기로 디코딩되지만, 여기서는 그 타이밍 자체가 아니라 "로드가 끝나면
      // 캔버스에 그려서 PNG로 내보낸다"는 우리 코드의 로직만 검증하면 된다.
      queueMicrotask(() => this.onload?.());
    }
  }

  beforeEach(() => {
    mockComputedStyle();
    createObjectURL = vi.fn(() => "blob:mock-svg-url");
    revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", { ...URL, createObjectURL, revokeObjectURL });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    vi.stubGlobal("Image", FakeImage);

    canvasSizes = [];
    drawImageSpy = vi.fn();
    toBlobSpy = vi.fn((callback: BlobCallback) => callback(new Blob(["png-bytes"], { type: "image/png" })));
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(function (
      this: HTMLCanvasElement,
    ) {
      canvasSizes.push({ width: this.width, height: this.height });
      return { drawImage: drawImageSpy } as unknown as CanvasRenderingContext2D;
    });
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(toBlobSpy as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("rasterizes the viewBox at the given scale and downloads it as a PNG", async () => {
    exportSvgAsPngFile(buildSampleSvg(), "tx-propagation", 2);

    // Image.onload fires on a microtask - flush it.
    await vi.waitFor(() => expect(drawImageSpy).toHaveBeenCalledTimes(1));

    expect(canvasSizes).toEqual([{ width: 240, height: 160 }]); // 120x80 viewBox * scale 2
    expect(toBlobSpy).toHaveBeenCalledTimes(1);
    expect(toBlobSpy.mock.calls[0][1]).toBe("image/png");
  });

  it("defaults to a 2x scale", async () => {
    exportSvgAsPngFile(buildSampleSvg(), "tx-propagation");
    await vi.waitFor(() => expect(drawImageSpy).toHaveBeenCalledTimes(1));
    expect(canvasSizes).toEqual([{ width: 240, height: 160 }]);
  });

  it("revokes the intermediate SVG object URL once the PNG blob is ready", async () => {
    exportSvgAsPngFile(buildSampleSvg(), "tx-propagation");
    await vi.waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith("blob:mock-svg-url"));
  });
});
