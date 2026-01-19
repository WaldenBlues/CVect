package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

public final class ContactAlwaysFactRule implements ChunkFactRule {

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.CONTACT) {
            return FactDecision.abstain();
        }

        var f = ctx.getFeatures();
        if (f.hasEmail()) {
            return FactDecision.accept("has email");
        }
        if (f.hasDigit()) {
            return FactDecision.accept("has phone number");
        }
        return FactDecision.reject("contact label only");
    }
}
