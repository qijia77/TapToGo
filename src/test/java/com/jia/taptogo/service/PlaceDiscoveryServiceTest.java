package com.jia.taptogo.service;

import com.jia.taptogo.config.AmapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceDiscoveryServiceTest {

    @Test
    void shouldReturnFallbackPlacesWhenAmapIsNotConfigured() {
        PlaceDiscoveryService service = new PlaceDiscoveryService(
                RestClient.builder(),
                new AmapProperties("https://restapi.amap.com", "", 3200, 2600)
        );

        PlaceDiscoveryService.DiscoveryBundle bundle = service.discover("Shanghai");

        assertEquals("Shanghai", bundle.locationLabel());
        assertEquals(1, bundle.hotels().size());
        assertEquals(1, bundle.restaurants().size());
        assertEquals("System fallback", bundle.hotels().get(0).source());
        assertEquals("System fallback", bundle.restaurants().get(0).source());
        assertTrue(bundle.attribution() != null && !bundle.attribution().isBlank());
    }
}
