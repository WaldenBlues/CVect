package com.walden.cvect.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JwtService unit tests")
class JwtServiceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("createToken should honor configured ttl below one minute")
    void createTokenShouldHonorConfiguredTtlBelowOneMinute() throws Exception {
        JwtService jwtService = new JwtService(objectMapper, "test-secret", 5L);

        String token = jwtService.createToken(new CurrentUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "demo",
                "Demo Owner",
                List.of("OWNER"),
                Set.of("JD_READ")));

        Map<String, Object> payload = decodePayload(token);
        long issuedAt = ((Number) payload.get("iat")).longValue();
        long expiresAt = ((Number) payload.get("exp")).longValue();

        assertEquals(5L, jwtService.ttlSeconds());
        assertEquals(5L, expiresAt - issuedAt);
    }

    private Map<String, Object> decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        return objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
    }
}
