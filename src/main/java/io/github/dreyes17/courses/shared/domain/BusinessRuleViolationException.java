package io.github.dreyes17.courses.shared.domain;

/** Well-formed input that breaks a business rule, e.g. progress going backwards (HTTP 422). */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
