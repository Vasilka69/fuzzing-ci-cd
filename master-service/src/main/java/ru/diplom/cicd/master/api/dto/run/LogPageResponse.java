package ru.diplom.cicd.master.api.dto.run;

import java.util.List;
import ru.diplom.cicd.master.opensearch.OpenSearchHistoryLogService;

public record LogPageResponse(
        List<OpenSearchHistoryLogService.LogLine> items,
        String nextCursor
) {
}
