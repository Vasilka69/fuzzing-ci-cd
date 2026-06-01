import { Tag } from "antd";

const STATUS_COLORS: Record<string, string> = {
  queued: "geekblue",
  running: "processing",
  waiting_approval: "gold",
  success: "success",
  failed: "error",
  timeout: "volcano",
  canceling: "orange",
  canceled: "default",
  retrying: "purple",
  skipped: "cyan"
};

export function StatusTag({ status }: { status?: string | null }) {
  if (!status) {
    return <Tag>unknown</Tag>;
  }
  const normalized = status.toLowerCase();
  return <Tag color={STATUS_COLORS[normalized] ?? "default"}>{normalized}</Tag>;
}
