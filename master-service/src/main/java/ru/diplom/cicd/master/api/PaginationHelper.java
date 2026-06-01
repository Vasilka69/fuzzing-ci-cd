package ru.diplom.cicd.master.api;

import java.util.List;
import ru.diplom.cicd.master.api.dto.PageInfoResponse;
import ru.diplom.cicd.master.api.dto.PageResponse;

public final class PaginationHelper {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    private PaginationHelper() {
    }

    public static <T> PageResponse<T> paginate(List<T> source, Integer page, Integer size) {
        return paginate(source, page, size, DEFAULT_SIZE, MAX_SIZE);
    }

    public static <T> PageResponse<T> paginate(List<T> source, Integer page, Integer size, int defaultSize, int maxSize) {
        List<T> safeSource = source == null ? List.of() : source;
        long totalItems = safeSource.size();

        if (page == null && size == null) {
            int effectiveSize = safeSource.size();
            int totalPages = safeSource.isEmpty() ? 0 : 1;
            return new PageResponse<>(safeSource, new PageInfoResponse(0, effectiveSize, totalItems, totalPages, false));
        }

        int effectivePage = normalizePage(page);
        int effectiveSize = normalizeSize(size, defaultSize, maxSize);

        int fromIndex = Math.min(effectivePage * effectiveSize, safeSource.size());
        int toIndex = Math.min(fromIndex + effectiveSize, safeSource.size());
        List<T> items = safeSource.subList(fromIndex, toIndex);

        int totalPages = effectiveSize == 0
                ? 0
                : (int) Math.ceil((double) totalItems / effectiveSize);
        boolean hasNext = effectivePage + 1 < totalPages;

        return new PageResponse<>(items, new PageInfoResponse(effectivePage, effectiveSize, totalItems, totalPages, hasNext));
    }

    private static int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    private static int normalizeSize(Integer size, int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }
}
