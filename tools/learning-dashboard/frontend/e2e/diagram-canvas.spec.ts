import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";

const HARNESS_URL = "/?e2e=diagram-canvas";
const HEIGHT_STORAGE_KEY = "trace-dash.diagram-height";

test.beforeEach(async ({ page }) => {
  await page.goto(HARNESS_URL);
  await expect(page.getByTestId("harness-svg")).toBeVisible();
});

test("dragging the native resize handle grows the box and persists the height across reloads", async ({ page }) => {
  const box = page.locator(".diagram-canvas");
  const before = await box.boundingBox();
  if (!before) throw new Error("diagram-canvas box not found");

  // 우하단 리사이즈 코너로 마우스를 옮겨 세로로 드래그한다 - textarea 스타일 네이티브
  // resize(CSS resize: vertical)라 클릭 가능한 DOM 요소가 따로 없다.
  const handleX = before.x + before.width - 3;
  const handleY = before.y + before.height - 3;
  await page.mouse.move(handleX, handleY);
  await page.mouse.down();
  await page.mouse.move(handleX, handleY + 120, { steps: 10 });
  await page.mouse.up();

  await expect(async () => {
    const after = await box.boundingBox();
    expect(after?.height).toBeGreaterThan(before.height + 80);
  }).toPass();

  // ResizeObserver 콜백이 requestAnimationFrame으로 한 틱 미뤄져 있으므로(DiagramCanvas.tsx),
  // localStorage 반영도 그만큼 기다렸다가 확인한다.
  await expect
    .poll(() => page.evaluate((key) => localStorage.getItem(key), HEIGHT_STORAGE_KEY))
    .not.toBeNull();

  const storedHeight = Number(await page.evaluate((key) => localStorage.getItem(key), HEIGHT_STORAGE_KEY));
  expect(storedHeight).toBeGreaterThan(380);

  await page.reload();
  await expect(page.getByTestId("harness-svg")).toBeVisible();
  const afterReload = await box.boundingBox();
  expect(afterReload?.height).toBeCloseTo(storedHeight, 0);
});

test("scrolling the wheel zooms in/out, and the reset button restores 100%", async ({ page }) => {
  const viewport = page.locator(".diagram-viewport");
  await expect(page.locator(".diagram-zoom-level")).toHaveText("100%");

  const box = await viewport.boundingBox();
  if (!box) throw new Error("diagram-viewport box not found");
  const center = { x: box.x + box.width / 2, y: box.y + box.height / 2 };

  // deltaY < 0 = 위로 스크롤 = DiagramCanvas.tsx의 onWheel 계산상 확대.
  await page.mouse.move(center.x, center.y);
  await page.mouse.wheel(0, -100);
  await expect(page.locator(".diagram-zoom-level")).toHaveText("120%");

  await page.mouse.wheel(0, -100);
  await expect(page.locator(".diagram-zoom-level")).toHaveText("140%");

  await page.getByTitle("보기 초기화(줌/이동 리셋)").click();
  await expect(page.locator(".diagram-zoom-level")).toHaveText("100%");
});

test("dragging inside the viewport pans the content layer", async ({ page }) => {
  const viewport = page.locator(".diagram-viewport");
  const content = page.locator(".diagram-zoom-content");

  const initialTransform = await content.evaluate((el) => (el as HTMLElement).style.transform);
  expect(initialTransform).toContain("translate(0px, 0px)");

  const box = await viewport.boundingBox();
  if (!box) throw new Error("diagram-viewport box not found");
  const start = { x: box.x + box.width / 2, y: box.y + box.height / 2 };

  await page.mouse.move(start.x, start.y);
  await page.mouse.down();
  await page.mouse.move(start.x + 60, start.y + 40, { steps: 10 });
  await page.mouse.up();

  const panned = await content.evaluate((el) => (el as HTMLElement).style.transform);
  expect(panned).toContain("translate(60px, 40px)");
});

test("the SVG toolbar button downloads a valid, self-contained SVG file", async ({ page }) => {
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByTitle("SVG 파일로 내보내기").click(),
  ]);

  expect(download.suggestedFilename()).toBe("e2e-harness.svg");
  const path = await download.path();
  if (!path) throw new Error("download did not save to a local path");
  const content = readFileSync(path, "utf8");

  // exportDiagram.ts에 실제로 있었던 회귀(xmlns가 두 번 찍혀 파싱 자체가 실패하던 버그,
  // 커밋 53187e2)가 다시 생기면 여기서 바로 걸린다 - jsdom DOMParser보다 진짜 파서다.
  expect((content.match(/xmlns="http:\/\/www\.w3\.org\/2000\/svg"/g) ?? []).length).toBe(1);
  expect(content).toContain("<rect");
  expect(content).toContain("<circle");
});

test("the PNG toolbar button downloads a valid PNG file", async ({ page }) => {
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByTitle("PNG 파일로 내보내기").click(),
  ]);

  expect(download.suggestedFilename()).toBe("e2e-harness.png");
  const path = await download.path();
  if (!path) throw new Error("download did not save to a local path");
  const bytes = readFileSync(path);

  // PNG 매직 바이트: 89 50 4E 47 0D 0A 1A 0A.
  expect(bytes.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");
  expect(bytes.length).toBeGreaterThan(100);
});
