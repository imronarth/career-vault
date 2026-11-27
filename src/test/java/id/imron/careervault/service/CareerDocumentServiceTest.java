package id.imron.careervault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.dto.DocumentEditForm;
import id.imron.careervault.dto.DocumentMetadataForm;
import id.imron.careervault.dto.DocumentUploadForm;
import id.imron.careervault.repository.CareerDocumentRepository;

// No test-managed transaction: file clean-up depends on real commits and rollbacks.
@DataJpaTest(properties =
        "spring.datasource.url=jdbc:h2:mem:documents;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CareerDocumentService.class, LocalFileStorageService.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CareerDocumentServiceTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", storageRoot::toString);
    }

    @Autowired
    private CareerDocumentService service;

    @Autowired
    private CareerDocumentRepository documents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() throws IOException {
        documents.deleteAll();
        for (String file : storedFiles()) {
            Files.delete(storageRoot.resolve(file));
        }
    }

    @Test
    void uploadStoresFileAndTrimmedMetadata() {
        DocumentUploadForm form = uploadForm("  My CV  ", DocumentCategory.CV, false);
        form.setVersionLabel(" v1 ");
        form.setTags("   ");

        CareerDocument saved = service.upload(form);

        CareerDocument loaded = service.get(saved.getId());
        assertThat(loaded.getTitle()).isEqualTo("My CV");
        assertThat(loaded.getVersionLabel()).isEqualTo("v1");
        assertThat(loaded.getTags()).isNull();
        assertThat(loaded.getOriginalFilename()).isEqualTo("cv.pdf");
        assertThat(loaded.getContentType()).isEqualTo("application/pdf");
        assertThat(loaded.getFileSizeBytes()).isEqualTo(5);
        assertThat(loaded.getSha256Checksum()).hasSize(64);
        assertThat(storedFiles()).containsExactly(loaded.getStoredFilename());
    }

    @Test
    void newPrimaryReplacesTheOldOneOnlyInItsCategory() {
        Long first = upload(DocumentCategory.CV, true);
        Long letter = upload(DocumentCategory.COVER_LETTER, true);

        Long second = upload(DocumentCategory.CV, true);

        assertThat(isPrimary(first)).isFalse();
        assertThat(isPrimary(second)).isTrue();
        assertThat(isPrimary(letter)).isTrue();

        Long plain = upload(DocumentCategory.CV, false);
        assertThat(isPrimary(second)).isTrue();
        assertThat(isPrimary(plain)).isFalse();
    }

    @Test
    void updateSwitchesPrimaryAndKeepsItWhenSavedAgain() {
        Long first = upload(DocumentCategory.CV, true);
        Long second = upload(DocumentCategory.CV, false);

        service.update(second, editForm(DocumentCategory.CV, true));
        service.update(second, editForm(DocumentCategory.CV, true));

        assertThat(isPrimary(first)).isFalse();
        assertThat(isPrimary(second)).isTrue();
    }

    @Test
    void movingAPrimaryToAnotherCategoryUnsetsThatCategorysPrimary() {
        Long cv = upload(DocumentCategory.CV, true);
        Long other = upload(DocumentCategory.OTHER, true);

        service.update(other, editForm(DocumentCategory.CV, true));

        assertThat(isPrimary(cv)).isFalse();
        assertThat(isPrimary(other)).isTrue();
    }

    @Test
    void updateWithoutAFileKeepsTheStoredFile() {
        Long id = upload(DocumentCategory.CV, false);
        String stored = service.get(id).getStoredFilename();
        DocumentEditForm form = editForm(DocumentCategory.CV, false);
        // What a browser sends for an untouched file input.
        form.setFile(new MockMultipartFile("file", "", "application/octet-stream", new byte[0]));

        service.update(id, form);

        assertThat(service.get(id).getTitle()).isEqualTo("Edited");
        assertThat(storedFiles()).containsExactly(stored);
    }

    @Test
    void replacementDeletesTheOldFileOnlyAfterCommit() {
        Long id = upload(DocumentCategory.CV, false);
        String old = service.get(id).getStoredFilename();
        DocumentEditForm form = editForm(DocumentCategory.CV, false);
        form.setFile(file("new.txt", "text/plain", "newer"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.update(id, form);
            assertThat(storedFiles()).hasSize(2).contains(old);
        });

        CareerDocument updated = service.get(id);
        assertThat(updated.getOriginalFilename()).isEqualTo("new.txt");
        assertThat(updated.getContentType()).isEqualTo("text/plain");
        assertThat(storedFiles()).containsExactly(updated.getStoredFilename()).doesNotContain(old);
    }

    @Test
    void rolledBackReplacementKeepsTheOldFileAndRemovesTheNewOne() {
        Long id = upload(DocumentCategory.CV, false);
        String old = service.get(id).getStoredFilename();
        DocumentEditForm form = editForm(DocumentCategory.CV, false);
        form.setFile(file("new.pdf", "application/pdf", "newer"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.update(id, form);
            status.setRollbackOnly();
        });

        assertThat(service.get(id).getStoredFilename()).isEqualTo(old);
        assertThat(storedFiles()).containsExactly(old);
    }

    @Test
    void failedDatabaseSaveRemovesTheUploadedFile() {
        DocumentUploadForm form = uploadForm("x".repeat(201), DocumentCategory.CV, false);

        assertThatThrownBy(() -> service.upload(form)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(documents.count()).isZero();
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void failedDatabaseUpdateKeepsTheOldFileAndPrimary() {
        Long primary = upload(DocumentCategory.CV, true);
        Long id = upload(DocumentCategory.CV, false);
        List<String> before = storedFiles();
        DocumentEditForm form = editForm(DocumentCategory.CV, true);
        form.setTitle("x".repeat(201));
        form.setFile(file("new.pdf", "application/pdf", "newer"));

        assertThatThrownBy(() -> service.update(id, form)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(storedFiles()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(isPrimary(primary)).isTrue();
    }

    @Test
    void rejectedReplacementChangesNothing() {
        Long id = upload(DocumentCategory.CV, false);
        List<String> before = storedFiles();
        DocumentEditForm form = editForm(DocumentCategory.CV, false);
        form.setFile(file("empty.pdf", "application/pdf", ""));

        assertThatThrownBy(() -> service.update(id, form))
                .isInstanceOf(LocalFileStorageService.InvalidFileException.class);

        assertThat(service.get(id).getTitle()).isEqualTo("Title");
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void deleteRemovesRowAndFile() {
        Long id = upload(DocumentCategory.CV, false);
        Long kept = upload(DocumentCategory.CV, false);

        service.delete(id);

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(EntityNotFoundException.class);
        assertThat(storedFiles()).containsExactly(service.get(kept).getStoredFilename());
    }

    @Test
    void rolledBackDeleteKeepsTheFile() {
        Long id = upload(DocumentCategory.CV, false);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.delete(id);
            status.setRollbackOnly();
        });

        assertThat(storedFiles()).containsExactly(service.get(id).getStoredFilename());
    }

    @Test
    void downloadReturnsOriginalNameTypeAndContent() throws IOException {
        Long id = upload(DocumentCategory.CV, false);

        CareerDocumentService.DocumentDownload download = service.download(id);

        assertThat(download.originalFilename()).isEqualTo("cv.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.resource().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void missingDocumentOrFileIsNotFound() throws IOException {
        assertThatThrownBy(() -> service.download(999L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(EntityNotFoundException.class);

        Long id = upload(DocumentCategory.CV, false);
        Files.delete(storageRoot.resolve(service.get(id).getStoredFilename()));
        assertThatThrownBy(() -> service.download(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void searchFiltersAndPagesNewestFirst() throws InterruptedException {
        service.upload(uploadForm("Java resume", DocumentCategory.CV, false));
        Thread.sleep(5);
        service.upload(uploadForm("Java certificate", DocumentCategory.CERTIFICATE, false));
        Thread.sleep(5);
        service.upload(uploadForm("Other resume", DocumentCategory.CV, false));

        var page = service.search(null, null, PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(CareerDocument::getTitle)
                .containsExactly("Other resume", "Java certificate");
        assertThat(service.search("java", DocumentCategory.CV, PageRequest.of(0, 10)).getContent())
                .extracting(CareerDocument::getTitle).containsExactly("Java resume");
    }

    private Long upload(DocumentCategory category, boolean primary) {
        return service.upload(uploadForm("Title", category, primary)).getId();
    }

    private boolean isPrimary(Long id) {
        return service.get(id).isPrimary();
    }

    private static DocumentUploadForm uploadForm(String title, DocumentCategory category, boolean primary) {
        DocumentUploadForm form = new DocumentUploadForm();
        fill(form, title, category, primary);
        form.setFile(file("cv.pdf", "application/pdf", "hello"));
        return form;
    }

    private static DocumentEditForm editForm(DocumentCategory category, boolean primary) {
        DocumentEditForm form = new DocumentEditForm();
        fill(form, "Edited", category, primary);
        return form;
    }

    private static void fill(DocumentMetadataForm form, String title, DocumentCategory category, boolean primary) {
        form.setTitle(title);
        form.setCategory(category);
        form.setIsPrimary(primary);
    }

    private static MockMultipartFile file(String name, String type, String content) {
        return new MockMultipartFile("file", name, type, content.getBytes());
    }

    private static List<String> storedFiles() {
        try (Stream<Path> files = Files.list(storageRoot)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
