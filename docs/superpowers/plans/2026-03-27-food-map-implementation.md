# Food Map Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement split food recommendations, self-drive support points, and map icon/legend upgrades without breaking old itinerary data.

**Architecture:** Extend the Spring response contract first, then refactor place discovery around a route-aware discovery request that can produce nearby food, hot food, and self-drive support lists independently. On the frontend, move grouping and marker metadata into pure helpers, then wire those helpers into the existing React/AMap view so grouped cards, marker kinds, focus behavior, and the legend all stay testable.

**Tech Stack:** Spring Boot, Java records, RestClient, JUnit 5, React 18 (Babel-in-browser), AMap JS API, Node test runner

---

### Task 1: Expand the API response contract for grouped recommendations

**Files:**
- Modify: `src/main/java/com/jia/taptogo/model/TripPlanResponse.java`
- Modify: `src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java`

- [ ] **Step 1: Write the failing controller test for the new JSON fields**

Update the controller assertion block in `src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java` so it checks the new grouped fields:

```java
mockMvc.perform(post("/api/trips/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trip_summary").value("City break"))
        .andExpect(jsonPath("$.recommended_hotels[0].name").value("Central Hotel"))
        .andExpect(jsonPath("$.recommended_food_nearby[0].name").value("Bell Tower Noodle"))
        .andExpect(jsonPath("$.recommended_food_nearby[0].day").value(1))
        .andExpect(jsonPath("$.recommended_food_hot[0].name").value("Shaanxi Banquet"))
        .andExpect(jsonPath("$.recommended_parking[0].category").value("Parking"))
        .andExpect(jsonPath("$.recommended_refuel[0].category").value("Fuel"))
        .andExpect(jsonPath("$.recommended_charging[0].category").value("Charging"))
        .andExpect(jsonPath("$.daily_itinerary[0].activities[0].transit_tip").value("Walk there"))
        .andExpect(jsonPath("$.planning_sources[0].url").value("https://example.com/search-result"));
```

Update `sampleResponse(boolean favorite)` in the same test file so the constructor shape reflects the new response shape:

```java
return new TripPlanResponse(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        "Xian",
        "Self-drive",
        "demo",
        Instant.parse("2026-03-25T10:15:30Z"),
        favorite,
        "City break",
        2,
        new TripPlanResponse.Accommodation("Downtown", "Easy to move around"),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "Central Hotel",
                "Hotel",
                "Downtown",
                "Named accommodation near the center.",
                "OpenStreetMap",
                "https://www.openstreetmap.org/node/1",
                34.0,
                108.0,
                null
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "Bell Tower Noodle",
                "Restaurant",
                "Bell Tower",
                "Close to the day 1 route.",
                "Amap",
                "https://www.xiaohongshu.com/search_result?keyword=Bell%20Tower%20Noodle",
                34.26,
                108.95,
                1
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "Shaanxi Banquet",
                "Restaurant",
                "Yongning Gate",
                "A destination-level hot pick.",
                "Amap",
                "https://www.xiaohongshu.com/search_result?keyword=Shaanxi%20Banquet",
                34.24,
                108.94,
                null
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "South Gate Parking",
                "Parking",
                "South Gate",
                "Useful before entering the wall area.",
                "Amap",
                null,
                34.23,
                108.95,
                1
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "Ring Road Fuel",
                "Fuel",
                "Ring Road",
                "Convenient before leaving downtown.",
                "Amap",
                null,
                34.20,
                108.98,
                1
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "South EV Hub",
                "Charging",
                "Qujiang",
                "Fast charging before the next route segment.",
                "Amap",
                null,
                34.19,
                108.99,
                2
        )),
        List.of(new TripPlanResponse.PlaceRecommendation(
                "Bell Tower Noodle",
                "Restaurant",
                "Bell Tower",
                "Close to the day 1 route.",
                "Amap",
                "https://www.xiaohongshu.com/search_result?keyword=Bell%20Tower%20Noodle",
                34.26,
                108.95,
                1
        )),
        List.of(new TripPlanResponse.DayPlan(
                1,
                "First look",
                List.of(new TripPlanResponse.Activity(
                        "Morning",
                        "Sight",
                        "City Gate",
                        "Start with the landmark",
                        "Walk there",
                        "https://www.openstreetmap.org/node/3",
                        34.1,
                        108.9
                ))
        )),
        List.of(new TripPlanResponse.PlanningSource(
                "Search result",
                "https://example.com/search-result"
        )),
        "OpenStreetMap attribution"
);
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run:

```powershell
mvn -q test -Dtest=TripPlanControllerTest
```

Expected: FAIL because `TripPlanResponse` does not yet expose `recommended_food_nearby`, `recommended_food_hot`, `recommended_parking`, `recommended_refuel`, `recommended_charging`, or `PlaceRecommendation.day`.

- [ ] **Step 3: Implement the expanded response model**

Update `src/main/java/com/jia/taptogo/model/TripPlanResponse.java` so the record shape matches the assertions:

```java
public record TripPlanResponse(
        UUID id,
        @NotBlank String destination,
        @JsonProperty("travel_mode")
        @NotBlank String travelMode,
        @JsonProperty("planning_mode")
        @NotBlank String planningMode,
        @JsonProperty("generated_at")
        Instant generatedAt,
        boolean favorite,
        @JsonProperty("trip_summary")
        @NotBlank String tripSummary,
        @JsonProperty("total_days")
        @Positive int totalDays,
        @JsonProperty("recommended_accommodation")
        @Valid Accommodation recommendedAccommodation,
        @JsonProperty("recommended_hotels")
        @Valid List<@Valid PlaceRecommendation> recommendedHotels,
        @JsonProperty("recommended_food_nearby")
        @Valid List<@Valid PlaceRecommendation> recommendedFoodNearby,
        @JsonProperty("recommended_food_hot")
        @Valid List<@Valid PlaceRecommendation> recommendedFoodHot,
        @JsonProperty("recommended_parking")
        @Valid List<@Valid PlaceRecommendation> recommendedParking,
        @JsonProperty("recommended_refuel")
        @Valid List<@Valid PlaceRecommendation> recommendedRefuel,
        @JsonProperty("recommended_charging")
        @Valid List<@Valid PlaceRecommendation> recommendedCharging,
        @JsonProperty("recommended_restaurants")
        @Valid List<@Valid PlaceRecommendation> recommendedRestaurants,
        @JsonProperty("daily_itinerary")
        @NotEmpty List<@Valid DayPlan> dailyItinerary,
        @JsonProperty("planning_sources")
        List<@Valid PlanningSource> planningSources,
        @NotBlank String attribution
) {
```

Normalize all optional lists in the compact constructor:

```java
public TripPlanResponse {
    recommendedHotels = recommendedHotels == null ? List.of() : List.copyOf(recommendedHotels);
    recommendedFoodNearby = recommendedFoodNearby == null ? List.of() : List.copyOf(recommendedFoodNearby);
    recommendedFoodHot = recommendedFoodHot == null ? List.of() : List.copyOf(recommendedFoodHot);
    recommendedParking = recommendedParking == null ? List.of() : List.copyOf(recommendedParking);
    recommendedRefuel = recommendedRefuel == null ? List.of() : List.copyOf(recommendedRefuel);
    recommendedCharging = recommendedCharging == null ? List.of() : List.copyOf(recommendedCharging);
    recommendedRestaurants = recommendedRestaurants == null ? List.of() : List.copyOf(recommendedRestaurants);
    planningSources = planningSources == null ? List.of() : List.copyOf(planningSources);
}
```

Extend `withFavorite` so it forwards every new list, and add `day` to `PlaceRecommendation`:

```java
public record PlaceRecommendation(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String address,
        @NotBlank String reason,
        @NotBlank String source,
        @JsonProperty("source_url")
        String sourceUrl,
        Double latitude,
        Double longitude,
        Integer day
) {
}
```

- [ ] **Step 4: Re-run the controller test to verify it passes**

Run:

```powershell
mvn -q test -Dtest=TripPlanControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit the response contract change**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add src/main/java/com/jia/taptogo/model/TripPlanResponse.java src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "feat: extend trip recommendation response"
```

### Task 2: Introduce a route-aware discovery request and fallback bundle

**Files:**
- Modify: `src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java`
- Modify: `src/main/java/com/jia/taptogo/service/TripPlannerService.java`
- Modify: `src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java`

- [ ] **Step 1: Write the failing fallback test for the new discovery shape**

Replace the no-key test in `src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java` with a request-aware version:

```java
@Test
void shouldReturnGroupedFallbackPlacesWhenAmapIsNotConfigured() {
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
    assertTrue(bundle.attribution() != null && !bundle.attribution().isBlank());
}
```

- [ ] **Step 2: Run the discovery test to verify it fails**

Run:

```powershell
mvn -q test -Dtest=PlaceDiscoveryServiceTest
```

Expected: FAIL because `discover()` still accepts only `destination`, and `DiscoveryBundle` does not yet expose grouped food or self-drive lists.

- [ ] **Step 3: Implement the request object, new bundle shape, and TripPlannerService call site**

In `src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java`, add request and route-anchor records near the bottom of the file:

```java
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
}
```

Refactor the public entrypoint and cache key:

```java
public DiscoveryBundle discover(DiscoveryRequest request) {
    String cacheKey = (request.destination() + "::" + request.travelMode()).trim().toLowerCase(Locale.ROOT);
    return cache.computeIfAbsent(cacheKey, ignored -> safeFetch(request));
}
```

Update the fallback path so it returns grouped empty arrays instead of fake named restaurants:

```java
private static DiscoveryBundle fallbackBundle(String destination, String reason) {
    String trimmedDestination = destination == null || destination.isBlank() ? "Current destination" : destination.trim();
    return new DiscoveryBundle(
            trimmedDestination,
            List.of(fallbackPlace(trimmedDestination + " central stay area", "Hotel", trimmedDestination, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "Amap place discovery unavailable, fallback enabled: " + reason
    );
}
```

In `src/main/java/com/jia/taptogo/service/TripPlannerService.java`, map the generated day plans before calling place discovery:

```java
List<TripPlanResponse.DayPlan> days = mapDays(draft.dailyItinerary());

PlaceDiscoveryService.DiscoveryBundle places = placeDiscoveryService.discover(
        new PlaceDiscoveryService.DiscoveryRequest(
                request.destination(),
                request.travelMode(),
                toRouteAnchors(days)
        )
);
```

Add the helper in the same file:

```java
private static List<PlaceDiscoveryService.RouteAnchor> toRouteAnchors(List<TripPlanResponse.DayPlan> days) {
    return days.stream()
            .flatMap(day -> day.activities().stream().map(activity -> new PlaceDiscoveryService.RouteAnchor(
                    day.day(),
                    activity.name(),
                    activity.latitude(),
                    activity.longitude()
            )))
            .toList();
}
```

- [ ] **Step 4: Re-run the discovery test to verify the fallback shape passes**

Run:

```powershell
mvn -q test -Dtest=PlaceDiscoveryServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit the discovery request refactor**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java src/main/java/com/jia/taptogo/service/TripPlannerService.java src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "refactor: add route-aware discovery request"
```

### Task 3: Implement route-aware food and self-drive discovery logic

**Files:**
- Modify: `src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java`
- Modify: `src/main/java/com/jia/taptogo/service/TripPlannerService.java`
- Modify: `src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java`

- [ ] **Step 1: Write failing unit tests for anchor selection and self-drive detection**

Append these tests to `src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java`:

```java
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
    assertTrue(!PlaceDiscoveryService.isSelfDriveMode("Public transit"));
}
```

- [ ] **Step 2: Run the discovery test to verify it fails**

Run:

```powershell
mvn -q test -Dtest=PlaceDiscoveryServiceTest
```

Expected: FAIL because `selectSearchAnchors()` and `isSelfDriveMode()` do not exist yet.

- [ ] **Step 3: Implement helper methods and route-aware place queries**

In `src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java`, add package-private pure helpers for the tests:

```java
static boolean isSelfDriveMode(String travelMode) {
    return travelMode != null && "self-drive".equals(travelMode.trim().toLowerCase(Locale.ROOT));
}

static List<RouteAnchor> selectSearchAnchors(List<RouteAnchor> anchors) {
    return anchors.stream()
            .filter(anchor -> anchor.latitude() != null && anchor.longitude() != null)
            .collect(java.util.stream.Collectors.groupingBy(RouteAnchor::day, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
            .values()
            .stream()
            .flatMap(dayAnchors -> dayAnchors.stream().limit(2))
            .toList();
}
```

Refactor `fetch()` so it builds grouped lists instead of a single restaurant list:

```java
private DiscoveryBundle fetch(DiscoveryRequest request) {
    GeoPoint geoPoint = geocode(request.destination());

    List<TripPlanResponse.PlaceRecommendation> hotels = queryPlaces(
            request.destination(),
            geoPoint,
            properties.hotelRadiusMeters(),
            "酒店",
            "Hotel",
            null
    );

    List<TripPlanResponse.PlaceRecommendation> foodNearby = queryNearbyFood(request.destination(), selectSearchAnchors(request.anchors()), geoPoint);
    List<TripPlanResponse.PlaceRecommendation> foodHot = queryHotFood(request.destination(), geoPoint);
    List<TripPlanResponse.PlaceRecommendation> parking = isSelfDriveMode(request.travelMode())
            ? queryDriveSupport(request.destination(), selectSearchAnchors(request.anchors()), geoPoint, "停车场", "Parking")
            : List.of();
    List<TripPlanResponse.PlaceRecommendation> refuel = isSelfDriveMode(request.travelMode())
            ? queryDriveSupport(request.destination(), selectSearchAnchors(request.anchors()), geoPoint, "加油站", "Fuel")
            : List.of();
    List<TripPlanResponse.PlaceRecommendation> charging = isSelfDriveMode(request.travelMode())
            ? queryDriveSupport(request.destination(), selectSearchAnchors(request.anchors()), geoPoint, "充电站", "Charging")
            : List.of();
    List<TripPlanResponse.PlaceRecommendation> restaurants = mergePlaces(foodNearby, foodHot, 8);

    if (hotels.isEmpty()) {
        hotels = List.of(fallbackPlace(request.destination() + " central stay area", "Hotel", request.destination(), null));
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
            "Hotels and food picks come from Amap nearby search."
    );
}
```

Add targeted query helpers in the same file:

```java
private List<TripPlanResponse.PlaceRecommendation> queryNearbyFood(String destination, List<RouteAnchor> anchors, GeoPoint fallbackCenter) {
    List<TripPlanResponse.PlaceRecommendation> results = new ArrayList<>();
    for (RouteAnchor anchor : anchors) {
        GeoPoint point = new GeoPoint(anchor.latitude(), anchor.longitude(), anchor.name());
        results = mergePlaces(results, queryPlaces(destination, point, properties.restaurantRadiusMeters(), "餐厅", "Restaurant", anchor.day()), 8);
        if (results.size() < 8) {
            results = mergePlaces(results, queryPlaces(destination, point, properties.restaurantRadiusMeters(), "美食", "Restaurant", anchor.day()), 8);
        }
    }
    return results;
}

private List<TripPlanResponse.PlaceRecommendation> queryHotFood(String destination, GeoPoint center) {
    List<TripPlanResponse.PlaceRecommendation> results = new ArrayList<>();
    for (String keyword : List.of("餐厅", "美食", "本地菜", "必吃", "热门餐厅")) {
        results = mergePlaces(results, queryPlaces(destination, center, properties.restaurantRadiusMeters(), keyword, "Restaurant", null), 8);
    }
    return results;
}

private List<TripPlanResponse.PlaceRecommendation> queryDriveSupport(
        String destination,
        List<RouteAnchor> anchors,
        GeoPoint fallbackCenter,
        String keyword,
        String category
) {
    List<TripPlanResponse.PlaceRecommendation> results = new ArrayList<>();
    if (anchors.isEmpty()) {
        return queryPlaces(destination, fallbackCenter, properties.restaurantRadiusMeters(), keyword, category, null);
    }
    for (RouteAnchor anchor : anchors) {
        GeoPoint point = new GeoPoint(anchor.latitude(), anchor.longitude(), anchor.name());
        results = mergePlaces(results, queryPlaces(destination, point, properties.restaurantRadiusMeters(), keyword, category, anchor.day()), 6);
    }
    return results;
}
```

Update `queryPlaces()` and the fallback helper to preserve `day`:

```java
private List<TripPlanResponse.PlaceRecommendation> queryPlaces(
        String destination,
        GeoPoint geoPoint,
        int radiusMeters,
        String keyword,
        String category,
        Integer day
) {
```

```java
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
```

```java
private static TripPlanResponse.PlaceRecommendation fallbackPlace(String name, String category, String destination, Integer day) {
    return new TripPlanResponse.PlaceRecommendation(
            name,
            category,
            destination,
            "No named place was returned, so this is a safe area-level fallback suggestion.",
            "System fallback",
            null,
            null,
            null,
            day
    );
}
```

Use a limit-aware merge helper:

```java
private static List<TripPlanResponse.PlaceRecommendation> mergePlaces(
        List<TripPlanResponse.PlaceRecommendation> primary,
        List<TripPlanResponse.PlaceRecommendation> secondary,
        int limit
) {
    return java.util.stream.Stream.concat(primary.stream(), secondary.stream())
            .distinct()
            .limit(limit)
            .toList();
}
```

Finally, in `src/main/java/com/jia/taptogo/service/TripPlannerService.java`, build the response with the new lists:

```java
return tripHistoryService.save(new TripPlanResponse(
        UUID.randomUUID(),
        request.destination(),
        request.travelMode(),
        mode,
        Instant.now(),
        false,
        draft.tripSummary(),
        draft.totalDays(),
        new TripPlanResponse.Accommodation(
                draft.recommendedAccommodation().area(),
                draft.recommendedAccommodation().reason()
        ),
        places.hotels(),
        places.foodNearby(),
        places.foodHot(),
        places.parking(),
        places.refuel(),
        places.charging(),
        places.restaurants(),
        days,
        planningSources,
        places.attribution()
));
```

- [ ] **Step 4: Run the backend tests for the new discovery behavior**

Run:

```powershell
mvn -q test -Dtest=PlaceDiscoveryServiceTest,TripPlanControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit the route-aware discovery logic**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java src/main/java/com/jia/taptogo/service/TripPlannerService.java src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "feat: add grouped food and drive support discovery"
```

### Task 4: Extract pure frontend helpers for grouped recommendations and marker kinds

**Files:**
- Modify: `frontend/app-helpers.mjs`
- Modify: `frontend/app-helpers.test.mjs`

- [ ] **Step 1: Write failing helper tests for grouping and marker metadata**

Append the following tests to `frontend/app-helpers.test.mjs`:

```javascript
import {
  buildMapEntriesData,
  buildPlaceKey,
  buildRecommendationSections,
  getMapKindMeta,
  isSelfDriveMode
} from "./app-helpers.mjs";

test("recommendation sections split nearby and hot food", () => {
  const plan = {
    travel_mode: "Self-drive",
    recommended_hotels: [{ name: "钟楼酒店", reason: "靠近核心区" }],
    recommended_food_nearby: [
      { name: "回民街泡馍", reason: "顺路吃", day: 1 }
    ],
    recommended_food_hot: [
      { name: "长安大排档", reason: "城市热门", day: null }
    ],
    recommended_parking: [
      { name: "南门停车场", reason: "进城前停车", day: 1 }
    ],
    recommended_refuel: [],
    recommended_charging: [
      { name: "曲江快充站", reason: "补电", day: 2 }
    ],
    recommended_restaurants: []
  };

  const sections = buildRecommendationSections(plan, 1);

  assert.deepEqual(
    sections.map((section) => section.key),
    ["stay", "food-nearby", "food-hot", "drive-support"]
  );
  assert.equal(sections[1].items[0].name, "回民街泡馍");
  assert.equal(sections[3].groups[0].items[0].name, "南门停车场");
});

test("marker metadata uses Chinese-first labels", () => {
  assert.equal(getMapKindMeta("spot").marker, "景");
  assert.equal(getMapKindMeta("stay").marker, "宿");
  assert.equal(getMapKindMeta("food").marker, "食");
  assert.equal(getMapKindMeta("charging").marker, "电");
});

test("map entry builder emits drive support kinds only when present", () => {
  const plan = {
    recommended_hotels: [],
    recommended_food_nearby: [],
    recommended_food_hot: [],
    recommended_parking: [{ name: "南门停车场", address: "城墙南侧", latitude: 34.23, longitude: 108.95, day: 1 }],
    recommended_refuel: [{ name: "环城加油站", address: "二环南路", latitude: 34.20, longitude: 108.98, day: 1 }],
    recommended_charging: [{ name: "曲江快充站", address: "曲江新区", latitude: 34.19, longitude: 108.99, day: 2 }],
    recommended_restaurants: [],
    daily_itinerary: []
  };

  const entries = buildMapEntriesData(plan, 1);

  assert.equal(entries.some((entry) => entry.kind === "parking"), true);
  assert.equal(entries.some((entry) => entry.kind === "refuel"), true);
  assert.equal(entries.some((entry) => entry.kind === "charging"), true);
  assert.equal(isSelfDriveMode("Self-drive"), true);
  assert.equal(buildPlaceKey("charging", plan.recommended_charging[0]), "charging::曲江快充站::2");
});
```

- [ ] **Step 2: Run the helper test to verify it fails**

Run:

```powershell
node --test frontend/app-helpers.test.mjs
```

Expected: FAIL because the new helper exports do not exist yet.

- [ ] **Step 3: Implement the helper exports**

Extend `frontend/app-helpers.mjs` with these pure helpers:

```javascript
export function isSelfDriveMode(mode) {
  return String(mode || "").trim().toLowerCase() === "self-drive";
}

export function buildPlaceKey(kind, item) {
  return `${kind}::${String(item?.name || "").trim()}::${item?.day ?? "all"}`;
}

export function getMapKindMeta(kind) {
  const palette = {
    spot: { label: "\u666f\u70b9", marker: "\u666f", background: "#0b8a78", foreground: "#ffffff" },
    stay: { label: "\u4f4f\u5bbf", marker: "\u5bbf", background: "#0057be", foreground: "#ffffff" },
    food: { label: "\u5403\u996d", marker: "\u98df", background: "#c4571e", foreground: "#ffffff" },
    parking: { label: "\u505c\u8f66", marker: "P", background: "#3348b8", foreground: "#ffffff" },
    refuel: { label: "\u52a0\u6cb9", marker: "\u6cb9", background: "#7b4d1f", foreground: "#ffffff" },
    charging: { label: "\u5145\u7535", marker: "\u7535", background: "#0f9fb8", foreground: "#ffffff" },
    other: { label: "\u70b9\u4f4d", marker: "\u70b9", background: "#6f9fff", foreground: "#ffffff" }
  };
  return palette[kind] || palette.other;
}
```

Add grouped recommendation builders:

```javascript
export function buildRecommendationSections(plan, selectedDay) {
  const nearby = (plan?.recommended_food_nearby || []).filter(
    (item) => item?.day == null || item.day === selectedDay
  );
  const hot = (plan?.recommended_food_hot || []).length
    ? plan.recommended_food_hot
    : plan?.recommended_restaurants || [];

  const sections = [];

  if ((plan?.recommended_hotels || []).length) {
    sections.push({
      key: "stay",
      kicker: "\u4f4f\u5bbf",
      title: "\u4f4f\u5bbf\u5468\u8fb9",
      items: plan.recommended_hotels.slice(0, 3).map((item) => ({ ...item, kind: "stay", mapKey: buildPlaceKey("stay", item) }))
    });
  }

  if (nearby.length) {
    sections.push({
      key: "food-nearby",
      kicker: "\u7f8e\u98df",
      title: "\u666f\u70b9\u9644\u8fd1",
      items: nearby.slice(0, 6).map((item) => ({ ...item, kind: "food", mapKey: buildPlaceKey("food", item) }))
    });
  }

  if (hot.length) {
    sections.push({
      key: "food-hot",
      kicker: "\u7f8e\u98df",
      title: "\u7206\u706b\u63a8\u8350",
      items: hot.slice(0, 6).map((item) => ({ ...item, kind: "food", mapKey: buildPlaceKey("food", item) }))
    });
  }

  if (isSelfDriveMode(plan?.travel_mode)) {
    const groups = [
      { key: "parking", title: "\u505c\u8f66", items: filterDriveItems(plan?.recommended_parking || [], selectedDay, "parking") },
      { key: "refuel", title: "\u52a0\u6cb9", items: filterDriveItems(plan?.recommended_refuel || [], selectedDay, "refuel") },
      { key: "charging", title: "\u5145\u7535", items: filterDriveItems(plan?.recommended_charging || [], selectedDay, "charging") }
    ].filter((group) => group.items.length);

    if (groups.length) {
      sections.push({
        key: "drive-support",
        kicker: "\u81ea\u9a7e",
        title: "\u81ea\u9a7e\u8865\u7ed9",
        groups
      });
    }
  }

  return sections;
}

function filterDriveItems(items, selectedDay, kind) {
  return items
    .filter((item) => item?.day == null || item.day === selectedDay)
    .slice(0, 4)
    .map((item) => ({ ...item, kind, mapKey: buildPlaceKey(kind, item) }));
}
```

Add a pure map-entry collector for the AMap view:

```javascript
export function buildMapEntriesData(plan, selectedDay) {
  const entries = [];

  const pushEntry = (item, kind, primary, day = 0, sequence = 0) => {
    if (!item) return;
    const meta = getMapKindMeta(kind);
    entries.push({
      key: buildPlaceKey(kind, item),
      lat: item.latitude,
      lon: item.longitude,
      label: String(item.name || ""),
      address: String(item.address || ""),
      kind,
      primary,
      day,
      sequence,
      kindLabel: meta.label
    });
  };

  (plan?.recommended_hotels || []).forEach((item, index) => pushEntry(item, "stay", index === 0));
  (plan?.recommended_food_nearby || []).forEach((item, index) => {
    if (item?.day == null || item.day === selectedDay) pushEntry(item, "food", index === 0, item.day ?? 0, index);
  });
  (plan?.recommended_food_hot || plan?.recommended_restaurants || []).forEach((item, index) => pushEntry(item, "food", false, item?.day ?? 0, index));
  (plan?.recommended_parking || []).forEach((item, index) => {
    if (item?.day == null || item.day === selectedDay) pushEntry(item, "parking", index === 0, item.day ?? 0, index);
  });
  (plan?.recommended_refuel || []).forEach((item, index) => {
    if (item?.day == null || item.day === selectedDay) pushEntry(item, "refuel", index === 0, item.day ?? 0, index);
  });
  (plan?.recommended_charging || []).forEach((item, index) => {
    if (item?.day == null || item.day === selectedDay) pushEntry(item, "charging", index === 0, item.day ?? 0, index);
  });
  (plan?.daily_itinerary || []).forEach((day) => {
    if (selectedDay && day.day !== selectedDay) return;
    (day.activities || []).forEach((item, index) => pushEntry(item, "spot", index === 0 && day.day === selectedDay, day.day, index));
  });

  return entries;
}
```

Expose the new functions through `window.TapToGoUiHelpers`.

- [ ] **Step 4: Re-run the helper test to verify it passes**

Run:

```powershell
node --test frontend/app-helpers.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit the frontend helper extraction**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add frontend/app-helpers.mjs frontend/app-helpers.test.mjs
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "feat: add grouped recommendation helpers"
```

### Task 5: Wire grouped recommendation panels, marker focus, and the map legend into the UI

**Files:**
- Modify: `frontend/app.jsx`
- Modify: `frontend/styles.css`
- Modify: `frontend/source-smoke.test.mjs`

- [ ] **Step 1: Write failing smoke tests for the new UI copy and map legend**

Extend `frontend/source-smoke.test.mjs` with these assertions:

```javascript
test("app source renders grouped recommendation copy", () => {
  assert.match(app, /\\u666f\\u70b9\\u9644\\u8fd1/);
  assert.match(app, /\\u7206\\u706b\\u63a8\\u8350/);
  assert.match(app, /\\u81ea\\u9a7e\\u8865\\u7ed9/);
  assert.match(app, /focusedPointKey/);
  assert.match(app, /handleRecommendationSelect/);
});

test("app source renders Chinese-first map markers and legend", () => {
  assert.match(app, /getMapKindMeta/);
  assert.match(app, /className=\"map-legend\"/);
  assert.match(app, /\\u666f\\u70b9/);
  assert.match(app, /\\u505c\\u8f66/);
  assert.match(app, /\\u5145\\u7535/);
});

test("style source has legend and active recommendation styles", () => {
  assert.match(css, /\\.map-legend/);
  assert.match(css, /\\.legend-item/);
  assert.match(css, /\\.mini-card\\.is-active/);
  assert.match(css, /\\.mini-card-group/);
});
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```powershell
node --test frontend/source-smoke.test.mjs
```

Expected: FAIL because the grouped panel copy, legend markup, and focus state do not exist yet.

- [ ] **Step 3: Implement the grouped UI and marker-focus behavior**

In `frontend/app.jsx`, extend the helper destructuring block:

```javascript
const {
  getUiCopy = () => ({
    nav: {
      generator: "\u884c\u7a0b\u751f\u6210",
      timeline: "\u884c\u7a0b\u65f6\u95f4\u7ebf",
      library: "\u884c\u7a0b\u5e93",
      map: "\u5730\u56fe\u5de5\u4f5c\u533a"
    },
    actions: {
      start: "\u5f00\u59cb\u751f\u6210",
      generateIdle: "\u751f\u6210 AI \u884c\u7a0b",
      generateLoading: "AI \u6b63\u5728\u751f\u6210\u5b89\u6392...",
      refresh: "\u5237\u65b0\u6570\u636e",
      export: "\u5bfc\u51fa\u884c\u7a0b\u5355"
    },
    library: {
      title: "\u5df2\u751f\u6210\u884c\u7a0b",
      all: "\u5168\u90e8",
      favorites: "\u6536\u85cf"
    }
  }),
  formatTravelModeZh = (mode) => String(mode || ""),
  formatModeZh = (mode) => String(mode || ""),
  describeDayZh = (day) => String(day || ""),
  getMotionClassName = (kind, delayIndex, visible) => String(kind || ""),
  describePlanModeZh = () => "\u8fd9\u4efd\u884c\u7a0b\u7684\u751f\u6210\u72b6\u6001\u6682\u65f6\u65e0\u6cd5\u51c6\u786e\u5224\u5b9a\u3002",
  buildRecommendationSections = () => [],
  buildMapEntriesData = () => [],
  buildPlaceKey = (kind, item) => `${kind}::${item?.name || ""}::all`,
  getMapKindMeta = () => ({ label: "\u70b9\u4f4d", marker: "\u70b9", background: "#6f9fff", foreground: "#ffffff" }),
  isSelfDriveMode = () => false,
  getStatusMessageZh,
  buildMapSummaryZh
} = window.TapToGoUiHelpers || {};
```

Add focus state near the top of `App()`:

```javascript
const [focusedPointKey, setFocusedPointKey] = useState("");
```

Replace the single `recommendationCards` array with grouped sections:

```javascript
const recommendationSections = selectedPlan
  ? buildRecommendationSections(selectedPlan, selectedDay)
  : [];

const legendKinds = selectedPlan
  ? [
      "spot",
      "stay",
      "food",
      ...(isSelfDriveMode(selectedPlan.travel_mode) ? ["parking", "refuel", "charging"] : [])
    ]
  : [];
```

Add a click handler in `App()`:

```javascript
function handleRecommendationSelect(item) {
  if (!item?.mapKey) {
    return;
  }
  pendingFocusRef.current = "assistant";
  setFocusedPointKey(item.mapKey);
}
```

Pass `focusedPointKey` into the map:

```jsx
<MapView
  plan={selectedPlan}
  selectedDay={selectedDay}
  amapConfig={amapConfig}
  focusedPointKey={focusedPointKey}
/>
```

Replace the old single recommendation panel with grouped sections:

```jsx
{recommendationSections.map((section) => (
  <section key={section.key} className="side-panel">
    <div className="section-kicker">{section.kicker}</div>
    <h3>{section.title}</h3>

    {section.items ? (
      <div className="mini-grid">
        {section.items.map((item) => (
          <button
            key={item.mapKey}
            type="button"
            className={`mini-card ${focusedPointKey === item.mapKey ? "is-active" : ""}`}
            onClick={() => handleRecommendationSelect(item)}
          >
            <div className={`mini-card-mark ${item.kind}`}></div>
            <div>
              <h4>{normalizeCopy(item.name)}</h4>
              <p>{normalizeCopy(item.reason)}</p>
            </div>
          </button>
        ))}
      </div>
    ) : (
      <div className="mini-card-stack">
        {section.groups.map((group) => (
          <div key={group.key} className="mini-card-group">
            <div className="mini-card-group-title">{group.title}</div>
            <div className="mini-grid">
              {group.items.map((item) => (
                <button
                  key={item.mapKey}
                  type="button"
                  className={`mini-card ${focusedPointKey === item.mapKey ? "is-active" : ""}`}
                  onClick={() => handleRecommendationSelect(item)}
                >
                  <div className={`mini-card-mark ${item.kind}`}></div>
                  <div>
                    <h4>{normalizeCopy(item.name)}</h4>
                    <p>{normalizeCopy(item.reason)}</p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    )}
  </section>
))}
```

Add the legend next to the map summary:

```jsx
<div className="map-legend">
  {legendKinds.map((kind) => {
    const meta = getMapKindMeta(kind);
    return (
      <div key={kind} className="legend-item">
        <span
          className="legend-dot"
          style={{ background: meta.background, color: meta.foreground }}
        >
          {meta.marker}
        </span>
        <span>{meta.label}</span>
      </div>
    );
  })}
</div>
```

Update `MapView` so it relies on the helper-built entries and can focus a clicked recommendation:

```javascript
function MapView({ plan, selectedDay, amapConfig, focusedPointKey }) {
  const markerIndexRef = useRef(new Map());
  const infoWindowIndexRef = useRef(new Map());
```

Inside the map rendering effect, replace `collectMapEntries(plan, selectedDay)` with:

```javascript
const mapEntries = buildMapEntriesData(plan, selectedDay);
markerIndexRef.current.clear();
infoWindowIndexRef.current.clear();
```

Store marker and info-window instances when creating overlays:

```javascript
const marker = createAmapMarker(AMap, point);
markerIndexRef.current.set(point.key, marker);
if (marker.__tapToGoInfoWindow) {
  infoWindowIndexRef.current.set(point.key, marker.__tapToGoInfoWindow);
}
```

Add a focus effect after markers are ready:

```javascript
useEffect(() => {
  if (!focusedPointKey || !mapInstance.current) {
    return;
  }
  const marker = markerIndexRef.current.get(focusedPointKey);
  const infoWindow = infoWindowIndexRef.current.get(focusedPointKey);
  if (!marker || !infoWindow) {
    return;
  }
  const position = marker.getPosition();
  mapInstance.current.setCenter(position);
  mapInstance.current.setZoom(Math.max(mapInstance.current.getZoom() || 13, 15));
  infoWindow.open(mapInstance.current, position);
}, [focusedPointKey, plan, selectedDay]);
```

Make `createAmapMarker()` expose the info window and drive marker copy from `getMapKindMeta()`:

```javascript
function createAmapMarker(AMap, point) {
  const size = point.primary ? 42 : 34;
  const marker = new AMap.Marker({
    position: [point.lon, point.lat],
    title: `${point.label} ${point.kindLabel}`,
    offset: new AMap.Pixel(-(size / 2), -(size / 2)),
    content: buildAmapMarkerMarkup(point.kind, point.primary)
  });

  const infoWindow = new AMap.InfoWindow({
    content: `<strong>${escapeHtml(point.label)}</strong><br/>${escapeHtml(point.kindLabel)}`,
    offset: new AMap.Pixel(0, -18)
  });

  marker.__tapToGoInfoWindow = infoWindow;
  marker.on("click", () => infoWindow.open(marker.getMap(), marker.getPosition()));
  return marker;
}

function buildAmapMarkerMarkup(kind, primary) {
  const meta = getMapKindMeta(kind);
  const size = primary ? 42 : 34;
  return `<div style="width:${size}px;height:${size}px;border-radius:999px;background:${meta.background};color:${meta.foreground};display:grid;place-items:center;box-shadow:0 18px 30px rgba(36,44,81,0.18);border:3px solid rgba(255,255,255,0.96);font-size:${primary ? "14px" : "12px"};font-weight:800;">${meta.marker}</div>`;
}
```

- [ ] **Step 4: Add the new styles**

Append these styles to `frontend/styles.css`:

```css
.map-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0 18px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(246, 247, 255, 0.9);
  border: 1px solid rgba(94, 112, 179, 0.14);
  color: #2c355a;
  font-size: 13px;
  font-weight: 700;
}

.legend-dot {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  font-weight: 800;
}

.mini-card {
  width: 100%;
  border: 1px solid transparent;
  text-align: left;
  cursor: pointer;
}

.mini-card.is-active {
  border-color: rgba(95, 143, 255, 0.36);
  box-shadow: 0 16px 34px rgba(72, 98, 188, 0.18);
  transform: translateY(-2px);
}

.mini-card-stack {
  display: grid;
  gap: 16px;
}

.mini-card-group {
  display: grid;
  gap: 10px;
}

.mini-card-group-title {
  color: #5a648e;
  font-size: 13px;
  font-weight: 800;
}

.mini-card-mark.parking { background: #3348b8; }
.mini-card-mark.refuel { background: #7b4d1f; }
.mini-card-mark.charging { background: #0f9fb8; }
```

- [ ] **Step 5: Re-run the frontend tests to verify the grouped UI passes**

Run:

```powershell
node --test frontend/app-helpers.test.mjs
node --test frontend/source-smoke.test.mjs
node --test frontend/css-motion.test.mjs
```

Expected: PASS for all three commands.

- [ ] **Step 6: Commit the UI integration**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add frontend/app.jsx frontend/styles.css frontend/source-smoke.test.mjs frontend/app-helpers.mjs frontend/app-helpers.test.mjs
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "feat: upgrade grouped food panels and map markers"
```

### Task 6: Run the final verification pass

**Files:**
- Modify: none
- Test: `src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java`
- Test: `src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java`
- Test: `frontend/app-helpers.test.mjs`
- Test: `frontend/source-smoke.test.mjs`
- Test: `frontend/css-motion.test.mjs`

- [ ] **Step 1: Run the backend verification suite**

Run:

```powershell
mvn -q test -Dtest=TripPlanControllerTest,PlaceDiscoveryServiceTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run the frontend verification suite**

Run:

```powershell
node --test frontend/app-helpers.test.mjs
node --test frontend/source-smoke.test.mjs
node --test frontend/css-motion.test.mjs
```

Expected: each command reports PASS.

- [ ] **Step 3: Run a final compile check**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit the verified implementation**

Run:

```powershell
git -c safe.directory=E:/CodeRepository/TapToGo add src/main/java/com/jia/taptogo/model/TripPlanResponse.java src/main/java/com/jia/taptogo/service/TripPlannerService.java src/main/java/com/jia/taptogo/service/PlaceDiscoveryService.java src/test/java/com/jia/taptogo/controller/TripPlanControllerTest.java src/test/java/com/jia/taptogo/service/PlaceDiscoveryServiceTest.java frontend/app.jsx frontend/styles.css frontend/app-helpers.mjs frontend/app-helpers.test.mjs frontend/source-smoke.test.mjs
git -c safe.directory=E:/CodeRepository/TapToGo commit -m "feat: deliver grouped food and map marker upgrade"
```
