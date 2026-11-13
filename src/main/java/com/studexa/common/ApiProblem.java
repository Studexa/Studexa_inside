package com.studexa.common;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 7807 {@link ProblemDetail} responses with the Studexa error
 * contract: a stable {@code code} plus, for validation failures, a
 * {@code fields} map.
 */
public final class ApiProblem {

    private static final String TYPE_BASE = "https://studexa.app/errors/";

    private ApiProblem() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        return of(status, code, defaultTitle(status), detail);
    }

    public static ProblemDetail validation(String detail, Map<String, String> fields) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", detail);
        problem.setProperty("fields", fields);
        return problem;
    }

    public static String typeUri(String code) {
        return TYPE_BASE + code.toLowerCase().replace('_', '-');
    }

    private static String defaultTitle(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Bad request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not found";
            case CONFLICT -> "Conflict";
            case UNPROCESSABLE_ENTITY -> "Unprocessable entity";
            case TOO_MANY_REQUESTS -> "Too many requests";
            case BAD_GATEWAY -> "Bad gateway";
            case SERVICE_UNAVAILABLE -> "Service unavailable";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> status.getReasonPhrase();
        };
    }
}
