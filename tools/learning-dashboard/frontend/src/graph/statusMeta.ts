import type { StatusMeta } from "./types";

export const BEAN_LIFECYCLE_STATUS_META: Record<string, StatusMeta> = {
  creating: { label: "CREATING", color: "var(--text-dim)" },
  registered: { label: "REGISTERED", color: "var(--blue)" },
  populating: { label: "POPULATING", color: "var(--text-dim)" },
  "early-ref-requested": { label: "EARLY REF", color: "var(--amber)" },
  "early-ref-proxied": { label: "EARLY REF · PROXIED", color: "var(--violet)" },
  initializing: { label: "INITIALIZING", color: "var(--jade)" },
};

export const AUTO_PROXY_STATUS_META: Record<string, StatusMeta> = {
  "advisor-pool": { label: "ADVISORS", color: "var(--violet)" },
  evaluating: { label: "EVALUATING", color: "var(--amber)" },
  advisor: { label: "ADVISOR (재귀 생성됨)", color: "var(--violet)" },
  proxied: { label: "PROXIED", color: "var(--jade)" },
  skipped: { label: "SKIPPED", color: "var(--rose)" },
};
