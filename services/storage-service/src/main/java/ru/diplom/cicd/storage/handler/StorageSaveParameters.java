package ru.diplom.cicd.storage.handler;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.executor.core.job.ExecutorJobException;
import ru.diplom.cicd.executor.core.storage.StorageChecksums;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.executor.core.storage.StorageUris;

record StorageSaveParameters(
        Path sourcePath,
        String destinationPath,
        String artifactType,
        String name,
        String contentType,
        String expectedChecksumSha256,
        Map<String, Object> metadata) {

    private static final String DEFAULT_ARTIFACT_TYPE = "source_snapshot";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final List<String> SOURCE_KEYS = List.of("sourceUri", "source_uri", "sourcePath", "source_path");
    private static final List<String> DESTINATION_KEYS =
            List.of("destinationPath", "destination_path", "storagePath", "storage_path");
    private static final List<String> CHECKSUM_KEYS = List.of("checksumSha256", "checksum_sha256", "sha256");

    public static final String METADATA_KEY = "metadata";

    StorageSaveParameters {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    static StorageSaveParameters from(JobMessage job) {
        String sourceUri = requiredString(job, SOURCE_KEYS, "Не задан sourceUri/source_uri для storage save job");
        Path sourcePath = sourcePath(sourceUri);
        validateSourcePath(sourcePath);
        String name =
                optionalString(job, "name", "artifactName", "artifact_name").orElseGet(() -> fileName(sourcePath));
        String destinationPath = optionalString(job, DESTINATION_KEYS)
                .orElseGet(() -> "source-snapshots/%s/%s".formatted(job.jobExecutionId(), name));

        try {
            destinationPath = StorageUris.normalizeNamespacePath(destinationPath);
        } catch (StorageClientException | IllegalArgumentException exception) {
            throw ExecutorJobException.validation(exception.getMessage());
        }

        String artifactType =
                optionalString(job, "artifactType", "artifact_type").orElse(DEFAULT_ARTIFACT_TYPE);
        String contentType = optionalString(job, "contentType", "content_type").orElse(DEFAULT_CONTENT_TYPE);
        String expectedChecksumSha256 = optionalString(job, CHECKSUM_KEYS)
                .map(StorageSaveParameters::normalizeChecksum)
                .orElse(null);

        Map<String, Object> metadata = new LinkedHashMap<>(metadata(job));
        metadata.put("jobExecutionId", job.jobExecutionId());
        metadata.put("pipelineRunId", job.pipelineRunId());
        metadata.put("pipelineId", job.pipelineId());
        metadata.put("stageId", job.stageId());
        metadata.put("jobId", job.jobId());
        metadata.put("jobType", job.jobType().wireValue());
        metadata.put("templatePath", job.templatePath());

        return new StorageSaveParameters(
                sourcePath, destinationPath, artifactType, name, contentType, expectedChecksumSha256, metadata);
    }

    private static String requiredString(JobMessage job, List<String> keys, String message) {
        return optionalString(job, keys).orElseThrow(() -> ExecutorJobException.validation(message));
    }

    private static java.util.Optional<String> optionalString(JobMessage job, String... keys) {
        return optionalString(job, List.of(keys));
    }

    private static java.util.Optional<String> optionalString(JobMessage job, List<String> keys) {
        return keys.stream()
                .map(key -> value(job.params(), job.inputs(), key))
                .flatMap(java.util.Optional::stream)
                .findFirst();
    }

    private static java.util.Optional<String> value(
            Map<String, Object> primary, Map<String, Object> secondary, String key) {
        Object value = primary.containsKey(key) ? primary.get(key) : secondary.get(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof String text && !text.isBlank()) {
            return java.util.Optional.of(text);
        }
        throw ExecutorJobException.validation("Параметр storage job должен быть непустой строкой: " + key);
    }

    private static Path sourcePath(String sourceUri) {
        try {
            URI uri = URI.create(sourceUri);
            if (uri.getScheme() == null) {
                return Path.of(sourceUri);
            }
            if (!"file".equals(uri.getScheme())) {
                throw ExecutorJobException.validation("storage save job поддерживает только file:// sourceUri");
            }
            return Path.of(uri);
        } catch (IllegalArgumentException exception) {
            throw ExecutorJobException.validation("Некорректный sourceUri/source_uri для storage save job");
        }
    }

    private static String fileName(Path sourcePath) {
        Path fileName = sourcePath.getFileName();
        return fileName == null ? "artifact" : fileName.toString();
    }

    private static void validateSourcePath(Path sourcePath) {
        if (!Files.isRegularFile(sourcePath.toAbsolutePath().normalize())) {
            throw ExecutorJobException.validation("Исходный файл для storage save job не найден: " + sourcePath);
        }
    }

    private static String normalizeChecksum(String checksum) {
        try {
            return StorageChecksums.normalizeSha256(checksum);
        } catch (IllegalArgumentException exception) {
            throw ExecutorJobException.validation(exception.getMessage());
        }
    }

    private static Map<String, Object> metadata(JobMessage job) {
        Object metadata = job.params().containsKey(METADATA_KEY)
                ? job.params().get(METADATA_KEY)
                : job.inputs().get(METADATA_KEY);
        if (metadata == null) {
            return Map.of();
        }
        if (!(metadata instanceof Map<?, ?> rawMetadata)) {
            throw ExecutorJobException.validation("metadata storage job должен быть JSON object");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        rawMetadata.forEach((key, value) -> {
            if (!(key instanceof String textKey) || textKey.isBlank()) {
                throw ExecutorJobException.validation("metadata storage job должен содержать только строковые ключи");
            }
            result.put(textKey, value);
        });
        return result;
    }
}
