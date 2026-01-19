package com.walden.cvect.model;

public class ResumeChunk {

    private final int index;
    private final String content;
    private final ChunkType type;
    private final int length;

    public ResumeChunk(int index, String content, ChunkType type) {
        this.index = index;
        this.content = content;
        this.type = type;
        this.length = content == null ? 0 : content.length();
    }

    public int getIndex() {
        return index;
    }

    public String getContent() {
        return content;
    }

    public ChunkType getType() {
        return type;
    }

    public int getLength() {
        return length;
    }
}
