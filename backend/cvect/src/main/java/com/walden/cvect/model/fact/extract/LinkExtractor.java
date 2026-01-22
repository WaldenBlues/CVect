package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.fact.Regex;

import java.util.regex.Matcher;

public class LinkExtractor implements FactExtractor {

    @Override
    public boolean supports(ResumeChunk chunk) {
        return chunk.getType() == ChunkType.LINK;
    }

    @Override
    public String extract(ResumeChunk chunk) {
        Matcher matcher = Regex.URL_STRICT.matcher(chunk.getContent());
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    @Override
    public ExtractorMode mode() {
        return ExtractorMode.ADDITIVE;
    }
}
