package com.walden.cvect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE = "CVECT_ACCESS_TOKEN";

    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                resolveToken(request)
                        .flatMap(jwtService::parseToken)
                        .ifPresent(tokenUser -> authenticate(tokenUser, response));
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("userId");
            MDC.remove("username");
        }
    }

    private void authenticate(CurrentUser tokenUser, HttpServletResponse response) {
        try {
            CurrentUser currentUser = authService.loadCurrentUser(tokenUser.userId());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    currentUser,
                    null,
                    currentUser.permissions().stream().map(SimpleGrantedAuthority::new).toList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put("tenantId", currentUser.tenantId().toString());
            MDC.put("userId", currentUser.userId().toString());
            MDC.put("username", currentUser.username());
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            clearAccessCookie(response);
        }
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return Optional.of(authorization.substring(7).trim());
        }
        String cookieToken = resolveCookieToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return Optional.of(cookieToken);
        }
        String queryToken = request.getParameter("access_token");
        if (StringUtils.hasText(queryToken)) {
            return Optional.of(queryToken.trim());
        }
        return Optional.empty();
    }

    private String resolveCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void clearAccessCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(ACCESS_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
