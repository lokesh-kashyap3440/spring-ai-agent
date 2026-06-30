package com.example.aiagent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DocumentInfoTest {

    @Test
    void testDefaultConstructor() {
        DocumentInfo info = new DocumentInfo();
        assertNull(info.getId());
        assertNull(info.getFilename());
    }

    @Test
    void testParameterizedConstructor() {
        DocumentInfo info = new DocumentInfo("doc-1", "test.pdf", "application/pdf", 1024, 5);
        assertEquals("doc-1", info.getId());
        assertEquals("test.pdf", info.getFilename());
        assertEquals("application/pdf", info.getContentType());
        assertEquals(1024, info.getSize());
        assertEquals(5, info.getChunks());
        assertNotNull(info.getUploadedAt());
    }

    @Test
    void testSettersAndGetters() {
        DocumentInfo info = new DocumentInfo();
        info.setId("id-1");
        info.setFilename("doc.txt");
        info.setContentType("text/plain");
        info.setSize(500L);
        info.setChunks(3);
        Instant now = Instant.now();
        info.setUploadedAt(now);

        assertEquals("id-1", info.getId());
        assertEquals("doc.txt", info.getFilename());
        assertEquals("text/plain", info.getContentType());
        assertEquals(500L, info.getSize());
        assertEquals(3, info.getChunks());
        assertEquals(now, info.getUploadedAt());
    }
}
