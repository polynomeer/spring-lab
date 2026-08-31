import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { StatusGraph } from "./StatusGraph";
import type { GraphEdge, GraphNode, StatusMeta } from "../graph/types";

const nodes: GraphNode[] = [
  { id: "a", label: "beanA", status: "created" },
  { id: "b", label: "beanB", status: "unknown-status" },
];
const edges: GraphEdge[] = [{ from: "a", to: "b" }];
const statusMeta: Record<string, StatusMeta> = {
  created: { label: "생성됨", color: "var(--jade)" },
};

function ringCircleFor(labelText: string) {
  const label = screen.getByText(labelText);
  const group = label.closest("g");
  return group?.querySelector("circle[stroke-dasharray]") ?? null;
}

describe("StatusGraph", () => {
  it("shows the empty hint when there are no nodes", () => {
    render(<StatusGraph nodes={[]} edges={[]} statusMeta={{}} emptyHint="아직 빈이 없습니다." />);
    expect(screen.getByText("아직 빈이 없습니다.")).toBeInTheDocument();
  });

  it("renders each node's label and status, falling back to the raw status when unmapped", () => {
    render(<StatusGraph nodes={nodes} edges={edges} statusMeta={statusMeta} emptyHint="empty" />);

    expect(screen.getByText("beanA")).toBeInTheDocument();
    expect(screen.getByText("생성됨")).toBeInTheDocument();
    expect(screen.getByText("beanB")).toBeInTheDocument();
    // 매핑되지 않은 status는 statusMeta 대신 원본 문자열을 그대로 보여준다.
    expect(screen.getByText("unknown-status")).toBeInTheDocument();
  });

  it("draws a highlight ring only around the node matching highlightedNodeId", () => {
    render(
      <StatusGraph nodes={nodes} edges={edges} statusMeta={statusMeta} emptyHint="empty" highlightedNodeId="b" />,
    );

    expect(ringCircleFor("beanA")).toBeNull();
    expect(ringCircleFor("beanB")).not.toBeNull();
  });

  it("reports hover in/out through onHoverNode", async () => {
    const user = userEvent.setup();
    const onHoverNode = vi.fn();
    render(
      <StatusGraph nodes={nodes} edges={edges} statusMeta={statusMeta} emptyHint="empty" onHoverNode={onHoverNode} />,
    );

    const group = screen.getByText("beanA").closest("g")!;
    await user.hover(group);
    expect(onHoverNode).toHaveBeenCalledWith("a");

    await user.unhover(group);
    expect(onHoverNode).toHaveBeenCalledWith(null);
  });

  it("skips edges whose endpoints aren't in the node list", () => {
    const { container } = render(
      <StatusGraph
        nodes={nodes}
        edges={[{ from: "a", to: "missing" }]}
        statusMeta={statusMeta}
        emptyHint="empty"
      />,
    );
    expect(container.querySelectorAll(".graph-edge")).toHaveLength(0);
  });
});
