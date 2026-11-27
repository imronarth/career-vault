package id.imron.careervault.dto;

import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;

/** Query parameters of the job list. Every field is optional. */
public class JobSearchFilter {

    private String keyword;

    private JobStatus status;

    private WorkMode workMode;

    private String platform;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
