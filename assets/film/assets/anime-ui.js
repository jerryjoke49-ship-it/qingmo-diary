(function () {
  "use strict";

  const STORAGE_KEY = "zhaohua:anime:comments:v1";
  const CUSTOM_KEY = "zhaohua:anime:custom:v1";
  const EPISODE_OVERRIDE_KEY = "zhaohua:anime:episode-overrides:v1";
  const STATUS_OVERRIDE_KEY = "zhaohua:anime:status-overrides:v1";
  const STATUS_ORDER = { "在看": 0, "想看": 1, "看过": 2 };
  const PAGE_SIZE = 36;
  const baseEntries = Array.isArray(window.ANIME_ENTRIES) ? window.ANIME_ENTRIES : [];
  let customEntries = readCustomEntries();
  const entries = mergeEntries(baseEntries, customEntries);
  let comments = readComments();
  let episodeOverrides = readEpisodeOverrides();
  let statusOverrides = readStatusOverrides();
  let activeStatus = "全部";
  let query = "";
  let visibleLimit = PAGE_SIZE;
  let selectedId = "";

  function initViewportHeight() {
    const update = () => {
      const height = window.visualViewport?.height || window.innerHeight;
      document.documentElement.style.setProperty("--app-height", `${height}px`);
    };
    update();
    window.addEventListener("resize", update, { passive: true });
    window.visualViewport?.addEventListener("resize", update, { passive: true });
    window.visualViewport?.addEventListener("scroll", update, { passive: true });
  }

  function initializeDateControls() {
    const yearSelect = document.getElementById("anime-watch-year");
    const monthInput = document.getElementById("anime-watch-month");
    const dayInput = document.getElementById("anime-watch-day");
    const currentYear = new Date().getFullYear();
    yearSelect.min = "2016";
    yearSelect.max = String(currentYear);
    const updateDays = () => {
      const numericYear = Number(yearSelect.value);
      const hasYear = numericYear >= 2016 && numericYear <= currentYear;
      monthInput.disabled = !hasYear;
      dayInput.disabled = !hasYear;
      const year = hasYear ? numericYear : currentYear;
      const month = Math.max(1, Math.min(12, Number(monthInput.value) || 1));
      const total = new Date(year, month, 0).getDate();
      const selected = Math.max(1, Math.min(total, Number(dayInput.value) || 1));
      monthInput.value = String(month);
      dayInput.max = String(total);
      dayInput.value = String(selected);
    };
    yearSelect.addEventListener("input", updateDays);
    yearSelect.addEventListener("change", updateDays);
    monthInput.addEventListener("change", updateDays);
    dayInput.addEventListener("change", updateDays);
    monthInput.value = String(new Date().getMonth() + 1);
    dayInput.value = String(new Date().getDate());
    updateDays();
  }

  function setWatchDate(value) {
    const yearSelect = document.getElementById("anime-watch-year");
    const monthInput = document.getElementById("anime-watch-month");
    const dayInput = document.getElementById("anime-watch-day");
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ""));
    if (!match) {
      yearSelect.value = "";
      monthInput.value = String(new Date().getMonth() + 1);
      dayInput.value = String(new Date().getDate());
      yearSelect.dispatchEvent(new Event("change"));
      return;
    }
    yearSelect.value = match[1];
    monthInput.value = String(Number(match[2]));
    dayInput.value = String(Number(match[3]));
    monthInput.dispatchEvent(new Event("change"));
  }

  function selectedWatchDate() {
    const year = document.getElementById("anime-watch-year").value;
    const month = Math.max(1, Math.min(12, Number(document.getElementById("anime-watch-month").value) || 0));
    const day = Number(document.getElementById("anime-watch-day").value) || 0;
    const numericYear = Number(year);
    const currentYear = new Date().getFullYear();
    if (!year || numericYear < 2016 || numericYear > currentYear || !month || !day) return "";
    const maxDay = new Date(Number(year), month, 0).getDate();
    if (day > maxDay) return "";
    return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  function readComments() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function readCustomEntries() {
    try {
      const raw = localStorage.getItem(CUSTOM_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }

  function readEpisodeOverrides() {
    try {
      const raw = localStorage.getItem(EPISODE_OVERRIDE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function writeEpisodeOverrides(value) {
    localStorage.setItem(EPISODE_OVERRIDE_KEY, JSON.stringify(value));
  }

  function readStatusOverrides() {
    try {
      const raw = localStorage.getItem(STATUS_OVERRIDE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function writeStatusOverrides(value) {
    localStorage.setItem(STATUS_OVERRIDE_KEY, JSON.stringify(value));
  }

  function effectiveStatus(entry) {
    return ["在看", "想看", "看过"].includes(statusOverrides[entry.id])
      ? statusOverrides[entry.id]
      : entry.status;
  }

  function writeCustomEntries(value) {
    localStorage.setItem(CUSTOM_KEY, JSON.stringify(value));
  }

  function mergeEntries(base, custom) {
    const result = [];
    const ids = new Set();
    [...base, ...custom].forEach((entry) => {
      const id = String(entry?.id || "");
      if (!id || ids.has(id)) return;
      ids.add(id);
      result.push(entry);
    });
    return result;
  }

  function writeComments(value) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function filtered() {
    const q = query.trim().toLowerCase();
    return entries
      .filter((entry) => activeStatus === "全部" || effectiveStatus(entry) === activeStatus)
      .filter((entry) => {
        if (!q) return true;
        return [entry.title, entry.originalTitle, entry.year, effectiveStatus(entry), ...(entry.tags || [])]
          .join(" ")
          .toLowerCase()
          .includes(q);
      })
      .sort((a, b) => {
        const statusDiff = (STATUS_ORDER[effectiveStatus(a)] ?? 9) - (STATUS_ORDER[effectiveStatus(b)] ?? 9);
        return statusDiff || a.title.localeCompare(b.title, "zh");
      });
  }

  function renderStats() {
    const counts = entries.reduce((map, entry) => {
      const status = effectiveStatus(entry);
      map[status] = (map[status] || 0) + 1;
      return map;
    }, {});
    document.getElementById("anime-stats").innerHTML = [
      ["在看", counts["在看"] || 0],
      ["想看", counts["想看"] || 0],
      ["看过", counts["看过"] || 0]
    ].map(([label, count]) => `
      <div class="anime-stat"><strong>${count}</strong><span>${label}</span></div>
    `).join("");
  }

  function posterMarkup(entry) {
    if (entry.posterData) {
      return `<img class="anime-poster" src="${escapeHtml(entry.posterData)}" alt="${escapeHtml(entry.title)}海报" decoding="async">`;
    }
    if (entry.poster) {
      return `<img class="anime-poster" src="./${escapeHtml(entry.poster)}" alt="${escapeHtml(entry.title)}海报" loading="lazy" decoding="async">`;
    }
    return `<span class="anime-poster anime-poster-placeholder">${escapeHtml(Array.from(entry.title)[0] || "番")}</span>`;
  }

  function episodeLabel(entry) {
    const override = Object.prototype.hasOwnProperty.call(episodeOverrides, entry.id)
      ? Number(episodeOverrides[entry.id])
      : Number(entry.totalEpisodes || 0);
    if (override > 0) return `全 ${override} 话`;
    if (entry.format === "剧场版") return "剧场版";
    if (entry.format === "OVA") return "OVA";
    return "集数未知";
  }

  function cardMarkup(entry) {
    const record = comments[entry.id] || {};
    const comment = typeof record === "string" ? record : (record.comment || "");
    const watchDate = typeof record === "string" ? "" : (record.watchDate || "");
    const episodes = episodeLabel(entry);
    const status = effectiveStatus(entry);
    return `
      <article class="anime-card" data-anime-id="${escapeHtml(entry.id)}">
        ${posterMarkup(entry)}
        <div class="anime-card-body">
          <div class="anime-card-meta">
            <span class="anime-status status-${escapeHtml(status)}">${escapeHtml(status)}</span>
            <span>${entry.year || "年份未知"}</span>
          </div>
          <h2>${escapeHtml(entry.title)}</h2>
          <button class="anime-episodes" type="button" data-edit-id="${escapeHtml(entry.id)}">${escapeHtml(episodes)}</button>
          <button class="anime-watch-date ${watchDate ? "has-date" : ""}" type="button" data-comment-id="${escapeHtml(entry.id)}">
            ${watchDate ? `观看 · ${escapeHtml(watchDate)}` : "＋ 设置观看时间"}
          </button>
          <button class="anime-comment ${comment ? "has-comment" : ""}" type="button" data-comment-id="${escapeHtml(entry.id)}">
            ${comment ? escapeHtml(comment) : "＋ 写一句留给自己的话"}
          </button>
        </div>
      </article>
    `;
  }

  function render() {
    const list = filtered();
    const visible = list.slice(0, visibleLimit);
    document.getElementById("anime-count").textContent = `${list.length} 部`;
    document.getElementById("anime-list").innerHTML = visible.length
      ? visible.map(cardMarkup).join("")
      : `<div class="empty-state">还没有动漫记录。点击上方“添加动漫”开始记录。</div>`;
    const loadMore = document.getElementById("anime-load-more");
    const remaining = Math.max(0, list.length - visible.length);
    loadMore.hidden = remaining === 0;
    loadMore.textContent = remaining ? `显示更多（还有 ${remaining} 部）` : "";
  }

  function openComment(id) {
    const entry = entries.find((item) => item.id === id);
    if (!entry) return;
    selectedId = id;
    document.getElementById("anime-modal-title").textContent = entry.title;
    const record = comments[id] || {};
    document.getElementById("anime-edit-episodes").value =
      Object.prototype.hasOwnProperty.call(episodeOverrides, id)
        ? String(episodeOverrides[id])
        : (entry.totalEpisodes ? String(entry.totalEpisodes) : "");
    document.getElementById("anime-edit-status").value = effectiveStatus(entry);
    setWatchDate(typeof record === "string" ? "" : (record.watchDate || ""));
    document.getElementById("anime-comment-input").value =
      typeof record === "string" ? record : (record.comment || "");
    const modal = document.getElementById("anime-modal");
    document.getElementById("anime-delete-custom").hidden = !entry.custom;
    modal.hidden = false;
    document.body.classList.add("no-scroll");
    setTimeout(() => document.getElementById("anime-comment-input").focus(), 30);
  }

  function closeComment() {
    document.getElementById("anime-modal").hidden = true;
    document.body.classList.remove("no-scroll");
    selectedId = "";
  }

  function openAddAnime() {
    document.getElementById("anime-add-form").reset();
    document.getElementById("anime-add-modal").hidden = false;
    document.body.classList.add("no-scroll");
    setTimeout(() => document.getElementById("anime-add-title").focus(), 30);
  }

  function closeAddAnime() {
    document.getElementById("anime-add-modal").hidden = true;
    document.body.classList.remove("no-scroll");
  }

  function deleteCustomAnime() {
    const index = entries.findIndex((entry) => entry.id === selectedId && entry.custom);
    if (index < 0) return;
    const entry = entries[index];
    if (!window.confirm(`确定删除《${entry.title}》吗？观看时间和简评也会删除。`)) {
      return;
    }
    customEntries = customEntries.filter((item) => item.id !== selectedId);
    writeCustomEntries(customEntries);
    delete comments[selectedId];
    writeComments(comments);
    delete episodeOverrides[selectedId];
    writeEpisodeOverrides(episodeOverrides);
    delete statusOverrides[selectedId];
    writeStatusOverrides(statusOverrides);
    entries.splice(index, 1);
    closeComment();
    renderStats();
    render();
  }

  function imageToPoster(file) {
    return new Promise((resolve) => {
      if (!file) {
        resolve("");
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        const image = new Image();
        image.onload = () => {
          const canvas = document.createElement("canvas");
          canvas.width = 180;
          canvas.height = 270;
          const context = canvas.getContext("2d");
          const sourceRatio = image.width / image.height;
          const targetRatio = 180 / 270;
          let sourceWidth = image.width;
          let sourceHeight = image.height;
          if (sourceRatio > targetRatio) {
            sourceWidth = image.height * targetRatio;
          } else {
            sourceHeight = image.width / targetRatio;
          }
          const sourceX = (image.width - sourceWidth) / 2;
          const sourceY = (image.height - sourceHeight) / 2;
          context.drawImage(
            image,
            sourceX,
            sourceY,
            sourceWidth,
            sourceHeight,
            0,
            0,
            180,
            270
          );
          resolve(canvas.toDataURL("image/jpeg", 0.82));
        };
        image.onerror = () => resolve("");
        image.src = reader.result;
      };
      reader.onerror = () => resolve("");
      reader.readAsDataURL(file);
    });
  }

  async function addAnime(event) {
    event.preventDefault();
    const title = document.getElementById("anime-add-title").value.trim();
    if (!title) {
      document.getElementById("anime-add-title").focus();
      return;
    }
    const posterFile = document.getElementById("anime-add-poster").files?.[0];
    const posterData = await imageToPoster(posterFile);
    const entry = {
      id: `custom-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
      title,
      originalTitle: document.getElementById("anime-add-original").value.trim(),
      year: Number(document.getElementById("anime-add-year").value) || 0,
      status: document.getElementById("anime-add-status").value,
      totalEpisodes: Number(document.getElementById("anime-add-episodes").value) || 0,
      poster: "",
      posterData,
      tags: [],
      source: "手动添加",
      custom: true,
      createdAt: new Date().toISOString()
    };
    customEntries.push(entry);
    writeCustomEntries(customEntries);
    entries.push(entry);
    visibleLimit = PAGE_SIZE;
    closeAddAnime();
    renderStats();
    render();
  }

  function exportBackup() {
    const payload = {
      version: 1,
      exportedAt: new Date().toISOString(),
      entries,
      comments,
      episodeOverrides,
      statusOverrides
    };
    const json = JSON.stringify(payload, null, 2);
    if (window.AndroidAnimeBridge && window.AndroidAnimeBridge.exportAnimeBackup) {
      window.AndroidAnimeBridge.exportAnimeBackup(json, "");
      return;
    }
    const blob = new Blob([json], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "青茉日记-动漫备份.json";
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  }

  async function importBackup(file) {
    if (!file) return;
    try {
      const payload = JSON.parse(await file.text());
      const incoming = Array.isArray(payload.entries) ? payload.entries : [];
      const baseIds = new Set(baseEntries.map((entry) => String(entry.id)));
      const merged = new Map(customEntries.map((entry) => [String(entry.id), entry]));
      incoming.forEach((entry) => {
        const id = String(entry?.id || "");
        if (!id || baseIds.has(id)) return;
        merged.set(id, entry);
      });
      customEntries = [...merged.values()];
      writeCustomEntries(customEntries);
      if (payload.comments && typeof payload.comments === "object") {
        comments = { ...comments, ...payload.comments };
        writeComments(comments);
      }
      if (payload.episodeOverrides && typeof payload.episodeOverrides === "object") {
        episodeOverrides = { ...episodeOverrides, ...payload.episodeOverrides };
        writeEpisodeOverrides(episodeOverrides);
      }
      if (payload.statusOverrides && typeof payload.statusOverrides === "object") {
        statusOverrides = { ...statusOverrides, ...payload.statusOverrides };
        writeStatusOverrides(statusOverrides);
      }
      window.location.reload();
    } catch (error) {
      window.alert("动漫备份格式不正确。");
    } finally {
      document.getElementById("anime-import").value = "";
    }
  }

  window.onAnimeExportResult = function (success, message) {
    window.alert(message || (success ? "动漫备份已保存。" : "导出失败。"));
  };

  document.getElementById("anime-search").addEventListener("input", (event) => {
    query = event.target.value;
    visibleLimit = PAGE_SIZE;
    render();
  });

  document.getElementById("anime-filters").addEventListener("click", (event) => {
    const button = event.target.closest("[data-status]");
    if (!button) return;
    activeStatus = button.dataset.status;
    visibleLimit = PAGE_SIZE;
    document.querySelectorAll(".anime-filter").forEach((item) => {
      item.classList.toggle("active", item === button);
    });
    render();
  });

  document.getElementById("anime-list").addEventListener("click", (event) => {
    const episodeButton = event.target.closest("[data-edit-id]");
    if (episodeButton) {
      openComment(episodeButton.dataset.editId);
      return;
    }
    const button = event.target.closest("[data-comment-id]");
    if (button) openComment(button.dataset.commentId);
  });

  document.getElementById("anime-load-more").addEventListener("click", () => {
    visibleLimit += PAGE_SIZE;
    render();
  });

  document.getElementById("anime-comment-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!selectedId) return;
    const value = document.getElementById("anime-comment-input").value.trim();
    const watchDate = selectedWatchDate();
    const episodeValue = document.getElementById("anime-edit-episodes").value.trim();
    const statusValue = document.getElementById("anime-edit-status").value;
    if (["在看", "想看", "看过"].includes(statusValue)) {
      statusOverrides[selectedId] = statusValue;
      writeStatusOverrides(statusOverrides);
    }
    if (episodeValue !== "") {
      const parsed = Math.max(0, Math.min(9999, Number(episodeValue) || 0));
      episodeOverrides[selectedId] = parsed;
    } else {
      delete episodeOverrides[selectedId];
    }
    writeEpisodeOverrides(episodeOverrides);
    if (value || watchDate) {
      comments[selectedId] = {
        comment: value,
        watchDate,
        updatedAt: new Date().toISOString()
      };
    } else {
      delete comments[selectedId];
    }
    writeComments(comments);
    closeComment();
    render();
  });

  document.getElementById("anime-modal").querySelectorAll("[data-close-anime]").forEach((button) => {
    button.addEventListener("click", closeComment);
  });

  document.getElementById("anime-add").addEventListener("click", openAddAnime);
  document.getElementById("anime-add-form").addEventListener("submit", addAnime);
  document.getElementById("anime-add-modal").querySelectorAll("[data-close-anime-add]").forEach((button) => {
    button.addEventListener("click", closeAddAnime);
  });
  document.getElementById("anime-export").addEventListener("click", exportBackup);
  document.getElementById("anime-delete-custom").addEventListener("click", deleteCustomAnime);
  document.getElementById("anime-import").addEventListener("change", (event) => {
    importBackup(event.target.files?.[0]);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !document.getElementById("anime-modal").hidden) {
      closeComment();
    }
    if (event.key === "Escape" && !document.getElementById("anime-add-modal").hidden) {
      closeAddAnime();
    }
  });

  initializeDateControls();
  initViewportHeight();
  renderStats();
  render();
  const requestedAnime = new URLSearchParams(location.search).get("openId");
  if (requestedAnime) {
    requestAnimationFrame(() => openComment(requestedAnime));
  }
})();
