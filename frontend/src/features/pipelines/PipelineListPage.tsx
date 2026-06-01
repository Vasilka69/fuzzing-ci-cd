import { useAuth } from "@/app/AuthProvider";
import { pipelinesApi } from "@/api/pipelinesApi";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Space, Table, Tag, Typography, message } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

type CreatePipelineForm = {
  name: string;
  description?: string;
};

export function PipelineListPage() {
  const { hasCapability } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [messageApi, contextHolder] = message.useMessage();
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<CreatePipelineForm>();

  const canView = hasCapability("view");
  const canEdit = hasCapability("edit");
  const canRun = hasCapability("run");

  const pipelinesQuery = useQuery({
    queryKey: ["pipelines"],
    queryFn: () => pipelinesApi.list(),
    enabled: canView
  });

  const createPipelineMutation = useMutation({
    mutationFn: pipelinesApi.create,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipelines"] });
      messageApi.success("Pipeline created");
      setCreateOpen(false);
      createForm.resetFields();
    }
  });

  const runPipelineMutation = useMutation({
    mutationFn: (pipelineId: string) => pipelinesApi.run(pipelineId),
    onSuccess: (run) => {
      messageApi.success("Pipeline started");
      navigate(`/runs/${run.id}`);
    }
  });

  const columns = useMemo(
    () => [
      {
        title: "Pipeline",
        key: "name",
        render: (row: { id: string; name: string; description: string | null }) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{row.name}</Typography.Text>
            <Typography.Text type="secondary">{row.description || "No description"}</Typography.Text>
          </Space>
        )
      },
      {
        title: "Active",
        dataIndex: "isActive",
        key: "isActive",
        width: 110,
        render: (isActive: boolean) => (isActive ? <Tag color="success">active</Tag> : <Tag>inactive</Tag>)
      },
      {
        title: "Updated",
        dataIndex: "updatedAt",
        key: "updatedAt",
        width: 190,
        render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm")
      },
      {
        title: "Actions",
        key: "actions",
        width: 240,
        render: (row: { id: string }) => (
          <Space>
            <Button
              size="small"
              onClick={() => runPipelineMutation.mutate(row.id)}
              loading={runPipelineMutation.isPending}
              disabled={!canRun}
            >
              Run
            </Button>
            <Button size="small">
              <Link to={`/pipelines/${row.id}`}>Details</Link>
            </Button>
          </Space>
        )
      }
    ],
    [navigate, runPipelineMutation, messageApi, canRun]
  );

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing pipelines requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <h2 className="section-title">Pipelines</h2>
            <p className="section-subtitle">Create pipelines and start runs directly from this table.</p>
          </div>
          <Button type="primary" onClick={() => setCreateOpen(true)} disabled={!canEdit}>
            New Pipeline
          </Button>
        </Space>
      </div>

      <ApiErrorAlert error={pipelinesQuery.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={pipelinesQuery.isLoading}
        dataSource={pipelinesQuery.data ?? []}
        columns={columns}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title="Create Pipeline"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={createPipelineMutation.isPending}
      >
        <Form form={createForm} layout="vertical" onFinish={(values) => createPipelineMutation.mutate(values)}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: "Name is required" }]}>
            <Input placeholder="Nightly Build" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Short pipeline purpose" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
