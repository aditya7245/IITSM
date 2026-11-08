package com.iitsm.service;

import com.iitsm.repository.CategoryRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the suggester against the real seeded categories and keywords from migration V2. */
@Transactional
class CategorySuggesterSeedDataTest extends AbstractIntegrationTest {

    @Autowired CategorySuggester suggester;
    @Autowired CategoryRepository categories;

    @Test
    void paymentProblemsSuggestApplication() {
        List<CategoryScore> result = suggester.suggest("payment transaction failing", categories.findAll());
        assertEquals("Application", result.get(0).category().getName());
        assertEquals(2, result.get(0).hits());
    }

    @Test
    void loginProblemsSuggestAuthentication() {
        List<CategoryScore> result = suggester.suggest("cannot login password locked", categories.findAll());
        assertEquals("Authentication", result.get(0).category().getName());
        assertEquals(3, result.get(0).hits());
    }

    @Test
    void unrelatedTextSuggestsNothing() {
        assertTrue(suggester.suggest("lunch menu for today", categories.findAll()).isEmpty());
    }
}
