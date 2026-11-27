package id.imron.careervault.repository;

import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

final class KeywordPredicates {

    private static final char ESCAPE = '\\';

    private KeywordPredicates() {
    }

    /** Case-insensitive "any field contains keyword"; LIKE wildcards in the keyword match literally. */
    static Predicate anyContains(CriteriaBuilder builder, String keyword, List<Expression<String>> fields) {
        String literal = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + literal + "%";
        return builder.or(fields.stream()
                .map(field -> builder.like(builder.lower(field), pattern, ESCAPE))
                .toArray(Predicate[]::new));
    }
}
