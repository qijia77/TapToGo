package com.jia.taptogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.osm")
public record OsmProperties(
        String nominatimBaseUrl,
        String overpassBaseUrl,
        String userAgent,
        int hotelRadiusMeters,
        int restaurantRadiusMeters
) {

    public OsmProperties {
        nominatimBaseUrl = nominatimBaseUrl == null || nominatimBaseUrl.isBlank()
                ? "https://nominatim.openstreetmap.org"
                : nominatimBaseUrl;
        overpassBaseUrl = overpassBaseUrl == null || overpassBaseUrl.isBlank()
                ? "https://overpass-api.de"
                : overpassBaseUrl;
        userAgent = userAgent == null || userAgent.isBlank()
                ? "TapToGo/1.0 (local development)"
                : userAgent;
        hotelRadiusMeters = hotelRadiusMeters <= 0 ? 3200 : hotelRadiusMeters;
        restaurantRadiusMeters = restaurantRadiusMeters <= 0 ? 2600 : restaurantRadiusMeters;
    }
}
