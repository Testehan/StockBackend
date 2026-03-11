package com.testehan.finana.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryParamTokenAuthFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    private QueryParamTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new QueryParamTokenAuthFilter(jwtDecoder);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonSsePath_skipsFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/stocks/valuation/dcf/AAPL");
        request.setServletPath("/stocks/valuation/dcf/AAPL");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void missingToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = buildSseRequest("/stocks/questions/answer-stream", null, "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void blankToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = buildSseRequest("/stocks/questions/answer-stream", "   ", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void invalidJwt_returns401() throws ServletException, IOException {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        MockHttpServletRequest request = buildSseRequest("/stocks/reporting/checklist-stream/AAPL", "bad-token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nullEmailClaim_returns401() throws ServletException, IOException {
        Jwt jwt = buildJwt(null);
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        MockHttpServletRequest request = buildSseRequest("/stocks/valuation/alerts-stream/user123", "token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void emailMismatch_returns401() throws ServletException, IOException {
        Jwt jwt = buildJwt("other@example.com");
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        MockHttpServletRequest request = buildSseRequest("/stocks/questions/answer-stream", "token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void happyPath_checklistStream_setsAuthAndContinues() throws ServletException, IOException {
        Jwt jwt = buildJwt("user@example.com");
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        MockHttpServletRequest request = buildSseRequest("/stocks/reporting/checklist-stream/AAPL", "valid-token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user@example.com");
    }

    @Test
    void happyPath_alertsStream_setsAuthAndContinues() throws ServletException, IOException {
        Jwt jwt = buildJwt("user@example.com");
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        MockHttpServletRequest request = buildSseRequest("/stocks/valuation/alerts-stream/user123", "valid-token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user@example.com");
    }

    @Test
    void happyPath_answerStream_setsAuthAndContinues() throws ServletException, IOException {
        Jwt jwt = buildJwt("user@example.com");
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        MockHttpServletRequest request = buildSseRequest("/stocks/questions/answer-stream", "valid-token", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user@example.com");
    }

    private MockHttpServletRequest buildSseRequest(String path, String token, String userEmail) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (token != null) {
            request.addParameter("token", token);
        }
        if (userEmail != null) {
            request.addParameter("userEmail", userEmail);
        }
        return request;
    }

    private Jwt buildJwt(String email) {
        Map<String, Object> claims = email != null ? Map.of("email", email) : Map.of();
        return Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claims(c -> c.putAll(claims))
            .build();
    }
}
