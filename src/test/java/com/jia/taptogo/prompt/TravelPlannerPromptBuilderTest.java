package com.jia.taptogo.prompt;

import com.jia.taptogo.model.TripPlanRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelPlannerPromptBuilderTest {

    private final TravelPlannerPromptBuilder builder = new TravelPlannerPromptBuilder();

    @Test
    void shouldRequireSimplifiedChineseAndRealNamedPlaces() {
        String prompt = builder.buildSystemPrompt();

        assertTrue(prompt.contains("Simplified Chinese"));
        assertTrue(prompt.contains("real named POIs"));
        assertTrue(prompt.contains("Never use generic placeholder names"));
        assertTrue(prompt.contains("Return JSON only"));
    }

    @Test
    void shouldEmbedConcreteUserRequestAndPlaceholderBan() {
        TripPlanRequest request = new TripPlanRequest("Anshun", "Self-drive", 4);

        String prompt = builder.buildUserPrompt(request);

        assertTrue(prompt.contains("Destination: Anshun"));
        assertTrue(prompt.contains("Travel mode: Self-drive"));
        assertTrue(prompt.contains("Days: 4"));
        assertTrue(prompt.contains("Anshun主地标区"));
        assertTrue(prompt.contains("Use concrete, real place names"));
        assertTrue(prompt.contains("Return valid JSON"));
    }
}
