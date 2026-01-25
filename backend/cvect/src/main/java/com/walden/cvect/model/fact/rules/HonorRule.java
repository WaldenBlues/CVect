package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

import java.util.Set;

public final class HonorRule implements ChunkFactRule {

    // 排除的纯标签
    private static final Set<String> HONOR_LABELS = Set.of(
            "荣誉奖励", "获奖情况", "个人荣誉", "HONORS", "AWARDS", "奖项");

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.HONOR) {
            return FactDecision.abstain();
        }

        String text = ctx.getText().trim();

        // 排除纯标签
        if (HONOR_LABELS.contains(text)) {
            return FactDecision.reject("pure honor section label");
        }

        // 过短且无关键词则拒绝
        if (text.length() < 4 && !ctx.getFeatures().hasHonorCue()) {
            return FactDecision.reject("too short for a valid honor fact");
        }

        // 含关键词或数字则接受
        if (ctx.getFeatures().hasHonorCue() || ctx.getFeatures().hasDigit()) {
            return FactDecision.accept("honor with semantic details");
        }

        // 长描述即使无关键词也保留
        if (text.length() > 20) {
            return FactDecision.accept("long descriptive honor info");
        }

        return FactDecision.reject("unrecognized honor format");
    }
}