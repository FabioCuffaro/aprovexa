package com.aprovexa.security;

import com.aprovexa.auth.model.Role;
import com.aprovexa.auth.repository.UserAccountRepository;
import com.aprovexa.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "aprovexa.security.jwt.issuer=aprovexa",
        "aprovexa.security.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "aprovexa.security.jwt.access-token-ttl=PT30M"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE request_comments, request_history, requests, users RESTART IDENTITY CASCADE");
    }

    @Test
    void registerHashesPasswordAndLoginReturnsUsableJwt() throws Exception {
        register("laura@example.com", "Laura");

        var user = userRepository.findByEmail("laura@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.USER);

        String token = login("laura@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("laura@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void incorrectPasswordReturnsNormalized401() throws Exception {
        register("laura@example.com", "Laura");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"laura@example.com",
                                  "password":"WrongPass123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void unauthenticatedRequestReturnsNormalized401() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void expiredJwtReturns401() throws Exception {
        register("laura@example.com", "Laura");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("aprovexa")
                .subject("laura@example.com")
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .claim("role", "USER")
                .claim("permissions", Role.USER.permissions().stream().map(Enum::name).toList())
                .build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void alteredJwtReturns401() throws Exception {
        register("laura@example.com", "Laura");
        String token = login("laura@example.com", PASSWORD);
        String alteredToken = alterSignature(token);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + alteredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void userCannotApproveButManagerCan() throws Exception {
        register("employee@example.com", "Employee");
        String employeeToken = login("employee@example.com", PASSWORD);
        Long requestId = createAndSubmitRequest(employeeToken);

        mockMvc.perform(post("/api/v1/requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        register("manager@example.com", "Manager");
        jdbcTemplate.update("UPDATE users SET role='MANAGER', updated_at=now() WHERE email=?", "manager@example.com");
        String managerToken = login("manager@example.com", PASSWORD);

        mockMvc.perform(post("/api/v1/requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String changedBy = jdbcTemplate.queryForObject(
                "SELECT changed_by FROM request_history WHERE request_id=? AND new_status='APPROVED'",
                String.class,
                requestId
        );
        assertThat(changedBy).isEqualTo("manager@example.com");
    }

    @Test
    void userCannotAccessAnotherUsersRequest() throws Exception {
        register("owner@example.com", "Owner");
        register("other@example.com", "Other");
        String ownerToken = login("owner@example.com", PASSWORD);
        String otherToken = login("other@example.com", PASSWORD);
        Long requestId = createRequest(ownerToken, "Owner laptop");

        mockMvc.perform(get("/api/v1/requests/{id}", requestId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void userListIsScopedToOwnershipWhileManagerSeesAll() throws Exception {
        register("owner@example.com", "Owner");
        register("other@example.com", "Other");
        register("manager@example.com", "Manager");
        String ownerToken = login("owner@example.com", PASSWORD);
        String otherToken = login("other@example.com", PASSWORD);

        createRequest(ownerToken, "Owner laptop");
        createRequest(otherToken, "Other laptop");

        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].requester").value("owner@example.com"));

        jdbcTemplate.update("UPDATE users SET role='MANAGER', updated_at=now() WHERE email=?", "manager@example.com");
        String managerToken = login("manager@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private void register(String email, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"%s",
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(displayName, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }

    private Long createAndSubmitRequest(String token) throws Exception {
        Long requestId = createRequest(token, "Development laptop");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", requestId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        return requestId;
    }

    private Long createRequest(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"PURCHASE",
                                  "title":"%s",
                                  "description":"Laptop required for authenticated security integration testing",
                                  "justification":"Security ownership validation"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String alterSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'a' ? 'b' : 'a') + parts[2].substring(1);
        return String.join(".", parts);
    }
}
