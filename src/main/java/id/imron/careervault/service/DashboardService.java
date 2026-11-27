package id.imron.careervault.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.dto.DashboardSummaryDto;
import id.imron.careervault.repository.CareerDocumentRepository;
import id.imron.careervault.repository.CareerDocumentRepository.CategoryCount;
import id.imron.careervault.repository.JobApplicationRepository;
import id.imron.careervault.repository.JobApplicationRepository.StatusCount;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int FOLLOW_UP_DAYS = 14;

    private static final List<JobStatus> ACTIVE_STATUSES = Arrays.stream(JobStatus.values())
            .filter(JobStatus::isActive)
            .toList();

    private static final List<String> SIZE_UNITS = List.of("KB", "MB", "GB", "TB");

    private final JobApplicationRepository jobs;

    private final CareerDocumentRepository documents;

    public DashboardService(JobApplicationRepository jobs, CareerDocumentRepository documents) {
        this.jobs = jobs;
        this.documents = documents;
    }

    public DashboardSummaryDto getSummary() {
        return summaryFor(LocalDate.now());
    }

    DashboardSummaryDto summaryFor(LocalDate today) {
        Map<JobStatus, Long> statusBreakdown = statusBreakdown();
        return new DashboardSummaryDto(
                sum(statusBreakdown, Arrays.asList(JobStatus.values())),
                sum(statusBreakdown, ACTIVE_STATUSES),
                statusBreakdown.get(JobStatus.OFFER),
                statusBreakdown,
                jobs.findByStatusInAndFollowUpDateBetweenOrderByFollowUpDateAscIdAsc(
                        ACTIVE_STATUSES, today, today.plusDays(FOLLOW_UP_DAYS)),
                jobs.findTop5ByOrderByCreatedAtDescIdDesc(),
                documents.findTop6ByOrderByCreatedAtDescIdDesc(),
                documents.count(),
                formatSize(documents.sumFileSizeBytes()),
                documentsPerCategory(),
                primaryCv());
    }

    /** Every status in pipeline order, including those without applications. */
    private Map<JobStatus, Long> statusBreakdown() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            counts.put(status, 0L);
        }
        for (StatusCount row : jobs.countGroupedByStatus()) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    /** Only categories that have documents, so that an empty vault gives an empty map. */
    private Map<DocumentCategory, Long> documentsPerCategory() {
        Map<DocumentCategory, Long> counts = new EnumMap<>(DocumentCategory.class);
        for (CategoryCount row : documents.countGroupedByCategory()) {
            counts.put(row.getCategory(), row.getTotal());
        }
        return counts;
    }

    private CareerDocument primaryCv() {
        return documents.findByCategoryAndPrimaryTrue(DocumentCategory.CV).stream()
                .max(Comparator.comparing(CareerDocument::getUpdatedAt))
                .orElse(null);
    }

    private static long sum(Map<JobStatus, Long> counts, List<JobStatus> statuses) {
        return statuses.stream().mapToLong(counts::get).sum();
    }

    /** Binary units with one decimal, for example {@code 2.4 MB}. */
    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double size = bytes / 1024.0;
        int unit = 0;
        // 1023.95 and above would be printed as "1024.0", so move to the next unit.
        while (size >= 1023.95 && unit < SIZE_UNITS.size() - 1) {
            size /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", size, SIZE_UNITS.get(unit));
    }
}
