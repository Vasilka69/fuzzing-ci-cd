package ru.diplom.cicd.executor.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StorageUrisTest {

    @Test
    void toStorageUriBuildsCanonicalStorageUriFromNamespacePath() {
        String storageUri = StorageUris.toStorageUri("source-snapshots/job-1/./snapshot.tar.gz");

        assertEquals("storage://source-snapshots/job-1/snapshot.tar.gz", storageUri);
    }

    @Test
    void namespacePathParsesStorageUriBackToRelativeNamespacePath() {
        String namespacePath = StorageUris.namespacePath("storage://source-snapshots/job-1/snapshot.tar.gz");

        assertEquals("source-snapshots/job-1/snapshot.tar.gz", namespacePath);
    }

    @Test
    void namespacePathAcceptsUriWithoutAuthorityForDownloadCompatibility() {
        String namespacePath = StorageUris.namespacePath("storage:///source-snapshots/job-1/snapshot.tar.gz");

        assertEquals("source-snapshots/job-1/snapshot.tar.gz", namespacePath);
    }

    @Test
    void normalizeNamespacePathRejectsAbsolutePath() {
        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> StorageUris.normalizeNamespacePath("/snapshots/a"));

        assertEquals("Storage namespace должен быть относительным: /snapshots/a", exception.getMessage());
    }

    @Test
    void normalizeNamespacePathRejectsPathTraversalOutsideRoot() {
        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> StorageUris.normalizeNamespacePath("../outside.txt"));

        assertEquals("Storage namespace выходит за пределы корня: ../outside.txt", exception.getMessage());
    }

    @Test
    void normalizeNamespacePathRejectsEmptySegments() {
        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> StorageUris.normalizeNamespacePath("snapshots//a"));

        assertEquals("Storage namespace не должен содержать пустые сегменты: snapshots//a", exception.getMessage());
    }

    @Test
    void normalizeNamespacePathRejectsUnsafeCharacters() {
        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> StorageUris.normalizeNamespacePath("snapshots/a b"));

        assertEquals("Storage namespace содержит недопустимые символы: snapshots/a b", exception.getMessage());
    }

    @Test
    void namespacePathRejectsNonStorageScheme() {
        StorageClientException exception =
                assertThrows(StorageClientException.class, () -> StorageUris.namespacePath("file:///tmp/a"));

        assertEquals("Storage URI должен использовать схему storage://", exception.getMessage());
    }

    @Test
    void namespacePathRejectsQuery() {
        StorageClientException exception = assertThrows(
                StorageClientException.class, () -> StorageUris.namespacePath("storage://snapshots/a?x=1"));

        assertEquals("Storage URI не должен содержать query или fragment", exception.getMessage());
    }

    @Test
    void namespacePathRejectsFragment() {
        StorageClientException exception = assertThrows(
                StorageClientException.class, () -> StorageUris.namespacePath("storage://snapshots/a#part"));

        assertEquals("Storage URI не должен содержать query или fragment", exception.getMessage());
    }
}
