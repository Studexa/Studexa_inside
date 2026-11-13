package com.studexa.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes an RFC 7807 problem body from places that run outside the MVC
 * exception handler (security filters and entry points).
 */
public final class ErrorResponseWriter {

    private static final String PROBLEM_JSON = "application/problem+json";

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"code\":\"%s\",\"detail\":\"%s\"}"
                        .formatted(escape(ApiProblem.typeUri(code)), escape(titleFor(status)), status,
                                escape(code), escape(detail)));
    }

    private static String titleFor(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
