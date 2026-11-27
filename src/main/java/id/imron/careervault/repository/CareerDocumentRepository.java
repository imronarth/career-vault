package id.imron.careervault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;

public interface CareerDocumentRepository
        extends JpaRepository<CareerDocument, Long>, JpaSpecificationExecutor<CareerDocument> {

    List<CareerDocument> findByCategoryAndPrimaryTrue(DocumentCategory category);

    /** One row per category that has at least one document. */
    @Query("select d.category as category, count(d) as total from CareerDocument d group by d.category")
    List<CategoryCount> countGroupedByCategory();

    /** Total size of all documents, 0 when there are none. */
    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CareerDocument d")
    long sumFileSizeBytes();

    interface CategoryCount {

        DocumentCategory getCategory();

        long getTotal();
    }
}
