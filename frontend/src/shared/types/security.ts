export type SecretRef = {
  id: string;
  name: string;
  provider: string;
  externalKey: string;
  description: string | null;
  scope: string;
  metadata: Record<string, unknown> | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateSecretRefRequest = {
  name: string;
  provider: string;
  externalKey: string;
  description?: string;
  scope?: string;
  metadata?: Record<string, unknown>;
};

export type ExternalConnection = {
  id: string;
  name: string;
  connectionType: string;
  url: string | null;
  credentialsRef: string | null;
  secretRefId: string | null;
  config: Record<string, unknown> | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateExternalConnectionRequest = {
  name: string;
  connectionType: string;
  url?: string;
  credentialsRef?: string;
  secretRefId?: string;
  config?: Record<string, unknown>;
};

export type PermissionAssignment = {
  id: string;
  subjectType: string;
  userId: string | null;
  roleId: string | null;
  resourceType: string;
  resourceId: string | null;
  permission: string;
  effect: string;
  createdAt: string;
};

export type CreatePermissionAssignmentRequest = {
  subjectType: string;
  userId?: string;
  roleId?: string;
  resourceType: string;
  resourceId?: string;
  permission: string;
  effect?: string;
};
