package id.imron.careervault.dto;

import id.imron.careervault.domain.JobStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Quick status change on the job detail page. */
public class JobStatusUpdateForm {

    @NotNull(message = "Choose a status.")
    private JobStatus newStatus;

    @Size(max = 2000, message = "Use at most 2000 characters.")
    private String note;

    public JobStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(JobStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
