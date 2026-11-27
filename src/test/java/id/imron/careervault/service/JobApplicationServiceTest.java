package id.imron.careervault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.JobStatusHistory;
import id.imron.careervault.domain.WorkMode;
import id.imron.careervault.dto.JobApplicationForm;
import id.imron.careervault.dto.JobSearchFilter;
import id.imron.careervault.dto.JobStatusUpdateForm;
import id.imron.careervault.repository.JobStatusHistoryRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobApplicationService.class)
@ActiveProfiles("test")
class JobApplicationServiceTest {

    private static final String HEADER = "Company,Role,Status,Work Mode,Platform,Location,Salary Range,"
            + "Applied Date,Follow-up Date,Contact Person,Job URL,CV,Cover Letter,Notes,Created At,Updated At";

    @Autowired
    private JobApplicationService service;

    @Autowired
    private JobStatusHistoryRepository history;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createStoresTrimmedFieldsLinksAndInitialHistory() {
        CareerDocument cv = document("My CV", DocumentCategory.CV);
        CareerDocument letter = document("Letter", DocumentCategory.COVER_LETTER);
        JobApplicationForm form = form("  Acme ", JobStatus.APPLIED);
        form.setJobUrl(" https://example.com/job ");
        form.setLocation("  ");
        form.setAppliedDate(LocalDate.of(2026, 1, 2));
        form.setCvDocumentId(cv.getId());
        form.setCoverLetterDocumentId(letter.getId());

        Long id = service.create(form).getId();
        flushAndClear();

        JobApplication job = service.get(id);
        entityManager.clear();
        assertThat(job.getCompanyName()).isEqualTo("Acme");
        assertThat(job.getJobUrl()).isEqualTo("https://example.com/job");
        assertThat(job.getLocation()).isNull();
        assertThat(job.getAppliedDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        // Readable after the entity is detached, as in a view.
        assertThat(job.getCvDocument().getTitle()).isEqualTo("My CV");
        assertThat(job.getCoverLetterDocument().getTitle()).isEqualTo("Letter");
        assertHistory(id, tuple(null, JobStatus.APPLIED, null));
    }

    @Test
    void updateRecordsHistoryOnlyWhenTheStatusChanges() throws InterruptedException {
        Long id = service.create(form("Acme", JobStatus.APPLIED)).getId();
        Thread.sleep(5);

        JobApplicationForm edit = form("Acme Corp", JobStatus.APPLIED);
        service.update(id, edit);
        edit.setStatus(JobStatus.INTERVIEW);
        service.update(id, edit);
        flushAndClear();

        assertThat(service.get(id).getCompanyName()).isEqualTo("Acme Corp");
        assertHistory(id,
                tuple(JobStatus.APPLIED, JobStatus.INTERVIEW, null),
                tuple(null, JobStatus.APPLIED, null));
    }

    @Test
    void updateReplacesAndRemovesDocumentLinks() {
        JobApplicationForm form = form("Acme", JobStatus.APPLIED);
        form.setCvDocumentId(document("Old", DocumentCategory.CV).getId());
        Long id = service.create(form).getId();

        form.setCvDocumentId(document("New", DocumentCategory.CV).getId());
        service.update(id, form);
        flushAndClear();
        assertThat(service.get(id).getCvDocument().getTitle()).isEqualTo("New");

        form.setCvDocumentId(null);
        service.update(id, form);
        flushAndClear();
        assertThat(service.get(id).getCvDocument()).isNull();
    }

    @Test
    void unknownDocumentLinkNamesTheField() {
        JobApplicationForm form = form("Acme", JobStatus.APPLIED);
        form.setCoverLetterDocumentId(999L);

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOfSatisfying(JobApplicationService.InvalidDocumentLinkException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("coverLetterDocumentId"));
        assertThat(history.count()).isZero();
    }

    @Test
    void quickStatusUpdateRecordsTheNote() throws InterruptedException {
        Long id = service.create(form("Acme", JobStatus.APPLIED)).getId();
        Thread.sleep(5);

        service.updateStatus(id, statusForm(JobStatus.HR_SCREEN, "  Call booked  "));
        flushAndClear();

        assertThat(service.get(id).getStatus()).isEqualTo(JobStatus.HR_SCREEN);
        assertHistory(id,
                tuple(JobStatus.APPLIED, JobStatus.HR_SCREEN, "Call booked"),
                tuple(null, JobStatus.APPLIED, null));
    }

    @Test
    void sameStatusIsRecordedOnlyWithANote() throws InterruptedException {
        Long id = service.create(form("Acme", JobStatus.APPLIED)).getId();
        Thread.sleep(5);

        service.updateStatus(id, statusForm(JobStatus.APPLIED, " "));
        assertThat(history.count()).isEqualTo(1);

        service.updateStatus(id, statusForm(JobStatus.APPLIED, "Sent a reminder"));
        assertHistory(id,
                tuple(JobStatus.APPLIED, JobStatus.APPLIED, "Sent a reminder"),
                tuple(null, JobStatus.APPLIED, null));
    }

    @Test
    void missingApplicationIsNotFound() {
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.update(999L, form("Acme", JobStatus.APPLIED)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus(999L, statusForm(JobStatus.OFFER, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void searchFiltersAndPagesNewestFirst() throws InterruptedException {
        service.create(form("First", JobStatus.APPLIED));
        Thread.sleep(5);
        service.create(form("Second", JobStatus.OFFER));
        Thread.sleep(5);
        service.create(form("Third", JobStatus.APPLIED));
        JobSearchFilter filter = new JobSearchFilter();

        assertThat(service.search(filter, PageRequest.of(0, 2)).getContent())
                .extracting(JobApplication::getCompanyName).containsExactly("Third", "Second");

        filter.setStatus(JobStatus.APPLIED);
        filter.setKeyword("fir");
        assertThat(service.search(filter, PageRequest.of(0, 10)).getContent())
                .extracting(JobApplication::getCompanyName).containsExactly("First");
    }

    @Test
    void csvHasHeaderAndAllColumns() {
        JobApplicationForm form = form("Acme", JobStatus.TECHNICAL_TEST);
        form.setPlatform("LinkedIn");
        form.setAppliedDate(LocalDate.of(2026, 1, 2));
        form.setCvDocumentId(document("My CV", DocumentCategory.CV).getId());
        JobApplication job = service.create(form);
        flushAndClear();

        assertThat(csv(new JobSearchFilter())).isEqualTo("﻿" + HEADER + "\r\n"
                + "Acme,Developer,Technical Test,Remote,LinkedIn,,,2026-01-02,,,,My CV,,,"
                + job.getCreatedAt() + "," + job.getUpdatedAt() + "\r\n");
    }

    @Test
    void csvEscapesQuotesCommasNewlinesAndKeepsUnicode() {
        JobApplicationForm form = form("Acme, \"Inc\"", JobStatus.APPLIED);
        form.setLocation("Zürich 東京");
        form.setNotes("line one\r\nline two\nthree");
        service.create(form);

        assertThat(csv(new JobSearchFilter()))
                .contains("\r\n\"Acme, \"\"Inc\"\"\",Developer,")
                .contains(",Zürich 東京,")
                .contains(",\"line one\r\nline two\nthree\",");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+62 812", "-cmd", "@SUM(A1)"})
    void csvNeutralisesFormulas(String value) {
        service.create(form(value, JobStatus.APPLIED));

        assertThat(csv(new JobSearchFilter())).contains("\r\n'" + value + ",Developer,");
    }

    @Test
    void csvFollowsTheFilterAndHasOnlyAHeaderWhenNothingMatches() {
        service.create(form("Keep", JobStatus.OFFER));
        service.create(form("Skip", JobStatus.APPLIED));
        JobSearchFilter filter = new JobSearchFilter();

        filter.setStatus(JobStatus.OFFER);
        assertThat(csv(filter)).contains("Keep").doesNotContain("Skip");

        filter.setStatus(JobStatus.REJECTED);
        assertThat(csv(filter)).isEqualTo("﻿" + HEADER + "\r\n");
    }

    private String csv(JobSearchFilter filter) {
        return new String(service.exportCsv(filter), StandardCharsets.UTF_8);
    }

    private void assertHistory(Long jobId, org.assertj.core.groups.Tuple... newestFirst) {
        assertThat(history.findByJobApplicationIdOrderByChangedAtDesc(jobId))
                .extracting(JobStatusHistory::getFromStatus, JobStatusHistory::getToStatus, JobStatusHistory::getNote)
                .containsExactly(newestFirst);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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
        entityManager.persist(document);
        return document;
    }

    private static JobApplicationForm form(String company, JobStatus status) {
        JobApplicationForm form = new JobApplicationForm();
        form.setCompanyName(company);
        form.setRoleTitle("Developer");
        form.setWorkMode(WorkMode.REMOTE);
        form.setStatus(status);
        return form;
    }

    private static JobStatusUpdateForm statusForm(JobStatus status, String note) {
        JobStatusUpdateForm form = new JobStatusUpdateForm();
        form.setNewStatus(status);
        form.setNote(note);
        return form;
    }
}
