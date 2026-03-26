package com.jia.taptogo.model;

import jakarta.validation.constraints.NotNull;

public record FavoriteUpdateRequest(
        @NotNull(message = "favorite is required")
        Boolean favorite
) {
}
