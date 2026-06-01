import type { TablePaginationConfig } from "antd";
import { useMemo, useState } from "react";

export type TablePageState = {
  current: number;
  pageSize: number;
};

export const PAGE_SIZE_OPTIONS = ["10", "20", "50", "100"];

export function useTablePagination(defaultPageSize = 10) {
  const [pageState, setPageState] = useState<TablePageState>({
    current: 1,
    pageSize: defaultPageSize
  });

  const pagination = useMemo<TablePaginationConfig>(
    () => ({
      current: pageState.current,
      pageSize: pageState.pageSize,
      showSizeChanger: true,
      pageSizeOptions: PAGE_SIZE_OPTIONS,
      showTotal: (total, range) => `${range[0]}-${range[1]} of ${total}`
    }),
    [pageState.current, pageState.pageSize]
  );

  const onPaginationChange = (next: TablePaginationConfig) => {
    setPageState({
      current: next.current ?? 1,
      pageSize: next.pageSize ?? defaultPageSize
    });
  };

  const resetPage = () => {
    setPageState((current) => ({
      ...current,
      current: 1
    }));
  };

  return {
    pageState,
    pagination,
    onPaginationChange,
    resetPage,
    setPageState
  };
}
