import { useAuth } from "@/app/AuthProvider";
import { pipelineRunsApi } from "@/api/pipelineRunsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { StatusTag } from "@/shared/components/StatusTag";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Select, Space, Table, Typography, message } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";

const statusOptions = [
  "queued",
  "running",
  "waiting_approval",
  "success",
  "failed",
  "canceling",
  "canceled",
  "timeout"
];

export function PipelineRunsPage() {
  const { hasCapability } = useAuth();
  const canView = hasCapability("view");
  const canRun = hasCapability("run");
  const canCancel = hasCapability("cancel");

  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [pipelineIdFilter, setPipelineIdFilter] = useState("");
  const [triggerTypeFilter, setTriggerTypeFilter] = useState<string | undefined>(undefined);
  const [appliedFilters, setAppliedFilters] = useState<{
    status?: string;
    pipelineId?: string;
    triggeredByType?: string;
  }>({});
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const { pagination, onPaginationChange, resetPage } = useTablePagination(12);

  const runsQuery = useQuery({
    queryKey: ["pipeline-runs", appliedFilters.status],
    queryFn: () => pipelineRunsApi.list({ status: appliedFilters.status }),
    enabled: canView
  });

  const cancelMutation = useMutation({
    mutationFn: pipelineRunsApi.cancel,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipeline-runs"] });
      messageApi.success("Run cancellation requested");
    }
  });

  const retryMutation = useMutation({
    mutationFn: pipelineRunsApi.retry,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipeline-runs"] });
      messageApi.success("Run retry triggered");
    }
  });

  const triggerTypeOptions = useMemo(() => {
    const options = new Set<string>();
    (runsQuery.data ?? []).forEach((run) => {
      if (run.triggeredByType) {
        options.add(run.triggeredByType);
      }
    });
    return Array.from(options).sort();
  }, [runsQuery.data]);

  const filteredRuns = useMemo(() => {
    const pipelineId = appliedFilters.pipelineId?.trim().toLowerCase();
    const triggerType = appliedFilters.triggeredByType?.trim().toLowerCase();
    return (runsQuery.data ?? []).filter((run) => {
      const pipelineMatch = !pipelineId || run.pipelineId.toLowerCase().includes(pipelineId);
      const triggerMatch = !triggerType || run.triggeredByType.toLowerCase() === triggerType;
      return pipelineMatch && triggerMatch;
    });
  }, [runsQuery.data, appliedFilters.pipelineId, appliedFilters.triggeredByType]);

  const columns = useMemo(
    () => [
      {
        title: "Run",
        key: "id",
        render: (row: { id: string; pipelineId: string; triggeredByType: string }) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>
              <Link to={`/runs/${row.id}`}>{row.id.slice(0, 8)}</Link>
            </Typography.Text>
            <Typography.Text type="secondary">
              pipeline {row.pipelineId.slice(0, 8)} | {row.triggeredByType}
            </Typography.Text>
          </Space>
        )
      },
      {
        title: "Status",
        dataIndex: "status",
        key: "status",
        width: 170,
        render: (status: string) => <StatusTag status={status} />
      },
      {
        title: "Started",
        dataIndex: "startedAt",
        key: "startedAt",
        width: 190,
        render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm:ss")
      },
      {
        title: "Finished",
        dataIndex: "finishedAt",
        key: "finishedAt",
        width: 190,
        render: (value: string | null) => (value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "in progress")
      },
      {
        title: "Actions",
        key: "actions",
        width: 230,
        render: (row: { id: string; status: string }) => (
          <Space>
            <Button
              size="small"
              onClick={() => cancelMutation.mutate(row.id)}
              disabled={!canCancel || ["success", "failed", "canceled", "timeout"].includes(row.status)}
              loading={cancelMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              size="small"
              onClick={() => retryMutation.mutate(row.id)}
              disabled={!canRun || !["failed", "timeout", "canceled", "skipped"].includes(row.status)}
              loading={retryMutation.isPending}
            >
              Retry
            </Button>
          </Space>
        )
      }
    ],
    [cancelMutation, retryMutation, canCancel, canRun]
  );

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing pipeline runs requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <h2 className="section-title">Pipeline Runs</h2>
            <p className="section-subtitle">Track historical runs and quickly retry or cancel when needed.</p>
          </div>
          <Space wrap>
            <Select
              allowClear
              style={{ minWidth: 180 }}
              placeholder="Status"
              options={statusOptions.map((status) => ({ value: status, label: status }))}
              value={statusFilter}
              onChange={(value) => setStatusFilter(value)}
            />
            <Select
              allowClear
              style={{ minWidth: 180 }}
              placeholder="Trigger type"
              options={triggerTypeOptions.map((value) => ({ value, label: value }))}
              value={triggerTypeFilter}
              onChange={(value) => setTriggerTypeFilter(value)}
            />
            <Input
              style={{ width: 260 }}
              placeholder="Pipeline ID contains..."
              value={pipelineIdFilter}
              onChange={(event) => setPipelineIdFilter(event.target.value)}
            />
            <Button
              type="primary"
              onClick={() => {
                setAppliedFilters({
                  status: statusFilter,
                  pipelineId: pipelineIdFilter || undefined,
                  triggeredByType: triggerTypeFilter
                });
                resetPage();
              }}
            >
              Apply
            </Button>
            <Button
              onClick={() => {
                setStatusFilter(undefined);
                setPipelineIdFilter("");
                setTriggerTypeFilter(undefined);
                setAppliedFilters({});
                resetPage();
              }}
            >
              Reset
            </Button>
          </Space>
        </Space>
      </div>

      <ApiErrorAlert error={runsQuery.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={runsQuery.isLoading}
        dataSource={filteredRuns}
        columns={columns}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />
    </Space>
  );
}
