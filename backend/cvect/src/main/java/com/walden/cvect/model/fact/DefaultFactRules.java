package com.walden.cvect.model.fact;

import java.util.List;

import com.walden.cvect.model.fact.rules.ContactAlwaysFactRule;
import com.walden.cvect.model.fact.rules.ExperienceWithTimeRule;
import com.walden.cvect.model.fact.rules.HeaderNeverFactRule;
import com.walden.cvect.model.fact.rules.HonorWithDetailRule;
import com.walden.cvect.model.fact.rules.LinkAlwaysFactRule;
import com.walden.cvect.model.fact.rules.SkillWithContentRule;

public final class DefaultFactRules {

    public static final List<ChunkFactRule> RULES = List.of(
            new HeaderNeverFactRule(),
            new ContactAlwaysFactRule(),
            new LinkAlwaysFactRule(),
            new ExperienceWithTimeRule(),
            new SkillWithContentRule(),
            new HonorWithDetailRule());

    private DefaultFactRules() {
    }
}
