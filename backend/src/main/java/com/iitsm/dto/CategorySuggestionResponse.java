package com.iitsm.dto;

/** A suggested category and how many of its keywords were found in the text (more means a better match). */
public record CategorySuggestionResponse(Long categoryId, String name, int score) { }
