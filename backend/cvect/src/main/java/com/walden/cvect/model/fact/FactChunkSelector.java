package com.walden.cvect.model.fact;

import com.walden.cvect.model.ResumeChunk;

public interface FactChunkSelector {
    boolean accept(ResumeChunk chunk);
}
