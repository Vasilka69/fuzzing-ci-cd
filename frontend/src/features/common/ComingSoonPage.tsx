import { Card, Empty } from "antd";

export function ComingSoonPage({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <Card className="glass-card" style={{ minHeight: 300 }}>
      <Empty description={`${title}: ${subtitle}`} />
    </Card>
  );
}
