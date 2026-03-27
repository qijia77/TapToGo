package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TripPlannerService {

    private static final Logger log = LoggerFactory.getLogger(TripPlannerService.class);
    private static final Pattern EMBEDDED_SOCIAL_LINK_PATTERN =
            Pattern.compile("'social_link'\\s*:\\s*'([^']+)'");
    private static final Pattern EMBEDDED_LATITUDE_PATTERN =
            Pattern.compile("'latitude'\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern EMBEDDED_LONGITUDE_PATTERN =
            Pattern.compile("'longitude'\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    private static final Set<String> GENERIC_ACTIVITY_MARKERS = Set.of(
            "核心片区",
            "美食聚集带",
            "返程收尾",
            "次日准备",
            "热门片区",
            "城市中心",
            "主地标区",
            "老城口碑餐馆街区",
            "夜景步道或河岸区域"
    );

    private final OpenAiTripDraftService openAiTripDraftService;
    private final DemoTripDraftService demoTripDraftService;
    private final PlaceDiscoveryService placeDiscoveryService;
    private final TripHistoryService tripHistoryService;

    public TripPlannerService(
            OpenAiTripDraftService openAiTripDraftService,
            DemoTripDraftService demoTripDraftService,
            PlaceDiscoveryService placeDiscoveryService,
            TripHistoryService tripHistoryService
    ) {
        this.openAiTripDraftService = openAiTripDraftService;
        this.demoTripDraftService = demoTripDraftService;
        this.placeDiscoveryService = placeDiscoveryService;
        this.tripHistoryService = tripHistoryService;
    }

    public TripPlanResponse planTrip(TripPlanRequest request) {
        AiTripDraft draft;
        String mode;
        List<TripPlanResponse.PlanningSource> planningSources = List.of();

        if (openAiTripDraftService.isConfigured()) {
            try {
                OpenAiTripDraftService.GenerationResult result = openAiTripDraftService.generate(request);
                if (isTooGeneric(result.draft(), request.destination())) {
                    log.warn("OpenAI itinerary for '{}' was too generic; falling back to demo template.", request.destination());
                    draft = demoTripDraftService.generate(request);
                    planningSources = List.of();
                    mode = "demo-fallback";
                } else {
                    draft = result.draft();
                    planningSources = result.planningSources();
                    mode = result.webSearchUsed() ? "openai-web-search" : "openai";
                }
            } catch (Exception exception) {
                log.warn("OpenAI itinerary generation failed for '{}'; falling back to demo template. Reason: {}",
                        request.destination(),
                        exception.getMessage());
                draft = demoTripDraftService.generate(request);
                mode = "demo-fallback";
            }
        } else {
            draft = demoTripDraftService.generate(request);
            mode = "demo";
        }

        List<TripPlanResponse.DayPlan> days = mapDays(draft.dailyItinerary());
        List<TripPlanResponse.DayPlan> enrichedDays = placeDiscoveryService.enrichActivities(request.destination(), days);
        if (enrichedDays != null && !enrichedDays.isEmpty()) {
            days = enrichedDays;
        }
        PlaceDiscoveryService.DiscoveryBundle places = placeDiscoveryService.discover(
                new PlaceDiscoveryService.DiscoveryRequest(
                        request.destination(),
                        request.travelMode(),
                        toRouteAnchors(days)
                )
        );

        TripPlanResponse response = new TripPlanResponse(
                UUID.randomUUID(),
                request.destination(),
                request.travelMode(),
                mode,
                Instant.now(),
                false,
                draft.tripSummary(),
                draft.totalDays(),
                new TripPlanResponse.Accommodation(
                        draft.recommendedAccommodation().area(),
                        draft.recommendedAccommodation().reason()
                ),
                places.hotels(),
                places.foodNearby(),
                places.foodHot(),
                places.parking(),
                places.refuel(),
                places.charging(),
                places.restaurants(),
                days,
                planningSources,
                places.attribution()
        );

        return tripHistoryService.save(response);
    }

    public List<TripPlanResponse> getHistory() {
        return tripHistoryService.listHistory().stream()
                .map(this::enrichSavedPlanActivities)
                .toList();
    }

    public List<TripPlanResponse> getFavorites() {
        return tripHistoryService.listFavorites().stream()
                .map(this::enrichSavedPlanActivities)
                .toList();
    }

    public TripPlanResponse updateFavorite(UUID id, boolean favorite) {
        return enrichSavedPlanActivities(tripHistoryService.updateFavorite(id, favorite));
    }

    private static List<TripPlanResponse.DayPlan> mapDays(List<AiTripDraft.DayPlan> input) {
        return input.stream()
                .map(day -> new TripPlanResponse.DayPlan(
                        day.day(),
                        day.theme(),
                        day.activities().stream()
                                .map(activity -> normalizeActivity(new TripPlanResponse.Activity(
                                        activity.time(),
                                        activity.type(),
                                        activity.name(),
                                        activity.description(),
                                        activity.transitTip(),
                                        activity.socialLink(),
                                        activity.latitude(),
                                        activity.longitude()
                                )))
                                .toList()
                ))
                .toList();
    }

    private static List<PlaceDiscoveryService.RouteAnchor> toRouteAnchors(List<TripPlanResponse.DayPlan> days) {
        return days.stream()
                .flatMap(day -> day.activities().stream()
                        .map(activity -> new PlaceDiscoveryService.RouteAnchor(
                                day.day(),
                                activity.name(),
                                activity.latitude(),
                                activity.longitude()
                        )))
                .toList();
    }

    static boolean isTooGeneric(AiTripDraft draft, String destination) {
        String normalizedDestination = normalize(destination);
        return draft.dailyItinerary().stream()
                .flatMap(day -> day.activities().stream())
                .anyMatch(activity -> {
                    String normalizedName = normalize(activity.name());
                    if (normalizedName.isBlank()) {
                        return true;
                    }
                    if (GENERIC_ACTIVITY_MARKERS.stream().map(TripPlannerService::normalize).anyMatch(normalizedName::contains)) {
                        return true;
                    }
                    return !normalizedDestination.isBlank() && (
                            normalizedName.equals(normalizedDestination)
                                    || normalizedName.equals(normalizedDestination + "核心片区")
                                    || normalizedName.equals(normalizedDestination + "美食聚集带")
                    );
                });
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private TripPlanResponse enrichSavedPlanActivities(TripPlanResponse plan) {
        if (plan == null) {
            return null;
        }

        TripPlanResponse normalizedPlan = withDays(plan, normalizeDays(plan.dailyItinerary()));
        if (!hasMissingActivityCoordinates(normalizedPlan.dailyItinerary())) {
            return persistIfChanged(plan, normalizedPlan);
        }

        List<TripPlanResponse.DayPlan> enrichedDays =
                placeDiscoveryService.enrichActivities(plan.destination(), normalizedPlan.dailyItinerary());
        if (enrichedDays == null || enrichedDays.isEmpty()) {
            return persistIfChanged(plan, normalizedPlan);
        }

        return persistIfChanged(normalizedPlan, withDays(normalizedPlan, normalizeDays(enrichedDays)));
    }

    private static boolean hasMissingActivityCoordinates(List<TripPlanResponse.DayPlan> days) {
        if (days == null || days.isEmpty()) {
            return false;
        }

        return days.stream()
                .flatMap(day -> day.activities().stream())
                .anyMatch(activity -> activity.latitude() == null || activity.longitude() == null);
    }

    private static List<TripPlanResponse.DayPlan> normalizeDays(List<TripPlanResponse.DayPlan> days) {
        if (days == null || days.isEmpty()) {
            return List.of();
        }

        return days.stream()
                .map(day -> new TripPlanResponse.DayPlan(
                        day.day(),
                        day.theme(),
                        day.activities().stream()
                                .map(TripPlannerService::normalizeActivity)
                                .toList()
                ))
                .toList();
    }

    private static TripPlanResponse.Activity normalizeActivity(TripPlanResponse.Activity activity) {
        if (activity == null) {
            return null;
        }

        String description = activity.description();
        String extractedSocialLink = extractFirstGroup(description, EMBEDDED_SOCIAL_LINK_PATTERN);
        Double extractedLatitude = extractDouble(description, EMBEDDED_LATITUDE_PATTERN);
        Double extractedLongitude = extractDouble(description, EMBEDDED_LONGITUDE_PATTERN);
        int metadataStart = findEmbeddedMetadataStart(description);

        String cleanedDescription = metadataStart >= 0
                ? description.substring(0, metadataStart).replaceFirst("[\\s,，'\"{]+$", "").trim()
                : description;

        return new TripPlanResponse.Activity(
                activity.time(),
                activity.type(),
                activity.name(),
                cleanedDescription == null || cleanedDescription.isBlank() ? activity.description() : cleanedDescription,
                activity.transitTip(),
                activity.socialLink() != null ? activity.socialLink() : extractedSocialLink,
                activity.latitude() != null ? activity.latitude() : extractedLatitude,
                activity.longitude() != null ? activity.longitude() : extractedLongitude
        );
    }

    private static int findEmbeddedMetadataStart(String description) {
        if (description == null || description.isBlank()) {
            return -1;
        }

        int start = Integer.MAX_VALUE;
        for (String token : List.of("'social_link'", "'latitude'", "'longitude'")) {
            int index = description.indexOf(token);
            if (index >= 0) {
                start = Math.min(start, index);
            }
        }
        return start == Integer.MAX_VALUE ? -1 : start;
    }

    private static String extractFirstGroup(String value, Pattern pattern) {
        if (value == null || value.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Double extractDouble(String value, Pattern pattern) {
        String matched = extractFirstGroup(value, pattern);
        if (matched == null || matched.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(matched);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private TripPlanResponse persistIfChanged(TripPlanResponse original, TripPlanResponse updated) {
        if (original.equals(updated)) {
            return updated;
        }
        TripPlanResponse persisted = tripHistoryService.save(updated);
        return persisted != null ? persisted : updated;
    }

    private static TripPlanResponse withDays(TripPlanResponse plan, List<TripPlanResponse.DayPlan> days) {
        return new TripPlanResponse(
                plan.id(),
                plan.destination(),
                plan.travelMode(),
                plan.planningMode(),
                plan.generatedAt(),
                plan.favorite(),
                plan.tripSummary(),
                plan.totalDays(),
                plan.recommendedAccommodation(),
                plan.recommendedHotels(),
                plan.recommendedFoodNearby(),
                plan.recommendedFoodHot(),
                plan.recommendedParking(),
                plan.recommendedRefuel(),
                plan.recommendedCharging(),
                plan.recommendedRestaurants(),
                days,
                plan.planningSources(),
                plan.attribution()
        );
    }
}
