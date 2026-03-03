package com.walden.cvect.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Locale;

/**
 * Ensures queue scan index exists for upload worker claim queries.
 */
@Component
public class UploadQueueIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(UploadQueueIndexInitializer.class);
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public UploadQueueIndexInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensureQueueIndexes() {
        try {
            if (isPostgres()) {
                jdbcTemplate.execute("""
                        CREATE INDEX IF NOT EXISTS idx_upload_items_queue_scan
                        ON upload_items (status, updated_at)
                        WHERE storage_path IS NOT NULL
                        """);
            } else {
                jdbcTemplate.execute("""
                        CREATE INDEX IF NOT EXISTS idx_upload_items_queue_scan
                        ON upload_items (status, updated_at)
                        """);
            }
        } catch (Exception ex) {
            log.warn("Failed to ensure upload queue index", ex);
        }
    }

    private boolean isPostgres() {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (Exception ex) {
            log.debug("Unable to detect database product", ex);
            return false;
        }
    }
}
