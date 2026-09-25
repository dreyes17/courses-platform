package com.example.courses.identity.application;

public record AccessToken(String accessToken, String tokenType, long expiresIn) {
}
