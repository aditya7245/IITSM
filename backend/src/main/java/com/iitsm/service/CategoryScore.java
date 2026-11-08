package com.iitsm.service;

import com.iitsm.domain.catalog.Category;

/** A category together with how many of its keywords appear in the text. */
public record CategoryScore(Category category, int hits) { }
