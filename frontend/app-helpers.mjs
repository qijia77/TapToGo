const copy = {
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
};

export function getUiCopy() {
  return copy;
}

export function formatTravelModeZh(mode) {
  if (mode === "Public transit") return "\u516c\u5171\u4ea4\u901a";
  if (mode === "Metro and walking") return "\u5730\u94c1 + \u6b65\u884c";
  if (mode === "Ride-hailing and walking") return "\u6253\u8f66 + \u6b65\u884c";
  if (mode === "Self-drive") return "\u81ea\u9a7e";
  return String(mode || "");
}

export function formatModeZh(mode) {
  if (mode === "openai-web-search") return "AI \u8054\u7f51\u751f\u6210";
  if (mode === "openai") return "AI \u5b9e\u65f6\u751f\u6210";
  if (mode === "demo") return "\u6f14\u793a\u6a21\u5f0f";
  if (mode === "demo-fallback") return "AI \u964d\u7ea7\u56de\u9000";
  if (mode === "offline") return "\u79bb\u7ebf";
  if (mode === "checking") return "\u68c0\u6d4b\u4e2d";
  return String(mode || "");
}

export function describeDayZh(day) {
  const first = String(
    day?.activities?.[0]?.name || "\u5f53\u5929\u91cd\u70b9"
  );
  const last = String(
    day?.activities?.[day?.activities?.length - 1]?.name ||
      "\u6536\u5c3e\u8282\u70b9"
  );
  const count = day?.activities?.length || 0;
  return `\u4ece ${first} \u5f00\u59cb\uff0c\u4e32\u8054 ${count} \u4e2a\u6267\u884c\u8282\u70b9\uff0c\u6700\u7ec8\u56de\u6536\u5230 ${last}\u3002`;
}

export function describePlanModeZh(mode, sourceCount = 0) {
  if (mode === "demo-fallback") {
    return "\u672c\u6b21\u6ca1\u6709\u76f4\u63a5\u91c7\u7528 AI \u8054\u7f51\u7ed3\u679c\uff0c\u7cfb\u7edf\u5df2\u81ea\u52a8\u56de\u9000\u4e3a\u53ef\u6267\u884c\u7684\u964d\u7ea7\u5b89\u6392\u3002";
  }
  if (mode === "openai-web-search") {
    return `\u8fd9\u6b21\u884c\u7a0b\u7531 AI \u8054\u7f51\u751f\u6210\uff0c\u5df2\u4fdd\u7559 ${sourceCount} \u6761\u8054\u7f51\u6765\u6e90\u4f9b\u4f60\u56de\u67e5\u3002`;
  }
  if (mode === "openai") {
    return "\u8fd9\u6b21\u884c\u7a0b\u7531 AI \u76f4\u63a5\u751f\u6210\uff0c\u4f46\u672a\u9644\u5e26\u8054\u7f51\u68c0\u7d22\u6765\u6e90\u3002";
  }
  if (mode === "demo") {
    return "\u8fd9\u6b21\u5c55\u793a\u7684\u662f\u6f14\u793a\u884c\u7a0b\uff0c\u4e0d\u4ee3\u8868 AI \u5b9e\u65f6\u8054\u7f51\u7ed3\u679c\u3002";
  }
  if (mode === "offline") {
    return "\u5f53\u524d\u540e\u7aef\u65e0\u6cd5\u8fde\u63a5 AI \u80fd\u529b\uff0c\u4ec5\u53ef\u67e5\u770b\u672c\u5730\u5df2\u751f\u6210\u5185\u5bb9\u3002";
  }
  return "\u8fd9\u4efd\u884c\u7a0b\u7684\u751f\u6210\u72b6\u6001\u6682\u65f6\u65e0\u6cd5\u51c6\u786e\u5224\u5b9a\u3002";
}

export function getMotionClassName(kind, delayIndex, visible) {
  const parts = ["motion-enter", `motion-${kind}`, `motion-delay-${delayIndex}`];
  if (visible) {
    parts.push("is-visible");
  }
  return parts.join(" ");
}

export function isSelfDriveMode(mode) {
  return String(mode || "").trim().toLowerCase() === "self-drive";
}

export function buildPlaceKey(kind, item) {
  return `${kind}::${String(item?.name || "").trim()}::${item?.day ?? "all"}`;
}

export function getMapKindMeta(kind) {
  const palette = {
    spot: {
      label: "\u666f\u70b9",
      marker: "\u666f",
      background: "#0b8a78",
      foreground: "#ffffff"
    },
    stay: {
      label: "\u4f4f\u5bbf",
      marker: "\u5bbf",
      background: "#0057be",
      foreground: "#ffffff"
    },
    food: {
      label: "\u5403\u996d",
      marker: "\u98df",
      background: "#c4571e",
      foreground: "#ffffff"
    },
    parking: {
      label: "\u505c\u8f66",
      marker: "P",
      background: "#3348b8",
      foreground: "#ffffff"
    },
    refuel: {
      label: "\u52a0\u6cb9",
      marker: "\u6cb9",
      background: "#7b4d1f",
      foreground: "#ffffff"
    },
    charging: {
      label: "\u5145\u7535",
      marker: "\u7535",
      background: "#0f9fb8",
      foreground: "#ffffff"
    },
    other: {
      label: "\u70b9\u4f4d",
      marker: "\u70b9",
      background: "#6f9fff",
      foreground: "#ffffff"
    }
  };

  return palette[kind] || palette.other;
}

export function buildRecommendationSections(plan, selectedDay) {
  const matchesDay = (item) =>
    !selectedDay || item?.day == null || Number(item.day) === Number(selectedDay);
  const nearby = (plan?.recommended_food_nearby || []).filter(matchesDay);
  const hot = (plan?.recommended_food_hot || []).length
    ? plan.recommended_food_hot
    : plan?.recommended_restaurants || [];
  const sections = [];

  if ((plan?.recommended_hotels || []).length) {
    sections.push({
      key: "stay",
      kicker: "\u4f4f\u5bbf",
      title: "\u4f4f\u5bbf\u5468\u8fb9",
      items: plan.recommended_hotels.slice(0, 3).map((item) => ({
        ...item,
        kind: "stay",
        mapKey: buildPlaceKey("stay", item)
      }))
    });
  }

  if (nearby.length) {
    sections.push({
      key: "food-nearby",
      kicker: "\u7f8e\u98df",
      title: "\u666f\u70b9\u9644\u8fd1",
      items: nearby.slice(0, 6).map((item) => ({
        ...item,
        kind: "food",
        mapKey: buildPlaceKey("food", item)
      }))
    });
  }

  if (hot.length) {
    sections.push({
      key: "food-hot",
      kicker: "\u7f8e\u98df",
      title: "\u7206\u706b\u63a8\u8350",
      items: hot.slice(0, 6).map((item) => ({
        ...item,
        kind: "food",
        mapKey: buildPlaceKey("food", item)
      }))
    });
  }

  if (isSelfDriveMode(plan?.travel_mode)) {
    const groups = [
      {
        key: "parking",
        title: "\u505c\u8f66",
        items: filterDriveItems(plan?.recommended_parking || [], selectedDay, "parking")
      },
      {
        key: "refuel",
        title: "\u52a0\u6cb9",
        items: filterDriveItems(plan?.recommended_refuel || [], selectedDay, "refuel")
      },
      {
        key: "charging",
        title: "\u5145\u7535",
        items: filterDriveItems(plan?.recommended_charging || [], selectedDay, "charging")
      }
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

export function buildMapEntriesData(plan, selectedDay) {
  const entries = [];

  const pushEntry = (item, kind, primary, day = 0, sequence = 0) => {
    if (!item) {
      return;
    }
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

  (plan?.recommended_hotels || []).forEach((item, index) => {
    pushEntry(item, "stay", index === 0);
  });

  (plan?.recommended_food_nearby || []).forEach((item, index) => {
    if (!selectedDay || item?.day == null || Number(item.day) === Number(selectedDay)) {
      pushEntry(item, "food", index === 0, item?.day ?? 0, index);
    }
  });

  const hotItems = (plan?.recommended_food_hot || []).length
    ? plan.recommended_food_hot
    : plan?.recommended_restaurants || [];
  hotItems.forEach((item, index) => {
    pushEntry(item, "food", false, item?.day ?? 0, index);
  });

  (plan?.recommended_parking || []).forEach((item, index) => {
    if (!selectedDay || item?.day == null || Number(item.day) === Number(selectedDay)) {
      pushEntry(item, "parking", index === 0, item?.day ?? 0, index);
    }
  });

  (plan?.recommended_refuel || []).forEach((item, index) => {
    if (!selectedDay || item?.day == null || Number(item.day) === Number(selectedDay)) {
      pushEntry(item, "refuel", index === 0, item?.day ?? 0, index);
    }
  });

  (plan?.recommended_charging || []).forEach((item, index) => {
    if (!selectedDay || item?.day == null || Number(item.day) === Number(selectedDay)) {
      pushEntry(item, "charging", index === 0, item?.day ?? 0, index);
    }
  });

  (plan?.daily_itinerary || []).forEach((day) => {
    if (selectedDay && Number(day.day) !== Number(selectedDay)) {
      return;
    }
    (day.activities || []).forEach((item, index) => {
      pushEntry(item, "spot", index === 0 && Number(day.day) === Number(selectedDay), day.day, index);
    });
  });

  return entries;
}

function filterDriveItems(items, selectedDay, kind) {
  return items
    .filter((item) => !selectedDay || item?.day == null || Number(item.day) === Number(selectedDay))
    .slice(0, 4)
    .map((item) => ({
      ...item,
      kind,
      mapKey: buildPlaceKey(kind, item)
    }));
}

if (typeof window !== "undefined") {
  window.TapToGoUiHelpers = {
    getUiCopy,
    formatTravelModeZh,
    formatModeZh,
    describeDayZh,
    describePlanModeZh,
    getMotionClassName,
    isSelfDriveMode,
    buildPlaceKey,
    getMapKindMeta,
    buildRecommendationSections,
    buildMapEntriesData
  };
}
