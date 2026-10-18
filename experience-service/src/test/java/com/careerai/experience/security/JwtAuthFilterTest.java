package com.careerai.experience.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthFilterTest {

    private static final byte[] SECRET_BYTES = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET_BASE64 = Base64.getEncoder().encodeToString(SECRET_BYTES);
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_BYTES);

    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthFilter(SECRET_BASE64);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- helpers ----------

    private static String token(Key key, Map<String, Object> claims, Date expiration) {
        JwtBuilder builder = Jwts.builder()
                .setSubject("laila@esprit.tn")
                .setExpiration(expiration);
        claims.forEach(builder::claim);
        return builder.signWith(key).compact();
    }

    private static Map<String, Object> claims(Object userId, String role) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (userId != null) {
            claims.put("userId", userId);
        }
        claims.put("role", role);
        return claims;
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    private void runFilter() throws Exception {
        filter.doFilter(request, response, chain);
    }

    private void assertChainContinued() {
        assertThat(chain.getRequest()).as("la requete doit toujours etre transmise a la suite de la chaine").isNotNull();
    }

    // ---------- tests ----------

    @Test
    @DisplayName("token valide -> l'utilisateur est authentifie avec id, email et role")
    void validToken_authenticatesUser() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(KEY, claims(42, "USER"), inOneHour()));

        runFilter();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(42L, "laila@esprit.tn", "USER"));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertChainContinued();
    }

    @Test
    @DisplayName("userId stocke en Long (grand nombre) est correctement lu")
    void validToken_longUserId() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(KEY, claims(5_000_000_000L, "ADMIN"), inOneHour()));

        runFilter();

        AuthUser user = (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(user.id()).isEqualTo(5_000_000_000L);
        assertThat(user.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("pas d'en-tete Authorization -> pas d'authentification, la chaine continue")
    void noHeader_noAuthentication() throws Exception {
        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("en-tete sans prefixe 'Bearer ' -> ignore")
    void headerWithoutBearerPrefix_ignored() throws Exception {
        request.addHeader("Authorization", token(KEY, claims(42, "USER"), inOneHour()));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("token mal forme -> pas d'authentification, pas d'exception")
    void malformedToken_noAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer ceci.nest.pas.un.jwt");

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("token expire -> pas d'authentification")
    void expiredToken_noAuthentication() throws Exception {
        Date past = new Date(System.currentTimeMillis() - 60_000);
        request.addHeader("Authorization", "Bearer " + token(KEY, claims(42, "USER"), past));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("token signe avec un autre secret -> pas d'authentification")
    void wrongSignature_noAuthentication() throws Exception {
        Key otherKey = Keys.hmacShaKeyFor("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer " + token(otherKey, claims(42, "USER"), inOneHour()));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("token sans claim userId -> pas d'authentification")
    void missingUserId_noAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(KEY, claims(null, "USER"), inOneHour()));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("userId de type texte -> pas d'authentification")
    void nonNumericUserId_noAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer " + token(KEY, claims("42", "USER"), inOneHour()));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertChainContinued();
    }

    @Test
    @DisplayName("une authentification deja presente n'est pas ecrasee")
    void existingAuthentication_isKept() throws Exception {
        Authentication existing = new TestingAuthenticationToken("deja-la", "pwd", "ROLE_X");
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("Authorization", "Bearer " + token(KEY, claims(42, "USER"), inOneHour()));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        assertChainContinued();
    }

    @Test
    @DisplayName("secret qui n'est pas du Base64 valide -> IllegalArgumentException a la creation")
    void invalidBase64Secret_throws() {
        assertThatThrownBy(() -> new JwtAuthFilter("***pas-du-base64***"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
