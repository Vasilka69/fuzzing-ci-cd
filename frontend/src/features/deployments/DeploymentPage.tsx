import { useAuth } from "@/app/AuthProvider";
import { deploymentsApi } from "@/api/deploymentsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { StatusTag } from "@/shared/components/StatusTag";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Col, Input, Row, Select, Space, Table, Tabs, Typography, message } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function toUuidOrUndefined(value?: string): string | undefined {
  const normalized = value?.trim();
  if (!normalized) {
    return undefined;
  }
  return UUID_PATTERN.test(normalized) ? normalized : undefined;
}

export function DeploymentPage() {
  const { hasCapability } = useAuth();
  const canView = hasCapability("view");
  const canApprove = hasCapability("approve_deployment");
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const [approvalStatusFilter, setApprovalStatusFilter] = useState<string | undefined>(undefined);
  const [approvalPipelineRunFilter, setApprovalPipelineRunFilter] = useState("");
  const [appliedApprovalsFilter, setAppliedApprovalsFilter] = useState<{ status?: string; pipelineRunId?: string }>({});
  const [releaseStatusFilter, setReleaseStatusFilter] = useState<string | undefined>(undefined);
  const [releaseEnvironmentFilter, setReleaseEnvironmentFilter] = useState<string | undefined>(undefined);
  const [releaseIdFilter, setReleaseIdFilter] = useState("");
  const [appliedReleasesFilter, setAppliedReleasesFilter] = useState<{
    status?: string;
    environmentId?: string;
    releaseId?: string;
  }>({});
  const {
    pagination: environmentsPagination,
    onPaginationChange: onEnvironmentsPaginationChange
  } = useTablePagination(10);
  const {
    pagination: approvalsPagination,
    onPaginationChange: onApprovalsPaginationChange,
    resetPage: resetApprovalsPage
  } = useTablePagination(10);
  const {
    pagination: releasesPagination,
    onPaginationChange: onReleasesPaginationChange,
    resetPage: resetReleasesPage
  } = useTablePagination(10);

  const environmentsQuery = useQuery({
    queryKey: ["deployments", "environments"],
    queryFn: () => deploymentsApi.environments(),
    enabled: canView
  });
  const approvalsQuery = useQuery({
    queryKey: ["deployments", "approvals", appliedApprovalsFilter.status],
    queryFn: () =>
      deploymentsApi.approvals({
        status: appliedApprovalsFilter.status,
        pipelineRunId: toUuidOrUndefined(appliedApprovalsFilter.pipelineRunId)
      }),
    enabled: canView
  });
  const releasesQuery = useQuery({
    queryKey: ["deployments", "releases", appliedReleasesFilter.status, appliedReleasesFilter.environmentId, appliedReleasesFilter.releaseId],
    queryFn: () =>
      deploymentsApi.releases({
        status: appliedReleasesFilter.status,
        environmentId: toUuidOrUndefined(appliedReleasesFilter.environmentId),
        releaseId: appliedReleasesFilter.releaseId
      }),
    enabled: canView
  });

  const approveMutation = useMutation({
    mutationFn: deploymentsApi.approve,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["deployments", "approvals"] });
      messageApi.success("Deployment approved");
    }
  });
  const rejectMutation = useMutation({
    mutationFn: deploymentsApi.reject,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["deployments", "approvals"] });
      messageApi.success("Deployment rejected");
    }
  });

  const filteredApprovals = useMemo(() => {
    const pipelineNeedle = appliedApprovalsFilter.pipelineRunId?.trim().toLowerCase();
    return (approvalsQuery.data ?? []).filter((approval) => {
      if (!pipelineNeedle) {
        return true;
      }
      return String(approval.pipelineRunId ?? "")
        .toLowerCase()
        .includes(pipelineNeedle);
    });
  }, [approvalsQuery.data, appliedApprovalsFilter.pipelineRunId]);

  const filteredReleases = useMemo(() => {
    const releaseNeedle = appliedReleasesFilter.releaseId?.trim().toLowerCase();
    if (!releaseNeedle) {
      return releasesQuery.data ?? [];
    }
    return (releasesQuery.data ?? []).filter((release) => release.releaseId.toLowerCase().includes(releaseNeedle));
  }, [releasesQuery.data, appliedReleasesFilter.releaseId]);

  const environmentColumns = useMemo(
    () => [
      { title: "Name", dataIndex: "name" },
      { title: "Description", dataIndex: "description", render: (value: string | null) => value || "-" },
      { title: "Protected", dataIndex: "config", render: (config: Record<string, unknown> | null) => String(Boolean(config?.protected)) },
      { title: "Requires Approval", dataIndex: "config", render: (config: Record<string, unknown> | null) => String(Boolean(config?.requires_approval)) }
    ],
    []
  );

  const approvalColumns = useMemo(
    () => [
      { title: "Approval", dataIndex: "id", render: (value: string) => value.slice(0, 8) },
      { title: "Run", dataIndex: "pipelineRunId", render: (value: string | null) => (value ? value.slice(0, 8) : "-") },
      { title: "Execution", dataIndex: "jobExecutionId", render: (value: string | null) => (value ? value.slice(0, 8) : "-") },
      { title: "Status", dataIndex: "status", render: (status: string) => <StatusTag status={status} /> },
      { title: "Created", dataIndex: "createdAt", render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm:ss") },
      {
        title: "Actions",
        key: "actions",
        render: (row: { id: string; status: string }) => (
          <Space>
            <Button
              size="small"
              type="primary"
              disabled={!canApprove || row.status !== "pending"}
              loading={approveMutation.isPending}
              onClick={() => approveMutation.mutate(row.id)}
            >
              Approve
            </Button>
            <Button
              size="small"
              danger
              disabled={!canApprove || row.status !== "pending"}
              loading={rejectMutation.isPending}
              onClick={() => rejectMutation.mutate(row.id)}
            >
              Reject
            </Button>
          </Space>
        )
      }
    ],
    [approveMutation, rejectMutation, canApprove]
  );

  const releaseColumns = useMemo(
    () => [
      { title: "Release", dataIndex: "releaseId" },
      { title: "Status", dataIndex: "status", render: (status: string) => <StatusTag status={status} /> },
      { title: "Environment", dataIndex: "environmentId", render: (value: string | null) => (value ? value.slice(0, 8) : "-") },
      { title: "Type", dataIndex: "deploymentType" },
      { title: "Artifact", dataIndex: "artifactUri", render: (value: string) => <Typography.Link href={value}>{value}</Typography.Link> },
      { title: "Deployed", dataIndex: "deployedAt", render: (value: string | null) => (value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-") }
    ],
    []
  );

  const firstError = environmentsQuery.error || approvalsQuery.error || releasesQuery.error;

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing deployments requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <h2 className="section-title">Deployments</h2>
        <p className="section-subtitle">Protected environments, pending approvals, and releases history.</p>
      </div>
      <ApiErrorAlert error={firstError} />

      <Tabs
        items={[
          {
            key: "environments",
            label: "Environments",
            children: (
              <Table
                className="glass-card"
                rowKey="id"
                loading={environmentsQuery.isLoading}
                dataSource={environmentsQuery.data ?? []}
                columns={environmentColumns}
                pagination={environmentsPagination}
                onChange={(nextPagination) => onEnvironmentsPaginationChange(nextPagination)}
              />
            )
          },
          {
            key: "approvals",
            label: "Approvals",
            children: (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                <Space wrap>
                  <Select
                    allowClear
                    style={{ minWidth: 170 }}
                    placeholder="Status"
                    value={approvalStatusFilter}
                    onChange={(value) => setApprovalStatusFilter(value)}
                    options={["pending", "approved", "rejected", "expired"].map((value) => ({ value, label: value }))}
                  />
                  <Input
                    style={{ width: 280 }}
                    placeholder="Pipeline run ID contains..."
                    value={approvalPipelineRunFilter}
                    onChange={(event) => setApprovalPipelineRunFilter(event.target.value)}
                  />
                  <Button
                    type="primary"
                    onClick={() => {
                      setAppliedApprovalsFilter({
                        status: approvalStatusFilter,
                        pipelineRunId: approvalPipelineRunFilter || undefined
                      });
                      resetApprovalsPage();
                    }}
                  >
                    Apply
                  </Button>
                  <Button
                    onClick={() => {
                      setApprovalStatusFilter(undefined);
                      setApprovalPipelineRunFilter("");
                      setAppliedApprovalsFilter({});
                      resetApprovalsPage();
                    }}
                  >
                    Reset
                  </Button>
                </Space>
                <Row gutter={16}>
                  <Col span={24}>
                    <Table
                      className="glass-card"
                      rowKey="id"
                      loading={approvalsQuery.isLoading}
                      dataSource={filteredApprovals}
                      columns={approvalColumns}
                      pagination={approvalsPagination}
                      onChange={(nextPagination) => onApprovalsPaginationChange(nextPagination)}
                    />
                  </Col>
                </Row>
              </Space>
            )
          },
          {
            key: "releases",
            label: "Releases",
            children: (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                <Space wrap>
                  <Select
                    allowClear
                    style={{ minWidth: 200 }}
                    placeholder="Environment"
                    value={releaseEnvironmentFilter}
                    onChange={(value) => setReleaseEnvironmentFilter(value)}
                    options={(environmentsQuery.data ?? []).map((env) => ({
                      value: env.id,
                      label: `${env.name} (${env.id.slice(0, 8)})`
                    }))}
                  />
                  <Select
                    allowClear
                    style={{ minWidth: 170 }}
                    placeholder="Status"
                    value={releaseStatusFilter}
                    onChange={(value) => setReleaseStatusFilter(value)}
                    options={["queued", "running", "success", "failed", "canceled", "pending"].map((value) => ({
                      value,
                      label: value
                    }))}
                  />
                  <Input
                    style={{ width: 260 }}
                    placeholder="Release ID contains..."
                    value={releaseIdFilter}
                    onChange={(event) => setReleaseIdFilter(event.target.value)}
                  />
                  <Button
                    type="primary"
                    onClick={() => {
                      setAppliedReleasesFilter({
                        status: releaseStatusFilter,
                        environmentId: releaseEnvironmentFilter,
                        releaseId: releaseIdFilter || undefined
                      });
                      resetReleasesPage();
                    }}
                  >
                    Apply
                  </Button>
                  <Button
                    onClick={() => {
                      setReleaseStatusFilter(undefined);
                      setReleaseEnvironmentFilter(undefined);
                      setReleaseIdFilter("");
                      setAppliedReleasesFilter({});
                      resetReleasesPage();
                    }}
                  >
                    Reset
                  </Button>
                </Space>
                <Table
                  className="glass-card"
                  rowKey="id"
                  loading={releasesQuery.isLoading}
                  dataSource={filteredReleases}
                  columns={releaseColumns}
                  pagination={releasesPagination}
                  onChange={(nextPagination) => onReleasesPaginationChange(nextPagination)}
                />
              </Space>
            )
          }
        ]}
      />
    </Space>
  );
}
