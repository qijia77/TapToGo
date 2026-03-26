package com.jia.taptogo.service;

import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoTripDraftServiceTest {

    private final DemoTripDraftService service = new DemoTripDraftService();

    @Test
    void shouldGenerateConcreteTokyoPlan() {
        AiTripDraft draft = service.generate(new TripPlanRequest("东京", "地铁+步行", 2));

        assertEquals("浅草寺", draft.dailyItinerary().get(0).activities().get(0).name());
        assertEquals("上野阿美横町", draft.dailyItinerary().get(0).activities().get(1).name());
        assertEquals("明治神宫", draft.dailyItinerary().get(1).activities().get(0).name());
        assertTrue(draft.dailyItinerary().stream()
                .flatMap(day -> day.activities().stream())
                .allMatch(activity -> activity.socialLink() != null && !activity.socialLink().isBlank()));
        assertFalse(draft.dailyItinerary().stream()
                .flatMap(day -> day.activities().stream())
                .anyMatch(activity -> activity.name().contains("核心片区")));
    }
}
