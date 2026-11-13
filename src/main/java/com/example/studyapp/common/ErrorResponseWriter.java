package com.example.studyapp.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the standard error JSON from places that run outside the MVC
 * exception handler (security filters).
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\"}"
                .formatted(status, escape(code), escape(message)));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
