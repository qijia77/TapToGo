# TapToGo

TapToGo is now split into:

- a Spring Boot backend in the project root
- a separate React frontend in `frontend/`

## Backend features

- `POST /api/trips/plan`
  generates a trip, enriches it with real hotels and restaurants, and saves it to history
- `GET /api/trips/history`
  returns saved trips
- `GET /api/trips/favorites`
  returns favorite trips only
- `PATCH /api/trips/history/{id}/favorite`
  updates favorite state
- `GET /api/trips/health`
  reports backend status and active planner mode

## Frontend features

- separate React UI
- history board
- favorites
- PDF export through browser print
- zero-dependency local static server in `frontend/server.mjs`

## Run

Backend:

```bash
mvn spring-boot:run
```

Frontend:

```bash
node frontend/server.mjs
```

Then open `http://localhost:4173`.

## Local YAML config

If you prefer writing keys and URLs in YAML instead of environment variables, create:

`config/application-local.yml`

The project already imports this file automatically when it exists. It is ignored by git, so local keys do not get committed.

You can start from:

`config/application-local.example.yml`

Example:

```yml
app:
  openai:
    enabled: true
    api-key: sk-your-openai-key
    base-url: https://api.openai.com
    model: gpt-5.4
    temperature: 0.5
    web-search-enabled: true
```

## Environment variables

- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_MODEL` default: `gpt-5.4`
- `OPENAI_TEMPERATURE`
- `OPENAI_ENABLED`
- `OPENAI_WEB_SEARCH_ENABLED`
- `AMAP_KEY`
- `AMAP_BASE_URL`
- `AMAP_HOTEL_RADIUS_METERS`
- `AMAP_RESTAURANT_RADIUS_METERS`

For relay/proxy providers, set `OPENAI_BASE_URL` to the provider host. Both forms are accepted:

- `https://your-proxy.example.com`
- `https://your-proxy.example.com/v1`

## Planner modes

- with `OPENAI_API_KEY`, the backend asks OpenAI for the itinerary draft
- when `OPENAI_WEB_SEARCH_ENABLED=true`, the OpenAI request enables web search and returns clickable planning sources
- without `OPENAI_API_KEY`, the backend falls back to a built-in draft generator
- in both cases, hotel and restaurant recommendations are enriched from Amap place data
