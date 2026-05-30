package ru.diplom.cicd.contracts.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Политика очистки workspace после выполнения job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkspacePolicy(String cleanup, boolean preserveOnFailure) {}
