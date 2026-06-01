import { useAuth } from "@/app/AuthProvider";
import { auditApi } from "@/api/auditApi";
import { useTablePagination } from "@/shared/pagination";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Select, Space, Table, Typography } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";

export function AuditPage() {
  const { hasCapability } = useAuth();
  const canAdmin = hasCapability("admin");
  const [eventTypeFilter, setEventTypeFilter] = useState<string | undefined>(undefined);
  const [actorFilter, setActorFilter] = useState("");
  const [appliedFilter, setAppliedFilter] = useState<{ eventType?: string; actorUserId?: string }>({});
  const { pagination, onPaginationChange, resetPage } = useTablePagination(12);

  const query = useQuery({
    queryKey: ["audit-events"],
    queryFn: () => auditApi.list(),
    enabled: canAdmin
  });

  const filteredRows = useMemo(() => {
    const actorNeedle = appliedFilter.actorUserId?.trim().toLowerCase();
    const typeNeedle = appliedFilter.eventType?.trim().toLowerCase();
    return (query.data ?? []).filter((row) => {
      const eventMatch = !typeNeedle || row.eventType.toLowerCase() === typeNeedle;
      const actorMatch = !actorNeedle || String(row.actorUserId ?? "").toLowerCase().includes(actorNeedle);
      return eventMatch && actorMatch;
    });
  }, [query.data, appliedFilter.actorUserId, appliedFilter.eventType]);

  if (!canAdmin) {
    return <AccessDeniedCard subtitle="Viewing audit events requires `admin` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div className="glass-card" style={{ padding: 20 }}>
        <h2 className="section-title">Audit Events</h2>
        <p className="section-subtitle">Administrative and runtime actions recorded by master-service.</p>
        <Space wrap style={{ marginTop: 12 }}>
          <Select
            allowClear
            style={{ minWidth: 220 }}
            placeholder="Event type"
            value={eventTypeFilter}
            onChange={(value) => setEventTypeFilter(value)}
            options={(query.data ?? [])
              .map((row) => row.eventType)
              .filter((value, index, arr) => arr.indexOf(value) === index)
              .map((value) => ({ value, label: value }))}
          />
          <Input
            style={{ width: 260 }}
            placeholder="Actor user ID contains..."
            value={actorFilter}
            onChange={(event) => setActorFilter(event.target.value)}
          />
          <Button
            type="primary"
            onClick={() => {
              setAppliedFilter({
                eventType: eventTypeFilter,
                actorUserId: actorFilter || undefined
              });
              resetPage();
            }}
          >
            Apply
          </Button>
          <Button
            onClick={() => {
              setEventTypeFilter(undefined);
              setActorFilter("");
              setAppliedFilter({});
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
        dataSource={filteredRows}
        columns={[
          { title: "Time", dataIndex: "createdAt", width: 180, render: (v: string) => dayjs(v).format("YYYY-MM-DD HH:mm:ss") },
          { title: "Event", dataIndex: "eventType", width: 180 },
          { title: "Actor", dataIndex: "actorUserId", width: 120, render: (v: string | null) => (v ? v.slice(0, 8) : "-") },
          { title: "Entity Type", dataIndex: "entityType", width: 120, render: (v: string | null) => v || "-" },
          { title: "Entity ID", dataIndex: "entityId", width: 120, render: (v: string | null) => (v ? v.slice(0, 8) : "-") },
          {
            title: "Payload",
            dataIndex: "payload",
            render: (payload: Record<string, unknown> | null) => (
              <Typography.Text type="secondary" style={{ fontFamily: "Consolas, monospace" }}>
                {payload ? JSON.stringify(payload) : "-"}
              </Typography.Text>
            )
          }
        ]}
        pagination={pagination}
        onChange={(nextPagination) => onPaginationChange(nextPagination)}
      />
    </Space>
  );
}
