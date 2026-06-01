import { useAuth } from "@/app/AuthProvider";
import { pipelinesApi } from "@/api/pipelinesApi";
import { pipelineRunsApi } from "@/api/pipelineRunsApi";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Col, Divider, Row, Space, Table, Typography, message } from "antd";
import dayjs from "dayjs";
import { useMemo } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

export function PipelineDetailsPage() {
  const { hasCapability } = useAuth();
  const { id } = useParams();
  const pipelineId = id ?? "";
  const navigate = useNavigate();
  const [messageApi, contextHolder] = message.useMessage();

  const canView = hasCapability("view");
  const canEdit = hasCapability("edit");
  const canRun = hasCapability("run");

  const detailsQuery = useQuery({
    queryKey: ["pipeline-details", pipelineId],
    queryFn: () => pipelinesApi.details(pipelineId),
    enabled: Boolean(pipelineId) && canView
  });

  const recentRunsQuery = useQuery({
    queryKey: ["pipeline-runs", "pipeline", pipelineId],
    queryFn: () => pipelineRunsApi.list({ pipelineId }),
    enabled: Boolean(pipelineId) && canView
  });

  const runMutation = useMutation({
    mutationFn: () => pipelinesApi.run(pipelineId),
    onSuccess: (run) => {
      messageApi.success("Pipeline started");
      navigate(`/runs/${run.id}`);
    }
  });

  const stageColumns = useMemo(
    () => [
      { title: "Position", dataIndex: "position", width: 90 },
      { title: "Stage", dataIndex: "name" },
      { title: "Policy", dataIndex: "runPolicy", width: 150 }
    ],
    []
  );

  const jobColumns = useMemo(
    () => [
      { title: "Job", dataIndex: "name" },
      { title: "Type", dataIndex: "jobType", width: 130 },
      { title: "Stage", dataIndex: "stageId", width: 130, render: (v: string) => v.slice(0, 8) },
      { title: "Condition", dataIndex: "condition", width: 130 },
      { title: "Attempts", dataIndex: "maxAttempts", width: 110 }
    ],
    []
  );

  const dependencyColumns = useMemo(
    () => [
      { title: "Job", dataIndex: "jobId", render: (v: string) => v.slice(0, 8) },
      { title: "Depends On", dataIndex: "dependsOnJobId", render: (v: string) => v.slice(0, 8) },
      { title: "Condition", dataIndex: "condition", width: 150 }
    ],
    []
  );

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing pipeline details requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <Card className="glass-card">
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              {detailsQuery.data?.pipeline.name ?? "Pipeline Details"}
            </Typography.Title>
            <Typography.Text type="secondary">
              {detailsQuery.data?.pipeline.description || "No description"}
            </Typography.Text>
          </div>
          <Space>
            <Button disabled={!canEdit}>
              <Link to={`/pipelines/${pipelineId}/designer`}>Open Designer</Link>
            </Button>
            <Button type="primary" onClick={() => runMutation.mutate()} loading={runMutation.isPending} disabled={!canRun}>
              Run Pipeline
            </Button>
          </Space>
        </Space>
      </Card>

      <ApiErrorAlert error={detailsQuery.error || recentRunsQuery.error} />

      <Row gutter={16}>
        <Col xs={24} xl={12}>
          <Card className="glass-card" title="Stages">
            <Table
              size="small"
              rowKey="id"
              loading={detailsQuery.isLoading}
              dataSource={detailsQuery.data?.stages ?? []}
              columns={stageColumns}
              pagination={false}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="glass-card" title="Recent Runs">
            <Table
              size="small"
              rowKey="id"
              loading={recentRunsQuery.isLoading}
              dataSource={(recentRunsQuery.data ?? []).slice(0, 8)}
              pagination={false}
              columns={[
                { title: "Run", dataIndex: "id", render: (v: string) => v.slice(0, 8) },
                { title: "Status", dataIndex: "status" },
                {
                  title: "Started",
                  dataIndex: "startedAt",
                  render: (v: string) => dayjs(v).format("YYYY-MM-DD HH:mm:ss")
                }
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card className="glass-card" title="Jobs">
        <Table
          rowKey="id"
          size="small"
          loading={detailsQuery.isLoading}
          dataSource={detailsQuery.data?.jobs ?? []}
          columns={jobColumns}
          pagination={{ pageSize: 8 }}
        />
        <Divider />
        <Typography.Title level={5}>Dependencies</Typography.Title>
        <Table
          rowKey="id"
          size="small"
          loading={detailsQuery.isLoading}
          dataSource={detailsQuery.data?.dependencies ?? []}
          columns={dependencyColumns}
          pagination={false}
        />
      </Card>
    </Space>
  );
}
