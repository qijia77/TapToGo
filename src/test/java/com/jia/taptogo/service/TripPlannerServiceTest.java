package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        given(placeDiscoveryService.discover(any(PlaceDiscoveryService.DiscoveryRequest.class))).willReturn(new PlaceDiscoveryService.DiscoveryBundle(
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

    @Test
    void shouldForwardDiscoveryRequestWithRouteAnchorsFromMappedDays() {
        TripPlanRequest request = new TripPlanRequest("上海", "Public transit", 2);
        AiTripDraft draft = new AiTripDraft(
                "测试行程",
                2,
                new AiTripDraft.Accommodation("外滩", "交通方便"),
                List.of(
                        new AiTripDraft.DayPlan(
                                1,
                                "城市经典",
                                List.of(new AiTripDraft.Activity(
                                        "上午",
                                        "景点",
                                        "The Bund",
                                        "Walk along the river",
                                        "地铁 2 号线",
                                        null,
                                        31.2400,
                                        121.4900
                                ))
                        ),
                        new AiTripDraft.DayPlan(
                                2,
                                "历史街区",
                                List.of(new AiTripDraft.Activity(
                                        "下午",
                                        "景点",
                                        "Yu Garden",
                                        "Explore old Shanghai",
                                        "地铁 10 号线",
                                        null,
                                        31.2273,
                                        121.4920
                                ))
                        )
                )
        );

        given(openAiTripDraftService.isConfigured()).willReturn(false);
        given(demoTripDraftService.generate(eq(request))).willReturn(draft);
        given(placeDiscoveryService.discover(any(PlaceDiscoveryService.DiscoveryRequest.class))).willReturn(
                new PlaceDiscoveryService.DiscoveryBundle("上海", List.of(), List.of(), "fallback")
        );
        given(tripHistoryService.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        tripPlannerService.planTrip(request);

        ArgumentCaptor<PlaceDiscoveryService.DiscoveryRequest> requestCaptor =
                ArgumentCaptor.forClass(PlaceDiscoveryService.DiscoveryRequest.class);
        verify(placeDiscoveryService).discover(requestCaptor.capture());
        PlaceDiscoveryService.DiscoveryRequest captured = requestCaptor.getValue();

        assertEquals("上海", captured.destination());
        assertEquals("Public transit", captured.travelMode());
        assertEquals(
                List.of(
                        new PlaceDiscoveryService.RouteAnchor(1, "The Bund", 31.2400, 121.4900),
                        new PlaceDiscoveryService.RouteAnchor(2, "Yu Garden", 31.2273, 121.4920)
                ),
                captured.anchors()
        );
    }
    @Test
    void shouldEnrichActivityCoordinatesBeforeDiscoveryAndResponse() {
        TripPlanRequest request = new TripPlanRequest("杭州", "Self-drive", 1);
        AiTripDraft draft = new AiTripDraft(
                "杭州西湖一日",
                1,
                new AiTripDraft.Accommodation("西湖东侧", "方便收尾"),
                List.of(new AiTripDraft.DayPlan(
                        1,
                        "西湖南线",
                        List.of(
                                new AiTripDraft.Activity("下午", "景点", "雷峰塔景区", "登塔看西湖", "停车后步行", null, null, null),
                                new AiTripDraft.Activity("晚上", "休闲", "湖滨步行街", "散步收尾", "回程顺路", null, null, null)
                        )
                ))
        );
        List<TripPlanResponse.DayPlan> enrichedDays = List.of(new TripPlanResponse.DayPlan(
                1,
                "西湖南线",
                List.of(
                        new TripPlanResponse.Activity("下午", "景点", "雷峰塔景区", "登塔看西湖", "停车后步行", null, 30.2311, 120.1488),
                        new TripPlanResponse.Activity("晚上", "休闲", "湖滨步行街", "散步收尾", "回程顺路", null, 30.2572, 120.1613)
                )
        ));

        given(openAiTripDraftService.isConfigured()).willReturn(false);
        given(demoTripDraftService.generate(eq(request))).willReturn(draft);
        given(placeDiscoveryService.discover(any(PlaceDiscoveryService.DiscoveryRequest.class))).willReturn(
                new PlaceDiscoveryService.DiscoveryBundle("杭州", List.of(), List.of(), "fallback")
        );
        given(tripHistoryService.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        TripPlanResponse response = tripPlannerService.planTrip(request);

        ArgumentCaptor<PlaceDiscoveryService.DiscoveryRequest> requestCaptor =
                ArgumentCaptor.forClass(PlaceDiscoveryService.DiscoveryRequest.class);
        verify(placeDiscoveryService).discover(requestCaptor.capture());
        PlaceDiscoveryService.DiscoveryRequest captured = requestCaptor.getValue();

        assertEquals(
                List.of(
                        new PlaceDiscoveryService.RouteAnchor(1, "雷峰塔景区", 30.2311, 120.1488),
                        new PlaceDiscoveryService.RouteAnchor(1, "湖滨步行街", 30.2572, 120.1613)
                ),
                captured.anchors()
        );
        assertNotNull(response.dailyItinerary().get(0).activities().get(0).latitude());
        assertNotNull(response.dailyItinerary().get(0).activities().get(0).longitude());
        assertEquals(30.2311, response.dailyItinerary().get(0).activities().get(0).latitude());
        assertEquals(120.1488, response.dailyItinerary().get(0).activities().get(0).longitude());
    }

    @Test
    void shouldEnrichHistoricalPlansWhenCoordinatesAreMissing() {
        TripPlanResponse stored = new TripPlanResponse(
                java.util.UUID.randomUUID(),
                "杭州",
                "Self-drive",
                "openai-web-search",
                java.time.Instant.now(),
                false,
                "杭州西湖一日",
                1,
                new TripPlanResponse.Accommodation("西湖东侧", "方便收尾"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new TripPlanResponse.DayPlan(
                        1,
                        "西湖南线",
                        List.of(
                                new TripPlanResponse.Activity("下午", "景点", "雷峰塔景区", "登塔看西湖", "停车后步行", null, null, null),
                                new TripPlanResponse.Activity("晚上", "休闲", "湖滨步行街", "散步收尾", "回程顺路", null, null, null)
                        )
                )),
                List.of(),
                "fallback"
        );
        List<TripPlanResponse.DayPlan> enrichedDays = List.of(new TripPlanResponse.DayPlan(
                1,
                "西湖南线",
                List.of(
                        new TripPlanResponse.Activity("下午", "景点", "雷峰塔景区", "登塔看西湖", "停车后步行", null, 30.2311, 120.1488),
                        new TripPlanResponse.Activity("晚上", "休闲", "湖滨步行街", "散步收尾", "回程顺路", null, 30.2572, 120.1613)
                )
        ));

        given(tripHistoryService.listHistory()).willReturn(List.of(stored));
        given(placeDiscoveryService.enrichActivities(eq("杭州"), any())).willReturn(enrichedDays);

        List<TripPlanResponse> result = tripPlannerService.getHistory();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).dailyItinerary().get(0).activities().size());
    }
    @Test
    void shouldExtractEmbeddedActivityMetadataBeforeSavingPlan() {
        TripPlanRequest request = new TripPlanRequest("Nanning", "Self-drive", 1);
        AiTripDraft draft = new AiTripDraft(
                "Nanning culture route",
                1,
                new AiTripDraft.Accommodation("Qingxiu", "Convenient for day trips"),
                List.of(new AiTripDraft.DayPlan(
                        1,
                        "Culture day",
                        List.of(new AiTripDraft.Activity(
                                "Morning",
                                "Spot",
                                "Guangxi Museum of Nationalities",
                                "Museum visit.','social_link':'https://example.com/post','latitude':22.818,'longitude':108.404},{",
                                "Drive there directly",
                                null,
                                null,
                                null
                        ))
                ))
        );

        given(openAiTripDraftService.isConfigured()).willReturn(false);
        given(demoTripDraftService.generate(eq(request))).willReturn(draft);
        given(placeDiscoveryService.enrichActivities(eq("Nanning"), any())).willAnswer(invocation -> invocation.getArgument(1));
        given(placeDiscoveryService.discover(any(PlaceDiscoveryService.DiscoveryRequest.class))).willReturn(
                new PlaceDiscoveryService.DiscoveryBundle("Nanning", List.of(), List.of(), "fallback")
        );
        given(tripHistoryService.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        TripPlanResponse response = tripPlannerService.planTrip(request);

        TripPlanResponse.Activity activity = response.dailyItinerary().get(0).activities().get(0);
        assertEquals("Museum visit.", activity.description());
        assertEquals("https://example.com/post", activity.socialLink());
        assertEquals(22.818, activity.latitude());
        assertEquals(108.404, activity.longitude());
    }

    @Test
    void shouldRepairEmbeddedActivityMetadataInSavedHistory() {
        TripPlanResponse stored = new TripPlanResponse(
                java.util.UUID.randomUUID(),
                "Nanning",
                "Self-drive",
                "openai-web-search",
                java.time.Instant.now(),
                false,
                "Nanning route",
                1,
                new TripPlanResponse.Accommodation("Qingxiu", "Convenient"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new TripPlanResponse.DayPlan(
                        1,
                        "Culture day",
                        List.of(new TripPlanResponse.Activity(
                                "Morning",
                                "Spot",
                                "Guangxi Museum of Nationalities",
                                "Museum visit.','social_link':'https://example.com/post','latitude':22.818,'longitude':108.404},{",
                                "Drive there directly",
                                null,
                                null,
                                null
                        ))
                )),
                List.of(),
                "fallback"
        );

        given(tripHistoryService.listHistory()).willReturn(List.of(stored));
        given(tripHistoryService.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<TripPlanResponse> result = tripPlannerService.getHistory();

        TripPlanResponse.Activity activity = result.get(0).dailyItinerary().get(0).activities().get(0);
        assertEquals("Museum visit.", activity.description());
        assertEquals("https://example.com/post", activity.socialLink());
        assertEquals(22.818, activity.latitude());
        assertEquals(108.404, activity.longitude());
        verify(tripHistoryService).save(any());
    }
}
