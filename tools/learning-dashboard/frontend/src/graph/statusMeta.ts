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

export const TX_MARKER_META: Record<string, StatusMeta> = {
  TX_STARTED: { label: "START", color: "var(--blue)" },
  TX_SUSPENDED: { label: "SUSPEND", color: "var(--amber)" },
  TX_RESUMED: { label: "RESUME", color: "var(--violet)" },
  TX_COMMITTED: { label: "COMMIT", color: "var(--jade)" },
  TX_ROLLED_BACK: { label: "ROLLBACK", color: "var(--rose)" },
};

export const EVENT_MULTICAST_MARKER_META: Record<string, StatusMeta> = {
  EVENT_PUBLISHED: { label: "PUBLISH", color: "var(--blue)" },
  MULTICAST_STARTED: { label: "MULTICAST", color: "var(--text-dim)" },
  LISTENER_INVOKED: { label: "INVOKE", color: "var(--jade)" },
  ASYNC_LISTENER_EXECUTING: { label: "ASYNC", color: "var(--violet)" },
  TX_LISTENER_EVENT_RECEIVED: { label: "RECEIVED", color: "var(--text-dim)" },
  TX_LISTENER_INVOKED: { label: "COMMIT-INVOKE", color: "var(--amber)" },
};
