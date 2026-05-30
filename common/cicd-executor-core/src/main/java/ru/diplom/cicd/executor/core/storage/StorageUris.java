package ru.diplom.cicd.executor.core.storage;

import java.net.URI;
import java.nio.file.Path;
import java.util.regex.Pattern;

final class StorageUris {

    private static final String SCHEME = "storage";
    private static final Pattern ALLOWED_NAMESPACE = Pattern.compile("[A-Za-z0-9._/-]+");

    private StorageUris() {}

    static String toStorageUri(String namespacePath) {
        return SCHEME + "://" + normalizeNamespacePath(namespacePath);
    }

    static String namespacePath(String storageUri) {
        URI uri;
        try {
            uri = URI.create(storageUri);
        } catch (IllegalArgumentException exception) {
            throw new StorageClientException("Некорректный storage URI: " + storageUri, exception);
        }

        if (!SCHEME.equals(uri.getScheme())) {
            throw new StorageClientException("Storage URI должен использовать схему storage://");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new StorageClientException("Storage URI не должен содержать query или fragment");
        }

        StringBuilder namespace = new StringBuilder();
        if (uri.getAuthority() != null) {
            namespace.append(uri.getAuthority());
        }
        if (uri.getPath() != null && !uri.getPath().isBlank()) {
            String path = uri.getPath();
            if (!namespace.isEmpty() && !path.startsWith("/")) {
                namespace.append('/');
            }
            namespace.append(path);
        }

        return normalizeNamespacePath(stripLeadingSlash(namespace.toString()));
    }

    static String normalizeNamespacePath(String namespacePath) {
        if (namespacePath == null || namespacePath.isBlank()) {
            throw new IllegalArgumentException("Storage namespace должен быть непустым");
        }

        String unixPath = namespacePath.replace('\\', '/');
        if (unixPath.startsWith("/")) {
            throw new StorageClientException("Storage namespace должен быть относительным: " + namespacePath);
        }
        if (unixPath.contains("//")) {
            throw new StorageClientException("Storage namespace не должен содержать пустые сегменты: " + namespacePath);
        }
        if (!ALLOWED_NAMESPACE.matcher(unixPath).matches()) {
            throw new StorageClientException("Storage namespace содержит недопустимые символы: " + namespacePath);
        }

        Path normalizedPath = Path.of(unixPath).normalize();
        String normalized = normalizedPath.toString().replace('\\', '/');
        if (normalized.isBlank() || ".".equals(normalized) || normalized.startsWith("../") || "..".equals(normalized)) {
            throw new StorageClientException("Storage namespace выходит за пределы корня: " + namespacePath);
        }
        return normalized;
    }

    private static String stripLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}
