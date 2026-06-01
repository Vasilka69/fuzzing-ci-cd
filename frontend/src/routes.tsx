import { AppLayout } from "@/app/AppLayout";
import { RequireAuth } from "@/app/RequireAuth";
import { PermissionsPage } from "@/features/admin/PermissionsPage";
import { ArtifactListPage } from "@/features/artifacts/ArtifactListPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { AuditPage } from "@/features/audit/AuditPage";
import { DeploymentPage } from "@/features/deployments/DeploymentPage";
import { ExecutorsPage } from "@/features/executors/ExecutorsPage";
import { PipelineDetailsPage } from "@/features/pipelines/PipelineDetailsPage";
import { PipelineDesignerPage } from "@/features/pipelines/PipelineDesignerPage";
import { PipelineListPage } from "@/features/pipelines/PipelineListPage";
import { PipelineRunPage } from "@/features/runs/PipelineRunPage";
import { PipelineRunsPage } from "@/features/runs/PipelineRunsPage";
import { ConnectionsPage } from "@/features/settings/ConnectionsPage";
import { SecretRefsPage } from "@/features/settings/SecretRefsPage";
import { Navigate, useRoutes } from "react-router-dom";

export function AppRoutes() {
  return useRoutes([
    { path: "/login", element: <LoginPage /> },
    {
      element: <RequireAuth />,
      children: [
        {
          element: <AppLayout />,
          children: [
            { path: "/", element: <Navigate to="/pipelines" replace /> },
            { path: "/pipelines", element: <PipelineListPage /> },
            { path: "/pipelines/:id", element: <PipelineDetailsPage /> },
            { path: "/pipelines/:id/designer", element: <PipelineDesignerPage /> },
            { path: "/runs", element: <PipelineRunsPage /> },
            { path: "/runs/:id", element: <PipelineRunPage /> },
            { path: "/artifacts", element: <ArtifactListPage /> },
            { path: "/executors", element: <ExecutorsPage /> },
            { path: "/deployments", element: <DeploymentPage /> },
            { path: "/settings/connections", element: <ConnectionsPage /> },
            { path: "/settings/secrets", element: <SecretRefsPage /> },
            { path: "/admin/permissions", element: <PermissionsPage /> },
            { path: "/audit", element: <AuditPage /> },
            { path: "*", element: <Navigate to="/pipelines" replace /> }
          ]
        }
      ]
    }
  ]);
}
