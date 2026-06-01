package ru.diplom.cicd.master.api.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        PageInfoResponse pageInfo
) {
}
