package id.imron.careervault.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EntityMappingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsRelationsAndSetsTimestamps() {
        CareerDocument cv = document("stored-1.pdf");
        JobApplication job = job(cv);
        JobStatusHistory history = new JobStatusHistory();
        history.setJobApplication(job);
        history.setToStatus(JobStatus.APPLIED);
        history.setNote("n".repeat(5000));
        UserAccount user = new UserAccount();
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setFullName("Admin");
        user.setRole("ADMIN");
        persist(cv, job, history, user);
        entityManager.clear();

        JobApplication loaded = entityManager.find(JobApplication.class, job.getId());
        assertThat(loaded.getCvDocument().getTitle()).isEqualTo("My CV");
        assertThat(loaded.getCoverLetterDocument()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull().isEqualTo(loaded.getUpdatedAt());
        assertThat(entityManager.find(JobStatusHistory.class, history.getId()).getChangedAt()).isNotNull();
        assertThat(entityManager.find(UserAccount.class, user.getId()).getCreatedAt()).isNotNull();
    }

    @Test
    void updateMovesOnlyUpdatedAt() throws InterruptedException {
        CareerDocument cv = document("stored-2.pdf");
        persist(cv);
        Instant created = cv.getCreatedAt();
        Thread.sleep(5);

        cv.setTitle("Renamed");
        entityManager.flush();

        assertThat(cv.getCreatedAt()).isEqualTo(created);
        assertThat(cv.getUpdatedAt()).isAfter(created);
    }

    @Test
    void deletingRowsFollowsSchemaDeleteRules() {
        CareerDocument cv = document("stored-3.pdf");
        JobApplication job = job(cv);
        JobStatusHistory history = new JobStatusHistory();
        history.setJobApplication(job);
        history.setToStatus(JobStatus.APPLIED);
        persist(cv, job, history);
        entityManager.clear();

        entityManager.remove(entityManager.find(CareerDocument.class, cv.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(JobApplication.class, job.getId()).getCvDocument()).isNull();

        entityManager.remove(entityManager.find(JobApplication.class, job.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(JobStatusHistory.class, history.getId())).isNull();
    }

    private void persist(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }

    private static CareerDocument document(String storedFilename) {
        CareerDocument document = new CareerDocument();
        document.setTitle("My CV");
        document.setCategory(DocumentCategory.CV);
        document.setOriginalFilename("cv.pdf");
        document.setStoredFilename(storedFilename);
        document.setContentType("application/pdf");
        document.setFileSizeBytes(3);
        document.setSha256Checksum("a".repeat(64));
        return document;
    }

    private static JobApplication job(CareerDocument cv) {
        JobApplication job = new JobApplication();
        job.setCompanyName("Acme");
        job.setRoleTitle("Developer");
        job.setWorkMode(WorkMode.REMOTE);
        job.setStatus(JobStatus.APPLIED);
        job.setCvDocument(cv);
        return job;
    }
}
