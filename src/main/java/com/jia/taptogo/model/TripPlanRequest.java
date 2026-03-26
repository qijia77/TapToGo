package com.jia.taptogo.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TripPlanRequest(
        @NotBlank(message = "destination is required")
        String destination,
        @NotBlank(message = "travelMode is required")
        String travelMode,
        @Min(value = 1, message = "days must be at least 1")
        @Max(value = 14, message = "days must be 14 or fewer")
        int days
) {
}
