package id.imron.careervault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.repository.CareerDocumentRepository;
import id.imron.careervault.repository.JobApplicationRepository;
import id.imron.careervault.repository.JobStatusHistoryRepository;

/** Full-stack MockMvc integration tests covering security, PRG flows, file delivery, and template rendering. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:securitymvc;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndMvcIntegrationTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", storageRoot::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobApplicationRepository jobs;

    @Autowired
    private JobStatusHistoryRepository history;

    @Autowired
    private CareerDocumentRepository documents;

    @AfterEach
    void cleanUp() throws IOException {
        history.deleteAll();
        jobs.deleteAll();
        documents.deleteAll();
        try (Stream<Path> files = Files.list(storageRoot)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/jobs", "/documents", "/documents/1/download"})
    void unauthenticatedRequestsRedirectToLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser
    void postJobsValidatesErrorsAndPersistsValidSubmission() throws Exception {
        mvc.perform(post("/jobs").with(csrf())
                        .param("companyName", "   ")
                        .param("roleTitle", "Backend Engineer")
                        .param("workMode", "REMOTE"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/form"))
                .andExpect(model().attributeHasFieldErrors("form", "companyName", "status"));
        assertThat(jobs.count()).isZero();

        mvc.perform(post("/jobs").with(csrf())
                        .param("companyName", "Acme Corp")
                        .param("roleTitle", "Backend Engineer")
                        .param("status", "APPLIED")
                        .param("workMode", "REMOTE"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/jobs/*"))
                .andExpect(flash().attribute("successMessage", "Job application created."));

        JobApplication saved = jobs.findAll().getFirst();
        assertThat(saved.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(saved.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(history.findByJobApplicationIdOrderByChangedAtDesc(saved.getId())).hasSize(1);
    }

    @Test
    @WithMockUser
    void uploadPdfAndServeInlinePreviewAndAttachmentDownload() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\n%sample".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "resume-2026.pdf", "application/pdf", pdfBytes);

        mvc.perform(multipart("/documents/upload").file(file).with(csrf())
                        .param("title", "Backend Resume")
                        .param("category", "CV")
                        .param("isPrimary", "true"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attribute("successMessage", "Document uploaded."));

        CareerDocument stored = documents.findAll().getFirst();

        mvc.perform(get("/documents/{id}/preview", stored.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''resume-2026.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(content().bytes(pdfBytes));

        mvc.perform(get("/documents/{id}/download", stored.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''resume-2026.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    @WithMockUser
    void everyPageRendersWith200AgainstRealModel() throws Exception {
        MockMultipartFile cvFile = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/documents/upload").file(cvFile).with(csrf())
                        .param("title", "Primary CV")
                        .param("category", "CV")
                        .param("versionLabel", "v2")
                        .param("description", "Main backend CV")
                        .param("isPrimary", "true"))
                .andExpect(status().isFound());

        Long docId = documents.findAll().getFirst().getId();

        mvc.perform(post("/jobs").with(csrf())
                        .param("companyName", "Nusantara Tech")
                        .param("roleTitle", "Java Engineer")
                        .param("status", "APPLIED")
                        .param("workMode", "HYBRID")
                        .param("followUpDate", LocalDate.now().plusDays(2).toString())
                        .param("cvDocumentId", docId.toString()))
                .andExpect(status().isFound());

        Long jobId = jobs.findAll().getFirst().getId();

        mvc.perform(post("/jobs/{id}/status", jobId).with(csrf())
                        .param("newStatus", "INTERVIEW")
                        .param("note", "Technical interview scheduled"))
                .andExpect(status().isFound());

        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/index"))
                .andExpect(content().string(containsString("Primary CV")))
                .andExpect(content().string(containsString("Nusantara Tech")));

        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/list"))
                .andExpect(content().string(containsString("Nusantara Tech")));

        mvc.perform(get("/jobs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/form"));

        mvc.perform(get("/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/detail"))
                .andExpect(content().string(containsString("Technical interview scheduled")))
                .andExpect(content().string(containsString("Primary CV")));

        mvc.perform(get("/jobs/{id}/edit", jobId))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs/form"));

        mvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/list"))
                .andExpect(content().string(containsString("Primary CV")));

        mvc.perform(get("/documents/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/form"));

        mvc.perform(get("/documents/{id}/edit", docId))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/form"));
    }
}
