package com.iitsm.dto;

import java.util.List;

/** The assignment strategy in use, and the names that can be chosen. */
public record AssignmentStrategyResponse(String strategy, List<String> available) { }
