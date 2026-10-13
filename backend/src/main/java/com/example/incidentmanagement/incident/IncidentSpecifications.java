package com.example.incidentmanagement.incident;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

public final class IncidentSpecifications {

    private static final Pattern INC_DISPLAY_PATTERN = Pattern.compile("(?i)^inc-(\\d+)$");
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^\\d+$");

    private IncidentSpecifications() {}

    public static Specification<Incident> forOrganization(UUID organizationId, IncidentListCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organization").get("id"), organizationId));

            if (criteria.status().isPresent()) {
                predicates.add(cb.equal(root.get("status"), criteria.status().get()));
            } else if (criteria.active().isPresent()) {
                if (criteria.active().get()) {
                    predicates.add(root.get("status").in(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED));
                } else {
                    predicates.add(root.get("status").in(IncidentStatus.RESOLVED, IncidentStatus.CANCELLED));
                }
            }

            criteria.severity().ifPresent(severity -> predicates.add(cb.equal(root.get("severity"), severity)));

            criteria.serviceId()
                    .ifPresent(serviceId -> predicates.add(cb.equal(root.get("service").get("id"), serviceId)));

            criteria.commanderId()
                    .ifPresent(
                            commanderId -> predicates.add(cb.equal(root.get("commander").get("id"), commanderId)));

            criteria.search().ifPresent(term -> predicates.add(searchPredicate(root, cb, term)));

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate searchPredicate(Root<Incident> root, CriteriaBuilder cb, String term) {
        Matcher incMatcher = INC_DISPLAY_PATTERN.matcher(term);
        if (incMatcher.matches()) {
            long incidentNumber = Long.parseLong(incMatcher.group(1));
            return cb.equal(root.get("incidentNumber"), incidentNumber);
        }
        if (DIGITS_ONLY_PATTERN.matcher(term).matches()) {
            long incidentNumber = Long.parseLong(term);
            Expression<String> lowerTitle = cb.lower(root.get("title"));
            String likePattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.equal(root.get("incidentNumber"), incidentNumber),
                    cb.like(lowerTitle, likePattern, '\\'));
        }
        Expression<String> lowerTitle = cb.lower(root.get("title"));
        String likePattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        return cb.like(lowerTitle, likePattern, '\\');
    }

    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
