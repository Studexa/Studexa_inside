package com.example.studyapp.ai;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.studyapp.ai.dto.AiGenerateRequest;
import com.example.studyapp.ai.dto.AiGenerateResponse;
import com.example.studyapp.ai.dto.AiTutorRequest;
import com.example.studyapp.ai.dto.AiTutorResponse;
import com.example.studyapp.common.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate")
    public AiGenerateResponse generate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiGenerateRequest request) {
        return aiService.generate(CurrentUser.id(jwt), request);
    }

    @PostMapping("/tutor")
    public AiTutorResponse tutor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiTutorRequest request) {
        return aiService.tutor(CurrentUser.id(jwt), request);
    }
}
