package com.example.aiagent.model;

import java.time.Instant;

public class DocumentInfo {

    private String id;
    private String filename;
    private String contentType;
    private long size;
    private int chunks;
    private Instant uploadedAt;

    public DocumentInfo() {
    }

    public DocumentInfo(String id, String filename, String contentType, long size, int chunks) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
        this.chunks = chunks;
        this.uploadedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getChunks() {
        return chunks;
    }

    public void setChunks(int chunks) {
        this.chunks = chunks;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
