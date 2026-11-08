package com.iitsm.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextTokenizerTest {

    @Test
    void splitsIntoLowercaseKeywords() {
        assertEquals(Set.of("payment", "application", "unavailable"),
                TextTokenizer.tokens("The Payment APPLICATION is unavailable!"));
    }

    @Test
    void punctuationAndExtraWhitespaceAreIgnored() {
        assertEquals(Set.of("login", "failed", "vpn"),
                TextTokenizer.tokens("  Login...failed;;; (VPN)\n\t "));
    }

    @Test
    void stopWordsAreDropped() {
        assertEquals(Set.of("unable", "make", "payment"), TextTokenizer.tokens("Unable to make a payment"));
        assertEquals(Set.of(), TextTokenizer.tokens("the and of it is not"));
    }

    @Test
    void everyStopWordFromTheChecklistIsDropped() {
        String all = "the a an is are was were be been to of in on at for and or with my our we i it its "
                + "this that has have had not no from by as can cannot please";
        assertEquals(Set.of(), TextTokenizer.tokens(all));
    }

    @Test
    void singleCharacterWordsAreDropped() {
        assertEquals(Set.of("db", "error"), TextTokenizer.tokens("a b db x error 7"));
    }

    @Test
    void digitsAreKeptAndDuplicatesCollapse() {
        assertEquals(Set.of("error", "500", "server"), TextTokenizer.tokens("Error 500 on server, error 500"));
    }

    @Test
    void thereIsNoStemming() {
        assertEquals(Set.of("payment", "payments"), TextTokenizer.tokens("payment payments"));
    }

    @Test
    void nonAsciiLettersAreKept() {
        assertEquals(Set.of("café", "grösse"), TextTokenizer.tokens("Café, GRÖSSE?"));
    }

    @Test
    void nullAndEmptyTextGiveAnEmptySet() {
        assertEquals(Set.of(), TextTokenizer.tokens(null));
        assertEquals(Set.of(), TextTokenizer.tokens(""));
        assertEquals(Set.of(), TextTokenizer.tokens("   "));
        assertEquals(Set.of(), TextTokenizer.tokens("!!! ... ???"));
    }

    @Test
    void resultCannotBeModified() {
        Set<String> tokens = TextTokenizer.tokens("payment failed");
        assertThrows(UnsupportedOperationException.class, () -> tokens.add("extra"));
    }
}
