package com.jia.taptogo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.taptogo.config.OpenAiProperties;
import com.jia.taptogo.model.FavoriteUpdateRequest;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import com.jia.taptogo.service.TripPlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripPlanController.class)
@Import(ApiExceptionHandler.class)
class TripPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TripPlannerService tripPlannerService;

    @MockBean
    private OpenAiProperties openAiProperties;

    @Test
    void shouldReturnTripPlan() throws Exception {
        given(tripPlannerService.planTrip(any())).willReturn(sampleResponse(false));

        TripPlanRequest request = new TripPlanRequest("Xian", "Public transit", 2);

        mockMvc.perform(post("/api/trips/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip_summary").value("City break"))
                .andExpect(jsonPath("$.recommended_hotels[0].name").value("Central Hotel"))
                .andExpect(jsonPath("$.recommended_food_nearby[0].name").value("Bell Tower Noodle"))
                .andExpect(jsonPath("$.recommended_food_nearby[0].day").value(1))
                .andExpect(jsonPath("$.recommended_food_hot[0].name").value("Shaanxi Banquet"))
                .andExpect(jsonPath("$.recommended_parking[0].category").value("Parking"))
                .andExpect(jsonPath("$.recommended_refuel[0].category").value("Fuel"))
                .andExpect(jsonPath("$.recommended_charging[0].category").value("Charging"))
                .andExpect(jsonPath("$.recommended_restaurants[0].name").value("Bell Tower Noodle"))
                .andExpect(jsonPath("$.daily_itinerary[0].activities[0].transit_tip").value("Walk there"))
                .andExpect(jsonPath("$.planning_sources[0].url").value("https://example.com/search-result"));
    }

    @Test
    void shouldReturnHistory() throws Exception {
        given(tripPlannerService.getHistory()).willReturn(List.of(sampleResponse(true)));

        mockMvc.perform(get("/api/trips/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].favorite").value(true))
                .andExpect(jsonPath("$[0].destination").value("Xian"));
    }

    @Test
    void shouldReturnFavorites() throws Exception {
        given(tripPlannerService.getFavorites()).willReturn(List.of(sampleResponse(true)));

        mockMvc.perform(get("/api/trips/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].favorite").value(true))
                .andExpect(jsonPath("$[0].planning_mode").value("demo"));
    }

    @Test
    void shouldUpdateFavorite() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(tripPlannerService.updateFavorite(eq(id), eq(true))).willReturn(sampleResponse(true));

        mockMvc.perform(patch("/api/trips/history/{id}/favorite", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FavoriteUpdateRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    void shouldRejectInvalidTripPlanRequest() throws Exception {
        mockMvc.perform(post("/api/trips/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination": "",
                                  "travelMode": "",
                                  "days": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.destination").value("destination is required"))
                .andExpect(jsonPath("$.errors.travelMode").value("travelMode is required"))
                .andExpect(jsonPath("$.errors.days").value("days must be at least 1"));
    }

    @Test
    void shouldRejectFavoriteUpdateWhenFavoriteIsMissing() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(patch("/api/trips/history/{id}/favorite", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.favorite").value("favorite is required"));
    }

    @Test
    void shouldReturnNotFoundWhenFavoriteTargetDoesNotExist() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(tripPlannerService.updateFavorite(eq(id), eq(true)))
                .willThrow(new NoSuchElementException("Trip plan not found: " + id));

        mockMvc.perform(patch("/api/trips/history/{id}/favorite", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FavoriteUpdateRequest(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trip plan not found: " + id));
    }

    @Test
    void shouldReturnHealthMode() throws Exception {
        given(openAiProperties.configured()).willReturn(true);
        given(openAiProperties.webSearchEnabled()).willReturn(true);

        mockMvc.perform(get("/api/trips/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.mode").value("openai-web-search"))
                .andExpect(jsonPath("$.capability_mode").value("openai-web-search"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.web_search_enabled").value(true));
    }

    @Test
    void shouldLeaveGroupedListsEmptyWhenUsingLegacyConstructor() {
        TripPlanResponse response = new TripPlanResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Xian",
                "Public transit",
                "demo",
                Instant.parse("2026-03-25T10:15:30Z"),
                false,
                "City break",
                2,
                new TripPlanResponse.Accommodation("Downtown", "Easy to move around"),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Central Hotel",
                        "Hotel",
                        "Downtown",
                        "Named accommodation near the center.",
                        "OpenStreetMap",
                        "https://www.openstreetmap.org/node/1",
                        34.0,
                        108.0
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Bell Tower Noodle",
                        "Restaurant",
                        "Bell Tower Plaza",
                        "Start the trip with local noodles steps from the landmarks.",
                        "Local Guide",
                        "https://example.com/bell-tower-noodle",
                        34.244,
                        108.946
                )),
                List.of(new TripPlanResponse.DayPlan(
                        1,
                        "First look",
                        List.of(new TripPlanResponse.Activity(
                                "Morning",
                                "Sight",
                                "City Gate",
                                "Start with the landmark",
                                "Walk there",
                                "https://www.openstreetmap.org/node/3",
                                34.1,
                                108.9
                        ))
                )),
                List.of(new TripPlanResponse.PlanningSource(
                        "Search result",
                        "https://example.com/search-result"
                )),
                "OpenStreetMap attribution"
        );

        assertTrue(response.recommendedFoodNearby().isEmpty());
        assertTrue(response.recommendedFoodHot().isEmpty());
        assertTrue(response.recommendedParking().isEmpty());
        assertTrue(response.recommendedRefuel().isEmpty());
        assertTrue(response.recommendedCharging().isEmpty());
    }

    @Test
    void shouldPreserveGroupedListsWhenTogglingFavorite() {
        TripPlanResponse original = sampleResponse(false);
        TripPlanResponse updated = original.withFavorite(true);

        assertEquals(original.recommendedFoodNearby(), updated.recommendedFoodNearby());
        assertEquals(original.recommendedFoodHot(), updated.recommendedFoodHot());
        assertEquals(original.recommendedParking(), updated.recommendedParking());
        assertEquals(original.recommendedRefuel(), updated.recommendedRefuel());
        assertEquals(original.recommendedCharging(), updated.recommendedCharging());
        assertTrue(updated.favorite());
    }

    @Test
    void legacyPlaceRecommendationDefaultsDayToNull() {
        TripPlanResponse.PlaceRecommendation place = new TripPlanResponse.PlaceRecommendation(
                "Legacy Place",
                "Restaurant",
                "Legacy Address",
                "Original constructor test",
                "Legacy Source",
                "https://example.com/legacy",
                1.0,
                2.0
        );

        assertNull(place.day());
    }

    private TripPlanResponse sampleResponse(boolean favorite) {
        return new TripPlanResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Xian",
                "Public transit",
                "demo",
                Instant.parse("2026-03-25T10:15:30Z"),
                favorite,
                "City break",
                2,
                new TripPlanResponse.Accommodation("Downtown", "Easy to move around"),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Central Hotel",
                        "Hotel",
                        "Downtown",
                        "Named accommodation near the center.",
                        "OpenStreetMap",
                        "https://www.openstreetmap.org/node/1",
                        34.0,
                        108.0,
                        null
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Bell Tower Noodle",
                        "Restaurant",
                        "Bell Tower Plaza",
                        "Start the trip with local noodles steps from the landmarks.",
                        "Local Guide",
                        "https://example.com/bell-tower-noodle",
                        34.244,
                        108.946,
                        1
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Shaanxi Banquet",
                        "Restaurant",
                        "East Market Street",
                        "Banquet-style dining for a celebratory evening.",
                        "Local Guide",
                        "https://example.com/shaanxi-banquet",
                        34.247,
                        108.933,
                        null
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "South Gate Parking",
                        "Parking",
                        "South Gate Plaza",
                        "Covered parking close to the day-one starting point.",
                        "City Parking Authority",
                        "https://example.com/south-gate-parking",
                        34.200,
                        108.920,
                        1
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Ring Road Fuel",
                        "Fuel",
                        "Ring Road Station",
                        "Full-service fuel stop before the ring road stretch.",
                        "State Fuel",
                        "https://example.com/ring-road-fuel",
                        34.205,
                        108.915,
                        1
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "South EV Hub",
                        "Charging",
                        "South Tech Park",
                        "Level 3 chargers mapped for the second-day stretch.",
                        "Green Charge",
                        "https://example.com/south-ev-hub",
                        34.210,
                        108.925,
                        2
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Bell Tower Noodle",
                        "Restaurant",
                        "Bell Tower Plaza",
                        "Repeated highlight grouped under restaurants.",
                        "Local Guide",
                        "https://example.com/bell-tower-noodle",
                        34.244,
                        108.946,
                        1
                )),
                List.of(new TripPlanResponse.DayPlan(
                        1,
                        "First look",
                        List.of(new TripPlanResponse.Activity(
                                "Morning",
                                "Sight",
                                "City Gate",
                                "Start with the landmark",
                                "Walk there",
                                "https://www.openstreetmap.org/node/3",
                                34.1,
                                108.9
                        ))
                )),
                List.of(new TripPlanResponse.PlanningSource(
                        "Search result",
                        "https://example.com/search-result"
                )),
                "OpenStreetMap attribution"
        );
    }
}
