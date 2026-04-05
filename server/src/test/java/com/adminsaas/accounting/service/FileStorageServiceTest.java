package com.adminsaas.accounting.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesUploadedFileUsingSanitizedFilename() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\inspection:template?.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "test".getBytes()
        );

        FileStorageService.StoredFile stored = service.store(4L, "evidence-template", file);

        assertEquals("inspection_template_.docx", stored.originalFilename());
        assertTrue(Files.exists(Path.of(stored.path())));
        assertFalse(stored.storedFilename().contains(":"));
        assertFalse(stored.storedFilename().contains("?"));
    }

    @Test
    void rejectsInvalidUploadCategory() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "inspection.docx", "application/octet-stream", "test".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.store(4L, "../outside", file));
    }

    @Test
    void truncatesVeryLongFilename() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        String longName = "a".repeat(200) + ".docx";
        MockMultipartFile file = new MockMultipartFile("file", longName, "application/octet-stream", "test".getBytes());

        FileStorageService.StoredFile stored = service.store(4L, "evidence-template", file);

        assertTrue(stored.originalFilename().endsWith(".docx"));
        assertTrue(stored.originalFilename().length() <= 120);
        assertTrue(Files.exists(Path.of(stored.path())));
    }
}
