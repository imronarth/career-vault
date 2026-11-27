package id.imron.careervault.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.repository.CareerDocumentRepository;
import id.imron.careervault.repository.JobApplicationRepository;
import id.imron.careervault.repository.JobStatusHistoryRepository;

// No test transaction: requests must behave as in production. Its own database keeps committed rows apart.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:jobweb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobApplicationRepository jobs;

    @Autowired
    private JobStatusHistoryRepository history;

    @Autowired
    private CareerDocumentRepository documents;

    @AfterEach
    void cleanUp() {
        history.deleteAll();
        jobs.deleteAll();
        documents.deleteAll();
    }

    @Test
    void emptyListAndNewFormRender() throws Exception {
        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/list"))
                .andExpect(model().attributeExists("page", "filter", "statuses", "workModes"));
        mvc.perform(get("/jobs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/form"))
                .andExpect(model().attributeExists("form", "cvDocuments", "coverLetterDocuments", "statuses",
                        "workModes"))
                .andExpect(content().string(containsString("New Job Application")));
    }

    @Test
    void createRedirectsToTheDetailPageWithAMessage() throws Exception {
        CareerDocument cv = document("My CV", DocumentCategory.CV);

        String location = mvc.perform(valid(post("/jobs")).param("cvDocumentId", cv.getId().toString()))
                .andExpect(flash().attribute("successMessage", "Job application created."))
                .andReturn().getResponse().getRedirectedUrl();

        JobApplication job = jobs.findAll().getFirst();
        assertThat(location).isEqualTo("/jobs/" + job.getId());
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/detail"))
                .andExpect(model().attributeExists("job", "history", "statusForm", "statuses"))
                .andExpect(content().string(containsString("My CV")));
    }

    @Test
    void invalidCreateShowsTheFormAgainWithFriendlyMessages() throws Exception {
        document("My CV", DocumentCategory.CV);

        mvc.perform(post("/jobs").with(csrf()).param("appliedDate", "tomorrow"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "companyName", "roleTitle", "status", "workMode",
                        "appliedDate"))
                .andExpect(model().attribute("cvDocuments", hasSize(1)))
                .andExpect(model().attributeExists("coverLetterDocuments", "statuses", "workModes"))
                .andExpect(content().string(containsString("Enter a valid date.")));
        assertThat(jobs.count()).isZero();
    }

    @Test
    void unknownLinkedDocumentIsAFieldError() throws Exception {
        mvc.perform(valid(post("/jobs")).param("cvDocumentId", "999"))
                .andExpect(view().name("jobs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "cvDocumentId"));
        assertThat(jobs.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @Test
    void editFormIsPrefilledAndUpdateRedirects() throws Exception {
        Long id = createJob();

        mvc.perform(get("/jobs/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobId", id))
                .andExpect(content().string(containsString("value=\"Acme\"")));
        mvc.perform(valid(post("/jobs/{id}", id)).param("platform", "LinkedIn"))
                .andExpect(redirectedUrl("/jobs/" + id))
                .andExpect(flash().attribute("successMessage", "Job application updated."));
        assertThat(jobs.findById(id).orElseThrow().getPlatform()).isEqualTo("LinkedIn");

        mvc.perform(post("/jobs/{id}", id).with(csrf()).param("companyName", ""))
                .andExpect(view().name("jobs/form"))
                .andExpect(model().attribute("jobId", id))
                .andExpect(model().attributeHasFieldErrors("form", "companyName"));
    }

    @Test
    void recategorisedLinkedDocumentStaysSelectable() throws Exception {
        CareerDocument cv = document("Moved", DocumentCategory.CV);
        mvc.perform(valid(post("/jobs")).param("cvDocumentId", cv.getId().toString()));
        cv = documents.findById(cv.getId()).orElseThrow();
        cv.setCategory(DocumentCategory.OTHER);
        documents.save(cv);

        mvc.perform(get("/jobs/{id}/edit", jobs.findAll().getFirst().getId()))
                .andExpect(model().attribute("cvDocuments", hasSize(1)));
    }

    @Test
    void quickStatusUpdateRedirectsAndRecordsTheNote() throws Exception {
        Long id = createJob();

        mvc.perform(post("/jobs/{id}/status", id).with(csrf())
                        .param("newStatus", "INTERVIEW").param("note", "Friday"))
                .andExpect(redirectedUrl("/jobs/" + id))
                .andExpect(flash().attribute("successMessage", "Status updated."));

        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.INTERVIEW);
        mvc.perform(get("/jobs/{id}", id)).andExpect(content().string(containsString("Friday")));
    }

    @Test
    void quickStatusUpdateWithoutAStatusShowsTheDetailPageAgain() throws Exception {
        Long id = createJob();

        mvc.perform(post("/jobs/{id}/status", id).with(csrf()).param("newStatus", "").param("note", "Keep me"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/detail"))
                .andExpect(model().attributeHasFieldErrors("statusForm", "newStatus"))
                .andExpect(model().attributeExists("job", "history", "statuses"))
                .andExpect(content().string(containsString("Keep me")));
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.APPLIED);
    }

    @Test
    void deleteRemovesTheApplicationAndItsHistory() throws Exception {
        Long id = createJob();

        mvc.perform(post("/jobs/{id}/delete", id).with(csrf()))
                .andExpect(redirectedUrl("/jobs"))
                .andExpect(flash().attribute("successMessage", "Job application deleted."));

        assertThat(jobs.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "5"})
    void listPagesAndIgnoresClientSortAndSize(String page) throws Exception {
        createJob();

        mvc.perform(get("/jobs").param("page", page).param("sort", "nonsense").param("size", "100000")
                        .param("status", "APPLIED").param("keyword", "acm"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/list"));
    }

    @Test
    void exportIsAFilteredCsvDownload() throws Exception {
        createJob();

        mvc.perform(get("/jobs/export.csv").param("keyword", "").param("status", ""))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"job-applications-\\d{4}-\\d{2}-\\d{2}\\.csv\"")))
                .andExpect(content().string(containsString("Acme,Developer")));
        mvc.perform(get("/jobs/export.csv").param("status", "OFFER"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Acme"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jobs", "/jobs/1", "/jobs/1/status", "/jobs/1/delete"})
    void writesNeedACsrfToken(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isForbidden());
    }

    private Long createJob() throws Exception {
        mvc.perform(valid(post("/jobs"))).andExpect(status().isFound());
        return jobs.findAll().getFirst().getId();
    }

    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request) {
        return request.with(csrf())
                .param("companyName", "Acme")
                .param("roleTitle", "Developer")
                .param("status", "APPLIED")
                .param("workMode", "REMOTE");
    }

    private CareerDocument document(String title, DocumentCategory category) {
        CareerDocument document = new CareerDocument();
        document.setTitle(title);
        document.setCategory(category);
        document.setOriginalFilename("file.pdf");
        document.setStoredFilename("stored-" + title);
        document.setContentType("application/pdf");
        document.setFileSizeBytes(1);
        document.setSha256Checksum("a".repeat(64));
        return documents.save(document);
    }
}
