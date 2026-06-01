import { useAuth } from "@/app/AuthProvider";
import { connectionsApi } from "@/api/connectionsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Select, Space, Table, message } from "antd";
import dayjs from "dayjs";
import { useState } from "react";

type ConnectionForm = {
  name: string;
  connectionType: string;
  url?: string;
  credentialsRef?: string;
  secretRefId?: string;
  config?: string;
};

export function ConnectionsPage() {
  const { hasCapability } = useAuth();
  const canManageConnections = hasCapability("manage_connections");
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<ConnectionForm>();
  const [connectionTypeFilter, setConnectionTypeFilter] = useState<string | undefined>(undefined);
  const [appliedConnectionTypeFilter, setAppliedConnectionTypeFilter] = useState<string | undefined>(undefined);
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const { pagination, onPaginationChange, resetPage } = useTablePagination(10);

  const query = useQuery({
    queryKey: ["settings", "connections", appliedConnectionTypeFilter],
    queryFn: () => connectionsApi.list({ connectionType: appliedConnectionTypeFilter }),
    enabled: canManageConnections
  });

  const createMutation = useMutation({
    mutationFn: connectionsApi.create,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["settings", "connections"] });
      messageApi.success("Connection created");
      setOpen(false);
      form.resetFields();
    }
  });

  const submit = (values: ConnectionForm) => {
    let parsedConfig: Record<string, unknown> | undefined;
    if (values.config?.trim()) {
      try {
        parsedConfig = JSON.parse(values.config) as Record<string, unknown>;
      } catch {
        messageApi.error("Config must be valid JSON");
        return;
      }
    }
    createMutation.mutate({
      name: values.name.trim(),
      connectionType: values.connectionType.trim(),
      url: values.url?.trim() || undefined,
      credentialsRef: values.credentialsRef?.trim() || undefined,
      secretRefId: values.secretRefId?.trim() || undefined,
      config: parsedConfig
    });
  };

  if (!canManageConnections) {
    return <AccessDeniedCard subtitle="Managing external connections requires `manage_connections` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <h2 className="section-title">External Connections</h2>
            <p className="section-subtitle">Manage VCS, deployment, storage, and integration connections.</p>
          </div>
          <Space wrap>
            <Select
              allowClear
              style={{ minWidth: 170 }}
              placeholder="Connection type"
              value={connectionTypeFilter}
              onChange={(value) => setConnectionTypeFilter(value)}
              options={(query.data ?? [])
                .map((connection) => connection.connectionType)
                .filter((value, index, arr) => arr.indexOf(value) === index)
                .map((value) => ({ value, label: value }))}
            />
            <Button
              type="primary"
              onClick={() => {
                setAppliedConnectionTypeFilter(connectionTypeFilter);
                resetPage();
              }}
            >
              Apply
            </Button>
            <Button
              onClick={() => {
                setConnectionTypeFilter(undefined);
                setAppliedConnectionTypeFilter(undefined);
                resetPage();
              }}
            >
              Reset
            </Button>
            <Button type="primary" onClick={() => setOpen(true)}>
              New Connection
            </Button>
          </Space>
        </Space>
      </div>

      <ApiErrorAlert error={query.error || createMutation.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={[
          { title: "Name", dataIndex: "name" },
          { title: "Type", dataIndex: "connectionType" },
          { title: "URL", dataIndex: "url", render: (value: string | null) => value || "-" },
          { title: "Secret Ref", dataIndex: "secretRefId", render: (value: string | null) => (value ? value.slice(0, 8) : "-") },
          { title: "Updated", dataIndex: "updatedAt", render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm:ss") }
        ]}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />

      <Modal
        title="Create External Connection"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form<ConnectionForm> layout="vertical" form={form} onFinish={submit}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: "Name is required" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="connectionType" label="Type" rules={[{ required: true, message: "Type is required" }]}>
            <Input placeholder="vcs | deploy | storage" />
          </Form.Item>
          <Form.Item name="url" label="URL">
            <Input />
          </Form.Item>
          <Form.Item name="credentialsRef" label="Credentials Ref">
            <Input />
          </Form.Item>
          <Form.Item name="secretRefId" label="Secret Ref ID">
            <Input />
          </Form.Item>
          <Form.Item name="config" label="Config JSON">
            <Input.TextArea rows={4} placeholder='{"project":"demo"}' />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
