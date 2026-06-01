import { useAuth } from "@/app/AuthProvider";
import { secretRefsApi } from "@/api/secretRefsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Select, Space, Table, message } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";

type SecretRefForm = {
  name: string;
  provider: string;
  externalKey: string;
  description?: string;
  scope?: string;
  metadata?: string;
};

export function SecretRefsPage() {
  const { hasCapability } = useAuth();
  const canManageSecrets = hasCapability("manage_secrets");
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<SecretRefForm>();
  const [providerFilter, setProviderFilter] = useState<string | undefined>(undefined);
  const [scopeFilter, setScopeFilter] = useState<string | undefined>(undefined);
  const [appliedFilters, setAppliedFilters] = useState<{ provider?: string; scope?: string }>({});
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const { pagination, onPaginationChange, resetPage } = useTablePagination(10);

  const query = useQuery({
    queryKey: ["settings", "secret-refs"],
    queryFn: () => secretRefsApi.list(),
    enabled: canManageSecrets
  });

  const createMutation = useMutation({
    mutationFn: secretRefsApi.create,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["settings", "secret-refs"] });
      messageApi.success("Secret reference created");
      setOpen(false);
      form.resetFields();
    }
  });

  const submit = (values: SecretRefForm) => {
    let parsedMetadata: Record<string, unknown> | undefined;
    if (values.metadata?.trim()) {
      try {
        parsedMetadata = JSON.parse(values.metadata) as Record<string, unknown>;
      } catch {
        messageApi.error("Metadata must be valid JSON");
        return;
      }
    }
    createMutation.mutate({
      name: values.name.trim(),
      provider: values.provider.trim(),
      externalKey: values.externalKey.trim(),
      description: values.description?.trim() || undefined,
      scope: values.scope?.trim() || undefined,
      metadata: parsedMetadata
    });
  };

  const filteredRows = useMemo(() => {
    const providerNeedle = appliedFilters.provider?.trim().toLowerCase();
    const scopeNeedle = appliedFilters.scope?.trim().toLowerCase();
    return (query.data ?? []).filter((row) => {
      const providerMatch = !providerNeedle || row.provider.toLowerCase() === providerNeedle;
      const scopeMatch = !scopeNeedle || String(row.scope ?? "").toLowerCase() === scopeNeedle;
      return providerMatch && scopeMatch;
    });
  }, [query.data, appliedFilters.provider, appliedFilters.scope]);

  if (!canManageSecrets) {
    return <AccessDeniedCard subtitle="Managing secret references requires `manage_secrets` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <h2 className="section-title">Secret References</h2>
            <p className="section-subtitle">Store only external references. Secret values never live in this UI.</p>
          </div>
          <Space wrap>
            <Select
              allowClear
              style={{ minWidth: 160 }}
              placeholder="Provider"
              value={providerFilter}
              onChange={(value) => setProviderFilter(value)}
              options={(query.data ?? [])
                .map((row) => row.provider)
                .filter((value, index, arr) => arr.indexOf(value) === index)
                .map((value) => ({ value, label: value }))}
            />
            <Select
              allowClear
              style={{ minWidth: 160 }}
              placeholder="Scope"
              value={scopeFilter}
              onChange={(value) => setScopeFilter(value)}
              options={(query.data ?? [])
                .map((row) => row.scope ?? "")
                .filter((value, index, arr) => value && arr.indexOf(value) === index)
                .map((value) => ({ value, label: value }))}
            />
            <Button
              type="primary"
              onClick={() => {
                setAppliedFilters({
                  provider: providerFilter,
                  scope: scopeFilter
                });
                resetPage();
              }}
            >
              Apply
            </Button>
            <Button
              onClick={() => {
                setProviderFilter(undefined);
                setScopeFilter(undefined);
                setAppliedFilters({});
                resetPage();
              }}
            >
              Reset
            </Button>
            <Button type="primary" onClick={() => setOpen(true)}>
              New Secret Ref
            </Button>
          </Space>
        </Space>
      </div>

      <ApiErrorAlert error={query.error || createMutation.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={query.isLoading}
        dataSource={filteredRows}
        columns={[
          { title: "Name", dataIndex: "name" },
          { title: "Provider", dataIndex: "provider" },
          { title: "External Key", dataIndex: "externalKey" },
          { title: "Scope", dataIndex: "scope" },
          { title: "Created", dataIndex: "createdAt", render: (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm:ss") }
        ]}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />

      <Modal
        title="Create Secret Ref"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form<SecretRefForm> layout="vertical" form={form} onFinish={submit}>
          <Form.Item name="name" label="Name" rules={[{ required: true, message: "Name is required" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="provider" label="Provider" rules={[{ required: true, message: "Provider is required" }]}>
            <Input placeholder="vault | aws-sm | gcp-sm" />
          </Form.Item>
          <Form.Item name="externalKey" label="External Key" rules={[{ required: true, message: "External key is required" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input />
          </Form.Item>
          <Form.Item name="scope" label="Scope">
            <Input placeholder="project" />
          </Form.Item>
          <Form.Item name="metadata" label="Metadata JSON">
            <Input.TextArea rows={3} placeholder='{"team":"ci"}' />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
