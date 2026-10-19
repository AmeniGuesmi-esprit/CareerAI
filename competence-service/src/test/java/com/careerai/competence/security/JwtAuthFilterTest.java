package com.careerai.competence.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", JwtServiceTest.SECRET);
        filter = new JwtAuthFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String jwt(Map<String, Object> claims) {
        return Jwts.builder().setClaims(new HashMap<>(claims))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(JwtServiceTest.SECRET)), SignatureAlgorithm.HS256)
                .compact();
    }

    private MockFilterChain run(String authHeader) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authHeader != null) req.addHeader("Authorization", authHeader);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validToken_setsUserIdAsPrincipalAndRole() throws Exception {
        MockFilterChain chain = run("Bearer " + jwt(Map.of("userId", 42, "role", "ADMIN")));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isNotNull(); // la chaîne a continué
    }

    @Test
    void noHeader_noAuthentication_butChainContinues() throws Exception {
        MockFilterChain chain = run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonBearerHeader_isIgnored() throws Exception {
        run("Basic abc");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_noAuthentication() throws Exception {
        run("Bearer garbage");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenWithoutUserIdClaim_noAuthentication() throws Exception {
        run("Bearer " + jwt(Map.of("role", "USER")));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonNumericUserId_noAuthentication() throws Exception {
        run("Bearer " + jwt(Map.of("userId", "abc", "role", "USER")));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
