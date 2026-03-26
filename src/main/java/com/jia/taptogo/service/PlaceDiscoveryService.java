package com.jia.taptogo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jia.taptogo.config.AmapProperties;
import com.jia.taptogo.model.TripPlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaceDiscoveryService {

    private static final Set<String> CHAIN_BRANDS = new HashSet<>(Arrays.asList(
            "mcdonald's", "mcdonalds", "kfc", "starbucks", "burger king", "subway",
            "pizza hut", "domino's", "dominos", "costa", "luckin coffee", "tim hortons",
            "\u9ea6\u5f53\u52b3", "\u80af\u5fb7\u57fa", "\u661f\u5df4\u514b", "\u6c49\u5821\u738b",
            "\u8d5b\u767e\u5473", "\u5fc5\u80dc\u5ba2", "\u8fbe\u7f8e\u4e50", "\u745e\u5e78", "tims"
    ));

    private final RestClient amapClient;
    private final AmapProperties properties;
    private final Map<String, DiscoveryBundle> cache = new ConcurrentHashMap<>();

    public PlaceDiscoveryService(RestClient.Builder restClientBuilder, AmapProperties properties) {
        this.amapClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public DiscoveryBundle discover(String destination) {
        String cacheKey = destination.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(cacheKey, ignored -> safeFetch(destination));
    }

    private DiscoveryBundle safeFetch(String destination) {
        if (!properties.configured()) {
            return fallbackBundle(destination, "高德 API Key 未配置。");
        }

        try {
            return fetch(destination);
        } catch (RuntimeException exception) {
            return fallbackBundle(destination, exception.getMessage());
        }
    }

    private DiscoveryBundle fetch(String destination) {
        GeoPoint geoPoint = geocode(destination);

        List<TripPlanResponse.PlaceRecommendation> hotels = queryPlaces(
                destination,
                geoPoint,
                properties.hotelRadiusMeters(),
                "\u9152\u5e97",
                "Hotel"
        );

        List<TripPlanResponse.PlaceRecommendation> restaurants = queryPlaces(
                destination,
                geoPoint,
                properties.restaurantRadiusMeters(),
                "\u9910\u5385",
                "Restaurant"
        );

        if (restaurants.size() < 4) {
            restaurants = mergePlaces(
                    restaurants,
                    queryPlaces(
                            destination,
                            geoPoint,
                            properties.restaurantRadiusMeters(),
                            "\u7f8e\u98df",
                            "Restaurant"
                    )
            );
        }

        if (hotels.isEmpty()) {
            hotels = List.of(fallbackPlace(destination + " central stay area", "Hotel", destination));
        }
        if (restaurants.isEmpty()) {
            restaurants = List.of(fallbackPlace(destination + " local dining area", "Restaurant", destination));
        }

        return new DiscoveryBundle(
                geoPoint.displayName(),
                hotels,
                restaurants,
                "酒店和餐饮推荐来自高德地图周边搜索。"
        );
    }

    private static DiscoveryBundle fallbackBundle(String destination, String reason) {
        String trimmedDestination = destination == null || destination.isBlank() ? "当前目的地" : destination.trim();
        return new DiscoveryBundle(
                trimmedDestination,
                List.of(fallbackPlace(trimmedDestination + " central stay area", "Hotel", trimmedDestination)),
                List.of(fallbackPlace(trimmedDestination + " local dining area", "Restaurant", trimmedDestination)),
                "当前无法使用高德地点补全，已启用降级推荐：" + reason
        );
    }

    private GeoPoint geocode(String destination) {
        String uri = "/v3/geocode/geo?address=" + encode(destination) + "&output=json&key=" + encode(properties.apiKey());

        JsonNode response = amapClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Amap geocoding returned an empty response.");
        }

        if (!"1".equals(response.path("status").asText())) {
            throw new IllegalStateException("Amap geocoding failed for destination: " + destination);
        }

        JsonNode geocodes = response.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw new IllegalStateException("Could not geocode destination: " + destination);
        }

        JsonNode first = geocodes.get(0);
        String location = first.path("location").asText("");
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new IllegalStateException("Amap geocoding returned an invalid location for destination: " + destination);
        }

        try {
            double longitude = Double.parseDouble(parts[0]);
            double latitude = Double.parseDouble(parts[1]);
            String displayName = first.path("formatted_address").asText(destination);
            return new GeoPoint(latitude, longitude, displayName);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Amap geocoding returned non-numeric coordinates for destination: " + destination, exception);
        }
    }

    private List<TripPlanResponse.PlaceRecommendation> queryPlaces(
            String destination,
            GeoPoint geoPoint,
            int radiusMeters,
            String keyword,
            String category
    ) {
        String uri = "/v3/place/around?key=" + encode(properties.apiKey())
                + "&location=" + encode(geoPoint.longitude() + "," + geoPoint.latitude())
                + "&radius=" + radiusMeters
                + "&keywords=" + encode(keyword)
                + "&sortrule=distance&offset=15&page=1&extensions=base";

        JsonNode response = amapClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Amap nearby search returned an empty response.");
        }

        if (!"1".equals(response.path("status").asText())) {
            throw new IllegalStateException("Amap nearby search failed for destination: " + destination);
        }

        List<TripPlanResponse.PlaceRecommendation> places = new ArrayList<>();
        for (JsonNode poi : response.path("pois")) {
            String name = poi.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            if (shouldSkip(name, poi.path("type").asText(""), category)) {
                continue;
            }

            Location location = parseLocation(poi.path("location").asText(""));
            places.add(new TripPlanResponse.PlaceRecommendation(
                    name,
                    category,
                    formatAddress(poi, geoPoint.displayName()),
                    buildReason(category, destination, poi.path("type").asText("")),
                    "Amap",
                    "https://www.xiaohongshu.com/search_result?keyword=" + encode(name),
                    location.latitude(),
                    location.longitude()
            ));

            if (places.size() > 12) {
                break;
            }
        }

        return places.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(place ->
                        squaredDistance(geoPoint.latitude(), geoPoint.longitude(), place.latitude(), place.longitude())))
                .limit(4)
                .toList();
    }

    private static List<TripPlanResponse.PlaceRecommendation> mergePlaces(
            List<TripPlanResponse.PlaceRecommendation> primary,
            List<TripPlanResponse.PlaceRecommendation> secondary
    ) {
        return java.util.stream.Stream.concat(primary.stream(), secondary.stream())
                .distinct()
                .limit(4)
                .toList();
    }

    private static TripPlanResponse.PlaceRecommendation fallbackPlace(String name, String category, String destination) {
        return new TripPlanResponse.PlaceRecommendation(
                name,
                category,
                destination,
                "No named place was returned, so this is a safe area-level fallback suggestion.",
                "System fallback",
                null,
                null,
                null
        );
    }

    private static boolean shouldSkip(String name, String poiType, String category) {
        String normalizedName = normalize(name);
        String normalizedType = normalize(poiType);

        if ("Restaurant".equals(category)) {
            if (normalizedType.contains("fast food") || normalizedType.contains("\u5feb\u9910")) {
                return true;
            }
            if (isChainBrand(normalizedName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isChainBrand(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return CHAIN_BRANDS.stream().anyMatch(value::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Location parseLocation(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return new Location(null, null);
        }

        String[] parts = rawLocation.split(",");
        if (parts.length != 2) {
            return new Location(null, null);
        }

        try {
            Double longitude = Double.parseDouble(parts[0]);
            Double latitude = Double.parseDouble(parts[1]);
            return new Location(latitude, longitude);
        } catch (NumberFormatException exception) {
            return new Location(null, null);
        }
    }

    private static double squaredDistance(double originLat, double originLon, Double lat, Double lon) {
        if (lat == null || lon == null) {
            return Double.MAX_VALUE;
        }
        double deltaLat = originLat - lat;
        double deltaLon = originLon - lon;
        return deltaLat * deltaLat + deltaLon * deltaLon;
    }

    private static String buildReason(String category, String destination, String poiType) {
        if ("Hotel".equals(category)) {
            return "位于" + destination + "主要游览区域附近，适合作为住宿落点。";
        }
        if (poiType == null || poiType.isBlank()) {
            return "位于" + destination + "热门游览区域附近，适合顺路安排用餐。";
        }
        return "位于" + destination + "热门游览区域附近，高德分类为" + poiType + "。";
    }

    private static String formatAddress(JsonNode poi, String fallback) {
        String address = poi.path("address").asText("");
        if (!address.isBlank()) {
            return address;
        }
        String district = poi.path("adname").asText("");
        if (!district.isBlank()) {
            return district;
        }
        return fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record DiscoveryBundle(
            String locationLabel,
            List<TripPlanResponse.PlaceRecommendation> hotels,
            List<TripPlanResponse.PlaceRecommendation> restaurants,
            String attribution
    ) {
    }

    private record GeoPoint(double latitude, double longitude, String displayName) {
    }

    private record Location(Double latitude, Double longitude) {
    }
}
