package com.jia.taptogo.controller;

import com.jia.taptogo.config.OpenAiProperties;
import com.jia.taptogo.model.FavoriteUpdateRequest;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import com.jia.taptogo.service.TripPlannerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripPlanController {

    private final TripPlannerService tripPlannerService;
    private final OpenAiProperties openAiProperties;

    public TripPlanController(TripPlannerService tripPlannerService, OpenAiProperties openAiProperties) {
        this.tripPlannerService = tripPlannerService;
        this.openAiProperties = openAiProperties;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        boolean configured = openAiProperties.configured();
        boolean webSearchEnabled = configured && openAiProperties.webSearchEnabled();
        String mode = configured
                ? (webSearchEnabled ? "openai-web-search" : "openai")
                : "demo";
        return Map.of(
                "status", "ok",
                "mode", mode,
                "capability_mode", mode,
                "configured", configured,
                "web_search_enabled", webSearchEnabled
        );
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TripPlanResponse planTrip(@Valid @RequestBody TripPlanRequest request) {
        return tripPlannerService.planTrip(request);
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TripPlanResponse> history() {
        return tripPlannerService.getHistory();
    }

    @GetMapping(value = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TripPlanResponse> favorites() {
        return tripPlannerService.getFavorites();
    }

    @PatchMapping(value = "/history/{id}/favorite", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TripPlanResponse updateFavorite(@PathVariable UUID id, @Valid @RequestBody FavoriteUpdateRequest request) {
        return tripPlannerService.updateFavorite(id, request.favorite());
    }
}
