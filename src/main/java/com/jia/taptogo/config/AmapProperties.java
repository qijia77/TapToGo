package com.jia.taptogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.amap")
public record AmapProperties(
        String baseUrl,
        String apiKey,
        int hotelRadiusMeters,
        int restaurantRadiusMeters
) {

    public AmapProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://restapi.amap.com"
                : baseUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        hotelRadiusMeters = hotelRadiusMeters <= 0 ? 3200 : hotelRadiusMeters;
        restaurantRadiusMeters = restaurantRadiusMeters <= 0 ? 2600 : restaurantRadiusMeters;
    }

    public boolean configured() {
        return !apiKey.isBlank();
    }
}
