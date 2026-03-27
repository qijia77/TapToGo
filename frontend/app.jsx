const { useEffect, useRef, useState } = React;

const API_BASE = window.TAPTOGO_API_BASE || "http://localhost:8080";
const FORM_STORAGE_KEY = "taptogo.frontend.form";
const SELECTION_STORAGE_KEY = "taptogo.frontend.selection";
const STACKED_PLANNER_MEDIA_QUERY = "(max-width: 1280px)";
const AMAP_JS_API_VERSION = "2.0";
const AMAP_PLUGIN_LIST = ["AMap.Scale", "AMap.ToolBar", "AMap.PlaceSearch", "AMap.Geocoder"];
let amapLoaderState = {
  key: "",
  securityJsCode: "",
  promise: null
};
const amapLookupCache = new Map();

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
  describeDayZh = (day) => {
    const first = String(day?.activities?.[0]?.name || "\u5f53\u5929\u91cd\u70b9");
    const last = String(
      day?.activities?.[day?.activities?.length - 1]?.name || "\u6536\u5c3e\u8282\u70b9"
    );
    const count = day?.activities?.length || 0;
    return `\u4ece ${first} \u5f00\u59cb\uff0c\u4e32\u8054 ${count} \u4e2a\u6267\u884c\u8282\u70b9\uff0c\u6700\u7ec8\u56de\u6536\u5230 ${last}\u3002`;
  },
  getMotionClassName = (kind, delayIndex, visible) => {
    const parts = ["motion-enter", `motion-${kind}`, `motion-delay-${delayIndex}`];
    if (visible) {
      parts.push("is-visible");
    }
    return parts.join(" ");
  },
  describePlanModeZh = () => "\u8fd9\u4efd\u884c\u7a0b\u7684\u751f\u6210\u72b6\u6001\u6682\u65f6\u65e0\u6cd5\u51c6\u786e\u5224\u5b9a\u3002",
  buildRecommendationSections = () => [],
  buildMapEntriesData = () => [],
  getMapKindMeta = () => ({
    label: "\u70b9\u4f4d",
    marker: "\u70b9",
    background: "#6f9fff",
    foreground: "#ffffff"
  }),
  isSelfDriveMode = () => false,
  getStatusMessageZh,
  buildMapSummaryZh
} = window.TapToGoUiHelpers || {};

const presets = [
  {
    label: "\u4eac\u90fd / 4 \u5929",
    destination: "Kyoto",
    travelMode: "Metro and walking",
    days: 4
  },
  {
    label: "\u4e1c\u4eac / 4 \u5929",
    destination: "Tokyo",
    travelMode: "Metro and walking",
    days: 4
  },
  {
    label: "\u9996\u5c14 / 3 \u5929",
    destination: "Seoul",
    travelMode: "Public transit",
    days: 3
  },
  {
    label: "\u6210\u90fd / 3 \u5929",
    destination: "Chengdu",
    travelMode: "Ride-hailing and walking",
    days: 3
  }
];

const zh = (value) => value;

function App() {
  const copy = getUiCopy();
  const [form, setForm] = useState(loadStoredForm());
  const [history, setHistory] = useState([]);
  const [selectedId, setSelectedId] = useState(localStorage.getItem(SELECTION_STORAGE_KEY) || "");
  const [filter, setFilter] = useState("all");
  const [loading, setLoading] = useState(false);
  const [backendMode, setBackendMode] = useState("checking");
  const [message, setMessage] = useState(getStatusMessage("idle"));
  const [selectedDay, setSelectedDay] = useState(0);
  const [hasMounted, setHasMounted] = useState(false);
  const [viewVersion, setViewVersion] = useState(0);
  const [amapConfig, setAmapConfig] = useState(loadAmapRuntimeConfig());
  const [focusedPointKey, setFocusedPointKey] = useState("");
  const timelineRef = useRef(null);
  const mapStageRef = useRef(null);
  const pendingFocusRef = useRef("");

  useEffect(() => {
    localStorage.setItem(FORM_STORAGE_KEY, JSON.stringify(form));
  }, [form]);

  useEffect(() => {
    if (selectedId) {
      localStorage.setItem(SELECTION_STORAGE_KEY, selectedId);
    }
  }, [selectedId]);

  useEffect(() => {
    bootstrap();
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setHasMounted(true), 60);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!history.length) {
      return;
    }
    if (!selectedId || !history.some((item) => item.id === selectedId)) {
      setSelectedId(history[0].id);
    }
  }, [history, selectedId]);

  const selectedPlan = history.find((item) => item.id === selectedId) || null;
  const visibleHistory = filter === "favorites" ? history.filter((item) => item.favorite) : history;
  const stats = selectedPlan ? summarizePlan(selectedPlan) : null;
  const mapSummary = selectedPlan ? buildMapSummary(selectedPlan, selectedDay) : null;
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
  const planModeNote = selectedPlan
    ? describePlanMode(selectedPlan.planning_mode, selectedPlan.planning_sources?.length || 0)
    : message;
  const activeDay =
    selectedPlan?.daily_itinerary?.find((day) => day.day === selectedDay) ||
    selectedPlan?.daily_itinerary?.[0] ||
    null;

  useEffect(() => {
    if (!selectedPlan?.daily_itinerary?.length) {
      setSelectedDay(0);
      return;
    }
    if (!selectedPlan.daily_itinerary.some((day) => day.day === selectedDay)) {
      setSelectedDay(selectedPlan.daily_itinerary[0].day);
    }
  }, [selectedPlan, selectedDay]);

  useEffect(() => {
    if (selectedPlan) {
      setViewVersion((value) => value + 1);
    }
  }, [selectedId, selectedDay]);

  useEffect(() => {
    setFocusedPointKey("");
  }, [selectedId, selectedDay]);

  useEffect(() => {
    if (!pendingFocusRef.current) {
      return;
    }

    const section = pendingFocusRef.current;
    const frameId = window.requestAnimationFrame(() => {
      const target = section === "assistant" ? mapStageRef.current : timelineRef.current;
      target?.scrollIntoView({ behavior: "smooth", block: "start" });
      pendingFocusRef.current = "";
    });

    return () => window.cancelAnimationFrame(frameId);
  }, [selectedId, selectedDay, selectedPlan]);

  async function bootstrap() {
    try {
      const [health, items] = await Promise.all([api("/api/trips/health"), api("/api/trips/history")]);
      setBackendMode(health?.capability_mode || health?.mode || "demo");
      setAmapConfig((current) => mergeAmapRuntimeConfig(current, health));
      setHistory(Array.isArray(items) ? items : []);
      setMessage(
        Array.isArray(items) && items.length
          ? getStatusMessage("history")
          : getStatusMessage("empty")
      );
    } catch (error) {
      setBackendMode("offline");
      setMessage(error.message || getStatusMessage("error"));
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setMessage(getStatusMessage("loading"));

    try {
      const created = await api("/api/trips/plan", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          destination: form.destination.trim(),
          travelMode: form.travelMode,
          days: Number(form.days)
        })
      });

      setHistory((current) => [created, ...current.filter((item) => item.id !== created.id)]);
      pendingFocusRef.current = "timeline";
      setSelectedId(created.id);
      setMessage(getStatusMessage("ready"));
    } catch (error) {
      setMessage(error.message || getStatusMessage("error"));
    } finally {
      setLoading(false);
    }
  }

  async function toggleFavorite(plan) {
    try {
      const updated = await api(`/api/trips/history/${plan.id}/favorite`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ favorite: !plan.favorite })
      });
      setHistory((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (error) {
      setMessage(error.message || getStatusMessage("error"));
    }
  }

  function handlePlanSelect(planId) {
    if (planId === selectedId) {
      timelineRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }

    pendingFocusRef.current = "timeline";
    setSelectedId(planId);
  }

  function handleDaySelect(dayNumber) {
    const shouldFocusMap = isStackedPlannerLayout();

    if (dayNumber === selectedDay) {
      if (shouldFocusMap) {
        mapStageRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }
      return;
    }

    if (shouldFocusMap) {
      pendingFocusRef.current = "assistant";
    }
    setSelectedDay(dayNumber);
  }

  function handleRecommendationSelect(item) {
    if (!item?.mapKey) {
      return;
    }
    pendingFocusRef.current = "assistant";
    mapStageRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    setFocusedPointKey(item.mapKey);
  }

  return (
    <div className="page-shell">
      <header className="site-header">
        <div className="brand-lockup">
          <span className="brand-mark">TapToGo</span>
          <span className="brand-tag">{zh("\u667a\u80fd\u884c\u7a0b\u5de5\u4f5c\u53f0")}</span>
        </div>

        <nav className="site-nav">
          <a href="#generator">{copy.nav.generator}</a>
          <a href="#timeline" className="active">
            {copy.nav.timeline}
          </a>
          <a href="#library">{copy.nav.library}</a>
          <a href="#assistant">{copy.nav.map}</a>
        </nav>

        <div className="header-actions">
          <div className="header-search">
            <span>AI</span>
            <span>
              {selectedPlan
                ? `\u6b63\u5728\u67e5\u770b\uff1a${normalizeCopy(selectedPlan.destination)}`
                : "\u641c\u7d22\u76ee\u7684\u5730\u6216\u5df2\u4fdd\u5b58\u884c\u7a0b"}
            </span>
          </div>
          <button className="header-icon" type="button" onClick={bootstrap} aria-label={copy.actions.refresh}>
            R
          </button>
          <button
            className="primary-pill"
            type="button"
            onClick={() => document.getElementById("generator")?.scrollIntoView({ behavior: "smooth" })}
          >
            {copy.actions.start}
          </button>
        </div>
      </header>

      <main className="planner-shell">
        <section className="planner-main">
          <section
            className={`generator-panel ${getMotionClassName("section", 0, hasMounted)}`}
            id="generator"
          >
            <div className="generator-header">
              <div>
                <div className="section-kicker">{zh("\u884c\u7a0b\u751f\u6210\u4e2d\u5fc3")}</div>
                <h1>{zh("\u7528 AI \u5feb\u901f\u751f\u6210\u53ef\u6267\u884c\u7684\u65c5\u884c\u5de5\u4f5c\u53f0\u3002")}</h1>
                <p>{zh("AI \u751f\u6210\u7684\u7ed3\u679c\u4f1a\u76f4\u63a5\u540c\u6b65\u5230\u65e5\u7a0b\u5361\u7247\uff0c\u8def\u7ebf\u5f15\u5bfc\uff0c\u4f4f\u5bbf\u63a8\u8350\u4e0e\u5730\u56fe\u5de5\u4f5c\u533a\u3002")}</p>
              </div>
              <div className="generator-orb" aria-hidden="true"></div>
            </div>

            <form className="generator-form" onSubmit={handleSubmit}>
              <label className="input-stack">
                <span>{zh("\u76ee\u7684\u5730")}</span>
                <div className="field-shell">
                  <span className="field-icon">AI</span>
                  <input
                    value={form.destination}
                    onChange={(event) => setForm({ ...form, destination: event.target.value })}
                    placeholder="\u4e1c\u4eac\u3001\u4eac\u90fd\u3001\u4e0a\u6d77\u3001\u9996\u5c14"
                    required
                  />
                </div>
              </label>

              <label className="input-stack">
                <span>{zh("\u51fa\u884c\u65b9\u5f0f")}</span>
                <div className="field-shell">
                  <span className="field-icon">Go</span>
                  <select
                    value={form.travelMode}
                    onChange={(event) => setForm({ ...form, travelMode: event.target.value })}
                  >
                    <option value="Public transit">{zh("\u516c\u5171\u4ea4\u901a")}</option>
                    <option value="Metro and walking">{zh("\u5730\u94c1 + \u6b65\u884c")}</option>
                    <option value="Ride-hailing and walking">{zh("\u6253\u8f66 + \u6b65\u884c")}</option>
                    <option value="Self-drive">{zh("\u81ea\u9a7e")}</option>
                  </select>
                </div>
              </label>

              <label className="input-stack">
                <span>{zh("\u5929\u6570")}</span>
                <div className="field-shell">
                  <span className="field-icon">#</span>
                  <input
                    type="number"
                    min="1"
                    max="14"
                    value={form.days}
                    onChange={(event) => setForm({ ...form, days: event.target.value })}
                    required
                  />
                </div>
              </label>

              <button
                className={`generator-button ${loading ? "is-generating" : ""}`}
                type="submit"
                disabled={loading}
              >
                {loading ? copy.actions.generateLoading : copy.actions.generateIdle}
              </button>
            </form>

            <div className="preset-row">
              {presets.map((preset) => (
                <button
                  key={preset.label}
                  type="button"
                  className="preset-chip"
                  onClick={() =>
                    setForm({
                      destination: preset.destination,
                      travelMode: preset.travelMode,
                      days: preset.days
                    })
                  }
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </section>

          <section
            className={`library-strip ${getMotionClassName("section", 1, hasMounted)}`}
            id="library"
          >
            <div className="library-head">
              <div>
                <div className="section-kicker">{zh("\u884c\u7a0b\u8d44\u4ea7")}</div>
                <h2>{copy.library.title}</h2>
              </div>

              <div className="library-filters">
                <button
                  type="button"
                  className={filter === "all" ? "filter-chip active" : "filter-chip"}
                  onClick={() => setFilter("all")}
                >
                  {copy.library.all}
                </button>
                <button
                  type="button"
                  className={filter === "favorites" ? "filter-chip active" : "filter-chip"}
                  onClick={() => setFilter("favorites")}
                >
                  {copy.library.favorites}
                </button>
              </div>
            </div>

            {visibleHistory.length ? (
              <div className="trip-rail">
                {visibleHistory.map((plan) => (
                  <article
                    key={plan.id}
                    className={plan.id === selectedId ? "trip-card selected" : "trip-card"}
                    onClick={() => handlePlanSelect(plan.id)}
                  >
                    <div className="trip-card-top">
                      <span className="trip-mode">{formatMode(plan.planning_mode)}</span>
                      <button
                        type="button"
                        className={plan.favorite ? "mini-favorite active" : "mini-favorite"}
                        onClick={(event) => {
                          event.stopPropagation();
                          toggleFavorite(plan);
                        }}
                      >
                        {plan.favorite ? "\u5df2\u6536\u85cf" : "\u6536\u85cf"}
                      </button>
                    </div>
                    <h3>{normalizeCopy(plan.destination)}</h3>
                    <p>{normalizeCopy(plan.trip_summary)}</p>
                    <div className="trip-card-meta">
                      <span>{`${plan.total_days} \u5929`}</span>
                      <span>{formatTravelMode(plan.travel_mode)}</span>
                      <span>{formatDate(plan.generated_at)}</span>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="empty-library">
                {zh("\u8fd8\u6ca1\u6709\u884c\u7a0b\u5361\u7247\uff0c\u751f\u6210\u4e00\u6761\u540e\u8fd9\u91cc\u4f1a\u81ea\u52a8\u586b\u5145\u3002")}
              </div>
            )}
          </section>

          {selectedPlan ? (
            <>
              <section
                key={`hero-${viewVersion}`}
                className={`plan-hero ${getMotionClassName("section", 2, hasMounted)}`}
                id="timeline"
                ref={timelineRef}
              >
                <div className="plan-hero-head">
                  <div>
                    <div className="hero-kicker">
                      <span>{zh("\u667a\u80fd\u65e5\u7a0b")}</span>
                      {selectedPlan.planning_sources?.length ? (
                        <span className="hero-chip">{zh("\u8054\u7f51\u589e\u5f3a AI")}</span>
                      ) : null}
                    </div>
                    <h2>{`${zh("\u63a2\u7d22\uff1a")}${normalizeCopy(selectedPlan.destination)}`}</h2>
                    <div className="hero-meta">
                      <span>{`${selectedPlan.total_days} \u5929`}</span>
                      <span>{formatTravelMode(selectedPlan.travel_mode)}</span>
                      <span>{formatMode(selectedPlan.planning_mode)}</span>
                      <span>{formatDate(selectedPlan.generated_at)}</span>
                    </div>
                  </div>

                  <div className="hero-summary-card">
                    <div className="hero-summary-label">{zh("\u672c\u6b21\u751f\u6210")}</div>
                    <strong>{formatMode(selectedPlan.planning_mode)}</strong>
                    <p>{planModeNote}</p>
                    <div className="hero-summary-capability">
                      <span>{zh("\u540e\u7aef\u80fd\u529b")}</span>
                      <strong>{formatMode(backendMode)}</strong>
                    </div>
                  </div>
                </div>

                <div className="day-tabs">
                  {selectedPlan.daily_itinerary.map((day) => (
                    <button
                      key={day.day}
                      type="button"
                      className={selectedDay === day.day ? "day-tab active" : "day-tab"}
                      onClick={() => handleDaySelect(day.day)}
                    >
                      {`\u7b2c ${day.day} \u5929`}
                    </button>
                  ))}
                </div>
              </section>

              <section className="timeline-stack">
                {selectedPlan.daily_itinerary.map((day) => (
                  <article
                    key={day.day}
                    className={`day-column ${selectedDay === day.day ? "active is-day-active" : ""} ${getMotionClassName(
                      "card",
                      day.day,
                      hasMounted
                    )}`}
                  >
                    <div className="day-header">
                      <div className="day-index">{String(day.day).padStart(2, "0")}</div>
                      <div>
                        <h3>{`${zh("\u7b2c")} ${day.day} ${zh("\u5929\uff1a")}${normalizeCopy(day.theme)}`}</h3>
                        <p>{describeDay(day)}</p>
                      </div>
                    </div>

                    <div className="activity-list">
                      {(day.activities || []).map((activity, index) => (
                        <article key={`${day.day}-${index}`} className="activity-card">
                          <div className="activity-topline">
                            <span className={`tone-chip ${toneClass(activity.type)}`}>{normalizeCopy(activity.time)}</span>
                            <span className="plain-chip">{normalizeCopy(activity.type)}</span>
                            {selectedDay === day.day && index === 0 ? (
                              <span className="smart-chip">{zh("\u5730\u56fe\u805a\u7126\u8d77\u70b9")}</span>
                            ) : null}
                          </div>

                          <div className="activity-grid">
                            <div className="activity-main">
                              <h4>{normalizeCopy(activity.name)}</h4>
                              <p>{normalizeCopy(activity.description)}</p>
                            </div>

                            <div className="activity-meta">
                              <div>
                                <span>{zh("\u4ea4\u901a\u5907\u6ce8")}</span>
                                <strong>
                                  {normalizeCopy(activity.transit_tip) ||
                                    "\u5c3d\u91cf\u4fdd\u6301\u5728\u540c\u4e00\u7247\u533a\u57df\uff0c\u884c\u8d70\u8def\u7ebf\u4f1a\u66f4\u987a\u7545\u3002"}
                                </strong>
                              </div>
                              {activity.social_link ? (
                                <a href={activity.social_link} target="_blank" rel="noreferrer">
                                  {zh("\u6253\u5f00\u53c2\u8003\u68c0\u7d22")}
                                </a>
                              ) : null}
                            </div>
                          </div>
                        </article>
                      ))}
                    </div>
                  </article>
                ))}
              </section>
            </>
          ) : (
            <section className="plan-empty">
              <div className="section-kicker">{zh("\u65b0\u5efa\u89c4\u5212")}</div>
              <h2>{zh("\u5148\u751f\u6210\u7b2c\u4e00\u4e2a\u884c\u7a0b\uff0c\u5373\u53ef\u89e3\u9501\u5b8c\u6574\u5de5\u4f5c\u53f0\u3002")}</h2>
              <p>{zh("\u751f\u6210\u540e\u4f1a\u540c\u6b65\u51fa\u73b0\u65f6\u95f4\u7ebf\uff0c\u8def\u7ebf\u5f15\u5bfc\uff0c\u63a8\u8350\u4e0e\u5730\u56fe\u8054\u52a8\u3002")}</p>
            </section>
          )}
        </section>

        <aside className="map-stage" id="assistant" ref={mapStageRef}>
          <div className="map-stage-inner">
            {selectedPlan ? (
              <>
                <div className={`map-insight-card ${loading ? "is-live" : ""}`}>
                  <div className="map-insight-head">
                    <strong>{mapSummary.title}</strong>
                    <span>{mapSummary.tag}</span>
                  </div>
                  <p>{mapSummary.description}</p>
                  <div className="map-insight-foot">
                    <span>{zh("\u4e0b\u4e00\u7ad9")}</span>
                    <strong>{mapSummary.nextStop}</strong>
                  </div>
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
                </div>

                <MapView
                  plan={selectedPlan}
                  selectedDay={selectedDay}
                  amapConfig={amapConfig}
                  focusedPointKey={focusedPointKey}
                />

                <div className="map-side-panels">
                  <section className="side-panel compact">
                    <div className="section-kicker">{zh("\u4f4f\u5bbf")}</div>
                    <h3>{normalizeCopy(selectedPlan.recommended_accommodation?.area || "\u5f85\u8865\u5145")}</h3>
                    <p>
                      {normalizeCopy(
                        selectedPlan.recommended_accommodation?.reason ||
                          "\u751f\u6210\u884c\u7a0b\u540e\uff0c\u8fd9\u91cc\u4f1a\u5c55\u793a\u4f4f\u5bbf\u5efa\u8bae\u3002"
                      )}
                    </p>
                  </section>

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

                  <section className="side-panel sources">
                    <div className="section-kicker">{zh("\u6765\u6e90\u4e0e\u72b6\u6001")}</div>
                    <div className="status-row">
                      <span>{zh("\u89c4\u5212\u6a21\u5f0f")}</span>
                      <strong>{formatMode(selectedPlan.planning_mode)}</strong>
                    </div>
                    <div className="status-row">
                      <span>{zh("\u5730\u56fe\u70b9\u4f4d")}</span>
                      <strong>{`${stats.points} \u4e2a\u771f\u5b9e\u5750\u6807`}</strong>
                    </div>
                    <div className="status-row">
                      <span>{zh("\u5f52\u56e0")}</span>
                      <strong>{normalizeCopy(selectedPlan.attribution || "TapToGo")}</strong>
                    </div>
                    {selectedPlan.planning_sources?.length ? (
                      <div className="source-list">
                        {selectedPlan.planning_sources.map((source, index) => (
                          <a key={`${source.url}-${index}`} href={source.url} target="_blank" rel="noreferrer">
                            {normalizeCopy(source.title)}
                          </a>
                        ))}
                      </div>
                    ) : (
                      <div className="source-empty">{planModeNote}</div>
                    )}
                  </section>

                  {activeDay ? (
                    <section className="side-panel">
                      <div className="section-kicker">{zh("\u5f53\u65e5\u805a\u7126")}</div>
                      <h3>{normalizeCopy(activeDay.theme)}</h3>
                      <p>{describeDay(activeDay)}</p>
                    </section>
                  ) : null}
                </div>
              </>
            ) : (
              <div className="map-empty">
                <div>
                  <div className="section-kicker">{copy.nav.map}</div>
                  <h3>{zh("\u7b49\u5f85\u751f\u6210\u884c\u7a0b")}</h3>
                  <p>{zh("\u751f\u6210\u540e\uff0c\u8fd9\u91cc\u4f1a\u5c55\u793a\u771f\u5b9e\u6807\u8bb0\uff0c\u8def\u7ebf\u805a\u7126\u4e0e AI \u5730\u56fe\u6d1e\u5bdf\u3002")}</p>
                </div>
              </div>
            )}
          </div>
        </aside>
      </main>

      <footer className="site-footer">
        <div>
          <div className="footer-brand">TapToGo</div>
          <p>{zh("AI \u534f\u4f5c\u7684\u65c5\u884c\u89c4\u5212\uff0c\u8ba9\u4e0b\u4e00\u6bb5\u65c5\u7a0b\u66f4\u9ad8\u6548\u3002")}</p>
        </div>

        <div>
          <h4>{zh("\u63a2\u7d22")}</h4>
          <a href="#generator">{copy.nav.generator}</a>
          <a href="#timeline">{copy.nav.timeline}</a>
          <a href="#library">{copy.nav.library}</a>
        </div>

        <div>
          <h4>{zh("\u64cd\u4f5c")}</h4>
          <a href="#assistant">{copy.nav.map}</a>
          <button type="button" onClick={bootstrap}>
            {copy.actions.refresh}
          </button>
          <button type="button" onClick={() => selectedPlan && exportToPdf(selectedPlan)}>
            {copy.actions.export}
          </button>
        </div>
      </footer>

      {stats && selectedPlan ? (
        <div className={`bottom-dock ${hasMounted ? "is-visible" : ""} ${loading ? "is-generating" : ""}`}>
          <div className="dock-total">
            <span className="dock-label">{zh("\u884c\u7a0b\u6458\u8981")}</span>
            <strong>
              {`${stats.days} \u5929 / ${stats.activities} \u4e2a\u6d3b\u52a8 / ${stats.points} \u4e2a\u5730\u56fe\u70b9 / ${stats.sources} \u4e2a\u6765\u6e90`}
            </strong>
          </div>

          <div className="dock-actions">
            <button type="button" className="ghost-action" onClick={() => toggleFavorite(selectedPlan)}>
              {selectedPlan.favorite ? "\u53d6\u6d88\u6536\u85cf" : "\u6536\u85cf\u884c\u7a0b"}
            </button>
            <button type="button" className="primary-pill dock-cta" onClick={() => exportToPdf(selectedPlan)}>
              {copy.actions.export}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function MapView({ plan, selectedDay, amapConfig, focusedPointKey }) {
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const overlays = useRef([]);
  const markerIndexRef = useRef(new Map());
  const infoWindowIndexRef = useRef(new Map());
  const [unavailable, setUnavailable] = useState(false);
  const [mapError, setMapError] = useState("");
  const [resolvedPoints, setResolvedPoints] = useState([]);
  const [resolving, setResolving] = useState(false);
  const mapEntries = buildMapEntriesData(plan, selectedDay);
  const routePoints = buildResolvedRoutePoints(resolvedPoints, selectedDay);
  const hasRenderablePoints = resolvedPoints.length > 0;

  useEffect(() => {
    return () => {
      clearAmapOverlays(overlays.current);
      overlays.current = [];
      markerIndexRef.current.clear();
      infoWindowIndexRef.current.clear();

      if (mapInstance.current) {
        mapInstance.current.destroy();
        mapInstance.current = null;
      }
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function syncAmapView() {
      setResolving(true);

      try {
        const AMap = await loadAmapJsApi(amapConfig);
        if (cancelled) {
          return;
        }

        if (!mapInstance.current && mapRef.current) {
          mapInstance.current = new AMap.Map(mapRef.current, {
            viewMode: "3D",
            zoom: 11,
            center: [116.397428, 39.90923],
            mapStyle: "amap://styles/whitesmoke"
          });
          mapInstance.current.addControl(new AMap.Scale());
          mapInstance.current.addControl(new AMap.ToolBar());
        }

        if (!mapInstance.current) {
          return;
        }

        const nextPoints = await resolveMapEntriesWithAmap(AMap, plan.destination, mapEntries);
        const destinationFallback = await resolveDestinationFallbackPoint(AMap, plan.destination);
        const pointsToRender = nextPoints.length ? nextPoints : destinationFallback ? [destinationFallback] : [];
        if (cancelled) {
          return;
        }

        setUnavailable(false);
        setResolvedPoints(pointsToRender);
        setMapError(
          nextPoints.length
            ? ""
            : destinationFallback
              ? zh("\u884c\u7a0b\u70b9\u4f4d\u6682\u672a\u5168\u90e8\u5339\u914d\uff0c\u5df2\u5148\u5b9a\u4f4d\u5230\u76ee\u7684\u5730\u4e2d\u5fc3\u70b9\u3002")
            : zh("\u9ad8\u5fb7\u5730\u56fe\u6682\u672a\u68c0\u7d22\u5230\u53ef\u7528\u70b9\u4f4d\uff0c\u8bf7\u68c0\u67e5 JS Key\u3001\u5b89\u5168\u5bc6\u94a5\u6216\u884c\u7a0b\u540d\u79f0\u3002")
        );

        renderAmapOverlays(
          AMap,
          mapInstance.current,
          overlays,
          markerIndexRef,
          infoWindowIndexRef,
          pointsToRender,
          selectedDay
        );
        mapInstance.current.resize();
      } catch (error) {
        if (cancelled) {
          return;
        }
        setUnavailable(true);
        setResolvedPoints([]);
        setMapError(error.message || zh("\u9ad8\u5fb7\u5730\u56fe\u6682\u65f6\u4e0d\u53ef\u7528"));
      } finally {
        if (!cancelled) {
          setResolving(false);
        }
      }
    }

    syncAmapView();

    return () => {
      cancelled = true;
      clearAmapOverlays(overlays.current);
      overlays.current = [];
      markerIndexRef.current.clear();
      infoWindowIndexRef.current.clear();
    };
  }, [amapConfig.key, amapConfig.securityJsCode, plan.id, plan.destination, selectedDay]);

  useEffect(() => {
    if (unavailable || !mapInstance.current || !mapRef.current || typeof ResizeObserver === "undefined") {
      return;
    }

    const resizeObserver = new ResizeObserver(() => {
      if (mapInstance.current) {
        mapInstance.current.resize();
      }
    });
    resizeObserver.observe(mapRef.current);

    const frameId = window.requestAnimationFrame(() => {
      if (mapInstance.current) {
        mapInstance.current.resize();
      }
    });

    return () => {
      window.cancelAnimationFrame(frameId);
      resizeObserver.disconnect();
    };
  }, [hasRenderablePoints, routePoints.length, unavailable]);

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
  }, [focusedPointKey, resolvedPoints, selectedDay]);

  if (unavailable) {
    return (
      <div className="map-fallback">
        <div>
          <strong>{zh("\u9ad8\u5fb7\u5730\u56fe\u6682\u65f6\u4e0d\u53ef\u7528")}</strong>
          <p>{normalizeCopy(mapError) || zh("\u8bf7\u68c0\u67e5 JS Key\uff0csecurityJsCode \u548c\u52a0\u8f7d\u811a\u672c\u914d\u7f6e\u3002")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="map-canvas-shell">
      <div ref={mapRef} className="map-canvas"></div>

      {resolving && !hasRenderablePoints ? (
        <div className="map-status-overlay">
          <div>
            <strong>{zh("\u9ad8\u5fb7\u5730\u56fe\u6b63\u5728\u5bf9\u9f50\u70b9\u4f4d")}</strong>
            <p>{zh("\u6b63\u5728\u4f18\u5148\u4f7f\u7528\u540e\u7aef\u5750\u6807\uff0c\u5e76\u7528\u9ad8\u5fb7 POI \u68c0\u7d22\u4e3a\u5f53\u524d\u884c\u7a0b\u8865\u70b9\u3002")}</p>
          </div>
        </div>
      ) : null}

      {!resolving && !hasRenderablePoints ? (
        <div className="map-status-overlay">
          <div>
            <strong>{zh("\u5f53\u524d\u5730\u56fe\u8fd8\u6ca1\u6709\u53ef\u7528\u5750\u6807")}</strong>
            <p>
              {selectedDay
                ? `\u7b2c ${selectedDay} \u5929\u6682\u672a\u5339\u914d\u5230\u53ef\u5c55\u793a\u7684\u70b9\u4f4d\uff0c\u4f60\u53ef\u4ee5\u7a0d\u540e\u91cd\u65b0\u751f\u6210\u6216\u68c0\u67e5\u9ad8\u5fb7 JS \u914d\u7f6e\u3002`
                : "\u5f53\u524d\u884c\u7a0b\u8fd8\u6ca1\u6709\u5339\u914d\u5230\u53ef\u7528\u70b9\u4f4d\uff0c\u53ef\u4ee5\u5148\u67e5\u770b\u65f6\u95f4\u7ebf\u4e0e\u4f4f\u5bbf\u63a8\u8350\u3002"}
            </p>
            {mapError ? <p className="map-empty-detail">{normalizeCopy(mapError)}</p> : null}
            {plan?.attribution ? <p className="map-empty-detail">{normalizeCopy(plan.attribution)}</p> : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function clearAmapOverlays(items) {
  items.forEach((item) => item?.setMap?.(null));
}

function renderAmapOverlays(
  AMap,
  map,
  overlaysRef,
  markerIndexRef,
  infoWindowIndexRef,
  points,
  selectedDay
) {
  clearAmapOverlays(overlaysRef.current);
  overlaysRef.current = [];
  markerIndexRef.current.clear();
  infoWindowIndexRef.current.clear();

  points.forEach((point) => {
    const marker = createAmapMarker(AMap, point);
    marker.setMap(map);
    overlaysRef.current.push(marker);
    markerIndexRef.current.set(point.key, marker);
    if (marker.__tapToGoInfoWindow) {
      infoWindowIndexRef.current.set(point.key, marker.__tapToGoInfoWindow);
    }
  });

  const routePoints = buildResolvedRoutePoints(points, selectedDay);
  if (routePoints.length > 1) {
    const polyline = new AMap.Polyline({
      path: routePoints.map((point) => [point.lon, point.lat]),
      strokeColor: "#5f8fff",
      strokeWeight: 5,
      strokeOpacity: 0.82,
      strokeStyle: "dashed"
    });
    polyline.setMap(map);
    overlaysRef.current.push(polyline);
  }

  if (overlaysRef.current.length) {
    map.setFitView(overlaysRef.current, false, [40, 40, 40, 40]);
  }
}

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

async function loadAmapJsApi(amapConfig) {
  const key = String(amapConfig?.key || window.TAPTOGO_AMAP_KEY || "").trim();
  const securityJsCode = String(amapConfig?.securityJsCode || window.TAPTOGO_AMAP_SECURITY_CODE || "").trim();

  if (!key || !securityJsCode) {
    throw new Error(zh("\u9ad8\u5fb7\u524d\u7aef\u5730\u56fe\u672a\u914d\u7f6e JS Key \u6216 securityJsCode\u3002"));
  }
  if (!window.AMapLoader) {
    throw new Error(zh("\u9ad8\u5fb7 Loader \u811a\u672c\u672a\u6b63\u786e\u52a0\u8f7d\u3002"));
  }

  window._AMapSecurityConfig = {
    securityJsCode
  };

  if (
    amapLoaderState.promise &&
    amapLoaderState.key === key &&
    amapLoaderState.securityJsCode === securityJsCode
  ) {
    return amapLoaderState.promise;
  }

  amapLoaderState = {
    key,
    securityJsCode,
    promise: window.AMapLoader.load({
      key,
      version: AMAP_JS_API_VERSION,
      plugins: AMAP_PLUGIN_LIST
    })
  };

  return amapLoaderState.promise;
}

async function resolveMapEntriesWithAmap(AMap, destination, entries) {
  const resolved = [];

  for (const entry of entries) {
    const point = await resolveMapEntryWithAmap(AMap, destination, entry);
    if (point) {
      resolved.push(point);
    }
  }

  return dedupeResolvedPoints(resolved);
}

async function resolveDestinationFallbackPoint(AMap, destination) {
  const keyword = String(destination || "").trim();
  if (!keyword) {
    return null;
  }

  const placeMatch = await searchAmapPlace(AMap, "", keyword);
  const geocodeMatch = placeMatch ? null : await geocodeAmapKeyword(AMap, "", keyword);
  const location = placeMatch || geocodeMatch;
  if (!location) {
    return null;
  }

  return {
    key: `destination::${keyword}`,
    lat: location.lat,
    lon: location.lon,
    label: `${keyword}\u4e2d\u5fc3`,
    address: keyword,
    kind: "other",
    primary: true,
    day: 0,
    sequence: 0,
    kindLabel: getMapKindMeta("other").label
  };
}

async function resolveMapEntryWithAmap(AMap, destination, entry) {
  const existingLocation = normalizeCoordinatePair(entry.lat, entry.lon);
  if (existingLocation) {
    return { ...entry, ...existingLocation };
  }

  const cacheKey = `${destination}::${entry.kind}::${entry.label}::${entry.address}`;
  if (amapLookupCache.has(cacheKey)) {
    const cached = amapLookupCache.get(cacheKey);
    return cached ? { ...entry, ...cached } : null;
  }

  const keywords = buildAmapSearchKeywords(entry, destination);
  const primaryLookup = await resolveMapEntryLookup(AMap, destination, keywords);
  if (primaryLookup) {
    amapLookupCache.set(cacheKey, primaryLookup);
    return { ...entry, ...primaryLookup };
  }

  const broadLookup = destination ? await resolveMapEntryLookup(AMap, "", keywords) : null;
  if (broadLookup) {
    amapLookupCache.set(cacheKey, broadLookup);
    return { ...entry, ...broadLookup };
  }

  amapLookupCache.set(cacheKey, null);
  return null;
}

async function resolveMapEntryLookup(AMap, destination, keywords) {
  for (const keyword of keywords) {
    const placeMatch = await searchAmapPlace(AMap, destination, keyword);
    if (placeMatch) {
      return placeMatch;
    }

    const geocodeMatch = await geocodeAmapKeyword(AMap, destination, keyword);
    if (geocodeMatch) {
      return geocodeMatch;
    }
  }

  return null;
}

function searchAmapPlace(AMap, destination, keyword) {
  return new Promise((resolve) => {
    const city = String(destination || "").trim();
    const placeSearch = new AMap.PlaceSearch({
      ...(city ? { city, citylimit: false } : {}),
      pageSize: 1,
      pageIndex: 1
    });

    placeSearch.search(keyword, (status, result) => {
      if (status !== "complete") {
        resolve(null);
        return;
      }

      const poi = result?.poiList?.pois?.[0];
      const location = extractAmapLngLat(poi?.location);
      resolve(location);
    });
  });
}

function geocodeAmapKeyword(AMap, destination, keyword) {
  return new Promise((resolve) => {
    const city = String(destination || "").trim();
    const geocoder = new AMap.Geocoder(city ? { city } : {});

    geocoder.getLocation(keyword, (status, result) => {
      if (status !== "complete") {
        resolve(null);
        return;
      }

      const location = extractAmapLngLat(result?.geocodes?.[0]?.location);
      resolve(location);
    });
  });
}

function extractAmapLngLat(location) {
  if (!location) {
    return null;
  }

  if (typeof location.getLng === "function" && typeof location.getLat === "function") {
    return {
      lon: location.getLng(),
      lat: location.getLat()
    };
  }

  if (isFiniteCoordinate(location.lng) && isFiniteCoordinate(location.lat)) {
    return {
      lon: location.lng,
      lat: location.lat
    };
  }

  if (Array.isArray(location) && location.length >= 2) {
    const lon = Number(location[0]);
    const lat = Number(location[1]);
    if (Number.isFinite(lon) && Number.isFinite(lat)) {
      return { lon, lat };
    }
  }

  return null;
}

function buildAmapSearchKeywords(entry, destination) {
  const keywords = [
    entry.address,
    entry.label,
    entry.label && destination ? `${entry.label} ${destination}` : ""
  ];

  return [...new Set(keywords.filter(Boolean))];
}

function dedupeResolvedPoints(points) {
  const seen = new Set();
  return points.filter((point) => {
    const key = `${point.kind}::${point.label}::${point.lat}::${point.lon}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function buildResolvedRoutePoints(points, selectedDay) {
  if (!selectedDay) {
    return [];
  }

  return points
    .filter((point) => Number(point.day) === Number(selectedDay) && point.kind === "spot")
    .sort((left, right) => left.sequence - right.sequence);
}

async function api(path, options) {
  const response = await fetch(`${API_BASE}${path}`, options);
  const text = await response.text();
  let data = null;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { detail: text };
    }
  }

  if (!response.ok) {
    throw new Error(data?.detail || "Request failed.");
  }

  return data;
}

function collectMapPoints(plan, selectedDay) {
  const points = [];

  (plan.recommended_hotels || []).forEach((item, index) => pushPoint(points, item, "stay", index === 0));
  (plan.recommended_restaurants || []).forEach((item, index) => pushPoint(points, item, "food", index === 0));

  (plan.daily_itinerary || []).forEach((day) => {
    if (selectedDay && day.day !== selectedDay) {
      return;
    }
    (day.activities || []).forEach((item, index) => {
      pushPoint(points, item, "spot", index === 0 && day.day === selectedDay);
    });
  });

  return points;
}

function collectMapEntries(plan, selectedDay) {
  const entries = [];

  (plan.recommended_hotels || []).forEach((item, index) =>
    pushMapEntry(entries, item, "stay", index === 0, 0, index)
  );
  (plan.recommended_restaurants || []).forEach((item, index) =>
    pushMapEntry(entries, item, "food", index === 0, 0, index)
  );

  (plan.daily_itinerary || []).forEach((day) => {
    if (selectedDay && day.day !== selectedDay) {
      return;
    }
    (day.activities || []).forEach((item, index) => {
      pushMapEntry(entries, item, "spot", index === 0 && day.day === selectedDay, day.day, index);
    });
  });

  return entries;
}

function collectRoutePoints(plan, selectedDay) {
  if (!selectedDay) {
    return [];
  }

  const day = (plan.daily_itinerary || []).find((entry) => entry.day === selectedDay);
  if (!day) {
    return [];
  }

  return (day.activities || [])
    .filter((item) => isFiniteCoordinate(item.latitude) && isFiniteCoordinate(item.longitude))
    .map((item) => ({ lat: item.latitude, lon: item.longitude }));
}

function pushMapEntry(entries, item, kind, primary, day, sequence) {
  const kindLabel = kind === "stay" ? "\u4f4f\u5bbf" : kind === "food" ? "\u7f8e\u98df" : "\u6d3b\u52a8";

  entries.push({
    lat: item.latitude,
    lon: item.longitude,
    label: normalizeCopy(item.name),
    address: normalizeCopy(item.address),
    kind,
    primary,
    day,
    sequence,
    kindLabel
  });
}

function pushPoint(points, item, kind, primary) {
  if (!isFiniteCoordinate(item.latitude) || !isFiniteCoordinate(item.longitude)) {
    return;
  }

  const kindLabel = kind === "stay" ? "\u4f4f\u5bbf" : kind === "food" ? "\u7f8e\u98df" : "\u6d3b\u52a8";
  points.push({
    lat: item.latitude,
    lon: item.longitude,
    label: normalizeCopy(item.name),
    kind,
    primary,
    kindLabel
  });
}

function isFiniteCoordinate(value) {
  return normalizeCoordinateValue(value) !== null;
}

function normalizeCoordinateValue(value) {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string" && value.trim()) {
    const next = Number(value);
    return Number.isFinite(next) ? next : null;
  }
  return null;
}

function normalizeCoordinatePair(lat, lon) {
  const nextLat = normalizeCoordinateValue(lat);
  const nextLon = normalizeCoordinateValue(lon);
  if (nextLat === null || nextLon === null) {
    return null;
  }

  return {
    lat: nextLat,
    lon: nextLon
  };
}

function isStackedPlannerLayout() {
  return Boolean(window.matchMedia?.(STACKED_PLANNER_MEDIA_QUERY).matches);
}

function loadAmapRuntimeConfig() {
  return {
    key: String(window.TAPTOGO_AMAP_KEY || "").trim(),
    securityJsCode: String(window.TAPTOGO_AMAP_SECURITY_CODE || "").trim()
  };
}

function mergeAmapRuntimeConfig(current, health) {
  return {
    key: String(health?.amap_js_key || current?.key || "").trim(),
    securityJsCode: String(health?.amap_security_js_code || current?.securityJsCode || "").trim()
  };
}

function loadStoredForm() {
  try {
    const parsed = JSON.parse(localStorage.getItem(FORM_STORAGE_KEY) || "{}");
    return {
      destination: parsed.destination || "Kyoto",
      travelMode: parsed.travelMode || "Metro and walking",
      days: parsed.days || 4
    };
  } catch {
    return {
      destination: "Kyoto",
      travelMode: "Metro and walking",
      days: 4
    };
  }
}

function normalizeCopy(value) {
  return String(value || "")
    .replaceAll("Morning", "\u4e0a\u5348")
    .replaceAll("Afternoon", "\u4e0b\u5348")
    .replaceAll("Evening", "\u665a\u95f4")
    .replaceAll("Sight", "\u666f\u70b9")
    .replaceAll("Food", "\u7f8e\u98df")
    .replaceAll("Transit", "\u4ea4\u901a");
}

function formatMode(mode) {
  return formatModeZh(mode);
}

function formatTravelMode(mode) {
  return formatTravelModeZh(mode);
}

function formatDate(value) {
  try {
    return new Intl.DateTimeFormat("zh-CN", {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(new Date(value));
  } catch {
    return value;
  }
}

function describeDay(day) {
  return describeDayZh(day);
}

function describePlanMode(mode, sourceCount) {
  return describePlanModeZh(mode, sourceCount);
}

function summarizePlan(plan) {
  const activities = (plan.daily_itinerary || []).reduce((count, day) => count + (day.activities || []).length, 0);
  const points = [
    ...(plan.recommended_hotels || []),
    ...(plan.recommended_restaurants || []),
    ...(plan.daily_itinerary || []).flatMap((day) => day.activities || [])
  ].filter((item) => isFiniteCoordinate(item.latitude) && isFiniteCoordinate(item.longitude)).length;

  return {
    days: plan.total_days || (plan.daily_itinerary || []).length,
    activities,
    points,
    sources: (plan.planning_sources || []).length
  };
}

function buildMapSummary(plan, selectedDay) {
  if (typeof buildMapSummaryZh === "function") {
    return buildMapSummaryZh(plan, selectedDay);
  }
  const activeDay = (plan.daily_itinerary || []).find((day) => day.day === selectedDay) || plan.daily_itinerary?.[0];

  return {
    title: activeDay ? `Day ${activeDay.day} \u8def\u7ebf\u7126\u70b9` : "\u5f53\u524d\u533a\u57df\u6d1e\u5bdf",
    tag: plan.planning_sources?.length ? "AI \u6d1e\u5bdf" : "\u7cbe\u9009\u63a8\u8350",
    description: activeDay
      ? `\u4eca\u5929\u56f4\u7ed5\u201c${normalizeCopy(activeDay.theme)}\u201d\u5c55\u5f00\uff0c\u5df2\u5c06 ${activeDay.activities?.length || 0} \u4e2a\u5b89\u6392\u538b\u7f29\u4e3a\u4e00\u6761\u66f4\u987a\u8def\u7684\u79fb\u52a8\u5e8f\u5217\u3002`
      : "\u5730\u56fe\u4f1a\u6839\u636e\u5f53\u524d\u9009\u4e2d\u7684\u884c\u7a0b\u81ea\u52a8\u5207\u6362\u6807\u8bb0\u4e0e\u8def\u7ebf\u3002",
    nextStop: normalizeCopy(activeDay?.activities?.[0]?.name || "\u7b49\u5f85\u9009\u62e9")
  };
}

function buildRecommendationCards(plan) {
  return [
    ...(plan.recommended_hotels || []).slice(0, 2).map((item) => ({ ...item, kind: "stay" })),
    ...(plan.recommended_restaurants || []).slice(0, 2).map((item) => ({ ...item, kind: "food" }))
  ];
}

function toneClass(type) {
  const normalized = String(type || "").toLowerCase();
  if (normalized.includes("sight")) return "tone-blue";
  if (normalized.includes("food")) return "tone-amber";
  if (normalized.includes("transit")) return "tone-slate";
  return "tone-green";
}

function getStatusMessage(kind) {
  if (typeof getStatusMessageZh === "function") {
    return getStatusMessageZh(kind);
  }

  if (kind === "loading") {
    return "AI \u6b63\u5728\u751f\u6210\u5177\u4f53\u5b89\u6392\uff0c\u5e76\u540c\u6b65\u8865\u5168\u5730\u56fe\u70b9\u4f4d\u4e0e\u63a8\u8350\u4fe1\u606f...";
  }
  if (kind === "ready") {
    return "\u65b0\u7684 AI \u884c\u7a0b\u5df2\u7ecf\u843d\u5230\u65f6\u95f4\u7ebf\u91cc\uff0c\u4e0b\u9762\u770b\u5230\u7684\u5c31\u662f\u53ef\u6267\u884c\u5b89\u6392\u3002";
  }
  if (kind === "history") {
    return "\u5df2\u52a0\u8f7d\u5386\u53f2\u884c\u7a0b\u3002\u5207\u6362\u5361\u7247\u540e\uff0c\u53f3\u4fa7\u5730\u56fe\u548c\u65f6\u95f4\u7ebf\u4f1a\u540c\u6b65\u8054\u52a8\u3002";
  }
  if (kind === "empty") {
    return "\u8fd8\u6ca1\u6709\u5386\u53f2\u884c\u7a0b\uff0c\u5148\u4ece\u4e0a\u65b9\u751f\u6210\u5668\u5f00\u59cb\u3002";
  }
  if (kind === "error") {
    return "\u64cd\u4f5c\u672a\u5b8c\u6210\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002";
  }
  return "\u8f93\u5165\u76ee\u7684\u5730\u540e\uff0cAI \u4f1a\u76f4\u63a5\u751f\u6210\u5230\u65e5\u7a0b\u65f6\u95f4\u7ebf\u3001\u5730\u56fe\u548c\u4f4f\u5bbf\u63a8\u8350\u91cc\u3002";
}

function exportToPdf(plan) {
  const printable = window.open("", "_blank", "noopener,noreferrer,width=1180,height=860");
  if (!printable) {
    return;
  }

  const daysMarkup = (plan.daily_itinerary || [])
    .map((day) => {
      const activitiesMarkup = (day.activities || [])
        .map(
          (activity) => `
            <article style="padding:16px 18px;border-radius:20px;background:#f4f5ff;margin-bottom:14px;">
              <div style="font-size:12px;color:#6d759e;margin-bottom:8px;">${escapeHtml(normalizeCopy(activity.time))} / ${escapeHtml(normalizeCopy(activity.type))}</div>
              <strong style="font-size:16px;">${escapeHtml(normalizeCopy(activity.name))}</strong>
              <p style="margin:8px 0 0;line-height:1.7;">${escapeHtml(normalizeCopy(activity.description))}</p>
              <p style="margin:8px 0 0;color:#515981;line-height:1.6;">Transit note: ${escapeHtml(normalizeCopy(activity.transit_tip))}</p>
            </article>
          `
        )
        .join("");

      return `
        <section style="margin-top:28px;">
          <h2 style="font-size:22px;margin:0 0 12px;">Day ${escapeHtml(day.day)} / ${escapeHtml(normalizeCopy(day.theme))}</h2>
          ${activitiesMarkup}
        </section>
      `;
    })
    .join("");

  printable.document.write(`
    <!DOCTYPE html>
    <html lang="en">
      <head>
        <meta charset="UTF-8" />
        <title>${escapeHtml(normalizeCopy(plan.destination))} itinerary</title>
      </head>
      <body style="font-family:Arial,sans-serif;padding:40px;color:#242c51;background:#ffffff;">
        <h1 style="margin:0 0 10px;">${escapeHtml(normalizeCopy(plan.destination))}</h1>
        <p style="margin:0 0 12px;color:#515981;">${escapeHtml(normalizeCopy(plan.trip_summary))}</p>
        <p style="margin:0;color:#6d759e;">${escapeHtml(plan.total_days)} days / ${escapeHtml(formatTravelMode(plan.travel_mode))} / ${escapeHtml(formatMode(plan.planning_mode))}</p>
        <section style="margin-top:24px;padding:18px 20px;border-radius:24px;background:#f0efff;">
          <h2 style="font-size:20px;margin:0 0 8px;">Stay recommendation</h2>
          <strong>${escapeHtml(normalizeCopy(plan.recommended_accommodation?.area || ""))}</strong>
          <p style="margin:8px 0 0;line-height:1.6;">${escapeHtml(normalizeCopy(plan.recommended_accommodation?.reason || ""))}</p>
        </section>
        ${daysMarkup}
      </body>
    </html>
  `);

  printable.document.close();
  printable.focus();
  printable.print();
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
