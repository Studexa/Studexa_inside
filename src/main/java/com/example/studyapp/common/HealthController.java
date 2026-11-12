package com.example.studyapp.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint used by the frontend and hosting health checks.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}
