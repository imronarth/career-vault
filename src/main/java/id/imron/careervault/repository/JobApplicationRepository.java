package id.imron.careervault.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {

    List<JobApplication> findTop5ByOrderByCreatedAtDescIdDesc();

    /** Both dates are inclusive; the earliest follow-up comes first. */
    List<JobApplication> findByStatusInAndFollowUpDateBetweenOrderByFollowUpDateAscIdAsc(
            Collection<JobStatus> statuses, LocalDate from, LocalDate to);

    /** One row per status that has at least one application. */
    @Query("select j.status as status, count(j) as total from JobApplication j group by j.status")
    List<StatusCount> countGroupedByStatus();

    interface StatusCount {

        JobStatus getStatus();

        long getTotal();
    }
}
