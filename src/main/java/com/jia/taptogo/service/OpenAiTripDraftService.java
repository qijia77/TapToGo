package com.jia.taptogo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.taptogo.config.OpenAiProperties;
import com.jia.taptogo.model.AiTripDraft;
import com.jia.taptogo.model.TripPlanRequest;
import com.jia.taptogo.model.TripPlanResponse;
import com.jia.taptogo.prompt.TravelPlannerPromptBuilder;
import com.jia.taptogo.schema.AiTripDraftSchemaFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenAiTripDraftService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;
    private final TravelPlannerPromptBuilder promptBuilder;
    private final Validator validator;

    public OpenAiTripDraftService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            OpenAiProperties properties,
            TravelPlannerPromptBuilder promptBuilder,
            Validator validator
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    public boolean webSearchEnabled() {
        return properties.webSearchEnabled();
    }

    public GenerationResult generate(TripPlanRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("temperature", properties.temperature());
        payload.put("max_output_tokens", 3200);
        payload.put("instructions", promptBuilder.buildSystemPrompt());
        payload.put("input", promptBuilder.buildUserPrompt(request));
        payload.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "travel_itinerary",
                        "strict", true,
                        "schema", AiTripDraftSchemaFactory.create()
                )
        ));

        if (properties.webSearchEnabled()) {
            payload.put("tools", List.of(Map.of("type", "web_search")));
            payload.put("tool_choice", "auto");
            payload.put("include", List.of("web_search_call.action.sources"));
        }

        JsonNode response = restClient.post()
                .uri("/v1/responses")
                .header("Authorization", "Bearer " + properties.apiKey())
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        try {
            AiTripDraft draft = objectMapper.readValue(extractOutputText(response), AiTripDraft.class);
            validate(draft);
            return new GenerationResult(
                    draft,
                    usedWebSearch(response),
                    extractSources(response)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse structured response from OpenAI.", exception);
        }
    }

    private static String extractOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                String type = content.path("type").asText();
                if ("refusal".equals(type)) {
                    throw new IllegalStateException("OpenAI refused to generate the itinerary.");
                }
                if ("output_text".equals(type)) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output_text.");
    }

    private void validate(AiTripDraft draft) {
        Set<ConstraintViolation<AiTripDraft>> violations = validator.validate(draft);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("OpenAI returned an invalid itinerary.", violations);
        }
    }

    private static boolean usedWebSearch(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            if ("web_search_call".equals(output.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private static List<TripPlanResponse.PlanningSource> extractSources(JsonNode response) {
        LinkedHashSet<TripPlanResponse.PlanningSource> sources = new LinkedHashSet<>();

        for (JsonNode output : response.path("output")) {
            if ("web_search_call".equals(output.path("type").asText())) {
                for (JsonNode source : output.path("action").path("sources")) {
                    addSource(sources, source.path("title").asText(""), source.path("url").asText(""));
                }
            }

            for (JsonNode content : output.path("content")) {
                for (JsonNode annotation : content.path("annotations")) {
                    if ("url_citation".equals(annotation.path("type").asText())) {
                        addSource(
                                sources,
                                annotation.path("title").asText(""),
                                annotation.path("url").asText("")
                        );
                    }
                }
            }
        }

        return new ArrayList<>(sources);
    }

    private static void addSource(Set<TripPlanResponse.PlanningSource> sources, String title, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String normalizedTitle = title == null || title.isBlank() ? url : title.strip();
        sources.add(new TripPlanResponse.PlanningSource(normalizedTitle, url.strip()));
    }

    public record GenerationResult(
            AiTripDraft draft,
            boolean webSearchUsed,
            List<TripPlanResponse.PlanningSource> planningSources
    ) {
    }
}
