package id.imron.careervault.dto;

import java.util.List;
import java.util.Map;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;

/**
 * Read-only data for the dashboard. Component names are part of the UI contract.
 *
 * @param primaryCv the primary CV, or {@code null} if none is marked
 */
public record DashboardSummaryDto(
        long totalJobs,
        long activePipeline,
        long offers,
        Map<JobStatus, Long> statusBreakdown,
        List<JobApplication> upcomingFollowUps,
        List<JobApplication> recentApplications,
        List<CareerDocument> recentDocuments,
        long totalFiles,
        String totalStorageFormatted,
        Map<DocumentCategory, Long> documentsPerCategory,
        CareerDocument primaryCv) {
}
