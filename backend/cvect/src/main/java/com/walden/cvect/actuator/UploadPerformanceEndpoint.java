package com.walden.cvect.actuator;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传链路性能观测端点：
 * 拆分队列等待与实际处理时长，便于快速识别瓶颈阶段。
 */
@Component
@Endpoint(id = "uploadperformance")
public class UploadPerformanceEndpoint {

    private final JdbcTemplate jdbcTemplate;

    public UploadPerformanceEndpoint(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @ReadOperation
    public Map<String, Object> performance() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                  count(*) AS total_completed,
                  round(avg(extract(epoch from (started_at - created_at)))::numeric, 3) AS queue_wait_avg_s,
                  round(percentile_cont(0.50) within group (order by extract(epoch from (started_at - created_at)))::numeric, 3) AS queue_wait_p50_s,
                  round(percentile_cont(0.90) within group (order by extract(epoch from (started_at - created_at)))::numeric, 3) AS queue_wait_p90_s,
                  round(percentile_cont(0.99) within group (order by extract(epoch from (started_at - created_at)))::numeric, 3) AS queue_wait_p99_s,
                  round(avg(extract(epoch from (updated_at - started_at)))::numeric, 3) AS processing_avg_s,
                  round(percentile_cont(0.50) within group (order by extract(epoch from (updated_at - started_at)))::numeric, 3) AS processing_p50_s,
                  round(percentile_cont(0.90) within group (order by extract(epoch from (updated_at - started_at)))::numeric, 3) AS processing_p90_s,
                  round(percentile_cont(0.99) within group (order by extract(epoch from (updated_at - started_at)))::numeric, 3) AS processing_p99_s,
                  round(avg(extract(epoch from (updated_at - created_at)))::numeric, 3) AS total_avg_s,
                  round(percentile_cont(0.50) within group (order by extract(epoch from (updated_at - created_at)))::numeric, 3) AS total_p50_s,
                  round(percentile_cont(0.90) within group (order by extract(epoch from (updated_at - created_at)))::numeric, 3) AS total_p90_s,
                  round(percentile_cont(0.99) within group (order by extract(epoch from (updated_at - created_at)))::numeric, 3) AS total_p99_s
                FROM upload_items
                WHERE status IN ('DONE','DUPLICATE','FAILED')
                  AND created_at IS NOT NULL
                  AND started_at IS NOT NULL
                  AND updated_at IS NOT NULL
                """);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCompleted", asLong(row.get("total_completed")));
        result.put("queueWait", timingBlock(
                row.get("queue_wait_avg_s"),
                row.get("queue_wait_p50_s"),
                row.get("queue_wait_p90_s"),
                row.get("queue_wait_p99_s")));
        result.put("processing", timingBlock(
                row.get("processing_avg_s"),
                row.get("processing_p50_s"),
                row.get("processing_p90_s"),
                row.get("processing_p99_s")));
        result.put("total", timingBlock(
                row.get("total_avg_s"),
                row.get("total_p50_s"),
                row.get("total_p90_s"),
                row.get("total_p99_s")));
        return result;
    }

    private static Map<String, Object> timingBlock(Object avg, Object p50, Object p90, Object p99) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("avgSeconds", asDecimal(avg));
        block.put("p50Seconds", asDecimal(p50));
        block.put("p90Seconds", asDecimal(p90));
        block.put("p99Seconds", asDecimal(p99));
        return block;
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
