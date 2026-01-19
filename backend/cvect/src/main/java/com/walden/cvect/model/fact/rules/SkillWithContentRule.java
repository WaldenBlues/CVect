package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

public final class SkillWithContentRule implements ChunkFactRule {

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.SKILL) {
            return FactDecision.abstain();
        }

        String text = ctx.getText();
        if (text == null || text.isBlank()) {
            return FactDecision.reject("empty skill block");
        }

        return FactDecision.accept("skill content");
    }
}
