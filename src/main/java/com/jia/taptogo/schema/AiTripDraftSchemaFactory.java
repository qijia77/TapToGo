package com.jia.taptogo.schema;

import java.util.List;
import java.util.Map;

public final class AiTripDraftSchemaFactory {

    private AiTripDraftSchemaFactory() {
    }

    public static Map<String, Object> create() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("trip_summary", "total_days", "recommended_accommodation", "daily_itinerary"),
                "properties", Map.of(
                        "trip_summary", Map.of("type", "string"),
                        "total_days", Map.of("type", "integer", "minimum", 1),
                        "recommended_accommodation", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("area", "reason"),
                                "properties", Map.of(
                                        "area", Map.of("type", "string"),
                                        "reason", Map.of("type", "string")
                                )
                        ),
                        "daily_itinerary", Map.of(
                                "type", "array",
                                "minItems", 1,
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("day", "theme", "activities"),
                                        "properties", Map.of(
                                                "day", Map.of("type", "integer"),
                                                "theme", Map.of("type", "string"),
                                                "activities", Map.of(
                                                        "type", "array",
                                                        "minItems", 1,
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "required", List.of("time", "type", "name", "description", "transit_tip"),
                                                                "properties", Map.of(
                                                                        "time", Map.of("type", "string"),
                                                                        "type", Map.of("type", "string"),
                                                                        "name", Map.of("type", "string"),
                                                                        "description", Map.of("type", "string"),
                                                                        "transit_tip", Map.of("type", "string")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
