package com.walden.cvect.model.fact.extract;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkExtractorTest {

    private final LinkExtractor extractor = new LinkExtractor();

    @Test
    void shouldExtractPlainUrl() {
        ResumeChunk chunk = new ResumeChunk(0, "链接\nhttps://github.com/WaldenBlues", ChunkType.LINK);

        String extracted = extractor.extract(chunk);

        assertEquals("https://github.com/WaldenBlues", extracted);
    }

    @Test
    void shouldTrimTrailingPunctuation() {
        ResumeChunk chunk = new ResumeChunk(0, "链接：https://github.com/WaldenBlues。", ChunkType.LINK);

        String extracted = extractor.extract(chunk);

        assertEquals("https://github.com/WaldenBlues", extracted);
    }

    @Test
    void shouldTrimDuplicatedGithubSuffixWhenLabelIsPrefixed() {
        ResumeChunk chunk = new ResumeChunk(
                0,
                "GitHub https://github.com/WaldenBluesGitHub",
                ChunkType.LINK
        );

        String extracted = extractor.extract(chunk);

        assertEquals("https://github.com/WaldenBlues", extracted);
    }

    @Test
    void shouldKeepSuffixIfNoPrefixedLabel() {
        ResumeChunk chunk = new ResumeChunk(0, "https://github.com/endswithgithub", ChunkType.LINK);

        String extracted = extractor.extract(chunk);

        assertEquals("https://github.com/endswithgithub", extracted);
    }
}
