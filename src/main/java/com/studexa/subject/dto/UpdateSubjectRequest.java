package com.studexa.subject.dto;

import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @Size(max = 100, message = "Subject name must be 100 characters or fewer.")
        String name,

        @Size(max = 20, message = "Color must be 20 characters or fewer.")
        String color,

        @Size(max = 32, message = "Icon must be 32 characters or fewer.")
        String icon,

        Boolean archived) {
}
