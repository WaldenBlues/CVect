package com.walden.cvect.model.fact.rules;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.ChunkFactContext;
import com.walden.cvect.model.fact.ChunkFactRule;
import com.walden.cvect.model.fact.FactDecision;

import java.util.Set;

public final class HonorRule implements ChunkFactRule {

    // 常见的荣誉部分标题（标签），这些不应作为事实提取
    private static final Set<String> HONOR_LABELS = Set.of(
            "荣誉奖励", "获奖情况", "个人荣誉", "HONORS", "AWARDS", "奖项");

    @Override
    public FactDecision apply(ChunkFactContext ctx) {
        if (ctx.getType() != ChunkType.HONOR) {
            return FactDecision.abstain();
        }

        String text = ctx.getText().trim();

        // 1. 排除纯标签 (Label Only)
        if (HONOR_LABELS.contains(text)) {
            return FactDecision.reject("pure honor section label");
        }

        // 2. 长度过短且不含核心关键词，判定为无效噪音
        if (text.length() < 4 && !ctx.getFeatures().hasHonorCue()) {
            return FactDecision.reject("too short for a valid honor fact");
        }

        // 3. 核心判定逻辑：
        // 含有荣誉关键词 (如 奖/赛/获) OR 含有数字(名次/年份)
        if (ctx.getFeatures().hasHonorCue() || ctx.getFeatures().hasDigit()) {
            return FactDecision.accept("honor with semantic details");
        }

        // 4. 如果长度足够长，即使没匹配到关键词，可能是一段描述，保留它
        if (text.length() > 20) {
            return FactDecision.accept("long descriptive honor info");
        }

        return FactDecision.reject("unrecognized honor format");
    }
}