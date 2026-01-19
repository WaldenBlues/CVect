package com.walden.cvect.model.fact;

import com.walden.cvect.model.ChunkType;

public final class ChunkFactContext {

    private final ChunkType type;
    private final String text;

    private final int index;
    private final int length;

    private final LazyFeatures features;

    public ChunkFactContext(
            ChunkType type,
            String text,
            int index,
            int length) {
        this.type = type;
        this.text = text;
        this.index = index;
        this.length = length;
        this.features = new LazyFeatures(text);
    }

    public ChunkType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getIndex() {
        return index;
    }

    public int getLength() {
        return length;
    }

    public LazyFeatures getFeatures() {
        return features;
    }
}
