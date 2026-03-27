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
        @JsonProperty("recommended_food_nearby")
        List<@Valid PlaceRecommendation> recommendedFoodNearby,
        @JsonProperty("recommended_food_hot")
        List<@Valid PlaceRecommendation> recommendedFoodHot,
        @JsonProperty("recommended_parking")
        List<@Valid PlaceRecommendation> recommendedParking,
        @JsonProperty("recommended_refuel")
        List<@Valid PlaceRecommendation> recommendedRefuel,
        @JsonProperty("recommended_charging")
        List<@Valid PlaceRecommendation> recommendedCharging,
        @JsonProperty("recommended_restaurants")
        @NotEmpty List<@Valid PlaceRecommendation> recommendedRestaurants,
        @JsonProperty("daily_itinerary")
        @NotEmpty List<@Valid DayPlan> dailyItinerary,
        @JsonProperty("planning_sources")
        List<@Valid PlanningSource> planningSources,
        @NotBlank String attribution
) {

    public TripPlanResponse {
        recommendedHotels = List.copyOf(recommendedHotels);
        recommendedRestaurants = List.copyOf(recommendedRestaurants);
        dailyItinerary = List.copyOf(dailyItinerary);
        recommendedFoodNearby = recommendedFoodNearby == null ? List.of() : List.copyOf(recommendedFoodNearby);
        recommendedFoodHot = recommendedFoodHot == null ? List.of() : List.copyOf(recommendedFoodHot);
        recommendedParking = recommendedParking == null ? List.of() : List.copyOf(recommendedParking);
        recommendedRefuel = recommendedRefuel == null ? List.of() : List.copyOf(recommendedRefuel);
        recommendedCharging = recommendedCharging == null ? List.of() : List.copyOf(recommendedCharging);
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
                recommendedFoodNearby,
                recommendedFoodHot,
                recommendedParking,
                recommendedRefuel,
                recommendedCharging,
                recommendedRestaurants,
                dailyItinerary,
                planningSources,
                attribution
        );
    }

    public TripPlanResponse(
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
        this(
                id,
                destination,
                travelMode,
                planningMode,
                generatedAt,
                favorite,
                tripSummary,
                totalDays,
                recommendedAccommodation,
                recommendedHotels,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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
            Double longitude,
            @JsonProperty("day")
            Integer day
    ) {
        public PlaceRecommendation(String name,
                                   String category,
                                   String address,
                                   String reason,
                                   String source,
                                   String sourceUrl,
                                   Double latitude,
                                   Double longitude) {
            this(name, category, address, reason, source, sourceUrl, latitude, longitude, null);
        }
    }

    public record PlanningSource(
            @NotBlank String title,
            @NotBlank String url
    ) {
    }
}
