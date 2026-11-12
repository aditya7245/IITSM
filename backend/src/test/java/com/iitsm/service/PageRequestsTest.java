package com.iitsm.service;

import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests of {@link PageRequests}: only known columns can be sorted, and pages get a stable last sort on the id. */
class PageRequestsTest {

    private static List<String> orders(Pageable pageable) {
        List<String> result = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            result.add(order.getProperty() + " " + order.getDirection());
        }
        return result;
    }

    @Test
    void theIdIsAddedAsTheLastSortInTheDirectionOfTheFirstOne() {
        Pageable descending = PageRequests.forIncidentSearch(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        Pageable ascending = PageRequests.forIncidentSearch(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "incidentNumber")));

        assertEquals(List.of("createdAt DESC", "id DESC"), orders(descending));
        assertEquals(List.of("incidentNumber ASC", "id ASC"), orders(ascending));
    }

    @Test
    void withoutAnySortItIsNewestFirstByIdAlone() {
        assertEquals(List.of("id DESC"), orders(PageRequests.forIncidentSearch(PageRequest.of(0, 20))));
    }

    @Test
    void severalSortOrdersAreKeptInOrder() {
        Sort sort = Sort.by(Sort.Direction.ASC, "priority").and(Sort.by(Sort.Direction.DESC, "createdAt"));

        Pageable result = PageRequests.forIncidentSearch(PageRequest.of(0, 20, sort));

        assertEquals(List.of("priority ASC", "createdAt DESC", "id ASC"), orders(result));
    }

    @Test
    void pageNumberAndSizeAreKept() {
        Pageable result = PageRequests.forIncidentSearch(PageRequest.of(3, 7, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(3, result.getPageNumber());
        assertEquals(7, result.getPageSize());
    }

    @Test
    void everyListedColumnCanBeSortedOn() {
        for (String field : PageRequests.INCIDENT_SORT_FIELDS) {
            assertDoesNotThrowSort(field);
        }
        assertEquals(7, PageRequests.INCIDENT_SORT_FIELDS.size());
    }

    private static void assertDoesNotThrowSort(String field) {
        Pageable result = PageRequests.forIncidentSearch(PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, field)));
        assertEquals(field + " ASC", orders(result).get(0));
    }

    @Test
    void anyOtherColumnIsRefusedWithA400StyleError() {
        for (String bad : List.of("foo", "password", "reporter.password", "id", "description")) {
            InvalidInputException refused = assertThrows(InvalidInputException.class,
                    () -> PageRequests.forIncidentSearch(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, bad))));
            assertEquals("Cannot sort by '" + bad + "'", refused.getMessage());
        }
    }

    @Test
    void newestFirstIgnoresTheRequestedSortButKeepsPageAndSize() {
        Pageable result = PageRequests.newestFirst(PageRequest.of(2, 15, Sort.by(Sort.Direction.ASC, "whatever")));

        assertEquals(List.of("createdAt DESC", "id DESC"), orders(result));
        assertEquals(2, result.getPageNumber());
        assertEquals(15, result.getPageSize());
    }
}
