import { useAuth } from "@/app/AuthProvider";
import { permissionsApi } from "@/api/permissionsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, message } from "antd";
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

type PermissionForm = {
  subjectType: string;
  userId?: string;
  roleId?: string;
  resourceType: string;
  resourceId?: string;
  permission: string;
  effect?: string;
};

export function PermissionsPage() {
  const { hasCapability } = useAuth();
  const canAdmin = hasCapability("admin");
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<PermissionForm>();
  const [resourceTypeFilter, setResourceTypeFilter] = useState<string | undefined>(undefined);
  const [resourceIdFilter, setResourceIdFilter] = useState("");
  const [appliedFilter, setAppliedFilter] = useState<{ resourceType?: string; resourceId?: string }>({});
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const { pagination, onPaginationChange, resetPage } = useTablePagination(10);

  const query = useQuery({
    queryKey: ["admin", "permissions", appliedFilter.resourceType],
    queryFn: () =>
      permissionsApi.list({
        resourceType: appliedFilter.resourceType,
        resourceId: toUuidOrUndefined(appliedFilter.resourceId)
      }),
    enabled: canAdmin
  });

  const createMutation = useMutation({
    mutationFn: permissionsApi.create,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin", "permissions"] });
      messageApi.success("Permission created");
      setOpen(false);
      form.resetFields();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: permissionsApi.remove,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin", "permissions"] });
      messageApi.success("Permission removed");
    }
  });

  const filteredRows = useMemo(() => {
    const resourceIdNeedle = appliedFilter.resourceId?.trim().toLowerCase();
    if (!resourceIdNeedle) {
      return query.data ?? [];
    }
    return (query.data ?? []).filter((row) => String(row.resourceId ?? "").toLowerCase().includes(resourceIdNeedle));
  }, [query.data, appliedFilter.resourceId]);

  if (!canAdmin) {
    return <AccessDeniedCard subtitle="Admin capability is required to manage permission assignments." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }}>
          <div>
            <h2 className="section-title">Permission Assignments</h2>
            <p className="section-subtitle">Resource-level RBAC assignments for users and roles.</p>
          </div>
          <Space wrap>
            <Select
              allowClear
              style={{ minWidth: 170 }}
              placeholder="Resource type"
              value={resourceTypeFilter}
              onChange={(value) => setResourceTypeFilter(value)}
              options={(query.data ?? [])
                .map((row) => row.resourceType)
                .filter((value, index, arr) => arr.indexOf(value) === index)
                .map((value) => ({ value, label: value }))}
            />
            <Input
              style={{ width: 250 }}
              placeholder="Resource ID contains..."
              value={resourceIdFilter}
              onChange={(event) => setResourceIdFilter(event.target.value)}
            />
            <Button
              type="primary"
              onClick={() => {
                setAppliedFilter({
                  resourceType: resourceTypeFilter,
                  resourceId: resourceIdFilter || undefined
                });
                resetPage();
              }}
            >
              Apply
            </Button>
            <Button
              onClick={() => {
                setResourceTypeFilter(undefined);
                setResourceIdFilter("");
                setAppliedFilter({});
                resetPage();
              }}
            >
              Reset
            </Button>
            <Button type="primary" onClick={() => setOpen(true)}>
              New Permission
            </Button>
          </Space>
        </Space>
      </div>

      <ApiErrorAlert error={query.error || createMutation.error || deleteMutation.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={query.isLoading}
        dataSource={filteredRows}
        columns={[
          { title: "Subject", dataIndex: "subjectType" },
          { title: "User", dataIndex: "userId", render: (v: string | null) => (v ? v.slice(0, 8) : "-") },
          { title: "Role", dataIndex: "roleId", render: (v: string | null) => (v ? v.slice(0, 8) : "-") },
          { title: "Resource", dataIndex: "resourceType" },
          { title: "Permission", dataIndex: "permission" },
          { title: "Effect", dataIndex: "effect" },
          { title: "Created", dataIndex: "createdAt", render: (v: string) => dayjs(v).format("YYYY-MM-DD HH:mm:ss") },
          {
            title: "Actions",
            key: "actions",
            render: (row: { id: string }) => (
              <Popconfirm title="Remove this assignment?" onConfirm={() => deleteMutation.mutate(row.id)}>
                <Button danger size="small" loading={deleteMutation.isPending}>
                  Delete
                </Button>
              </Popconfirm>
            )
          }
        ]}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />

      <Modal
        title="Create Permission Assignment"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
      >
        <Form
          layout="vertical"
          form={form}
          initialValues={{ subjectType: "user", resourceType: "system", permission: "view", effect: "allow" }}
          onFinish={(values) =>
            createMutation.mutate({
              subjectType: values.subjectType.trim(),
              userId: values.userId?.trim() || undefined,
              roleId: values.roleId?.trim() || undefined,
              resourceType: values.resourceType.trim(),
              resourceId: values.resourceId?.trim() || undefined,
              permission: values.permission.trim(),
              effect: values.effect?.trim() || undefined
            })
          }
        >
          <Form.Item name="subjectType" label="Subject Type" rules={[{ required: true }]}>
            <Input placeholder="user | role" />
          </Form.Item>
          <Form.Item name="userId" label="User ID">
            <Input />
          </Form.Item>
          <Form.Item name="roleId" label="Role ID">
            <Input />
          </Form.Item>
          <Form.Item name="resourceType" label="Resource Type" rules={[{ required: true }]}>
            <Input placeholder="system | pipeline | environment" />
          </Form.Item>
          <Form.Item name="resourceId" label="Resource ID">
            <Input />
          </Form.Item>
          <Form.Item name="permission" label="Permission" rules={[{ required: true }]}>
            <Input placeholder="view | edit | run | admin" />
          </Form.Item>
          <Form.Item name="effect" label="Effect">
            <Input placeholder="allow" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
