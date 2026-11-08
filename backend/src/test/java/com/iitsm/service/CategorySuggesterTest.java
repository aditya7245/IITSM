package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests: categories are built in memory, no database needed. */
class CategorySuggesterTest {

    private final CategorySuggester suggester = new CategorySuggester();

    private static Category category(long id, String name, String keywords) {
        Category c = new Category(name, null, keywords, null);
        setField(c, "id", id);
        return c;
    }

    private static void setField(Category c, String field, Object value) {
        try {
            Field f = Category.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(c, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private final Category network = category(3, "Network", "network,wifi,vpn,dns");
    private final Category database = category(4, "Database", "database,sql,query");
    private final Category application = category(8, "Application", "application,payment,transaction,error");

    private List<Category> all() {
        return List.of(network, database, application);
    }

    @Test
    void bestMatchComesFirstWithItsHitCount() {
        List<CategoryScore> result = suggester.suggest("payment transaction failing", all());
        assertEquals(1, result.size());
        assertSame(application, result.get(0).category());
        assertEquals(2, result.get(0).hits());
    }

    @Test
    void resultsAreSortedByHitsDescending() {
        // application: payment, error = 2; database: query = 1
        List<CategoryScore> result = suggester.suggest("payment error in query", all());
        assertEquals(2, result.size());
        assertSame(application, result.get(0).category());
        assertSame(database, result.get(1).category());
    }

    @Test
    void tiesGoToTheLowerCategoryId() {
        // application: payment = 1; database: query = 1  ->  database (id 4) beats application (id 8)
        List<CategoryScore> result = suggester.suggest("payment query", List.of(application, database));
        assertSame(database, result.get(0).category());
        assertSame(application, result.get(1).category());
    }

    @Test
    void textWithNoKeywordsGivesAnEmptyList() {
        assertTrue(suggester.suggest("lunch menu for today", all()).isEmpty());
        assertTrue(suggester.suggest("", all()).isEmpty());
        assertTrue(suggester.suggest(null, all()).isEmpty());
        assertTrue(suggester.suggest("payment", null).isEmpty());
    }

    @Test
    void inactiveCategoriesAreSkipped() {
        Category retired = category(1, "Retired", "payment");
        setField(retired, "active", false);
        List<CategoryScore> result = suggester.suggest("payment failed", List.of(retired, application));
        assertEquals(1, result.size());
        assertSame(application, result.get(0).category());
    }

    @Test
    void keywordsAreTrimmedCaseInsensitiveAndCountedOnce() {
        Category messy = category(9, "Messy", "  VPN , Wifi,,vpn ,");
        List<CategoryScore> result = suggester.suggest("My vpn and WIFI are down, vpn vpn", List.of(messy));
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).hits());   // vpn and wifi, each once
    }

    @Test
    void categoryWithoutKeywordsNeverMatches() {
        Category empty = category(10, "Empty", "");
        assertTrue(suggester.suggest("anything at all", List.of(empty)).isEmpty());
    }

    @Test
    void keywordsMatchWholeWordsOnly() {
        // "sqlite" must not match the keyword "sql"
        assertTrue(suggester.suggest("sqlite crashed", List.of(database)).isEmpty());
    }
}
