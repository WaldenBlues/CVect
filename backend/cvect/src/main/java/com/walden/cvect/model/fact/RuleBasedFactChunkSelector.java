package com.walden.cvect.model.fact;

import java.util.List;

import com.walden.cvect.model.ResumeChunk;

public class RuleBasedFactChunkSelector implements FactChunkSelector {

    private final List<ChunkFactRule> rules;

    public RuleBasedFactChunkSelector(List<ChunkFactRule> rules) {
        this.rules = rules;
    }

    @Override
    public boolean accept(ResumeChunk chunk) {
        ChunkFactContext ctx = buildContext(chunk);

        for (ChunkFactRule rule : rules) {
            FactDecision decision = rule.apply(ctx);
            if (!decision.isAbstain()) {
                return decision.isAccepted();
            }
        }
        return false; // 默认拒绝
    }

    private ChunkFactContext buildContext(ResumeChunk chunk) {
        return new ChunkFactContext(
                chunk.getType(),
                chunk.getContent(),
                chunk.getIndex(),
                chunk.getLength());
    }
}
