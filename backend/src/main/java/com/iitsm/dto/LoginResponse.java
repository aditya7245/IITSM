package com.iitsm.dto;

public record LoginResponse(String token, String tokenType, long expiresInSeconds, String role, String email) { }
