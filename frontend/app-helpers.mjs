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

if (typeof window !== "undefined") {
  window.TapToGoUiHelpers = {
    getUiCopy,
    formatTravelModeZh,
    formatModeZh,
    describeDayZh,
    describePlanModeZh,
    getMotionClassName
  };
}
