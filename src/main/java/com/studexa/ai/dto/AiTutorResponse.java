package com.studexa.ai.dto;

import java.util.List;

public record AiTutorResponse(String answer, List<AiTutorReference> references) {
}
