package id.imron.careervault.repository;

import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import id.imron.careervault.domain.JobApplication;
import id.imron.careervault.domain.JobStatus;
import id.imron.careervault.domain.WorkMode;
import id.imron.careervault.dto.JobSearchFilter;

/** Job list filters. A null or blank argument does not restrict the result. */
public final class JobApplicationSpecs {

    private JobApplicationSpecs() {
    }

    public static Specification<JobApplication> matching(JobSearchFilter filter) {
        return Specification.allOf(
                keyword(filter.getKeyword()),
                hasStatus(filter.getStatus()),
                hasWorkMode(filter.getWorkMode()),
                hasPlatform(filter.getPlatform()));
    }

    /** Searches company name, role title, location and contact person. */
    public static Specification<JobApplication> keyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> KeywordPredicates.anyContains(builder, keyword, List.of(
                root.get("companyName"),
                root.get("roleTitle"),
                root.get("location"),
                root.get("contactPerson")));
    }

    public static Specification<JobApplication> hasStatus(JobStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<JobApplication> hasWorkMode(WorkMode workMode) {
        if (workMode == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("workMode"), workMode);
    }

    /** Whole platform name, ignoring case. */
    public static Specification<JobApplication> hasPlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return Specification.unrestricted();
        }
        String wanted = platform.strip().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> builder.equal(builder.lower(root.get("platform")), wanted);
    }
}
