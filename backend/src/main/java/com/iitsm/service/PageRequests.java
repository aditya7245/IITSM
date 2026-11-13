package com.iitsm.service;

import com.iitsm.exception.InvalidInputException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Prepares the page requests that arrive from the web before they reach the database: only known columns may be
 * sorted on (anything else would be an internal error), and a last sort on the id makes pages stable when many rows
 * have the same value, so no row is shown twice or skipped when paging through.
 */
final class PageRequests {

    /** The incident columns a list may be sorted by. */
    static final Set<String> INCIDENT_SORT_FIELDS = Set.of("createdAt", "updatedAt", "incidentNumber", "priority",
            "status", "slaStatus", "resolutionDeadline");

    private PageRequests() { }

    /**
     * Keeps the requested sort (after checking it) and adds the id as the final tie-break, in the direction of the
     * first sort order (newest first if there is none).
     *
     * @throws InvalidInputException if the sort names a column that may not be sorted on
     */
    static Pageable forIncidentSearch(Pageable requested) {
        Sort sort = requested.getSort();
        Sort.Direction tieBreak = Sort.Direction.DESC;
        boolean first = true;
        for (Sort.Order order : sort) {
            if (!INCIDENT_SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidInputException("Cannot sort by '" + order.getProperty() + "'");
            }
            if (first) {
                tieBreak = order.getDirection();
                first = false;
            }
        }
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort.and(Sort.by(tieBreak, "id")));
    }

    /** Same page and size, but always in the given order; any sort in the request is ignored. */
    static Pageable ordered(Pageable requested, Sort sort) {
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort);
    }

    /** Same page and size, but always newest first; any sort in the request is ignored. */
    static Pageable newestFirst(Pageable requested) {
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
