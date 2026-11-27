package id.imron.careervault.repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import id.imron.careervault.domain.CareerDocument;
import id.imron.careervault.domain.DocumentCategory;

/** Document list filters. A null or blank argument does not restrict the result. */
public final class CareerDocumentSpecs {

    private CareerDocumentSpecs() {
    }

    public static Specification<CareerDocument> matching(String keyword, DocumentCategory category) {
        return Specification.allOf(keyword(keyword), hasCategory(category));
    }

    /** Searches title, tags, issuer/context and original filename. */
    public static Specification<CareerDocument> keyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> KeywordPredicates.anyContains(builder, keyword, List.of(
                root.get("title"),
                root.get("tags"),
                root.get("issuerOrContext"),
                root.get("originalFilename")));
    }

    public static Specification<CareerDocument> hasCategory(DocumentCategory category) {
        if (category == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("category"), category);
    }
}
