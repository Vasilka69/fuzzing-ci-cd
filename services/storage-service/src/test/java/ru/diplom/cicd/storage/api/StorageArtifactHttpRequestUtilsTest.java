package ru.diplom.cicd.storage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.diplom.cicd.executor.core.storage.StorageClientException;
import ru.diplom.cicd.storage.backend.StorageSaveRequest;

class StorageArtifactHttpRequestUtilsTest {

    @Test
    void normalizeCapturedPathTrimsSpringCapturedLeadingSlash() {
        String namespacePath = StorageArtifactHttpRequestUtils.normalizeCapturedPath("/reports/job-1/result.txt");

        assertEquals("reports/job-1/result.txt", namespacePath);
    }

    @Test
    void normalizeCapturedPathRejectsTraversal() {
        StorageClientException exception = assertThrows(
                StorageClientException.class,
                () -> StorageArtifactHttpRequestUtils.normalizeCapturedPath("/../outside.txt"));

        assertEquals("Storage namespace выходит за пределы корня: ../outside.txt", exception.getMessage());
    }

    @Test
    void saveRequestUsesArtifactHeadersAndContentType() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(StorageArtifactHttpRequestUtils.HEADER_ARTIFACT_TYPE, "report");
        headers.set(StorageArtifactHttpRequestUtils.HEADER_ARTIFACT_NAME, "result.txt");
        MockHttpServletRequest request = new MockHttpServletRequest();

        StorageSaveRequest saveRequest =
                StorageArtifactHttpRequestUtils.saveRequest("reports/job-1/result.txt", headers, request);

        assertEquals("reports/job-1/result.txt", saveRequest.destinationPath());
        assertEquals("report", saveRequest.artifactType());
        assertEquals("result.txt", saveRequest.name());
        assertEquals("text/plain", saveRequest.contentType());
    }

    @Test
    void saveRequestFallsBackToFilenameAndDefaultContentType() {
        HttpHeaders headers = new HttpHeaders();
        MockHttpServletRequest request = new MockHttpServletRequest();

        StorageSaveRequest saveRequest =
                StorageArtifactHttpRequestUtils.saveRequest("reports/job-1/result.txt", headers, request);

        assertEquals("result.txt", saveRequest.name());
        assertEquals(StorageSaveRequest.DEFAULT_CONTENT_TYPE, saveRequest.contentType());
    }

    @Test
    void downloadFileNameUsesLastPathSegment() {
        String fileName = StorageArtifactHttpRequestUtils.downloadFileName("reports/job-1/result.txt");

        assertEquals("result.txt", fileName);
    }
}
