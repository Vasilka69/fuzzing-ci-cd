import { useAuth } from "@/app/AuthProvider";
import type { Capability } from "@/app/capabilities";
import { Layout, Menu, Space, Typography, Button } from "antd";
import {
  AuditOutlined,
  DeploymentUnitOutlined,
  FolderOpenOutlined,
  FundProjectionScreenOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  PlaySquareOutlined,
  RadarChartOutlined
} from "@ant-design/icons";
import { Link, Outlet, useLocation } from "react-router-dom";
import type { ReactNode } from "react";

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

type MenuAccess = {
  key: string;
  icon?: ReactNode;
  label: ReactNode | string;
  capability: Capability;
  children?: MenuAccess[];
};

const menuConfig: MenuAccess[] = [
  { key: "/pipelines", icon: <FundProjectionScreenOutlined />, label: <Link to="/pipelines">Pipelines</Link>, capability: "view" },
  { key: "/runs", icon: <PlaySquareOutlined />, label: <Link to="/runs">Runs</Link>, capability: "view" },
  { key: "/artifacts", icon: <FolderOpenOutlined />, label: <Link to="/artifacts">Artifacts</Link>, capability: "view" },
  { key: "/executors", icon: <RadarChartOutlined />, label: <Link to="/executors">Executors</Link>, capability: "view" },
  { key: "/deployments", icon: <DeploymentUnitOutlined />, label: <Link to="/deployments">Deployments</Link>, capability: "view" },
  {
    key: "/settings",
    icon: <SettingOutlined />,
    label: "Settings",
    capability: "view",
    children: [
      { key: "/settings/connections", label: <Link to="/settings/connections">Connections</Link>, capability: "manage_connections" },
      { key: "/settings/secrets", label: <Link to="/settings/secrets">Secret Refs</Link>, capability: "manage_secrets" }
    ]
  },
  {
    key: "/admin/permissions",
    icon: <SafetyCertificateOutlined />,
    label: <Link to="/admin/permissions">Permissions</Link>,
    capability: "admin"
  },
  { key: "/audit", icon: <AuditOutlined />, label: <Link to="/audit">Audit</Link>, capability: "admin" }
];

export function AppLayout() {
  const location = useLocation();
  const { logout, meLogin, hasCapability } = useAuth();
  const selectedKey = resolveSelectedKey(location.pathname);
  const menuItems = buildMenuItems(menuConfig, hasCapability);

  return (
    <Layout className="page-shell">
      <Sider
        width={260}
        style={{
          background: "var(--bg-sidebar)",
          borderRight: "1px solid rgba(255,255,255,0.15)",
          boxShadow: "0 20px 40px rgba(5, 13, 24, 0.45)"
        }}
      >
        <div style={{ padding: 24 }}>
          <Title level={3} style={{ margin: 0, color: "#f2fbff", lineHeight: 1.2 }}>
            Master Console
          </Title>
          <Text style={{ color: "rgba(226,244,255,0.8)" }}>CI/CD control center</Text>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={["/settings"]}
          items={menuItems}
          style={{ background: "transparent", borderInlineEnd: "none", paddingInline: 10 }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: "transparent",
            borderBottom: "1px solid var(--border-soft)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            height: 78,
            paddingInline: 28
          }}
        >
          <Space direction="vertical" size={0}>
            <Text style={{ color: "var(--text-muted)" }}>Signed in as</Text>
            <Text strong>{meLogin ?? "anonymous"}</Text>
          </Space>
          <Button onClick={logout}>Logout</Button>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

function resolveSelectedKey(pathname: string): string {
  if (pathname.startsWith("/runs/")) {
    return "/runs";
  }
  if (pathname.startsWith("/pipelines/")) {
    return "/pipelines";
  }
  if (pathname.startsWith("/settings/")) {
    return pathname;
  }
  if (pathname.startsWith("/artifacts/")) {
    return "/artifacts";
  }
  if (pathname.startsWith("/deployments/")) {
    return "/deployments";
  }
  if (pathname.startsWith("/executors/")) {
    return "/executors";
  }
  if (pathname.startsWith("/admin/permissions/")) {
    return "/admin/permissions";
  }
  if (pathname.startsWith("/audit/")) {
    return "/audit";
  }
  return pathname;
}

function buildMenuItems(
  config: MenuAccess[],
  hasCapability: (capability: Capability) => boolean
) {
  return config
    .filter((item) => hasCapability(item.capability))
    .map((item) => {
      if (!item.children) {
        return {
          key: item.key,
          icon: item.icon,
          label: item.label
        };
      }
      const children = item.children.filter((child) => hasCapability(child.capability)).map((child) => ({
        key: child.key,
        label: child.label
      }));
      if (children.length === 0) {
        return null;
      }
      return {
        key: item.key,
        icon: item.icon,
        label: item.label,
        children
      };
    })
    .filter(Boolean);
}
