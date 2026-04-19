package com.walden.cvect.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt.secret:cvect-dev-secret-change-me}") String secret,
            @Value("${app.security.jwt.ttl-seconds:86400}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        this.secret = resolveSecret(secret);
        this.ttlSeconds = Math.max(60L, ttlSeconds);
    }

    public String createToken(CurrentUser user) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.userId().toString());
            payload.put("tenant_id", user.tenantId().toString());
            payload.put("username", user.username());
            payload.put("display_name", user.displayName());
            payload.put("roles", user.roles());
            payload.put("permissions", List.copyOf(user.permissions()));
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());

            String unsigned = encodeJson(header) + "." + encodeJson(payload);
            return unsigned + "." + sign(unsigned);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create JWT", e);
        }
    }

    public Optional<CurrentUser> parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            long exp = longClaim(payload.get("exp"));
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(String.valueOf(payload.get("sub")));
            UUID tenantId = UUID.fromString(String.valueOf(payload.get("tenant_id")));
            String username = stringClaim(payload.get("username"));
            String displayName = stringClaim(payload.get("display_name"));
            List<String> roles = stringListClaim(payload.get("roles"));
            Set<String> permissions = Set.copyOf(stringListClaim(payload.get("permissions")));
            return Optional.of(new CurrentUser(userId, tenantId, username, displayName, roles, permissions));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private static byte[] resolveSecret(String rawSecret) {
        String value = StringUtils.hasText(rawSecret) ? rawSecret.trim() : "cvect-dev-secret-change-me";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static long longClaim(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String stringClaim(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringListClaim(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .toList();
    }
}
