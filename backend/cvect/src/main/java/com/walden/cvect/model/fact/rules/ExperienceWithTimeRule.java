package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

public final class ExperienceWithTimeRule implements ChunkFactRule {

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.EXPERIENCE) {
            return FactDecision.abstain();
        }

        var f = ctx.getFeatures();

        if (f.hasTimePattern()) {
            return FactDecision.accept("has time span");
        }
        if (f.hasResponsibilityCue()) {
            return FactDecision.accept("has responsibility cue");
        }
        if (f.startsWithVerbLike()) {
            return FactDecision.accept("verb-like start");
        }

        return FactDecision.reject("no experience signal");
    }
}
