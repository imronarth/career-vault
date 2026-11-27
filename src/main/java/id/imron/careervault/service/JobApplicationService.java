package id.imron.careervault.service;

import java.util.List;

import jakarta.persistence.EntityNotFoundException;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.JobStatusHistory;
import id.imron.careervault.dto.JobApplicationForm;
import id.imron.careervault.dto.JobSearchFilter;
import id.imron.careervault.dto.JobStatusUpdateForm;
import id.imron.careervault.repository.CareerDocumentRepository;
import id.imron.careervault.repository.JobApplicationRepository;
import id.imron.careervault.repository.JobApplicationSpecs;
import id.imron.careervault.repository.JobStatusHistoryRepository;

/** Job applications; every status change leaves a history entry. */
@Service
@Transactional
public class JobApplicationService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final JobApplicationRepository jobs;

    private final JobStatusHistoryRepository history;

    private final CareerDocumentRepository documents;

    public JobApplicationService(JobApplicationRepository jobs, JobStatusHistoryRepository history,
            CareerDocumentRepository documents) {
        this.jobs = jobs;
        this.history = history;
        this.documents = documents;
    }

    /** Newest first unless the pageable asks for another order. */
    @Transactional(readOnly = true)
    public Page<JobApplication> search(JobSearchFilter filter, Pageable pageable) {
        Pageable sorted = pageable.isPaged() && pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)
                : pageable;
        return jobs.findAll(JobApplicationSpecs.matching(filter), sorted);
    }

    /**
     * Returns the application with its linked documents loaded.
     * @throws EntityNotFoundException if there is no such application
     */
    @Transactional(readOnly = true)
    public JobApplication get(Long id) {
        JobApplication job = find(id);
        Hibernate.initialize(job.getCvDocument());
        Hibernate.initialize(job.getCoverLetterDocument());
        return job;
    }

    /** @throws InvalidDocumentLinkException if a linked document does not exist */
    public JobApplication create(JobApplicationForm form) {
        JobApplication job = new JobApplication();
        apply(form, job);
        jobs.save(job);
        record(job, null, job.getStatus(), null);
        return job;
    }

    /**
     * @throws EntityNotFoundException if there is no such application
     * @throws InvalidDocumentLinkException if a linked document does not exist
     */
    public JobApplication update(Long id, JobApplicationForm form) {
        JobApplication job = find(id);
        JobStatus previous = job.getStatus();
        apply(form, job);
        if (job.getStatus() != previous) {
            record(job, previous, job.getStatus(), null);
        }
        return job;
    }

    /**
     * Quick status change. A note is kept in the history even if the status stays the same; with neither a new status
     * nor a note nothing is recorded.
     * @throws EntityNotFoundException if there is no such application
     */
    public JobApplication updateStatus(Long id, JobStatusUpdateForm form) {
        JobApplication job = find(id);
        JobStatus previous = job.getStatus();
        String note = blankToNull(form.getNote());
        if (form.getNewStatus() != previous || note != null) {
            job.setStatus(form.getNewStatus());
            record(job, previous, form.getNewStatus(), note);
        }
        return job;
    }

    /**
     * Status history, newest first.
     * @throws EntityNotFoundException if there is no such application
     */
    @Transactional(readOnly = true)
    public List<JobStatusHistory> history(Long id) {
        find(id);
        return history.findByJobApplicationIdOrderByChangedAtDesc(id);
    }

    /**
     * Deletes the application; the database removes its history.
     * @throws EntityNotFoundException if there is no such application
     */
    public void delete(Long id) {
        jobs.delete(find(id));
    }

    /** All applications matching the filter, newest first, as a UTF-8 CSV file. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(JobSearchFilter filter) {
        List<JobApplication> matching = jobs.findAll(JobApplicationSpecs.matching(filter), NEWEST_FIRST);
        return JobCsvWriter.write(matching);
    }

    private JobApplication find(Long id) {
        return jobs.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job application not found: " + id));
    }

    private void apply(JobApplicationForm form, JobApplication job) {
        job.setCompanyName(form.getCompanyName().strip());
        job.setRoleTitle(form.getRoleTitle().strip());
        job.setJobUrl(blankToNull(form.getJobUrl()));
        job.setPlatform(blankToNull(form.getPlatform()));
        job.setWorkMode(form.getWorkMode());
        job.setLocation(blankToNull(form.getLocation()));
        job.setSalaryRange(blankToNull(form.getSalaryRange()));
        job.setStatus(form.getStatus());
        job.setAppliedDate(form.getAppliedDate());
        job.setFollowUpDate(form.getFollowUpDate());
        job.setContactPerson(blankToNull(form.getContactPerson()));
        job.setNotes(blankToNull(form.getNotes()));
        job.setCvDocument(linkedDocument("cvDocumentId", form.getCvDocumentId()));
        job.setCoverLetterDocument(linkedDocument("coverLetterDocumentId", form.getCoverLetterDocumentId()));
    }

    private CareerDocument linkedDocument(String field, Long id) {
        if (id == null) {
            return null;
        }
        return documents.findById(id).orElseThrow(() -> new InvalidDocumentLinkException(field));
    }

    private void record(JobApplication job, JobStatus from, JobStatus to, String note) {
        JobStatusHistory entry = new JobStatusHistory();
        entry.setJobApplication(job);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setNote(note);
        history.save(entry);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    /** A form refers to a document that does not exist. The message is safe to show next to the field. */
    public static class InvalidDocumentLinkException extends IllegalArgumentException {

        private final String field;

        public InvalidDocumentLinkException(String field) {
            super("The selected document no longer exists.");
            this.field = field;
        }

        /** Name of the form field that holds the bad id. */
        public String getField() {
            return field;
        }
    }
}
