package com.jia.taptogo.prompt;

import com.jia.taptogo.model.TripPlanRequest;
import org.springframework.stereotype.Component;

@Component
public class TravelPlannerPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are an expert travel planner serving Chinese-speaking users.
                Every user-facing field in the final JSON must be natural Simplified Chinese.

                Core requirements:
                1. Build a realistic, executable itinerary with practical sequencing by geography and time of day.
                2. Use real named POIs, neighborhoods, streets, museums, scenic spots, restaurants, and markets.
                3. Never use generic placeholder names such as "<destination>主地标区", "<destination>老城口碑餐馆街区",
                   "<destination>夜景步道或河岸区域", "central district", "food corridor", "main landmark area",
                   or any similarly vague label that cannot be searched on a map immediately.
                4. Prefer specific places that are genuinely associated with the destination. Avoid fabricated or overly broad area labels.
                5. Keep each day geographically coherent and aligned with the selected travel mode.
                6. Avoid generic chain fast food or generic mall food courts unless they are unusually destination-specific.
                7. When web search is available, prefer current and reputable information and ground the itinerary in those findings.
                8. Provide latitude and longitude when you can identify a real place with reasonable confidence. If uncertain, return null instead of inventing coordinates.
                9. Generate a Xiaohongshu search URL in social_link for worthwhile stops.
                10. Return JSON only. No Markdown, no code fences, and no explanation outside the schema.
                11. Make every description and transit_tip concrete, useful, and specific.
                """;
    }

    public String buildUserPrompt(TripPlanRequest request) {
        return """
                User request:
                - Destination: %s
                - Travel mode: %s
                - Days: %d

                Output requirements:
                1. All visible copy inside JSON must be Simplified Chinese.
                2. Recommended accommodation must describe the best area to stay in, not just a hotel name.
                3. Each day should cover a practical morning / afternoon / evening flow unless the destination strongly requires a different rhythm.
                4. Use concrete, real place names that a traveler can search on a map immediately.
                5. Do not output placeholder names derived from the destination, such as "%s主地标区", "%s老城口碑餐馆街区", or "%s夜景步道或河岸区域".
                6. Do not output vague labels like "central district", "food street area", or "night view area" unless a real named place is provided.
                7. If you are uncertain about a place, choose a better-known real alternative rather than inventing a vague area.
                8. Keep the route practical for %s and avoid long zig-zag cross-city movement.
                9. Return valid JSON matching this structure exactly:
                {
                  "trip_summary": "One sentence summary in Simplified Chinese",
                  "total_days": %d,
                  "recommended_accommodation": {
                    "area": "Recommended area in Simplified Chinese",
                    "reason": "Why this area is best in Simplified Chinese"
                  },
                  "daily_itinerary": [
                    {
                      "day": 1,
                      "theme": "Day theme in Simplified Chinese",
                      "activities": [
                        {
                          "time": "上午/下午/晚上",
                          "type": "景点/美食/交通/休闲",
                          "name": "Specific real place name in Simplified Chinese",
                          "description": "Specific activity details in Simplified Chinese",
                          "transit_tip": "Practical transit advice in Simplified Chinese",
                          "social_link": "Xiaohongshu search URL",
                          "latitude": 31.2304,
                          "longitude": 121.4737
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                request.destination(),
                request.travelMode(),
                request.days(),
                request.destination(),
                request.destination(),
                request.destination(),
                request.travelMode(),
                request.days()
        );
    }
}
