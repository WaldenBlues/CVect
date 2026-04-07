package com.walden.cvect.service.matching;

import com.walden.cvect.web.controller.search.SearchController;

final class SearchWeightNormalizer {

    private SearchWeightNormalizer() {
    }

    static Weights resolve(SearchController.SearchRequest request) {
        boolean includeExperience = request.filterByExperience() || !request.filterBySkill();
        boolean includeSkill = request.filterBySkill() || !request.filterByExperience();

        float experience = sanitizeWeight(request.experienceWeight(), includeExperience ? 0.5f : 0.0f);
        float skill = sanitizeWeight(request.skillWeight(), includeSkill ? 0.5f : 0.0f);

        if (!includeExperience) {
            experience = 0.0f;
        }
        if (!includeSkill) {
            skill = 0.0f;
        }

        if (includeExperience && !includeSkill) {
            return new Weights(1.0f, 0.0f);
        }
        if (!includeExperience && includeSkill) {
            return new Weights(0.0f, 1.0f);
        }

        float sum = experience + skill;
        if (sum <= 0.0f) {
            return new Weights(0.5f, 0.5f);
        }
        return new Weights(experience / sum, skill / sum);
    }

    private static float sanitizeWeight(Float value, float defaultValue) {
        if (value == null || !Float.isFinite(value) || value < 0.0f) {
            return defaultValue;
        }
        return value;
    }

    record Weights(float experienceWeight, float skillWeight) {
    }
}
