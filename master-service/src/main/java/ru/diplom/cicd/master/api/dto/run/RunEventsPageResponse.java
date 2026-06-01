package ru.diplom.cicd.master.api.dto.run;

import java.util.List;

public record RunEventsPageResponse(
        List<JobExecutionResponse> items,
        String nextCursor
) {
}
