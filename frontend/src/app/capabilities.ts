export type Capability =
  | "view"
  | "edit"
  | "run"
  | "cancel"
  | "approve_deployment"
  | "manage_secrets"
  | "manage_connections"
  | "admin";

const ALL_CAPABILITIES: Capability[] = [
  "view",
  "edit",
  "run",
  "cancel",
  "approve_deployment",
  "manage_secrets",
  "manage_connections",
  "admin"
];

const ROLE_CAPABILITIES: Record<string, Capability[]> = {
  ADMIN: ALL_CAPABILITIES,
  DEVELOPER: ["view", "edit", "run", "cancel"],
  OPERATOR: ["view", "run", "cancel", "approve_deployment", "manage_connections"],
  VIEWER: ["view"]
};

export function resolveCapabilities(roles: string[]): Set<Capability> {
  const caps = new Set<Capability>();
  roles.forEach((role) => {
    const roleCaps = ROLE_CAPABILITIES[role.toUpperCase()] ?? [];
    roleCaps.forEach((cap) => caps.add(cap));
  });
  if (caps.has("admin")) {
    ALL_CAPABILITIES.forEach((cap) => caps.add(cap));
  }
  return caps;
}
