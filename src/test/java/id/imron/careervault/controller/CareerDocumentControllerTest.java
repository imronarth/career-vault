package id.imron.careervault.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.repository.CareerDocumentRepository;

// No test transaction: file clean-up depends on real commits. Its own database keeps committed rows apart.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:documentweb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class CareerDocumentControllerTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", storageRoot::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CareerDocumentRepository documents;

    @AfterEach
    void cleanUp() throws IOException {
        documents.deleteAll();
        try (Stream<Path> files = Files.list(storageRoot)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    @Test
    void emptyListAndUploadFormRender() throws Exception {
        mvc.perform(get("/documents").param("category", "").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/list"))
                .andExpect(model().attributeExists("page", "categories", "categoryCounts"));
        mvc.perform(get("/documents/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/form"))
                .andExpect(content().string(containsString("multipart/form-data")));
    }

    @Test
    void listShowsMatchingDocumentsAndCountsAllCategories() throws Exception {
        upload("Java CV", "cv.pdf", "application/pdf", "CV");
        upload("Diploma", "diploma.pdf", "application/pdf", "IJAZAH");

        mvc.perform(get("/documents").param("category", "CV").param("keyword", "java")
                        .param("sort", "nonsense").param("page", "-3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Java CV")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Diploma"))))
                .andExpect(model().attribute("categoryCounts",
                        Map.of(DocumentCategory.CV, 1L, DocumentCategory.IJAZAH, 1L)))
                .andExpect(model().attribute("category", DocumentCategory.CV))
                .andExpect(model().attribute("keyword", "java"));
    }

    @Test
    void invalidUploadShowsTheFormAgain() throws Exception {
        mvc.perform(multipart("/documents/upload").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("documents/form"))
                .andExpect(model().attributeHasFieldErrors("form", "file", "title", "category"));
        assertThat(documents.count()).isZero();
    }

    @Test
    void rejectedFileTypeIsAFieldError() throws Exception {
        mvc.perform(multipart("/documents/upload")
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {1}))
                        .with(csrf()).param("title", "Tool").param("category", "OTHER"))
                .andExpect(view().name("documents/form"))
                .andExpect(model().attributeHasFieldErrors("form", "file"))
                .andExpect(content().string(containsString("This file type is not allowed.")));
        assertThat(documents.count()).isZero();
    }

    @Test
    void editFormIsPrefilledAndUpdateReplacesTheMetadata() throws Exception {
        Long id = upload("Old title", "cv.pdf", "application/pdf", "CV");

        mvc.perform(get("/documents/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(model().attribute("documentId", id))
                .andExpect(content().string(containsString("value=\"Old title\"")));
        mvc.perform(multipart("/documents/{id}/edit", id).with(csrf())
                        .param("title", "New title").param("category", "CV").param("isPrimary", "true"))
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attribute("successMessage", "Document updated."));

        CareerDocument updated = documents.findById(id).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.isPrimary()).isTrue();
    }

    @Test
    void invalidReplacementKeepsTheDocument() throws Exception {
        Long id = upload("Title", "cv.pdf", "application/pdf", "CV");

        mvc.perform(multipart("/documents/{id}/edit", id)
                        .file(new MockMultipartFile("file", "run.exe", null, new byte[] {1}))
                        .with(csrf()).param("title", "Changed").param("category", "CV"))
                .andExpect(view().name("documents/form"))
                .andExpect(model().attribute("documentId", id))
                .andExpect(model().attributeHasFieldErrors("form", "file"));
        assertThat(documents.findById(id).orElseThrow().getTitle()).isEqualTo("Title");
    }

    @Test
    void downloadIsAnAttachmentWithAnEncodedName() throws Exception {
        Long id = upload("CV", "Résumé \"final\"; v2.pdf", "application/pdf", "CV");

        mvc.perform(get("/documents/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''R%C3%A9sum%C3%A9%20%22final%22%3B%20v2.pdf"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().longValue("Content-Length", 5))
                .andExpect(content().string("hello"));
    }

    @ParameterizedTest
    @CsvSource({"a.pdf, application/pdf", "a.png, image/png", "a.jpg, image/jpeg", "a.webp, image/webp"})
    void previewShowsPdfsAndImagesInline(String name, String type) throws Exception {
        Long id = upload("Preview", name, type, "OTHER");

        mvc.perform(get("/documents/{id}/preview", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''" + name))
                .andExpect(header().string("Content-Type", type))
                .andExpect(header().stringValues("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(content().string("hello"));
    }

    @ParameterizedTest
    @CsvSource({"a.txt, text/plain", "a.md, text/markdown", "a.zip, application/zip", "a.doc, application/msword"})
    void previewOfOtherTypesRedirectsToDownload(String name, String type) throws Exception {
        Long id = upload("Other", name, type, "OTHER");

        mvc.perform(get("/documents/{id}/preview", id))
                .andExpect(redirectedUrl("/documents/" + id + "/download"))
                .andExpect(content().string(""));
    }

    @Test
    void deleteRemovesTheDocumentAndItsFile() throws Exception {
        Long id = upload("Title", "cv.pdf", "application/pdf", "CV");

        mvc.perform(post("/documents/{id}/delete", id).with(csrf()))
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attribute("successMessage", "Document deleted."));

        assertThat(documents.count()).isZero();
        assertThat(storageRoot).isEmptyDirectory();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/documents/upload", "/documents/1/edit", "/documents/1/delete"})
    void writesNeedACsrfToken(String path) throws Exception {
        mvc.perform(multipart(path)).andExpect(status().isForbidden());
    }

    private Long upload(String title, String filename, String type, String category) throws Exception {
        mvc.perform(multipart("/documents/upload")
                        .file(new MockMultipartFile("file", filename, type, "hello".getBytes()))
                        .with(csrf()).param("title", title).param("category", category))
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attribute("successMessage", "Document uploaded."));
        return documents.findAll().stream().map(CareerDocument::getId).max(Long::compare).orElseThrow();
    }
}
