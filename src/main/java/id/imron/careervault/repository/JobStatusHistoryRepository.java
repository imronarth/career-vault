package id.imron.careervault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.imron.careervault.domain.JobStatusHistory;

public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, Long> {

    List<JobStatusHistory> findByJobApplicationIdOrderByChangedAtDesc(Long jobApplicationId);
}
