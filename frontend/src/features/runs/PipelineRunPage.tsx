import { useAuth } from "@/app/AuthProvider";
import { jobExecutionsApi } from "@/api/jobExecutionsApi";
import { pipelineRunsApi } from "@/api/pipelineRunsApi";
import { connectLogStream } from "@/api/sseClient";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { StatusTag } from "@/shared/components/StatusTag";
import type { JobExecution, LogLine } from "@/shared/types/run";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Empty, Input, List, Row, Space, Spin, Switch, Table, Typography, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

export function PipelineRunPage() {
  const { hasCapability } = useAuth();
  const { id } = useParams();
  const runId = id ?? "";
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();
  const canView = hasCapability("view");
  const canRun = hasCapability("run");
  const canCancel = hasCapability("cancel");
  const [eventCursor, setEventCursor] = useState<string | null>(null);
  const [eventRows, setEventRows] = useState<JobExecution[]>([]);
  const [selectedExecutionId, setSelectedExecutionId] = useState<string | null>(null);
  const [liveLogLines, setLiveLogLines] = useState<LogLine[]>([]);
  const [sseConnected, setSseConnected] = useState(true);
  const [sseReconnectAttempt, setSseReconnectAttempt] = useState(0);
  const [isLogPaused, setIsLogPaused] = useState(false);
  const [logSearch, setLogSearch] = useState("");

  const runQuery = useQuery({
    queryKey: ["pipeline-run", runId],
    queryFn: () => pipelineRunsApi.byId(runId),
    enabled: Boolean(runId) && canView,
    refetchInterval: 10000
  });

  const graphQuery = useQuery({
    queryKey: ["pipeline-run", runId, "graph"],
    queryFn: () => pipelineRunsApi.graph(runId),
    enabled: Boolean(runId) && canView,
    refetchInterval: 10000
  });

  const eventsQuery = useQuery({
    queryKey: ["pipeline-run", runId, "events", eventCursor],
    queryFn: () => pipelineRunsApi.events(runId, { limit: 20, cursor: eventCursor ?? undefined }),
    enabled: Boolean(runId) && canView
  });

  const logsQuery = useQuery({
    queryKey: ["job-execution", selectedExecutionId, "logs"],
    queryFn: () => jobExecutionsApi.logs(selectedExecutionId!, { tail: 100 }),
    enabled: Boolean(selectedExecutionId) && canView,
    refetchInterval: !sseConnected && selectedExecutionId ? 5000 : false
  });

  const cancelMutation = useMutation({
    mutationFn: () => pipelineRunsApi.cancel(runId),
    onSuccess: async () => {
      messageApi.success("Run cancellation requested");
      await queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId] });
      await queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId, "graph"] });
      await queryClient.invalidateQueries({ queryKey: ["pipeline-runs"] });
    }
  });

  const retryMutation = useMutation({
    mutationFn: () => pipelineRunsApi.retry(runId),
    onSuccess: async (run) => {
      messageApi.success("Run retry triggered");
      await queryClient.invalidateQueries({ queryKey: ["pipeline-runs"] });
      navigate(`/runs/${run.id}`);
    }
  });

  useEffect(() => {
    setEventCursor(null);
    setEventRows([]);
    setSelectedExecutionId(null);
    setLiveLogLines([]);
    setSseConnected(true);
    setSseReconnectAttempt(0);
    setIsLogPaused(false);
    setLogSearch("");
  }, [runId]);

  useEffect(() => {
    if (!eventsQuery.data) {
      return;
    }
    setEventRows((current) => {
      const existing = new Set(current.map((item) => item.id));
      const merged = [...current];
      eventsQuery.data.items.forEach((item) => {
        if (!existing.has(item.id)) {
          merged.push(item);
        }
      });
      return merged;
    });
  }, [eventsQuery.data]);

  useEffect(() => {
    if (!graphQuery.data || graphQuery.data.length === 0) {
      return;
    }
    setSelectedExecutionId((current) => current ?? graphQuery.data![0].id);
  }, [graphQuery.data]);

  useEffect(() => {
    if (!runId || !canView) {
      return;
    }
    const reconnectDelayMs = Math.min(1000 * 2 ** sseReconnectAttempt, 15000);
    let reconnectScheduled = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    const source = connectLogStream(
      { pipelineRunId: runId },
      {
        onOpen: () => {
          setSseConnected(true);
        },
        onJobEvent: (payload) => {
          queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId] });
          queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId, "graph"] });
          queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId, "events"] });
          if (!selectedExecutionId || !payload || typeof payload !== "object") {
            return;
          }
          const data = payload as Record<string, unknown>;
          if (data.eventType !== "JOB_LOG" || data.jobExecutionId !== selectedExecutionId) {
            return;
          }
          const logMessage = data.logs;
          if (typeof logMessage !== "string") {
            return;
          }
          if (!isLogPaused) {
            setLiveLogLines((current) => [
              ...current.slice(-99),
              { id: crypto.randomUUID(), message: logMessage, ts: new Date().toISOString() }
            ]);
          }
        },
        onJobLog: (payload) => {
          if (!selectedExecutionId || !payload || typeof payload !== "object") {
            return;
          }
          const maybeExecutionId = (payload as Record<string, unknown>).jobExecutionId;
          const logs = (payload as Record<string, unknown>).logs;
          if (maybeExecutionId === selectedExecutionId && typeof logs === "string") {
            if (!isLogPaused) {
              setLiveLogLines((current) => [
                ...current.slice(-99),
                { id: crypto.randomUUID(), message: logs, ts: new Date().toISOString() }
              ]);
            }
          }
        },
        onError: () => {
          if (reconnectScheduled) {
            return;
          }
          reconnectScheduled = true;
          setSseConnected(false);
          source.close();
          queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId] });
          queryClient.invalidateQueries({ queryKey: ["pipeline-run", runId, "graph"] });
          queryClient.invalidateQueries({ queryKey: ["job-execution", selectedExecutionId, "logs"] });
          reconnectTimer = setTimeout(() => {
            setSseReconnectAttempt((current) => current + 1);
          }, reconnectDelayMs);
        }
      }
    );
    return () => {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      source.close();
    };
  }, [runId, selectedExecutionId, queryClient, canView, isLogPaused, sseReconnectAttempt]);

  const mergedLogs = useMemo(() => {
    const logs = logsQuery.data?.items ?? [];
    return [...logs, ...liveLogLines];
  }, [logsQuery.data?.items, liveLogLines]);

  const visibleLogs = useMemo(() => {
    const needle = logSearch.trim().toLowerCase();
    if (!needle) {
      return mergedLogs;
    }
    return mergedLogs.filter((line) => line.message.toLowerCase().includes(needle));
  }, [mergedLogs, logSearch]);

  const isFinalStatus = runQuery.data ? ["success", "failed", "canceled", "timeout"].includes(runQuery.data.status) : true;
  const canRetryCurrent = runQuery.data ? ["failed", "timeout", "canceled", "skipped"].includes(runQuery.data.status) : false;

  const downloadLogs = () => {
    if (visibleLogs.length === 0) {
      return;
    }
    const payload = visibleLogs
      .map((line) => `[${line.ts ? dayjs(line.ts).format("YYYY-MM-DD HH:mm:ss") : "--:--:--"}] ${line.message}`)
      .join("\n");
    const blob = new Blob([payload], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `run-${runId.slice(0, 8)}-logs.txt`;
    link.click();
    URL.revokeObjectURL(url);
  };

  if (!runId) {
    return <Alert type="warning" showIcon message="Run id is missing in route." />;
  }

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing run details requires `view` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <Space style={{ width: "100%", justifyContent: "space-between" }} align="start">
          <Space direction="vertical" size={4}>
            <h2 className="section-title">Run Details</h2>
            <p className="section-subtitle">Runtime graph, events, and logs for run {runId.slice(0, 8)}.</p>
          </Space>
          <Space>
            <Button
              onClick={() => cancelMutation.mutate()}
              loading={cancelMutation.isPending}
              disabled={!canCancel || isFinalStatus || runQuery.data?.status === "canceling"}
            >
              Cancel Run
            </Button>
            <Button
              type="primary"
              onClick={() => retryMutation.mutate()}
              loading={retryMutation.isPending}
              disabled={!canRun || !canRetryCurrent}
            >
              Retry Run
            </Button>
          </Space>
        </Space>
      </div>

      <ApiErrorAlert error={runQuery.error || graphQuery.error || eventsQuery.error || logsQuery.error} />
      {!sseConnected ? (
        <Alert
          type="warning"
          showIcon
          message="Realtime stream temporarily unavailable"
          description={`SSE connection dropped. Reconnect attempt ${sseReconnectAttempt + 1}; UI continues with polling fallback.`}
        />
      ) : null}

      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card className="glass-card" title="Run Snapshot">
            {runQuery.isLoading || !runQuery.data ? (
              <Spin />
            ) : (
              <Space direction="vertical">
                <Typography.Text>
                  <strong>Status:</strong> <StatusTag status={runQuery.data.status} />
                </Typography.Text>
                <Typography.Text>
                  <strong>Triggered by:</strong> {runQuery.data.triggeredByType}
                </Typography.Text>
                <Typography.Text>
                  <strong>Started:</strong> {dayjs(runQuery.data.startedAt).format("YYYY-MM-DD HH:mm:ss")}
                </Typography.Text>
                <Typography.Text>
                  <strong>Finished:</strong>{" "}
                  {runQuery.data.finishedAt ? dayjs(runQuery.data.finishedAt).format("YYYY-MM-DD HH:mm:ss") : "in progress"}
                </Typography.Text>
              </Space>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card className="glass-card" title="Execution Graph">
            <Table
              size="small"
              rowKey="id"
              loading={graphQuery.isLoading}
              dataSource={graphQuery.data ?? []}
              pagination={false}
              onRow={(row) => ({
                onClick: () => {
                  setSelectedExecutionId(row.id);
                  setLiveLogLines([]);
                }
              })}
              rowClassName={(row) => (row.id === selectedExecutionId ? "ant-table-row-selected" : "")}
              columns={[
                { title: "Job", dataIndex: "jobId", render: (jobId: string) => jobId.slice(0, 8) },
                { title: "Attempt", dataIndex: "attempt", width: 80 },
                { title: "Worker", dataIndex: "workerId", render: (v: string | null) => v || "-" },
                { title: "Status", dataIndex: "status", render: (status: string) => <StatusTag status={status} /> }
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card
            className="glass-card"
            title="Run Events"
            extra={
              <Button
                size="small"
                onClick={() => setEventCursor(eventsQuery.data?.nextCursor ?? null)}
                disabled={!eventsQuery.data?.nextCursor}
                loading={eventsQuery.isFetching}
              >
                Load more
              </Button>
            }
          >
            {eventRows.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No events yet" />
            ) : (
                <List
                  dataSource={eventRows}
                  renderItem={(item) => (
                    <List.Item>
                      <Space direction="vertical" size={0}>
                        <Typography.Text strong>{item.jobId.slice(0, 8)}</Typography.Text>
                        <Typography.Text type="secondary">
                          {item.status} |{" "}
                          {item.finishedAt
                            ? dayjs(item.finishedAt).format("HH:mm:ss")
                            : item.startedAt
                              ? dayjs(item.startedAt).format("HH:mm:ss")
                              : "--:--:--"}
                        </Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            className="glass-card"
            title={`Logs ${selectedExecutionId ? selectedExecutionId.slice(0, 8) : ""}`}
            extra={
              <Space wrap>
                <Input
                  size="small"
                  allowClear
                  value={logSearch}
                  onChange={(event) => setLogSearch(event.target.value)}
                  placeholder="Search logs"
                  style={{ width: 190 }}
                />
                <Switch
                  size="small"
                  checked={isLogPaused}
                  onChange={setIsLogPaused}
                  checkedChildren="Paused"
                  unCheckedChildren="Live"
                />
                <Button size="small" onClick={downloadLogs} disabled={visibleLogs.length === 0}>
                  Download
                </Button>
                {selectedExecutionId ? <StatusTag status={graphQuery.data?.find((e) => e.id === selectedExecutionId)?.status} /> : null}
              </Space>
            }
          >
            {!selectedExecutionId ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Select execution to view logs" />
            ) : (
              <div
                style={{
                  maxHeight: 360,
                  overflow: "auto",
                  background: "#111927",
                  color: "#ddf1ff",
                  borderRadius: 10,
                  padding: 12,
                  fontFamily: "Consolas, monospace",
                  fontSize: 12
                }}
              >
                {logsQuery.isLoading ? (
                  <Spin />
                ) : visibleLogs.length === 0 ? (
                  <Typography.Text style={{ color: "#a7b7cc" }}>No logs available yet.</Typography.Text>
                ) : (
                  visibleLogs.map((line) => (
                    <div key={line.id}>
                      [{line.ts ? dayjs(line.ts).format("HH:mm:ss") : "--:--:--"}] {line.message}
                    </div>
                  ))
                )}
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
