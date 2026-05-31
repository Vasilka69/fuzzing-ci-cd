package ru.diplom.cicd.storage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class StorageArtifactControllerTest {

    private static final Path TEST_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "cicd-storage-service-controller-test",
            UUID.randomUUID().toString());

    @Autowired
    private WebApplicationContext webApplicationContext;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("cicd.storage.local.root", TEST_ROOT::toString);
    }

    @BeforeAll
    static void createRoot() throws Exception {
        Files.createDirectories(TEST_ROOT);
    }

    @AfterAll
    static void cleanupRoot() throws Exception {
        if (Files.notExists(TEST_ROOT)) {
            return;
        }
        try (var paths = Files.walk(TEST_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void uploadReturnsArtifactDescriptorAndDownloadReturnsFileContent() throws Exception {
        MockMvc mockMvc = mockMvc();

        MvcResult uploadResult = mockMvc.perform(put("/artifacts/reports/job-1/result.txt")
                        .content("hello storage api")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(StorageArtifactHttpRequestUtils.HEADER_ARTIFACT_TYPE, "report")
                        .header(StorageArtifactHttpRequestUtils.HEADER_ARTIFACT_NAME, "result.txt")
                        .header(
                                StorageArtifactHttpRequestUtils.HEADER_CHECKSUM_SHA256,
                                "65b2c35fdd89a7c2d3c8645e8a0816c3a4f1d39d7364eff1e2e8113cc80f19a2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactType").value("report"))
                .andExpect(jsonPath("$.name").value("result.txt"))
                .andExpect(jsonPath("$.uri").value("storage://reports/job-1/result.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").value(17))
                .andExpect(jsonPath("$.checksumSha256").isString())
                .andReturn();

        JsonNode json = objectMapper().readTree(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("storage://reports/job-1/result.txt", json.get("uri").textValue());
        assertEquals(
                "65b2c35fdd89a7c2d3c8645e8a0816c3a4f1d39d7364eff1e2e8113cc80f19a2",
                json.get("checksumSha256").textValue());
        assertFalse(json.has("storage_uri"));
        assertEquals("hello storage api", Files.readString(TEST_ROOT.resolve("reports/job-1/result.txt")));

        mockMvc.perform(get("/artifacts/reports/job-1/result.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"result.txt\""))
                .andExpect(content().bytes("hello storage api".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void uploadRejectsChecksumMismatch() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(put("/artifacts/reports/job-2/result.txt")
                        .content("hello storage api")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(
                                StorageArtifactHttpRequestUtils.HEADER_CHECKSUM_SHA256,
                                "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_storage_request"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.startsWith(
                                "SHA-256 checksum артефакта не совпадает для storage://reports/job-2/result.txt")));

        assertTrue(Files.notExists(TEST_ROOT.resolve("reports/job-2/result.txt")));
    }

    @Test
    void uploadRejectsPathTraversal() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(put("/artifacts/../outside.txt").content("outside").contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_storage_request"));

        assertTrue(Files.notExists(TEST_ROOT.resolve("../outside.txt").normalize()));
    }

    @Test
    void downloadReturnsNotFoundForMissingArtifact() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(get("/artifacts/reports/missing.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"))
                .andExpect(jsonPath("$.message").value("Артефакт не найден: storage://reports/missing.txt"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
