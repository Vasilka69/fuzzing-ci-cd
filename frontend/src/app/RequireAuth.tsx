import { useAuth } from "@/app/AuthProvider";
import { Navigate, Outlet, useLocation } from "react-router-dom";

export function RequireAuth() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
