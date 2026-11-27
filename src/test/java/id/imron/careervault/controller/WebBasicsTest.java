package id.imron.careervault.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.dto.DashboardSummaryDto;
import id.imron.careervault.repository.UserAccountRepository;
import id.imron.careervault.service.DashboardService;

// Its own database: the saved account must not leak into tests that share the default one.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:webbasics;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser("owner")
class WebBasicsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository users;

    @MockitoBean
    private DashboardService dashboard;

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    @Test
    @org.springframework.security.test.context.support.WithAnonymousUser
    void loginPageRendersForAnonymousUsers() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/login").param("error", "")).andExpect(content().string(containsString("Invalid username")));
        mvc.perform(get("/login").param("logout", "")).andExpect(content().string(containsString("signed out")));
    }

    @Test
    void rootRedirectsToTheDashboard() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void dashboardShowsTheSummaryAndTheAccountName() throws Exception {
        UserAccount account = new UserAccount();
        account.setUsername("owner");
        account.setPasswordHash("hash");
        account.setFullName("Vault Owner");
        account.setRole("ADMIN");
        users.save(account);
        DashboardSummaryDto summary = emptySummary();
        when(dashboard.getSummary()).thenReturn(summary);

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/index"))
                .andExpect(model().attribute("summary", summary))
                .andExpect(model().attribute("currentUserFullName", "Vault Owner"))
                .andExpect(content().string(containsString("Vault Owner")));
    }

    @Test
    void usernameIsShownWhenThereIsNoAccountRow() throws Exception {
        when(dashboard.getSummary()).thenReturn(emptySummary());

        mvc.perform(get("/dashboard")).andExpect(model().attribute("currentUserFullName", "owner"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jobs/999", "/jobs/999/edit", "/documents/999/edit", "/documents/999/download",
            "/documents/999/preview", "/no/such/page"})
    void missingThingsGive404(String path) throws Exception {
        expectErrorPage(mvc.perform(get(path)), 404, "does not exist");
    }

    @Test
    void oversizeUploadGives413() throws Exception {
        when(dashboard.getSummary()).thenThrow(new MaxUploadSizeExceededException(1));

        expectErrorPage(mvc.perform(get("/dashboard")), 413, "The upload is too large.");
    }

    @Test
    void unexpectedFailureGives500WithoutDetails() throws Exception {
        when(dashboard.getSummary()).thenThrow(new IllegalStateException("secret internal detail"));

        expectErrorPage(mvc.perform(get("/dashboard")), 500, "An unexpected error occurred.")
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalState"))));
    }

    @Test
    void requestMistakesKeepTheirClientErrorStatus() throws Exception {
        expectErrorPage(mvc.perform(get("/jobs/abc")), 400, "could not be processed");
        expectErrorPage(mvc.perform(get("/jobs").param("status", "NOPE")), 400, "could not be processed");
        expectErrorPage(mvc.perform(post("/dashboard").with(csrf())), 405, "could not be processed");
    }

    private static ResultActions expectErrorPage(ResultActions result, int code, String message) throws Exception {
        return result.andExpect(status().is(code))
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("status", code))
                .andExpect(content().string(containsString(message)));
    }

    private static DashboardSummaryDto emptySummary() {
        return new DashboardSummaryDto(0, 0, 0, Map.of(), List.of(), List.of(), List.of(), 0, "0 B", Map.of(), null);
    }
}
