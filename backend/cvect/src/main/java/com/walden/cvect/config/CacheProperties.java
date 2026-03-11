package com.walden.cvect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private boolean enabled = true;
    private final Search search = new Search();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Search getSearch() {
        return search;
    }

    public static class Search {
        private final CacheSpec queryEmbedding = new CacheSpec(Duration.ofMinutes(30), 128);
        private final CacheSpec response = new CacheSpec(Duration.ofSeconds(10), 256);

        public CacheSpec getQueryEmbedding() {
            return queryEmbedding;
        }

        public CacheSpec getResponse() {
            return response;
        }
    }

    public static class CacheSpec {
        private Duration ttl;
        private long maximumSize;

        public CacheSpec() {
        }

        public CacheSpec(Duration ttl, long maximumSize) {
            this.ttl = ttl;
            this.maximumSize = maximumSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }
}
