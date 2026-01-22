package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

public final class LinkRule implements ChunkFactRule {

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.LINK) {
            return FactDecision.abstain();
        }

        return ctx.getFeatures().hasUrl()
                ? FactDecision.accept("has url")
                : FactDecision.reject("no valid url");
    }
}
