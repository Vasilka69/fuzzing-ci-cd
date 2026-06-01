import { useAuth } from "@/app/AuthProvider";
import { executorsApi } from "@/api/executorsApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { StatusTag } from "@/shared/components/StatusTag";
import { useQuery } from "@tanstack/react-query";
import { Space, Table } from "antd";
import dayjs from "dayjs";

export function ExecutorsPage() {
  const { hasCapability } = useAuth();
  const canView = hasCapability("view");
  const { pagination, onPaginationChange } = useTablePagination(12);

  const query = useQuery({
    queryKey: ["executors"],
    queryFn: () => executorsApi.list(),
    refetchInterval: 15000,
    enabled: canView
  });

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing executors requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div className="glass-card" style={{ padding: 20 }}>
        <h2 className="section-title">Executor Health</h2>
        <p className="section-subtitle">Live heartbeat table from executor service instances.</p>
      </div>
      <ApiErrorAlert error={query.error} />
      <Table
        className="glass-card"
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
        columns={[
          { title: "Worker", dataIndex: "workerId" },
          { title: "Service", dataIndex: "serviceType" },
          { title: "Status", dataIndex: "status", render: (status: string) => <StatusTag status={status} /> },
          { title: "Last Seen", dataIndex: "lastSeenAt", render: (v: string) => dayjs(v).format("YYYY-MM-DD HH:mm:ss") },
          {
            title: "Capacity",
            dataIndex: "capacity",
            render: (capacity: Record<string, unknown>) => JSON.stringify(capacity)
          }
        ]}
      />
    </Space>
  );
}
