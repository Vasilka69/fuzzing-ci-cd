package ru.diplom.cicd.executor.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Общие checksum-операции для storage adapters и storage-service.
 */
public final class StorageChecksums {

    private StorageChecksums() {}

    public static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = Files.newInputStream(path);
                DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK не поддерживает SHA-256", exception);
        }
    }
}
