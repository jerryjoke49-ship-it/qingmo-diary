(function () {
  "use strict";

  const STORAGE_KEY = "silver-screen:custom-entries:v1";

  function read() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function write(entries) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  }

  function normalize(entry) {
    const title = String(entry.title || "").trim();
    if (!title) return null;
    return {
      id: String(entry.id || `custom-${Date.now().toString(36)}`),
      title,
      original: String(entry.original || "").trim(),
      year: Number(entry.year) > 0 ? Number(entry.year) : 0,
      medium: ["电影", "剧集", "动画", "纪录片"].includes(entry.medium) ? entry.medium : "电影",
      genres: Array.isArray(entry.genres)
        ? [...new Set(entry.genres.map((value) => String(value).trim()).filter(Boolean))]
        : [],
      creator: String(entry.creator || "").trim(),
      region: String(entry.region || "").trim(),
      note: "",
      blurb: String(entry.blurb || "").trim(),
      posterData: String(entry.posterData || ""),
      featured: false,
      custom: true,
      createdAt: entry.createdAt || new Date().toISOString()
    };
  }

  const baseEntries = Array.isArray(window.WIKI_ENTRIES) ? window.WIKI_ENTRIES : [];
  const customEntries = read().map(normalize).filter(Boolean);
  const knownIds = new Set(baseEntries.map((entry) => String(entry.id || entry.title)));
  customEntries.forEach((entry) => {
    if (!knownIds.has(entry.id)) {
      baseEntries.push(entry);
      knownIds.add(entry.id);
    }
  });
  window.WIKI_ENTRIES = baseEntries;

  window.SilverCustomEntries = {
    storageKey: STORAGE_KEY,
    read,
    write,
    normalize
  };
})();
