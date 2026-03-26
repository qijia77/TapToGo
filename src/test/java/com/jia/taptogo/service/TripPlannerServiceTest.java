package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TripPlannerServiceTest {

    @Mock
    private OpenAiTripDraftService openAiTripDraftService;

    @Mock
    private DemoTripDraftService demoTripDraftService;

    @Mock
    private PlaceDiscoveryService placeDiscoveryService;

    @Mock
    private TripHistoryService tripHistoryService;

    @InjectMocks
    private TripPlannerService tripPlannerService;

    @Test
    void shouldFallbackToConcreteTemplateWhenAiDraftIsGeneric() {
        TripPlanRequest request = new TripPlanRequest("东京", "地铁+步行", 2);
        AiTripDraft genericDraft = new AiTripDraft(
                "泛化行程",
                2,
                new AiTripDraft.Accommodation("东京中心区", "方便移动"),
                List.of(new AiTripDraft.DayPlan(
                        1,
                        "初识东京",
                        List.of(
                                new AiTripDraft.Activity("上午", "景点", "东京核心片区", "泛化描述", "步行", null, null, null),
                                new AiTripDraft.Activity("下午", "美食", "东京美食聚集带", "泛化描述", "步行", null, null, null)
                        )
                ))
        );
        AiTripDraft refinedDraft = new DemoTripDraftService().generate(request);

        given(openAiTripDraftService.isConfigured()).willReturn(true);
        given(openAiTripDraftService.generate(eq(request))).willReturn(
                new OpenAiTripDraftService.GenerationResult(
                        genericDraft,
                        true,
                        List.of(new TripPlanResponse.PlanningSource("Source", "https://example.com"))
                )
        );
        given(demoTripDraftService.generate(eq(request))).willReturn(refinedDraft);
        given(placeDiscoveryService.discover("东京")).willReturn(new PlaceDiscoveryService.DiscoveryBundle(
                "东京",
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Hotel",
                        "Hotel",
                        "Tokyo",
                        "Reason",
                        "System fallback",
                        null,
                        null,
                        null
                )),
                List.of(new TripPlanResponse.PlaceRecommendation(
                        "Food",
                        "Restaurant",
                        "Tokyo",
                        "Reason",
                        "System fallback",
                        null,
                        null,
                        null
                )),
                "fallback"
        ));
        given(tripHistoryService.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        TripPlanResponse response = tripPlannerService.planTrip(request);

        assertEquals("demo-fallback", response.planningMode());
        assertTrue(response.planningSources().isEmpty());
        assertEquals("浅草寺", response.dailyItinerary().get(0).activities().get(0).name());
        verify(demoTripDraftService).generate(eq(request));
    }
}
