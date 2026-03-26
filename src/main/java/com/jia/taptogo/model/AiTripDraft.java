package com.jia.taptogo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AiTripDraft(
        @JsonProperty("trip_summary")
        @NotBlank
        String tripSummary,
        @JsonProperty("total_days")
        @Positive
        int totalDays,
        @JsonProperty("recommended_accommodation")
        @Valid
        Accommodation recommendedAccommodation,
        @JsonProperty("daily_itinerary")
        @NotEmpty
        List<@Valid DayPlan> dailyItinerary
) {

    public record Accommodation(
            @NotBlank String area,
            @NotBlank String reason
    ) {
    }

    public record DayPlan(
            @Positive int day,
            @NotBlank String theme,
            @NotEmpty List<@Valid Activity> activities
    ) {
    }

    public record Activity(
            @NotBlank String time,
            @NotBlank String type,
            @NotBlank String name,
            @NotBlank String description,
            @JsonProperty("transit_tip")
            @NotBlank String transitTip,
            @JsonProperty("social_link")
            String socialLink,
            Double latitude,
            Double longitude
    ) {
    }
}
