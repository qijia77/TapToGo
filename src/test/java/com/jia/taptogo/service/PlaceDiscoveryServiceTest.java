package com.jia.taptogo.service;

import com.jia.taptogo.config.AmapProperties;
import com.jia.taptogo.model.TripPlanResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceDiscoveryServiceTest {

    @Test
    void shouldReturnFallbackPlacesWhenAmapIsNotConfigured() {
        PlaceDiscoveryService service = new PlaceDiscoveryService(
                RestClient.builder(),
                new AmapProperties("https://restapi.amap.com", "", "", "", 3200, 2600)
        );

        PlaceDiscoveryService.DiscoveryRequest request = new PlaceDiscoveryService.DiscoveryRequest(
                "Shanghai",
                "Public transit",
                List.of(new PlaceDiscoveryService.RouteAnchor(1, "The Bund", 31.2400, 121.4900))
        );
        PlaceDiscoveryService.DiscoveryBundle bundle = service.discover(request);

        assertEquals("Shanghai", bundle.locationLabel());
        assertEquals(1, bundle.hotels().size());
        assertTrue(bundle.foodNearby().isEmpty());
        assertTrue(bundle.foodHot().isEmpty());
        assertTrue(bundle.parking().isEmpty());
        assertTrue(bundle.refuel().isEmpty());
        assertTrue(bundle.charging().isEmpty());
        assertTrue(bundle.restaurants().isEmpty());
        assertEquals("System fallback", bundle.hotels().get(0).source());
        assertEquals("Shanghai", bundle.hotels().get(0).address());
        assertTrue(bundle.attribution() != null && !bundle.attribution().isBlank());
    }

    @Test
    void shouldSelectTwoCoordinateAnchorsPerDay() {
        List<PlaceDiscoveryService.RouteAnchor> selected = PlaceDiscoveryService.selectSearchAnchors(List.of(
                new PlaceDiscoveryService.RouteAnchor(1, "Bell Tower", 34.2610, 108.9480),
                new PlaceDiscoveryService.RouteAnchor(1, "City Wall", 34.2590, 108.9520),
                new PlaceDiscoveryService.RouteAnchor(1, "Generic Core", null, null),
                new PlaceDiscoveryService.RouteAnchor(1, "Muslim Quarter", 34.2650, 108.9440),
                new PlaceDiscoveryService.RouteAnchor(2, "Big Wild Goose Pagoda", 34.2180, 108.9600)
        ));

        assertEquals(
                List.of("Bell Tower", "City Wall", "Big Wild Goose Pagoda"),
                selected.stream().map(PlaceDiscoveryService.RouteAnchor::name).toList()
        );
    }

    @Test
    void shouldDetectSelfDriveMode() {
        assertTrue(PlaceDiscoveryService.isSelfDriveMode("Self-drive"));
        assertTrue(PlaceDiscoveryService.isSelfDriveMode("self-drive"));
        assertTrue(PlaceDiscoveryService.isSelfDriveMode(" Self-drive "));
        assertFalse(PlaceDiscoveryService.isSelfDriveMode("Public transit"));
    }

    @Test
    void shouldEnrichDailyActivitiesWithCoordinatesWhenMissing() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("杭州", "120.1551,30.2741", "杭州市");
            amap.stubGeocode("雷峰塔景区", "120.1488,30.2311", "雷峰塔景区");
            amap.stubGeocode("湖滨步行街", "120.1613,30.2572", "湖滨步行街");

            PlaceDiscoveryService service = createService(amap);
            List<TripPlanResponse.DayPlan> input = List.of(new TripPlanResponse.DayPlan(
                    1,
                    "西湖南线",
                    List.of(
                            new TripPlanResponse.Activity("下午", "景点", "雷峰塔景区", "登塔看西湖", "停车后步行", null, null, null),
                            new TripPlanResponse.Activity("晚上", "休闲", "湖滨步行街", "散步收尾", "回程顺路", null, null, null)
                    )
            ));

            List<TripPlanResponse.DayPlan> enriched = service.enrichActivities("杭州", input);

            assertEquals(2, enriched.get(0).activities().size());
            assertEquals(30.2311, enriched.get(0).activities().get(0).latitude());
            assertEquals(120.1488, enriched.get(0).activities().get(0).longitude());
            assertEquals(30.2572, enriched.get(0).activities().get(1).latitude());
            assertEquals(120.1613, enriched.get(0).activities().get(1).longitude());
        }
    }

    @Test
    void shouldStillResolveConcreteActivitiesWhenDestinationGeocodeFails() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("瀹夐『鏂囧簷", "105.9443,26.2532", "瀹夐『鏂囧簷");
            amap.stubGeocode("瀹夐『鍙ゅ煄鍘嗗彶鏂囧寲琛楀尯", "105.9398,26.2456", "瀹夐『鍙ゅ煄鍘嗗彶鏂囧寲琛楀尯");

            PlaceDiscoveryService service = createService(amap);
            List<TripPlanResponse.DayPlan> input = List.of(new TripPlanResponse.DayPlan(
                    1,
                    "瀹夐『鑰佸煄",
                    List.of(
                            new TripPlanResponse.Activity("涓嬪崍", "鏅偣", "瀹夐『鏂囧簷", "鍙ょ珯浜烘枃", "姝ヨ", null, null, null),
                            new TripPlanResponse.Activity("鏅氫笂", "浼戦棽", "瀹夐『鍙ゅ煄鍘嗗彶鏂囧寲琛楀尯", "鑰佸煄鏁ｄ茎", "姝ヨ", null, null, null)
                    )
            ));

            List<TripPlanResponse.DayPlan> enriched = service.enrichActivities("瀹夐『", input);

            assertEquals(26.2532, enriched.get(0).activities().get(0).latitude());
            assertEquals(105.9443, enriched.get(0).activities().get(0).longitude());
            assertEquals(26.2456, enriched.get(0).activities().get(1).latitude());
            assertEquals(105.9398, enriched.get(0).activities().get(1).longitude());
        }
    }

    @Test
    void shouldKeepRestaurantFallbackWhenAmapFoodResultsAreEmpty() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("昆明", "102.833,24.88", "昆明市");
            amap.stubAround("102.833,24.88", "酒店", List.of(
                    new FakePoi("翠湖酒店", "酒店服务", "翠湖北路", "五华区", "102.833,24.88")
            ));

            PlaceDiscoveryService.DiscoveryBundle bundle = createService(amap).discover(
                    new PlaceDiscoveryService.DiscoveryRequest("昆明", "Public transit", List.of())
            );

            assertEquals(1, bundle.restaurants().size(), bundle.attribution());
            assertEquals("Restaurant", bundle.restaurants().get(0).category());
            assertEquals("System fallback", bundle.restaurants().get(0).source());
            assertTrue(bundle.restaurants().get(0).name().contains("昆明"));
        }
    }

    @Test
    void shouldBalanceNearbyFoodAcrossDaysBeforeApplyingOverallLimit() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("昆明", "102.7,25.0", "昆明市");
            amap.stubAround("102.7,25.0", "酒店", List.of(
                    new FakePoi("翠湖酒店", "酒店服务", "翠湖北路", "五华区", "102.7,25.0")
            ));
            amap.stubAround("102.0,24.0", "餐厅", buildPois("翠湖餐馆", 8, "餐饮服务;中餐厅", "翠湖片区", "102.0,24.0"));
            amap.stubAround("103.0,25.0", "餐厅", List.of(
                    new FakePoi("滇池边小馆", "餐饮服务;中餐厅", "海埂大坝", "西山区", "103.0,25.0")
            ));
            amap.stubAround("104.0,26.0", "餐厅", List.of(
                    new FakePoi("石林风味馆", "餐饮服务;中餐厅", "石林景区外", "石林县", "104.0,26.0")
            ));

            PlaceDiscoveryService.DiscoveryBundle bundle = createService(amap).discover(
                    new PlaceDiscoveryService.DiscoveryRequest(
                            "昆明",
                            "Public transit",
                            List.of(
                                    new PlaceDiscoveryService.RouteAnchor(1, "翠湖", 24.0, 102.0),
                                    new PlaceDiscoveryService.RouteAnchor(2, "滇池", 25.0, 103.0),
                                    new PlaceDiscoveryService.RouteAnchor(3, "石林", 26.0, 104.0)
                            )
                    )
            );

            Set<Integer> coveredDays = bundle.foodNearby().stream()
                    .map(place -> place.day() == null ? -1 : place.day())
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(
                    coveredDays.containsAll(Set.of(1, 2, 3)),
                    bundle.foodNearby() + " | " + bundle.attribution() + " | " + amap.requestLog()
            );
            assertTrue(bundle.foodNearby().size() <= 8);
        }
    }

    @Test
    void shouldBalanceDriveSupportAcrossDaysForSelfDrive() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("昆明", "102.7,25.0", "昆明市");
            amap.stubAround("102.7,25.0", "酒店", List.of(
                    new FakePoi("翠湖酒店", "酒店服务", "翠湖北路", "五华区", "102.7,25.0")
            ));
            amap.stubAround("102.0,24.0", "停车场", buildPois("翠湖停车场", 6, "交通设施服务;停车场", "翠湖片区", "102.0,24.0"));
            amap.stubAround("103.0,25.0", "停车场", List.of(
                    new FakePoi("滇池游客停车场", "交通设施服务;停车场", "海埂大坝", "西山区", "103.0,25.0")
            ));

            PlaceDiscoveryService.DiscoveryBundle bundle = createService(amap).discover(
                    new PlaceDiscoveryService.DiscoveryRequest(
                            "昆明",
                            "Self-drive",
                            List.of(
                                    new PlaceDiscoveryService.RouteAnchor(1, "翠湖", 24.0, 102.0),
                                    new PlaceDiscoveryService.RouteAnchor(2, "滇池", 25.0, 103.0)
                            )
                    )
            );

            Set<Integer> coveredDays = bundle.parking().stream()
                    .map(place -> place.day() == null ? -1 : place.day())
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(
                    coveredDays.containsAll(Set.of(1, 2)),
                    bundle.parking() + " | " + bundle.attribution() + " | " + amap.requestLog()
            );
        }
    }

    @Test
    void shouldDeduplicateRestaurantsWhenNearbyAndHotListsContainSamePlace() throws Exception {
        try (FakeAmapApi amap = new FakeAmapApi()) {
            amap.stubGeocode("昆明", "102.7,25.0", "昆明市");
            amap.stubAround("102.7,25.0", "酒店", List.of(
                    new FakePoi("翠湖酒店", "酒店服务", "翠湖北路", "五华区", "102.7,25.0")
            ));
            amap.stubAround("102.0,24.0", "餐厅", List.of(
                    new FakePoi("过桥米线馆", "餐饮服务;中餐厅", "翠湖片区", "五华区", "102.0,24.0")
            ));
            amap.stubAround("102.7,25.0", "餐厅", List.of(
                    new FakePoi("过桥米线馆", "餐饮服务;中餐厅", "翠湖片区", "五华区", "102.0,24.0")
            ));

            PlaceDiscoveryService.DiscoveryBundle bundle = createService(amap).discover(
                    new PlaceDiscoveryService.DiscoveryRequest(
                            "昆明",
                            "Public transit",
                            List.of(new PlaceDiscoveryService.RouteAnchor(1, "翠湖", 24.0, 102.0))
                    )
            );

            assertEquals(1, bundle.foodNearby().size(), bundle.attribution() + " | " + amap.requestLog());
            assertEquals(1, bundle.foodHot().size(), bundle.attribution() + " | " + amap.requestLog());
            assertEquals(1, bundle.restaurants().size(), bundle.attribution() + " | " + amap.requestLog());
        }
    }

    private static PlaceDiscoveryService createService(FakeAmapApi amap) {
        return new PlaceDiscoveryService(
                RestClient.builder(),
                new AmapProperties(amap.baseUrl(), "test-key", "", "", 3200, 2600)
        );
    }

    private static List<FakePoi> buildPois(String prefix, int count, String type, String addressPrefix, String location) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new FakePoi(
                        prefix + index,
                        type,
                        addressPrefix + index + "号",
                        "测试区",
                        location
                ))
                .toList();
    }

    private record FakePoi(String name, String type, String address, String adname, String location) {
    }

    private static final class FakeAmapApi implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, GeocodeResponse> geocodeResponses = new LinkedHashMap<>();
        private final Map<String, List<FakePoi>> aroundResponses = new LinkedHashMap<>();
        private final List<String> requestLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        private FakeAmapApi() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v3/geocode/geo", this::handleGeocode);
            server.createContext("/v3/place/around", this::handleAround);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void stubGeocode(String address, String location, String formattedAddress) {
            geocodeResponses.put(normalizeLookup(address), new GeocodeResponse(location, formattedAddress));
        }

        private void stubAround(String location, String keyword, List<FakePoi> pois) {
            aroundResponses.put(key(location, keyword), List.copyOf(pois));
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private void handleGeocode(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            requestLog.add("geo:" + normalizeLookup(query.getOrDefault("address", "")));
            GeocodeResponse response = geocodeResponses.get(normalizeLookup(query.getOrDefault("address", "")));
            if (response == null && geocodeResponses.size() == 1) {
                response = geocodeResponses.values().iterator().next();
            }
            if (response == null) {
                writeJson(exchange, "{\"status\":\"0\",\"geocodes\":[]}");
                return;
            }

            writeJson(exchange, "{\"status\":\"1\",\"geocodes\":[{\"location\":\""
                    + escapeJson(response.location())
                    + "\",\"formatted_address\":\""
                    + escapeJson(response.formattedAddress())
                    + "\"}]}");
        }

        private void handleAround(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            requestLog.add("around:" + key(query.getOrDefault("location", ""), query.getOrDefault("keywords", "")));
            List<FakePoi> pois = aroundResponses.getOrDefault(
                    key(query.getOrDefault("location", ""), query.getOrDefault("keywords", "")),
                    List.of()
            );

            String body = pois.stream()
                    .map(poi -> "{\"name\":\"" + escapeJson(poi.name()) + "\","
                            + "\"type\":\"" + escapeJson(poi.type()) + "\","
                            + "\"address\":\"" + escapeJson(poi.address()) + "\","
                            + "\"adname\":\"" + escapeJson(poi.adname()) + "\","
                            + "\"location\":\"" + escapeJson(poi.location()) + "\"}")
                    .collect(java.util.stream.Collectors.joining(","));
            writeJson(exchange, "{\"status\":\"1\",\"pois\":[" + body + "]}");
        }

        private static void writeJson(HttpExchange exchange, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            }
        }

        private static Map<String, String> parseQuery(URI uri) {
            Map<String, String> values = new LinkedHashMap<>();
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return values;
            }

            for (String pair : rawQuery.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = decode(parts[0]);
                String value = parts.length > 1 ? decode(parts[1]) : "";
                values.put(key, value);
            }
            return values;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        private static String key(String location, String keyword) {
            return normalizeLookup(location) + "::" + normalizeLookup(keyword);
        }

        private static String escapeJson(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String normalizeLookup(String value) {
            return value == null ? "" : value.trim();
        }

        private List<String> requestLog() {
            return List.copyOf(requestLog);
        }
    }

    private record GeocodeResponse(String location, String formattedAddress) {
    }
}
