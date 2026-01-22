package com.walden.cvect.model.fact;

import java.util.List;

import com.walden.cvect.model.fact.rules.ContactRule;
import com.walden.cvect.model.fact.rules.ExperienceRule;
import com.walden.cvect.model.fact.rules.HeaderRule;
import com.walden.cvect.model.fact.rules.HonorRule;
import com.walden.cvect.model.fact.rules.LinkRule;
import com.walden.cvect.model.fact.rules.SkillRule;

public final class DefaultFactRules {

    public static final List<ChunkFactRule> RULES = List.of(
            new HeaderRule(),
            new ContactRule(),
            new LinkRule(),
            new ExperienceRule(),
            new SkillRule(),
            new HonorRule());

    private DefaultFactRules() {
    }
}
