import { useAuth } from "@/app/AuthProvider";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { Button, Card, Form, Input, Space, Typography } from "antd";
import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

type LoginForm = {
  login: string;
  password: string;
};

export function LoginPage() {
  const { login } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const onSubmit = async (values: LoginForm) => {
    try {
      setSubmitting(true);
      setError(null);
      await login(values);
      const target = (location.state as { from?: string } | undefined)?.from ?? "/pipelines";
      navigate(target, { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 24
      }}
    >
      <Card className="glass-card" style={{ width: "100%", maxWidth: 430 }}>
        <Space direction="vertical" size={18} style={{ width: "100%" }}>
          <div>
            <Typography.Title level={2} style={{ marginBottom: 8 }}>
              Master Login
            </Typography.Title>
            <Typography.Text type="secondary">
              Sign in to orchestrate pipelines, runs, approvals, and executor health.
            </Typography.Text>
          </div>
          <ApiErrorAlert error={error} />
          <Form<LoginForm>
            layout="vertical"
            initialValues={{
              login: import.meta.env.VITE_DEFAULT_LOGIN ?? "developer",
              password: "dev"
            }}
            onFinish={onSubmit}
          >
            <Form.Item name="login" label="Login" rules={[{ required: true, message: "Login is required" }]}>
              <Input autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" label="Password" rules={[{ required: true, message: "Password is required" }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              Sign In
            </Button>
          </Form>
        </Space>
      </Card>
    </div>
  );
}
