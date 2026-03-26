package com.jia.taptogo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.taptogo.model.TripPlanResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TripHistoryService {

    private static final TypeReference<List<TripPlanResponse>> HISTORY_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storagePath = Path.of("data", "trip-history.json");
    private final Map<UUID, TripPlanResponse> history = new LinkedHashMap<>();

    public TripHistoryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            List<TripPlanResponse> items = objectMapper.readValue(storagePath.toFile(), HISTORY_TYPE);
            for (TripPlanResponse item : items) {
                history.put(item.id(), item);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load trip history store.", exception);
        }
    }

    public synchronized TripPlanResponse save(TripPlanResponse response) {
        history.put(response.id(), response);
        persist();
        return response;
    }

    public synchronized List<TripPlanResponse> listHistory() {
        return sorted(history.values());
    }

    public synchronized List<TripPlanResponse> listFavorites() {
        return sorted(history.values().stream().filter(TripPlanResponse::favorite).toList());
    }

    public synchronized TripPlanResponse updateFavorite(UUID id, boolean favorite) {
        TripPlanResponse current = history.get(id);
        if (current == null) {
            throw new NoSuchElementException("Trip plan not found: " + id);
        }

        TripPlanResponse updated = current.withFavorite(favorite);
        history.put(id, updated);
        persist();
        return updated;
    }

    private void persist() {
        try {
            Files.createDirectories(storagePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new ArrayList<>(history.values()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist trip history.", exception);
        }
    }

    private static List<TripPlanResponse> sorted(Iterable<TripPlanResponse> items) {
        List<TripPlanResponse> list = new ArrayList<>();
        items.forEach(list::add);
        list.sort(Comparator.comparing(TripPlanResponse::generatedAt).reversed());
        return list;
    }
}
