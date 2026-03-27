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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlaceDiscoveryService {

    private static final int HOTEL_LIMIT = 4;
    private static final int FOOD_LIMIT = 8;
    private static final int DRIVE_SUPPORT_LIMIT = 6;
    private static final int PLACE_QUERY_LIMIT = 12;
    private static final int NEARBY_FOOD_DAY_SUPPLEMENT_THRESHOLD = 2;

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

    public DiscoveryBundle discover(DiscoveryRequest request) {
        DiscoveryRequest normalizedRequest = normalizeRequest(request);
        String cacheKey = buildCacheKey(normalizedRequest.destination(), normalizedRequest.travelMode());
        return cache.computeIfAbsent(cacheKey, ignored -> safeFetch(normalizedRequest));
    }

    public DiscoveryBundle discover(String destination) {
        return discover(new DiscoveryRequest(destination, "", List.of()));
    }

    public List<TripPlanResponse.DayPlan> enrichActivities(String destination, List<TripPlanResponse.DayPlan> days) {
        if (days == null || days.isEmpty()) {
            return days == null ? List.of() : List.copyOf(days);
        }

        if (!properties.configured()) {
            return List.copyOf(days);
        }

        String normalizedDestination = normalizeDestination(destination);
        GeoPoint center = null;
        try {
            center = geocode(normalizedDestination);
        } catch (RuntimeException exception) {
            center = null;
        }

        List<TripPlanResponse.DayPlan> enrichedDays = new ArrayList<>();
        for (TripPlanResponse.DayPlan day : days) {
            List<TripPlanResponse.Activity> enrichedActivities = new ArrayList<>();
            for (TripPlanResponse.Activity activity : day.activities()) {
                Location resolved = hasCoordinate(activity.latitude(), activity.longitude())
                        ? new Location(activity.latitude(), activity.longitude())
                        : resolveActivityLocation(normalizedDestination, center, activity.name());

                if (resolved == null || !hasCoordinate(resolved.latitude(), resolved.longitude())) {
                    enrichedActivities.add(activity);
                    continue;
                }

                enrichedActivities.add(new TripPlanResponse.Activity(
                        activity.time(),
                        activity.type(),
                        activity.name(),
                        activity.description(),
                        activity.transitTip(),
                        activity.socialLink(),
                        resolved.latitude(),
                        resolved.longitude()
                ));
            }
            enrichedDays.add(new TripPlanResponse.DayPlan(day.day(), day.theme(), enrichedActivities));
        }

        return List.copyOf(enrichedDays);
    }

    private DiscoveryBundle safeFetch(DiscoveryRequest request) {
        String destination = request.destination();
        if (!properties.configured()) {
            return fallbackBundle(destination, "高德 API Key 未配置。");
        }

        try {
            return fetch(request);
        } catch (RuntimeException exception) {
            return fallbackBundle(destination, exception.getMessage());
        }
    }

    private Location resolveActivityLocation(String destination, GeoPoint center, String activityName) {
        for (String keyword : buildActivityKeywords(destination, activityName)) {
            Location geocodeLocation = geocodeLocation(keyword);
            if (hasCoordinate(geocodeLocation.latitude(), geocodeLocation.longitude())) {
                return geocodeLocation;
            }

            Location placeLocation = queryActivityLocation(destination, center, keyword);
            if (hasCoordinate(placeLocation.latitude(), placeLocation.longitude())) {
                return placeLocation;
            }
        }
        return new Location(null, null);
    }

    private Location geocodeLocation(String address) {
        if (address == null || address.isBlank()) {
            return new Location(null, null);
        }

        JsonNode response = amapClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/geocode/geo")
                        .queryParam("address", address)
                        .queryParam("output", "json")
                        .queryParam("key", properties.apiKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !"1".equals(response.path("status").asText())) {
            return new Location(null, null);
        }

        JsonNode geocodes = response.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            return new Location(null, null);
        }

        JsonNode first = geocodes.get(0);
        return parseLocation(first.path("location").asText(""));
    }

    private Location queryActivityLocation(String destination, GeoPoint center, String keyword) {
        if (center == null) {
            return new Location(null, null);
        }

        try {
            List<TripPlanResponse.PlaceRecommendation> places = queryPlaces(
                    destination,
                    center,
                    Math.max(properties.hotelRadiusMeters(), properties.restaurantRadiusMeters()),
                    keyword,
                    "Activity",
                    null
            );
            if (places.isEmpty()) {
                return new Location(null, null);
            }
            TripPlanResponse.PlaceRecommendation first = places.get(0);
            return new Location(first.latitude(), first.longitude());
        } catch (RuntimeException exception) {
            return new Location(null, null);
        }
    }

    private static List<String> buildActivityKeywords(String destination, String activityName) {
        String normalizedDestination = normalizeDestination(destination);
        String normalizedName = normalizeText(activityName);
        if (normalizedName.isBlank()) {
            return List.of(normalizedDestination);
        }

        LinkedHashMap<String, Boolean> keywords = new LinkedHashMap<>();
        keywords.put(normalizedName, true);
        keywords.put(normalizedDestination + normalizedName, true);
        keywords.put(normalizedName + " " + normalizedDestination, true);
        return keywords.keySet().stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .toList();
    }

    private static boolean hasCoordinate(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude);
    }

    private DiscoveryBundle fetch(DiscoveryRequest request) {
        String destination = request.destination();
        GeoPoint geoPoint = geocode(destination);
        List<RouteAnchor> selectedAnchors = selectSearchAnchors(request.anchors());

        List<TripPlanResponse.PlaceRecommendation> hotels = queryPlaces(
                destination,
                geoPoint,
                properties.hotelRadiusMeters(),
                "\u9152\u5e97",
                "Hotel",
                null
        ).stream().limit(HOTEL_LIMIT).toList();

        List<TripPlanResponse.PlaceRecommendation> foodNearby = queryNearbyFood(destination, selectedAnchors, geoPoint);
        List<TripPlanResponse.PlaceRecommendation> foodHot = queryHotFood(destination, geoPoint);
        List<TripPlanResponse.PlaceRecommendation> parking = List.of();
        List<TripPlanResponse.PlaceRecommendation> refuel = List.of();
        List<TripPlanResponse.PlaceRecommendation> charging = List.of();

        if (isSelfDriveMode(request.travelMode())) {
            parking = queryDriveSupport(destination, selectedAnchors, geoPoint, "\u505c\u8f66\u573a", "Parking");
            refuel = queryDriveSupport(destination, selectedAnchors, geoPoint, "\u52a0\u6cb9\u7ad9", "Fuel");
            charging = queryDriveSupport(destination, selectedAnchors, geoPoint, "\u5145\u7535\u7ad9", "Charging");
        }

        List<TripPlanResponse.PlaceRecommendation> restaurants = mergePlaces(foodNearby, foodHot, FOOD_LIMIT);

        if (hotels.isEmpty()) {
            hotels = List.of(fallbackPlace(destination + "\u4f4f\u5bbf\u6838\u5fc3\u533a", "Hotel", destination, null));
        }
        if (restaurants.isEmpty()) {
            restaurants = List.of(fallbackPlace(destination + "\u7f8e\u98df\u7247\u533a", "Restaurant", destination, null));
        }

        return new DiscoveryBundle(
                geoPoint.displayName(),
                hotels,
                foodNearby,
                foodHot,
                parking,
                refuel,
                charging,
                restaurants,
                "\u4f4f\u5bbf\u3001\u9910\u996e\u4e0e\u8865\u7ed9\u63a8\u8350\u6765\u81ea\u9ad8\u5fb7\u5730\u56fe\u5468\u8fb9\u641c\u7d22\u3002"
        );
    }

    private static DiscoveryBundle fallbackBundle(String destination, String reason) {
        String trimmedDestination = normalizeDestination(destination);
        String safeReason = reason == null || reason.isBlank() ? "\u672a\u77e5\u539f\u56e0\u3002" : reason;
        return new DiscoveryBundle(
                trimmedDestination,
                List.of(fallbackPlace(trimmedDestination + "\u4f4f\u5bbf\u6838\u5fc3\u533a", "Hotel", trimmedDestination, null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "\u5f53\u524d\u65e0\u6cd5\u4f7f\u7528\u9ad8\u5fb7\u5730\u70b9\u63a8\u8350\uff0c\u5df2\u542f\u7528\u515c\u5e95\u5efa\u8bae\u3002\u539f\u56e0\uff1a" + safeReason
        );
    }

    private static DiscoveryRequest normalizeRequest(DiscoveryRequest request) {
        if (request == null) {
            return new DiscoveryRequest("Current destination", "", List.of());
        }

        String destination = normalizeDestination(request.destination());
        String travelMode = normalizeText(request.travelMode());
        List<RouteAnchor> anchors = request.anchors() == null ? List.of() : List.copyOf(request.anchors());
        return new DiscoveryRequest(destination, travelMode, anchors);
    }

    private static String normalizeDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return "Current destination";
        }
        return destination.trim();
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private static String buildCacheKey(String destination, String travelMode) {
        return normalizeText(destination).toLowerCase(Locale.ROOT)
                + "::"
                + normalizeText(travelMode).toLowerCase(Locale.ROOT);
    }

    static boolean isSelfDriveMode(String travelMode) {
        return "self-drive".equalsIgnoreCase(normalizeText(travelMode));
    }

    static List<RouteAnchor> selectSearchAnchors(List<RouteAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }

        return groupAnchorsByDay(anchors).values().stream()
                .flatMap(dayAnchors -> dayAnchors.stream().limit(2))
                .toList();
    }

    private GeoPoint geocode(String destination) {
        JsonNode response = amapClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/geocode/geo")
                        .queryParam("address", destination)
                        .queryParam("output", "json")
                        .queryParam("key", properties.apiKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("\u9ad8\u5fb7\u5730\u7406\u7f16\u7801\u8fd4\u56de\u4e3a\u7a7a\u3002");
        }

        if (!"1".equals(response.path("status").asText())) {
            throw new IllegalStateException(
                    "\u9ad8\u5fb7\u5730\u7406\u7f16\u7801\u5931\u8d25\uff1a" + destination
                            + " (status=" + response.path("status").asText()
                            + ", info=" + response.path("info").asText()
                            + ", infocode=" + response.path("infocode").asText() + ")"
            );
        }

        JsonNode geocodes = response.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw new IllegalStateException("\u672a\u627e\u5230\u76ee\u7684\u5730\u5750\u6807\uff1a" + destination);
        }

        JsonNode first = geocodes.get(0);
        String location = first.path("location").asText("");
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new IllegalStateException("\u9ad8\u5fb7\u5730\u7406\u7f16\u7801\u5750\u6807\u683c\u5f0f\u65e0\u6548\uff1a" + destination);
        }

        try {
            double longitude = Double.parseDouble(parts[0]);
            double latitude = Double.parseDouble(parts[1]);
            String displayName = first.path("formatted_address").asText(destination);
            return new GeoPoint(latitude, longitude, displayName);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("\u9ad8\u5fb7\u5730\u7406\u7f16\u7801\u5750\u6807\u4e0d\u662f\u6570\u5b57\uff1a" + destination, exception);
        }
    }

    private List<TripPlanResponse.PlaceRecommendation> queryNearbyFood(
            String destination,
            List<RouteAnchor> anchors,
            GeoPoint fallbackCenter
    ) {
        if (anchors == null || anchors.isEmpty()) {
            List<TripPlanResponse.PlaceRecommendation> nearby = queryPlaces(
                    destination,
                    fallbackCenter,
                    properties.restaurantRadiusMeters(),
                    "\u9910\u5385",
                    "Restaurant",
                    null
            );
            if (nearby.size() < FOOD_LIMIT) {
                nearby = mergePlaces(
                        nearby,
                        queryPlaces(
                                destination,
                                fallbackCenter,
                                properties.restaurantRadiusMeters(),
                                "\u7f8e\u98df",
                                "Restaurant",
                                null
                        ),
                        FOOD_LIMIT
                );
            }
            return nearby;
        }

        List<List<TripPlanResponse.PlaceRecommendation>> nearbyByDay = new ArrayList<>();
        for (List<RouteAnchor> dayAnchors : groupAnchorsByDay(anchors).values()) {
            List<TripPlanResponse.PlaceRecommendation> dayNearby = List.of();
            for (RouteAnchor anchor : dayAnchors) {
                dayNearby = mergePlaces(
                        dayNearby,
                        queryPlaces(
                                destination,
                                toGeoPoint(anchor),
                                properties.restaurantRadiusMeters(),
                                "\u9910\u5385",
                                "Restaurant",
                                anchor.day()
                        ),
                        FOOD_LIMIT
                );
                if (dayNearby.size() >= FOOD_LIMIT) {
                    break;
                }
            }
            if (dayNearby.size() < NEARBY_FOOD_DAY_SUPPLEMENT_THRESHOLD) {
                for (RouteAnchor anchor : dayAnchors) {
                    dayNearby = mergePlaces(
                            dayNearby,
                            queryPlaces(
                                    destination,
                                    toGeoPoint(anchor),
                                    properties.restaurantRadiusMeters(),
                                    "\u7f8e\u98df",
                                    "Restaurant",
                                    anchor.day()
                            ),
                            FOOD_LIMIT
                    );
                    if (dayNearby.size() >= NEARBY_FOOD_DAY_SUPPLEMENT_THRESHOLD) {
                        break;
                    }
                }
            }
            if (!dayNearby.isEmpty()) {
                nearbyByDay.add(dayNearby);
            }
        }
        return interleaveByDay(nearbyByDay, FOOD_LIMIT);
    }

    private List<TripPlanResponse.PlaceRecommendation> queryHotFood(String destination, GeoPoint center) {
        List<TripPlanResponse.PlaceRecommendation> hot = List.of();
        for (String keyword : List.of(
                "\u9910\u5385",
                "\u7f8e\u98df",
                "\u672c\u5730\u83dc",
                "\u5fc5\u5403",
                "\u70ed\u95e8\u9910\u5385"
        )) {
            hot = mergePlaces(
                    hot,
                    queryPlaces(
                            destination,
                            center,
                            properties.restaurantRadiusMeters(),
                            keyword,
                            "Restaurant",
                            null
                    ),
                    FOOD_LIMIT
            );
            if (hot.size() >= FOOD_LIMIT) {
                break;
            }
        }
        return hot;
    }

    private List<TripPlanResponse.PlaceRecommendation> queryDriveSupport(
            String destination,
            List<RouteAnchor> anchors,
            GeoPoint fallbackCenter,
            String keyword,
            String category
    ) {
        if (anchors == null || anchors.isEmpty()) {
            return queryPlaces(
                    destination,
                    fallbackCenter,
                    properties.hotelRadiusMeters(),
                    keyword,
                    category,
                    null
            ).stream().limit(DRIVE_SUPPORT_LIMIT).toList();
        }

        List<List<TripPlanResponse.PlaceRecommendation>> supportByDay = new ArrayList<>();
        for (List<RouteAnchor> dayAnchors : groupAnchorsByDay(anchors).values()) {
            List<TripPlanResponse.PlaceRecommendation> daySupport = List.of();
            for (RouteAnchor anchor : dayAnchors) {
                daySupport = mergePlaces(
                        daySupport,
                        queryPlaces(
                                destination,
                                toGeoPoint(anchor),
                                properties.hotelRadiusMeters(),
                                keyword,
                                category,
                                anchor.day()
                        ),
                        DRIVE_SUPPORT_LIMIT
                );
                if (daySupport.size() >= DRIVE_SUPPORT_LIMIT) {
                    break;
                }
            }
            if (!daySupport.isEmpty()) {
                supportByDay.add(daySupport);
            }
        }
        return interleaveByDay(supportByDay, DRIVE_SUPPORT_LIMIT);
    }

    private List<TripPlanResponse.PlaceRecommendation> queryPlaces(
            String destination,
            GeoPoint geoPoint,
            int radiusMeters,
            String keyword,
            String category,
            Integer day
    ) {
        JsonNode response = amapClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/place/around")
                        .queryParam("key", properties.apiKey())
                        .queryParam("location", geoPoint.longitude() + "," + geoPoint.latitude())
                        .queryParam("radius", radiusMeters)
                        .queryParam("keywords", keyword)
                        .queryParam("sortrule", "distance")
                        .queryParam("offset", 15)
                        .queryParam("page", 1)
                        .queryParam("extensions", "base")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("\u9ad8\u5fb7\u5468\u8fb9\u641c\u7d22\u8fd4\u56de\u4e3a\u7a7a\u3002");
        }

        if (!"1".equals(response.path("status").asText())) {
            throw new IllegalStateException(
                    "\u9ad8\u5fb7\u5468\u8fb9\u641c\u7d22\u5931\u8d25\uff1a" + destination
                            + " (status=" + response.path("status").asText()
                            + ", info=" + response.path("info").asText()
                            + ", infocode=" + response.path("infocode").asText() + ")"
            );
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
                    location.longitude(),
                    day
            ));

            if (places.size() > PLACE_QUERY_LIMIT) {
                break;
            }
        }

        return limitPlaces(places.stream()
                .sorted(Comparator.comparingDouble(place ->
                        squaredDistance(geoPoint.latitude(), geoPoint.longitude(), place.latitude(), place.longitude())))
                .toList(), PLACE_QUERY_LIMIT);
    }

    private static List<TripPlanResponse.PlaceRecommendation> mergePlaces(
            List<TripPlanResponse.PlaceRecommendation> primary,
            List<TripPlanResponse.PlaceRecommendation> secondary,
            int limit
    ) {
        return limitPlaces(java.util.stream.Stream.concat(primary.stream(), secondary.stream()).toList(), limit);
    }

    private static List<TripPlanResponse.PlaceRecommendation> interleaveByDay(
            List<List<TripPlanResponse.PlaceRecommendation>> placesByDay,
            int limit
    ) {
        if (placesByDay == null || placesByDay.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<TripPlanResponse.PlaceRecommendation> interleaved = new ArrayList<>();
        int round = 0;
        boolean addedInRound = true;
        while (interleaved.size() < limit && addedInRound) {
            addedInRound = false;
            for (List<TripPlanResponse.PlaceRecommendation> dayPlaces : placesByDay) {
                if (round < dayPlaces.size()) {
                    interleaved.add(dayPlaces.get(round));
                    addedInRound = true;
                }
            }
            round++;
        }
        return limitPlaces(interleaved, limit);
    }

    private static List<TripPlanResponse.PlaceRecommendation> limitPlaces(
            List<TripPlanResponse.PlaceRecommendation> candidates,
            int limit
    ) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        Map<String, TripPlanResponse.PlaceRecommendation> unique = new LinkedHashMap<>();
        for (TripPlanResponse.PlaceRecommendation candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            unique.putIfAbsent(placeKey(candidate), candidate);
            if (unique.size() >= limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private static String placeKey(TripPlanResponse.PlaceRecommendation place) {
        return normalize(place.category())
                + "::"
                + normalize(place.name())
                + "::"
                + normalize(place.address())
                + "::"
                + coordinateKey(place.latitude())
                + "::"
                + coordinateKey(place.longitude());
    }

    private static String coordinateKey(Double value) {
        return value == null ? "" : Double.toString(value);
    }

    private static TripPlanResponse.PlaceRecommendation fallbackPlace(
            String name,
            String category,
            String destination,
            Integer day
    ) {
        return new TripPlanResponse.PlaceRecommendation(
                name,
                category,
                destination,
                "\u6682\u672a\u68c0\u7d22\u5230\u660e\u786e\u5e97\u540d\uff0c\u5148\u7ed9\u51fa\u66f4\u7a33\u5985\u7684\u7247\u533a\u7ea7\u515c\u5e95\u5efa\u8bae\u3002",
                "System fallback",
                null,
                null,
                null,
                day
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

    private static Map<Integer, List<RouteAnchor>> groupAnchorsByDay(List<RouteAnchor> anchors) {
        Map<Integer, List<RouteAnchor>> anchorsByDay = new LinkedHashMap<>();
        if (anchors == null || anchors.isEmpty()) {
            return anchorsByDay;
        }

        for (RouteAnchor anchor : anchors) {
            if (anchor == null || anchor.latitude() == null || anchor.longitude() == null) {
                continue;
            }
            anchorsByDay.computeIfAbsent(anchor.day(), ignored -> new ArrayList<>()).add(anchor);
        }
        return anchorsByDay;
    }

    private static GeoPoint toGeoPoint(RouteAnchor anchor) {
        return new GeoPoint(anchor.latitude(), anchor.longitude(), anchor.name());
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
            return "\u4f4d\u4e8e" + destination + "\u4e3b\u8981\u6e38\u73a9\u533a\u57df\u9644\u8fd1\uff0c\u9002\u5408\u4f5c\u4e3a\u4f4f\u5bbf\u843d\u70b9\u3002";
        }
        if ("Parking".equals(category)) {
            return "\u9760\u8fd1" + destination + "\u5f53\u5929\u884c\u7a0b\uff0c\u9002\u5408\u4f5c\u4e3a\u81ea\u9a7e\u505c\u8f66\u8865\u7ed9\u70b9\u3002";
        }
        if ("Fuel".equals(category)) {
            return "\u9760\u8fd1" + destination + "\u5f53\u5929\u884c\u7a0b\uff0c\u9002\u5408\u4f5c\u4e3a\u81ea\u9a7e\u52a0\u6cb9\u8865\u7ed9\u70b9\u3002";
        }
        if ("Charging".equals(category)) {
            return "\u9760\u8fd1" + destination + "\u5f53\u5929\u884c\u7a0b\uff0c\u9002\u5408\u4f5c\u4e3a\u81ea\u9a7e\u5145\u7535\u8865\u7ed9\u70b9\u3002";
        }
        if (poiType == null || poiType.isBlank()) {
            return "\u4f4d\u4e8e" + destination + "\u70ed\u95e8\u6e38\u73a9\u533a\u57df\u9644\u8fd1\uff0c\u9002\u5408\u987a\u8def\u5403\u996d\u3002";
        }
        return "\u4f4d\u4e8e" + destination + "\u70ed\u95e8\u6e38\u73a9\u533a\u57df\u9644\u8fd1\uff0c\u9ad8\u5fb7\u5206\u7c7b\u4e3a" + poiType + "\u3002";
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

    public record DiscoveryRequest(
            String destination,
            String travelMode,
            List<RouteAnchor> anchors
    ) {
    }

    public record RouteAnchor(
            int day,
            String name,
            Double latitude,
            Double longitude
    ) {
    }

    public record DiscoveryBundle(
            String locationLabel,
            List<TripPlanResponse.PlaceRecommendation> hotels,
            List<TripPlanResponse.PlaceRecommendation> foodNearby,
            List<TripPlanResponse.PlaceRecommendation> foodHot,
            List<TripPlanResponse.PlaceRecommendation> parking,
            List<TripPlanResponse.PlaceRecommendation> refuel,
            List<TripPlanResponse.PlaceRecommendation> charging,
            List<TripPlanResponse.PlaceRecommendation> restaurants,
            String attribution
    ) {
        public DiscoveryBundle(
                String locationLabel,
                List<TripPlanResponse.PlaceRecommendation> hotels,
                List<TripPlanResponse.PlaceRecommendation> restaurants,
                String attribution
        ) {
            this(locationLabel, hotels, List.of(), List.of(), List.of(), List.of(), List.of(), restaurants, attribution);
        }
    }

    private record GeoPoint(double latitude, double longitude, String displayName) {
    }

    private record Location(Double latitude, Double longitude) {
    }
}
