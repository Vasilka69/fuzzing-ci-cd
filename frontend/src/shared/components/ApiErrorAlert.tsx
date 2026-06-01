import { Alert } from "antd";
import { ApiError } from "@/shared/types/api";

type ApiErrorAlertProps = {
  error: unknown;
};

export function ApiErrorAlert({ error }: ApiErrorAlertProps) {
  if (!error) {
    return null;
  }
  if (error instanceof ApiError) {
    const details = [error.code, error.correlationId].filter(Boolean).join(" | ");
    return (
      <Alert
        type="error"
        showIcon
        message={error.message}
        description={details ? `Details: ${details}` : undefined}
      />
    );
  }
  return <Alert type="error" showIcon message="Unexpected error" description={String(error)} />;
}
