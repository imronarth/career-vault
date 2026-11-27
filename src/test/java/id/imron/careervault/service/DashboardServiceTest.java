package id.imron.careervault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;
import id.imron.careervault.dto.DashboardSummaryDto;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DashboardService.class)
@ActiveProfiles("test")
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Autowired
    private DashboardService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void emptyVaultGivesZerosAndEmptyLists() {
        DashboardSummaryDto summary = service.getSummary();

        assertThat(summary.totalJobs()).isZero();
        assertThat(summary.activePipeline()).isZero();
        assertThat(summary.offers()).isZero();
        assertThat(summary.statusBreakdown()).containsOnlyKeys(JobStatus.values()).containsValue(0L);
        assertThat(summary.upcomingFollowUps()).isEmpty();
        assertThat(summary.recentApplications()).isEmpty();
        assertThat(summary.recentDocuments()).isEmpty();
        assertThat(summary.totalFiles()).isZero();
        assertThat(summary.totalStorageFormatted()).isEqualTo("0 B");
        assertThat(summary.documentsPerCategory()).isEmpty();
        assertThat(summary.primaryCv()).isNull();
    }

    @Test
    void countsTheTotalActivePipelineAndOffers() {
        for (JobStatus status : JobStatus.values()) {
            job(status.name(), status, null);
        }
        job("second offer", JobStatus.OFFER, null);

        DashboardSummaryDto summary = service.summaryFor(TODAY);

        assertThat(summary.totalJobs()).isEqualTo(9);
        // Everything except the two offers, rejected and withdrawn.
        assertThat(summary.activePipeline()).isEqualTo(5);
        assertThat(summary.offers()).isEqualTo(2);
        assertThat(summary.statusBreakdown().keySet()).containsExactly(JobStatus.values());
        assertThat(summary.statusBreakdown()).containsEntry(JobStatus.OFFER, 2L).containsEntry(JobStatus.SAVED, 1L);
    }

    @Test
    void followUpsCoverTheNext14DaysForActiveJobsInDateOrder() {
        job("last day", JobStatus.APPLIED, TODAY.plusDays(14));
        job("today", JobStatus.SAVED, TODAY);
        job("soon", JobStatus.INTERVIEW, TODAY.plusDays(3));
        job("yesterday", JobStatus.APPLIED, TODAY.minusDays(1));
        job("too late", JobStatus.APPLIED, TODAY.plusDays(15));
        job("no date", JobStatus.APPLIED, null);
        job("offer", JobStatus.OFFER, TODAY.plusDays(1));
        job("rejected", JobStatus.REJECTED, TODAY.plusDays(1));
        job("withdrawn", JobStatus.WITHDRAWN, TODAY.plusDays(1));

        assertThat(service.summaryFor(TODAY).upcomingFollowUps())
                .extracting(JobApplication::getCompanyName)
                .containsExactly("today", "soon", "last day");
    }

    @Test
    void listsTheFiveNewestJobsAndSixNewestDocuments() throws InterruptedException {
        for (int i = 1; i <= 7; i++) {
            job("job " + i, JobStatus.APPLIED, null);
            document("doc " + i, DocumentCategory.OTHER, false, 1);
            Thread.sleep(2);
        }

        DashboardSummaryDto summary = service.summaryFor(TODAY);

        assertThat(summary.recentApplications()).extracting(JobApplication::getCompanyName)
                .containsExactly("job 7", "job 6", "job 5", "job 4", "job 3");
        assertThat(summary.recentDocuments()).extracting(CareerDocument::getTitle)
                .containsExactly("doc 7", "doc 6", "doc 5", "doc 4", "doc 3", "doc 2");
    }

    @Test
    void summarisesDocumentsAndFindsThePrimaryCv() {
        document("old cv", DocumentCategory.CV, false, 1024);
        document("main cv", DocumentCategory.CV, true, 1024 * 1024);
        document("primary letter", DocumentCategory.COVER_LETTER, true, 512 * 1024);

        DashboardSummaryDto summary = service.summaryFor(TODAY);

        assertThat(summary.totalFiles()).isEqualTo(3);
        assertThat(summary.totalStorageFormatted()).isEqualTo("1.5 MB");
        assertThat(summary.documentsPerCategory())
                .containsExactly(entry(DocumentCategory.CV, 2L), entry(DocumentCategory.COVER_LETTER, 1L));
        assertThat(summary.primaryCv().getTitle()).isEqualTo("main cv");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0 B", "1023, 1023 B", "1024, 1.0 KB", "5000, 4.9 KB", "1048575, 1.0 MB", "1048576, 1.0 MB",
            "2500000, 2.4 MB", "3221225472, 3.0 GB", "1099511627776, 1.0 TB"})
    void formatsSizes(long bytes, String expected) {
        assertThat(DashboardService.formatSize(bytes)).isEqualTo(expected);
    }

    private void job(String company, JobStatus status, LocalDate followUp) {
        JobApplication job = new JobApplication();
        job.setCompanyName(company);
        job.setRoleTitle("Developer");
        job.setWorkMode(WorkMode.REMOTE);
        job.setStatus(status);
        job.setFollowUpDate(followUp);
        entityManager.persist(job);
    }

    private void document(String title, DocumentCategory category, boolean primary, long size) {
        CareerDocument document = new CareerDocument();
        document.setTitle(title);
        document.setCategory(category);
        document.setPrimary(primary);
        document.setOriginalFilename("file.pdf");
        document.setStoredFilename("stored-" + title);
        document.setContentType("application/pdf");
        document.setFileSizeBytes(size);
        document.setSha256Checksum("a".repeat(64));
        entityManager.persist(document);
    }
}
