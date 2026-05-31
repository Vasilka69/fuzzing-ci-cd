package ru.diplom.cicd.storage.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import ru.diplom.cicd.executor.core.storage.StorageUris;
import ru.diplom.cicd.storage.backend.StorageSaveRequest;

final class StorageArtifactHttpRequestUtils {

    static final String HEADER_ARTIFACT_TYPE = "X-CICD-Artifact-Type";
    static final String HEADER_ARTIFACT_NAME = "X-CICD-Artifact-Name";

    private StorageArtifactHttpRequestUtils() {}

    static String normalizeCapturedPath(String namespacePath) {
        String relativePath = StringUtils.trimLeadingCharacter(namespacePath, '/');
        return StorageUris.normalizeNamespacePath(relativePath);
    }

    static StorageSaveRequest saveRequest(String namespacePath, HttpHeaders headers, HttpServletRequest request) {
        return new StorageSaveRequest(
                namespacePath,
                headerValue(headers, HEADER_ARTIFACT_TYPE),
                uploadName(headers, namespacePath),
                contentType(headers, request),
                Map.of());
    }

    static String downloadFileName(String namespacePath) {
        String fileName = StringUtils.getFilename(namespacePath);
        return StringUtils.hasText(fileName) ? fileName : namespacePath;
    }

    private static String uploadName(HttpHeaders headers, String namespacePath) {
        String headerName = headerValue(headers, HEADER_ARTIFACT_NAME);
        return StringUtils.hasText(headerName) ? headerName : downloadFileName(namespacePath);
    }

    private static String contentType(HttpHeaders headers, HttpServletRequest request) {
        MediaType mediaType = headers.getContentType();
        if (mediaType != null) {
            return mediaType.toString();
        }
        return headerValue(request.getContentType());
    }

    private static String headerValue(HttpHeaders headers, String name) {
        return headerValue(headers.getFirst(name));
    }

    private static String headerValue(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
