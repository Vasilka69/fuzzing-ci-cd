import { Card, Result } from "antd";

export function AccessDeniedCard({ title = "Access Denied", subtitle }: { title?: string; subtitle?: string }) {
  return (
    <Card className="glass-card">
      <Result status="403" title={title} subTitle={subtitle ?? "You do not have enough permissions for this section."} />
    </Card>
  );
}
