package com.studexa.plan;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExamRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String subject,
        @NotNull LocalDate examDate,
        @NotNull Exam.Importance importance
) {}
