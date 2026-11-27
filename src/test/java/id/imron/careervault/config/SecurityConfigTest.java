package id.imron.careervault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.repository.UserAccountRepository;

// Its own database: the seeded admin must not leak into tests that share the default one.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "APP_ADMIN_USERNAME=owner",
        "APP_ADMIN_PASSWORD=test-only-password"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/", "/jobs", "/jobs/export.csv", "/documents", "/documents/1/download",
            "/documents/1/preview", "/actuator", "/actuator/env", "/actuator/health/db", "/error", "/login/other"})
    void anonymousRequestRedirectsToLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/vendor/tabler/tabler.min.css", "/css/app.css", "/js/app.js", "/actuator/health"})
    void publicPathsNeedNoLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void loginPageIsNotRedirected() throws Exception {
        // The view itself comes with AuthController; here only the access rule matters.
        int status = mvc.perform(get("/login")).andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(301, 302, 401, 403);
    }

    @Test
    void seededAdminCanSignInAndSignOut() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
                .perform(formLogin("/login").user("owner").password("test-only-password"))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(authenticated().withUsername("owner").withRoles("ADMIN"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "nobody"})
    void badCredentialsAreRejected(String username) throws Exception {
        mvc.perform(formLogin("/login").user(username).password("wrong"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/logout", "/jobs"})
    void postWithoutCsrfTokenIsForbidden(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isForbidden());
    }

    @Test
    void framingIsLimitedToTheSameOrigin() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void storesOnlyABcryptHash() {
        UserAccount admin = users.findByUsername("owner").orElseThrow();

        assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain("test-only-password");
        assertThat(passwordEncoder.matches("test-only-password", admin.getPasswordHash())).isTrue();
    }
}
