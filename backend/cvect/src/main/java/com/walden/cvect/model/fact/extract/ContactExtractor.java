package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.Regex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class ContactExtractor implements FactExtractor {

    @Override
    public boolean supports(ResumeChunk chunk) {
        return chunk.getType() == ChunkType.CONTACT;
    }

    @Override
    public String extract(ResumeChunk chunk) {
        String text = chunk.getContent();
        List<String> results = new ArrayList<>();

        Matcher emailMatcher = Regex.EMAIL_STRICT.matcher(text);
        while (emailMatcher.find()) {
            results.add(emailMatcher.group());
        }

        Matcher phoneMatcher = Regex.PHONE_STRICT.matcher(text);
        while (phoneMatcher.find()) {
            results.add(phoneMatcher.group());
        }

        return String.join("\n", results);
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.ADDITIVE;
    }
}
