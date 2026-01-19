package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

public final class HonorWithDetailRule implements ChunkFactRule {

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.HONOR) {
            return FactDecision.abstain();
        }

        return ctx.getFeatures().hasDigit()
                ? FactDecision.accept("honor with detail")
                : FactDecision.reject("honor label only");
    }
}
