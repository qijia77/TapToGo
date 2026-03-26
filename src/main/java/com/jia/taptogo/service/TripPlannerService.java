package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TripPlannerService {

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
                    draft = demoTripDraftService.generate(request);
                    planningSources = List.of();
                    mode = "demo-fallback";
                } else {
                    draft = result.draft();
                    planningSources = result.planningSources();
                    mode = result.webSearchUsed() ? "openai-web-search" : "openai";
                }
            } catch (Exception exception) {
                draft = demoTripDraftService.generate(request);
                mode = "demo-fallback";
            }
        } else {
            draft = demoTripDraftService.generate(request);
            mode = "demo";
        }

        PlaceDiscoveryService.DiscoveryBundle places = placeDiscoveryService.discover(request.destination());

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
                places.restaurants(),
                mapDays(draft.dailyItinerary()),
                planningSources,
                places.attribution()
        );

        return tripHistoryService.save(response);
    }

    public List<TripPlanResponse> getHistory() {
        return tripHistoryService.listHistory();
    }

    public List<TripPlanResponse> getFavorites() {
        return tripHistoryService.listFavorites();
    }

    public TripPlanResponse updateFavorite(UUID id, boolean favorite) {
        return tripHistoryService.updateFavorite(id, favorite);
    }

    private static List<TripPlanResponse.DayPlan> mapDays(List<AiTripDraft.DayPlan> input) {
        return input.stream()
                .map(day -> new TripPlanResponse.DayPlan(
                        day.day(),
                        day.theme(),
                        day.activities().stream()
                                .map(activity -> new TripPlanResponse.Activity(
                                        activity.time(),
                                        activity.type(),
                                        activity.name(),
                                        activity.description(),
                                        activity.transitTip(),
                                        activity.socialLink(),
                                        activity.latitude(),
                                        activity.longitude()
                                ))
                                .toList()
                ))
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
}
