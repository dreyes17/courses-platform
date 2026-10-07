package io.github.dreyes17.courses.identity.application;

public record AccessToken(String accessToken, String tokenType, long expiresIn) {
}
