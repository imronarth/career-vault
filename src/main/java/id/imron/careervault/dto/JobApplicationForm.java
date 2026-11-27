package id.imron.careervault.dto;

import java.time.LocalDate;

import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/** Create and edit form for a job application. Size limits match the database columns. */
public class JobApplicationForm {

    @NotBlank(message = "Company name is required.")
    @Size(max = 200, message = "Use at most 200 characters.")
    private String companyName;

    @NotBlank(message = "Role title is required.")
    @Size(max = 200, message = "Use at most 200 characters.")
    private String roleTitle;

    // Only web links: the URL is rendered as a clickable href.
    @Pattern(regexp = "(?i)\\s*(https?://\\S+)?\\s*", message = "Enter a URL starting with http:// or https://.")
    @Size(max = 2048, message = "Use at most 2048 characters.")
    private String jobUrl;

    @Size(max = 100, message = "Use at most 100 characters.")
    private String platform;

    @NotNull(message = "Choose a work mode.")
    private WorkMode workMode;

    @Size(max = 200, message = "Use at most 200 characters.")
    private String location;

    @Size(max = 100, message = "Use at most 100 characters.")
    private String salaryRange;

    @NotNull(message = "Choose a status.")
    private JobStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appliedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate followUpDate;

    @Size(max = 200, message = "Use at most 200 characters.")
    private String contactPerson;

    @Size(max = 10000, message = "Use at most 10000 characters.")
    private String notes;

    private Long cvDocumentId;

    private Long coverLetterDocumentId;

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getCvDocumentId() {
        return cvDocumentId;
    }

    public void setCvDocumentId(Long cvDocumentId) {
        this.cvDocumentId = cvDocumentId;
    }

    public Long getCoverLetterDocumentId() {
        return coverLetterDocumentId;
    }

    public void setCoverLetterDocumentId(Long coverLetterDocumentId) {
        this.coverLetterDocumentId = coverLetterDocumentId;
    }
}
