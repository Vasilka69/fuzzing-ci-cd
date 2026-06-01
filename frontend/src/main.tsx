import App from "@/App";
import { AuthProvider } from "@/app/AuthProvider";
import "@/styles/theme.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ConfigProvider, theme } from "antd";
import "antd/dist/reset.css";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <ConfigProvider
    theme={{
      algorithm: theme.defaultAlgorithm,
      token: {
        colorPrimary: "#0f9d8f",
        borderRadius: 12,
        colorBgContainer: "rgba(255,255,255,0.82)",
        fontFamily: "Manrope, Segoe UI, sans-serif"
      }
    }}
  >
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </ConfigProvider>
);
