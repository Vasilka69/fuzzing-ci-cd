import { useAuth } from "@/app/AuthProvider";
import { artifactsApi } from "@/api/artifactsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Space, Table, Tag, Typography, message } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";

export function ArtifactListPage() {
  const { hasCapability } = useAuth();
  const canView = hasCapability("view");
  const [pipelineRunId, setPipelineRunId] = useState<string>("");
  const [jobExecutionId, setJobExecutionId] = useState<string>("");
  const [appliedFilters, setAppliedFilters] = useState<{ pipelineRunId?: string; jobExecutionId?: string }>({});
  const [messageApi, contextHolder] = message.useMessage();
  const { pagination, onPaginationChange, resetPage } = useTablePagination(12);

  const query = useQuery({
    queryKey: ["artifacts", appliedFilters],
    queryFn: () => artifactsApi.list(appliedFilters),
    enabled: canView
  });

  const openDownload = async (artifactId: string) => {
    const popup = window.open("", "_blank", "noopener,noreferrer");
    try {
      const result = await artifactsApi.downloadUrl(artifactId);
      if (!result.downloadUrl) {
        popup?.close();
        messageApi.warning("Download URL is empty for this artifact");
        return;
      }
      if (popup) {
        popup.location.href = result.downloadUrl;
      } else {
        window.open(result.downloadUrl, "_blank", "noopener,noreferrer");
      }
    } catch (error) {
      popup?.close();
      messageApi.error(`Failed to fetch download URL: ${String(error)}`);
    }
  };

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing artifacts requires `view` capability." />;
  }

  const columns = useMemo(
    () => [
      {
        title: "Artifact",
        key: "name",
        render: (row: {
          id: string;
          name: string;
          artifactType: string;
          status: string;
          checksumVerified: boolean;
        }) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{row.name}</Typography.Text>
            <Typography.Text type="secondary">{row.artifactType}</Typography.Text>
            <Button size="small" type="link" style={{ padding: 0 }} onClick={() => openDownload(row.id)}>
              Open Download URL
            </Button>
          </Space>
        )
      },
      {
        title: "Run",
        dataIndex: "pipelineRunId",
        width: 130,
        render: (value: string | null) => (value ? value.slice(0, 8) : "-")
      },
      {
        title: "Execution",
        dataIndex: "jobExecutionId",
        width: 130,
        render: (value: string | null) => (value ? value.slice(0, 8) : "-")
      },
      {
        title: "Size",
        dataIndex: "sizeBytes",
        width: 120,
        render: (value: number | null) => (value == null ? "-" : `${(value / 1024).toFixed(1)} KB`)
      },
      {
        title: "Status",
        dataIndex: "status",
        width: 120,
        render: (value: string) => <Tag color={value === "available" ? "success" : "default"}>{value}</Tag>
      },
      {
        title: "Checksum",
        dataIndex: "checksumVerified",
        width: 130,
        render: (value: boolean) => <Tag color={value ? "success" : "warning"}>{value ? "verified" : "pending"}</Tag>
      },
      {
        title: "Created",
        dataIndex: "createdAt",
        width: 180,
        render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm:ss")
      }
    ],
    [openDownload]
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <h2 className="section-title">Artifacts</h2>
        <p className="section-subtitle">Search artifacts by pipeline run or job execution and open download URIs.</p>
        <Space wrap style={{ marginTop: 12 }}>
          <Input
            placeholder="pipelineRunId"
            value={pipelineRunId}
            onChange={(e) => setPipelineRunId(e.target.value)}
            style={{ width: 280 }}
          />
          <Input
            placeholder="jobExecutionId"
            value={jobExecutionId}
            onChange={(e) => setJobExecutionId(e.target.value)}
            style={{ width: 280 }}
          />
          <Button
            type="primary"
            onClick={() => {
              setAppliedFilters({
                pipelineRunId: pipelineRunId || undefined,
                jobExecutionId: jobExecutionId || undefined
              });
              resetPage();
            }}
          >
            Apply
          </Button>
          <Button
            onClick={() => {
              setPipelineRunId("");
              setJobExecutionId("");
              setAppliedFilters({});
              resetPage();
            }}
          >
            Reset
          </Button>
        </Space>
      </div>

      <ApiErrorAlert error={query.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />
    </Space>
  );
}
