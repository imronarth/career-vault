package id.imron.careervault.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.JobStatusHistory;
import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.domain.WorkMode;
import id.imron.careervault.dto.JobSearchFilter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryTest {

    @Autowired
    private JobApplicationRepository jobs;

    @Autowired
    private CareerDocumentRepository documents;

    @Autowired
    private JobStatusHistoryRepository history;

    @Autowired
    private UserAccountRepository users;

    private JobApplication acme;

    @BeforeEach
    void seed() {
        acme = jobs.save(job("Acme Corp", "Backend Developer", "Jakarta", "Dewi", "LinkedIn",
                WorkMode.REMOTE, JobStatus.APPLIED));
        jobs.save(job("Globex", "Data Engineer", null, null, "Glints", WorkMode.HYBRID, JobStatus.APPLIED));
        jobs.save(job("Initech 100%", "QA_Lead", "Bandung", null, null, WorkMode.ONSITE, JobStatus.OFFER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACME", "backend dev", "jakarta", "dEwI", "  acme  "})
    void jobKeywordSearchesEachFieldIgnoringCase(String keyword) {
        assertThat(jobs.findAll(JobApplicationSpecs.keyword(keyword)))
                .extracting(JobApplication::getCompanyName).containsExactly("Acme Corp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_"})
    void jobKeywordTreatsWildcardsLiterally(String keyword) {
        assertThat(jobs.findAll(JobApplicationSpecs.keyword(keyword)))
                .extracting(JobApplication::getCompanyName).containsExactly("Initech 100%");
    }

    @Test
    void jobKeywordHandlesBackslashAndNoMatch() {
        assertThat(jobs.findAll(JobApplicationSpecs.keyword("\\"))).isEmpty();
        assertThat(jobs.findAll(JobApplicationSpecs.keyword("nothing"))).isEmpty();
    }

    @Test
    void jobFiltersByStatusWorkModeAndPlatform() {
        assertThat(jobs.findAll(JobApplicationSpecs.hasStatus(JobStatus.APPLIED))).hasSize(2);
        assertThat(jobs.findAll(JobApplicationSpecs.hasWorkMode(WorkMode.HYBRID)))
                .extracting(JobApplication::getCompanyName).containsExactly("Globex");
        assertThat(jobs.findAll(JobApplicationSpecs.hasPlatform(" linkedin ")))
                .extracting(JobApplication::getCompanyName).containsExactly("Acme Corp");
        assertThat(jobs.findAll(JobApplicationSpecs.hasPlatform("linked"))).isEmpty();
    }

    @Test
    void emptyJobFilterMatchesEverything() {
        JobSearchFilter filter = new JobSearchFilter();
        filter.setKeyword(" ");
        filter.setPlatform("");

        assertThat(jobs.findAll(JobApplicationSpecs.matching(filter))).hasSize(3);
    }

    @Test
    void jobFiltersCombineAndPaginate() {
        JobSearchFilter filter = new JobSearchFilter();
        filter.setStatus(JobStatus.APPLIED);
        assertThat(jobs.findAll(JobApplicationSpecs.matching(filter), PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(2);

        filter.setKeyword("engineer");
        filter.setWorkMode(WorkMode.HYBRID);
        filter.setPlatform("GLINTS");
        assertThat(jobs.findAll(JobApplicationSpecs.matching(filter)))
                .extracting(JobApplication::getCompanyName).containsExactly("Globex");

        filter.setWorkMode(WorkMode.REMOTE);
        assertThat(jobs.findAll(JobApplicationSpecs.matching(filter))).isEmpty();
    }

    @Test
    void countsJobsByStatus() {
        assertThat(jobs.countGroupedByStatus())
                .extracting(JobApplicationRepository.StatusCount::getStatus,
                        JobApplicationRepository.StatusCount::getTotal)
                .containsExactlyInAnyOrder(
                        tuple(JobStatus.APPLIED, 2L),
                        tuple(JobStatus.OFFER, 1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESUME", "spring", "oracle", "scan.PDF"})
    void documentKeywordSearchesEachFieldIgnoringCase(String keyword) {
        seedDocuments();

        assertThat(documents.findAll(CareerDocumentSpecs.keyword(keyword)))
                .extracting(CareerDocument::getTitle).containsExactly("Resume");
    }

    @Test
    void documentFiltersCombine() {
        seedDocuments();

        assertThat(documents.findAll(CareerDocumentSpecs.matching(null, null))).hasSize(3);
        assertThat(documents.findAll(CareerDocumentSpecs.matching(null, DocumentCategory.CERTIFICATE))).hasSize(2);
        assertThat(documents.findAll(CareerDocumentSpecs.matching("aws", DocumentCategory.CERTIFICATE)))
                .extracting(CareerDocument::getTitle).containsExactly("AWS");
        assertThat(documents.findAll(CareerDocumentSpecs.matching("aws", DocumentCategory.CV))).isEmpty();
    }

    @Test
    void aggregatesDocuments() {
        assertThat(documents.sumFileSizeBytes()).isZero();
        assertThat(documents.countGroupedByCategory()).isEmpty();

        seedDocuments();

        assertThat(documents.sumFileSizeBytes()).isEqualTo(3_000_000_600L);
        assertThat(documents.countGroupedByCategory())
                .extracting(CareerDocumentRepository.CategoryCount::getCategory,
                        CareerDocumentRepository.CategoryCount::getTotal)
                .containsExactlyInAnyOrder(
                        tuple(DocumentCategory.CV, 1L),
                        tuple(DocumentCategory.CERTIFICATE, 2L));
    }

    @Test
    void findsHistoryNewestFirstForOneJob() throws InterruptedException {
        JobApplication other = jobs.save(job("Other", "Role", null, null, null,
                WorkMode.REMOTE, JobStatus.SAVED));
        history.saveAndFlush(entry(acme, JobStatus.SAVED));
        Thread.sleep(5);
        history.saveAndFlush(entry(acme, JobStatus.APPLIED));
        history.saveAndFlush(entry(other, JobStatus.SAVED));

        List<JobStatusHistory> found = history.findByJobApplicationIdOrderByChangedAtDesc(acme.getId());

        assertThat(found).extracting(JobStatusHistory::getToStatus)
                .containsExactly(JobStatus.APPLIED, JobStatus.SAVED);
    }

    @Test
    void findsUserByUsername() {
        UserAccount user = new UserAccount();
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setFullName("Admin");
        user.setRole("ADMIN");
        users.save(user);

        assertThat(users.findByUsername("admin")).isPresent();
        assertThat(users.findByUsername("nobody")).isEmpty();
    }

    private void seedDocuments() {
        documents.save(document("Resume", DocumentCategory.CV, "java,spring", "Oracle", "scan.pdf", 100));
        documents.save(document("AWS", DocumentCategory.CERTIFICATE, null, null, "a.pdf", 500));
        documents.save(document("Azure", DocumentCategory.CERTIFICATE, null, null, "b.pdf", 3_000_000_000L));
    }

    private static CareerDocument document(String title, DocumentCategory category, String tags, String issuer,
            String filename, long size) {
        CareerDocument document = new CareerDocument();
        document.setTitle(title);
        document.setCategory(category);
        document.setTags(tags);
        document.setIssuerOrContext(issuer);
        document.setOriginalFilename(filename);
        document.setStoredFilename("stored-" + title);
        document.setContentType("application/pdf");
        document.setFileSizeBytes(size);
        document.setSha256Checksum("a".repeat(64));
        return document;
    }

    private static JobApplication job(String company, String role, String location, String contact, String platform,
            WorkMode workMode, JobStatus status) {
        JobApplication job = new JobApplication();
        job.setCompanyName(company);
        job.setRoleTitle(role);
        job.setLocation(location);
        job.setContactPerson(contact);
        job.setPlatform(platform);
        job.setWorkMode(workMode);
        job.setStatus(status);
        return job;
    }

    private static JobStatusHistory entry(JobApplication job, JobStatus to) {
        JobStatusHistory entry = new JobStatusHistory();
        entry.setJobApplication(job);
        entry.setToStatus(to);
        return entry;
    }
}
