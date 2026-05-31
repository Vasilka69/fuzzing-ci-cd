package ru.diplom.cicd.executor.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StorageChecksumsTest {

    @Test
    void normalizeSha256TrimsAndLowercasesChecksum() {
        String checksum = StorageChecksums.normalizeSha256(
                "  65B2C35FDD89A7C2D3C8645E8A0816C3A4F1D39D7364EFF1E2E8113CC80F19A2  ");

        assertEquals("65b2c35fdd89a7c2d3c8645e8a0816c3a4f1d39d7364eff1e2e8113cc80f19a2", checksum);
    }

    @Test
    void normalizeSha256ReturnsNullForMissingChecksum() {
        assertNull(StorageChecksums.normalizeSha256(null));
        assertNull(StorageChecksums.normalizeSha256(" "));
    }

    @Test
    void normalizeSha256RejectsInvalidChecksum() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> StorageChecksums.normalizeSha256("not-sha256"));

        assertEquals("SHA-256 checksum должен содержать 64 hex-символа", exception.getMessage());
    }
}
