(function () {
  "use strict";

  const REVIEW_KEY = "silver-screen:reviews:v1";
  const ANIME_KEY = "zhaohua:anime:comments:v1";
  const ANIME_EPISODE_KEY = "zhaohua:anime:episode-overrides:v1";
  const ANIME_STATUS_KEY = "zhaohua:anime:status-overrides:v1";
  const MOODS = {
    calm: { emoji: "☁️", label: "平静" },
    happy: { emoji: "☀️", label: "开心" },
    soft: { emoji: "🌿", label: "松弛" },
    low: { emoji: "🌧️", label: "低落" },
    tired: { emoji: "🌙", label: "疲惫" }
  };

  const notes = readNotes();
  const reviews = readObject(REVIEW_KEY);
  const animeRecords = readObject(ANIME_KEY);
  const animeEpisodeOverrides = readObject(ANIME_EPISODE_KEY);
  const animeStatusOverrides = readObject(ANIME_STATUS_KEY);
  const entryList = Array.isArray(window.WIKI_ENTRIES) ? window.WIKI_ENTRIES : [];
  const entries = new Map(entryList.map((entry) => [String(entry.id || entry.title), entry]));
  const animeList = Array.isArray(window.ANIME_ENTRIES) ? window.ANIME_ENTRIES : [];
  const animeEntries = new Map(animeList.map((entry) => [String(entry.id), entry]));
  const events = buildEvents();
  let activeFilter = "all";

  function readObject(key) {
    try {
      const raw = localStorage.getItem(key);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function readNotes() {
    try {
      if (!window.AndroidTimelineBridge || !window.AndroidTimelineBridge.getNotesJson) {
        return [];
      }
      const parsed = JSON.parse(window.AndroidTimelineBridge.getNotesJson());
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function excerpt(value, length) {
    const text = String(value || "").replace(/\s+/g, " ").trim();
    return text.length > length ? `${text.slice(0, length)}…` : text;
  }

  function noteTitle(note) {
    const explicit = String(note.title || "").trim();
    if (explicit) return explicit;
    const firstLine = String(note.content || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .find(Boolean);
    if (!firstLine) return "无题随笔";
    return firstLine.length > 32 ? `${firstLine.slice(0, 32)}…` : firstLine;
  }

  function notePreview(note, title) {
    const lines = String(note.content || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    if (lines[0] === title) lines.shift();
    if (!lines.length) {
      return note.imageCount ? `含 ${note.imageCount} 张照片` : "";
    }
    const preview = lines.slice(0, 3).join("\n");
    return preview.length > 120 ? `${preview.slice(0, 120)}…` : preview;
  }

  function animeEpisodeText(entry) {
    const override = Object.prototype.hasOwnProperty.call(animeEpisodeOverrides, entry.id)
      ? Number(animeEpisodeOverrides[entry.id])
      : Number(entry.totalEpisodes || 0);
    if (override > 0) return ` · 全 ${override} 话`;
    if (entry.format === "剧场版") return " · 剧场版";
    if (entry.format === "OVA") return " · OVA";
    return "";
  }

  function animeStatus(entry) {
    return ["在看", "想看", "看过"].includes(animeStatusOverrides[entry.id])
      ? animeStatusOverrides[entry.id]
      : (entry.status || "动漫");
  }

  function validTimestamp(value) {
    const time = Number(value);
    return Number.isFinite(time) && time > 0 ? time : 0;
  }

  function dateFromWatchDate(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ""));
    if (!match) return 0;
    return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 20, 0, 0).getTime();
  }

  function buildEvents() {
    const list = [];

    notes.forEach((note) => {
      const timestamp = validTimestamp(note.createdAt);
      if (!timestamp) return;
      const mood = MOODS[note.mood] || MOODS.calm;
      const title = noteTitle(note);
      list.push({
        type: "note",
        timestamp,
        id: String(note.id),
        title,
        detail: notePreview(note, title),
        meta: `${mood.emoji} ${mood.label}${note.imageCount ? ` · ${note.imageCount} 张照片` : ""}`,
        href: ""
      });
    });

    Object.entries(reviews).forEach(([id, review]) => {
      const entry = entries.get(String(id));
      if (!entry || !review || typeof review !== "object") return;
      const watchDates = Array.isArray(review.watchDates)
        ? review.watchDates.filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(String(date)))
        : [];
      const reviewText = excerpt(review.review || "", 110);
      watchDates.forEach((date) => {
        const timestamp = dateFromWatchDate(date);
        if (!timestamp) return;
        list.push({
          type: "film",
          timestamp,
          id: String(id),
          title: entry.title,
          detail: reviewText || `${date} 观看`,
          meta: `观影 · ${entry.medium || "影视"}${review.rating != null ? ` · ${Number(review.rating).toFixed(1)} 分` : ""}`,
          href: `./entry.html?id=${encodeURIComponent(id)}`
        });
      });
      if (!watchDates.length && validTimestamp(new Date(review.updatedAt || 0).getTime())) {
        list.push({
          type: "film",
          timestamp: new Date(review.updatedAt).getTime(),
          id: String(id),
          title: entry.title,
          detail: reviewText || "更新了影评与标签",
          meta: `影评更新 · ${entry.medium || "影视"}`,
          href: `./entry.html?id=${encodeURIComponent(id)}`
        });
      }
    });

    entryList.filter((entry) => entry.custom).forEach((entry) => {
      if (reviews[entry.id]) return;
      const timestamp = validTimestamp(new Date(entry.createdAt || 0).getTime());
      if (!timestamp) return;
      list.push({
        type: "film",
        timestamp,
        id: String(entry.id),
        title: entry.title,
        detail: entry.blurb || "加入了个人片单",
        meta: `加入片单 · ${entry.medium || "影视"}`,
        href: `./entry.html?id=${encodeURIComponent(entry.id)}`
      });
    });

    Object.entries(animeRecords).forEach(([id, rawRecord]) => {
      const entry = animeEntries.get(String(id));
      if (!entry) return;
      const record = rawRecord && typeof rawRecord === "object" ? rawRecord : {};
      const watchTimestamp = dateFromWatchDate(record.watchDate);
      const updatedTimestamp = validTimestamp(new Date(record.updatedAt || 0).getTime());
      const timestamp = watchTimestamp || updatedTimestamp;
      if (!timestamp) return;
      list.push({
        type: "anime",
        timestamp,
        id: String(id),
        title: entry.title,
        detail: excerpt(record.comment || "", 110) || `${animeStatus(entry)} · ${entry.year || "年份未知"}`,
        meta: watchTimestamp
          ? `${animeStatus(entry)} · ${entry.year || "年份未知"}${animeEpisodeText(entry)}`
          : "更新了动漫简评",
        href: "./anime.html"
      });
    });

    return list.sort((a, b) => b.timestamp - a.timestamp);
  }

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function monthKey(timestamp) {
    const date = new Date(timestamp);
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
  }

  function dateLabel(timestamp) {
    const date = new Date(timestamp);
    return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
  }

  function timeLabel(timestamp) {
    const date = new Date(timestamp);
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function renderStats() {
    const now = new Date();
    const monthCount = events.filter((event) => {
      const date = new Date(event.timestamp);
      return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
    }).length;
    const filmIds = new Set(events.filter((event) => event.type === "film").map((event) => event.id));
    const animeIds = new Set(events.filter((event) => event.type === "anime").map((event) => event.id));
    const watchCount = Object.values(reviews).reduce(
      (total, review) => total + (Array.isArray(review.watchDates) ? review.watchDates.length : 0),
      0
    );
    const stats = [
      [notes.length, "随笔"],
      [filmIds.size, "影视条目"],
      [animeIds.size, "动漫"],
      [watchCount, "观看次数"],
      [monthCount, "本月记录"]
    ];
    document.getElementById("timeline-stats").innerHTML = stats
      .map(([value, label]) => `<div class="timeline-stat"><strong>${value}</strong><span>${label}</span></div>`)
      .join("");
  }

  function render() {
    const visible = activeFilter === "all"
      ? events
      : events.filter((event) => event.type === activeFilter);
    const list = document.getElementById("timeline-list");
    const count = document.getElementById("timeline-count");
    count.textContent = `${visible.length} 条`;

    if (!visible.length) {
      list.innerHTML = `<div class="timeline-empty"><span>○</span><strong>还没有记录</strong><p>随笔和影视记录会在这里汇成时间线。</p></div>`;
      return;
    }

    let currentMonth = "";
    list.innerHTML = visible.map((event) => {
      const key = monthKey(event.timestamp);
      const monthHeading = key === currentMonth
        ? ""
        : `<h2 class="timeline-month">${escapeHtml(key.replace("-", "年"))}月</h2>`;
      currentMonth = key;
      const cardContent = `
        <div class="timeline-card-top">
          <span>${event.type === "note" ? "随笔" : event.type === "anime" ? "动漫" : "影视"}</span>
          <time>${dateLabel(event.timestamp)} · ${timeLabel(event.timestamp)}</time>
        </div>
        <h3>${escapeHtml(event.title)}</h3>
        <p>${escapeHtml(event.detail)}</p>
        <small>${escapeHtml(event.meta)}</small>`;
      let card;
      if (event.type === "film") {
        card = `<a class="timeline-card film" href="${escapeHtml(event.href)}">${cardContent}</a>`;
      } else if (event.type === "anime") {
        card = `<button class="timeline-card anime" type="button" data-anime-id="${escapeHtml(event.id)}">${cardContent}</button>`;
      } else {
        card = `<button class="timeline-card note" type="button" data-note-id="${escapeHtml(event.id)}">${cardContent}</button>`;
      }
      return `<section class="timeline-group">${monthHeading}<div class="timeline-item"><span class="timeline-dot"></span>${card}</div></section>`;
    }).join("");
  }

  document.getElementById("timeline-list").addEventListener("click", (event) => {
    const anime = event.target.closest("[data-anime-id]");
    if (anime) {
      if (window.AndroidTimelineBridge && window.AndroidTimelineBridge.openAnime) {
        window.AndroidTimelineBridge.openAnime(anime.dataset.animeId);
      }
      return;
    }
    const note = event.target.closest("[data-note-id]");
    if (!note) return;
    if (window.AndroidTimelineBridge && window.AndroidTimelineBridge.openNote) {
      window.AndroidTimelineBridge.openNote(note.dataset.noteId);
    }
  });

  document.getElementById("timeline-filters").addEventListener("click", (event) => {
    const button = event.target.closest("[data-filter]");
    if (!button) return;
    activeFilter = button.dataset.filter;
    document.querySelectorAll(".timeline-filter").forEach((item) => {
      item.classList.toggle("active", item === button);
    });
    render();
  });

  renderStats();
  render();
})();
