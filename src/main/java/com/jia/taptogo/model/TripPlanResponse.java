package com.jia.taptogo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripPlanResponse(
        UUID id,
        @NotBlank String destination,
        @JsonProperty("travel_mode")
        @NotBlank String travelMode,
        @JsonProperty("planning_mode")
        @NotBlank String planningMode,
        @JsonProperty("generated_at")
        Instant generatedAt,
        boolean favorite,
        @JsonProperty("trip_summary")
        @NotBlank String tripSummary,
        @JsonProperty("total_days")
        @Positive int totalDays,
        @JsonProperty("recommended_accommodation")
        @Valid Accommodation recommendedAccommodation,
        @JsonProperty("recommended_hotels")
        @NotEmpty List<@Valid PlaceRecommendation> recommendedHotels,
        @JsonProperty("recommended_restaurants")
        @NotEmpty List<@Valid PlaceRecommendation> recommendedRestaurants,
        @JsonProperty("daily_itinerary")
        @NotEmpty List<@Valid DayPlan> dailyItinerary,
        @JsonProperty("planning_sources")
        List<@Valid PlanningSource> planningSources,
        @NotBlank String attribution
) {

    public TripPlanResponse {
        planningSources = planningSources == null ? List.of() : List.copyOf(planningSources);
    }

    public TripPlanResponse withFavorite(boolean updatedFavorite) {
        return new TripPlanResponse(
                id,
                destination,
                travelMode,
                planningMode,
                generatedAt,
                updatedFavorite,
                tripSummary,
                totalDays,
                recommendedAccommodation,
                recommendedHotels,
                recommendedRestaurants,
                dailyItinerary,
                planningSources,
                attribution
        );
    }

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

    public record PlaceRecommendation(
            @NotBlank String name,
            @NotBlank String category,
            @NotBlank String address,
            @NotBlank String reason,
            @NotBlank String source,
            @JsonProperty("source_url")
            String sourceUrl,
            Double latitude,
            Double longitude
    ) {
    }

    public record PlanningSource(
            @NotBlank String title,
            @NotBlank String url
    ) {
    }
}
