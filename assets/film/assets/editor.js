(function () {
  "use strict";

  const STORAGE_KEY = "silver-screen:reviews:v1";
  const STATUS_OPTIONS = ["看过", "在看", "想看", "弃看"];
  const palettes = [
    ["#38566b", "#17242e"],
    ["#7d3f32", "#3b1c17"],
    ["#315b49", "#172d24"],
    ["#a97843", "#4a331d"],
    ["#6c4b7b", "#2c2037"],
    ["#8a4b2f", "#3a2118"],
    ["#2f4b5e", "#14232d"],
    ["#7a2e3a", "#34141a"],
    ["#5e6048", "#282a1d"],
    ["#4f3f63", "#211a2b"]
  ];

  function hashString(value) {
    let hash = 0;
    for (const char of String(value)) hash = (hash * 31 + char.codePointAt(0)) % 1000000007;
    return hash;
  }

  function coverFor(entry) {
    const palette = palettes[hashString(entry.title) % palettes.length];
    return `linear-gradient(135deg, ${palette[0]} 0%, ${palette[1]} 100%)`;
  }

  const posterMap = window.WIKI_POSTERS && typeof window.WIKI_POSTERS === "object" ? window.WIKI_POSTERS : {};
  function posterFor(entry) {
    if (entry.posterData) return entry.posterData;
    const value = posterMap[entry.id] || posterMap[entry.title];
    if (!value) return "";
    if (/^(https?:|data:)/.test(value)) return value;
    return `./${value.replace(/^\.?\//, "")}`;
  }

  const entries = (Array.isArray(window.WIKI_ENTRIES) ? window.WIKI_ENTRIES : []).map((item) => ({
    ...item,
    id: item.id || item.title,
    mark: item.mark || Array.from(item.title || "?")[0],
    cover: item.cover || coverFor(item),
    genres: Array.isArray(item.genres) ? item.genres : [],
    status: item.status || "看过"
  }));
  const byId = new Map(entries.map((entry) => [entry.id, entry]));
  const entryUrl = (id) => `./entry.html?id=${encodeURIComponent(id)}`;

  const listNode = document.getElementById("editor-list");
  if (!listNode) return;

  const searchInput = document.getElementById("editor-search");
  const form = document.getElementById("review-form");
  const empty = document.getElementById("editor-empty");
  const titleNode = document.getElementById("editor-title");
  const metaNode = document.getElementById("editor-meta");
  const markNode = document.getElementById("editor-mark");
  const viewLink = document.getElementById("editor-view");
  const statusInput = document.getElementById("edit-status");
  const ratingInput = document.getElementById("edit-rating");
  const watchYearSelect = document.getElementById("film-watch-year");
  const watchMonthSelect = document.getElementById("film-watch-month");
  const watchDaySelect = document.getElementById("film-watch-day");
  const addWatchDateButton = document.getElementById("add-watch-date");
  const watchChips = document.getElementById("watch-chips");
  const watchSummary = document.getElementById("watch-summary");
  const favoriteShotInput = document.getElementById("edit-favorite-shot");
  const reviewInput = document.getElementById("edit-review");
  const tagChips = document.getElementById("tag-chips");
  const tagInput = document.getElementById("tag-input");
  const resetTagsButton = document.getElementById("reset-tags");
  const countNode = document.getElementById("review-count");
  const saveStatus = document.getElementById("save-status");
  const localCount = document.getElementById("local-count");
  const resetButton = document.getElementById("reset-entry");
  const exportButton = document.getElementById("export-json");
  const importInput = document.getElementById("import-json");
  const clearButton = document.getElementById("clear-all");
  const addEntryButton = document.getElementById("add-entry");
  const addEntryModal = document.getElementById("add-entry-modal");
  const addEntryForm = document.getElementById("add-entry-form");
  const deleteCustomEntryButton = document.getElementById("delete-custom-entry");

  let store = readStore();
  let selectedId = new URLSearchParams(window.location.search).get("id") || entries[0]?.id || "";
  if (!byId.has(selectedId)) selectedId = entries[0]?.id || "";
  let dirty = false;
  window.hasUnsavedFilmChanges = function () { return dirty; };
  window.clearFilmDirtyChanges = function () { dirty = false; };
  let tagValues = [];
  let watchDates = [];

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function normalize(value) {
    return String(value || "").toLowerCase().replace(/\s+/g, " ").trim();
  }

  function searchText(entry) {
    const local = store[entry.id];
    const localTags = local && Array.isArray(local.tags) ? local.tags : [];
    return normalize([
      entry.title,
      entry.original,
      entry.year,
      entry.medium,
      entry.creator,
      entry.region,
      ...entry.genres,
      ...localTags,
      ...(Array.isArray(local?.watchDates) ? local.watchDates : []),
      local?.favoriteShot || ""
    ].join(" "));
  }

  function readStore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  function writeStore(next) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return true;
    } catch (error) {
      setStatus("当前浏览器不允许写入 localStorage，可能是隐私模式或权限设置。", "error");
      return false;
    }
  }

  function openAddEntry() {
    if (!addEntryModal || !addEntryForm) return;
    addEntryForm.reset();
    addEntryModal.hidden = false;
    document.body.classList.add("no-scroll");
    const titleInput = document.getElementById("new-entry-title");
    setTimeout(() => titleInput?.focus(), 30);
  }

  function closeAddEntry() {
    if (!addEntryModal) return;
    addEntryModal.hidden = true;
    document.body.classList.remove("no-scroll");
  }

  function parseGenres(value) {
    return String(value || "")
      .split(/[,，、]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 12);
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
          context.drawImage(
            image,
            (image.width - sourceWidth) / 2,
            (image.height - sourceHeight) / 2,
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

  async function addCustomEntry(event) {
    event.preventDefault();
    const api = window.SilverCustomEntries;
    if (!api) {
      setStatus("当前浏览器不支持添加影片。", "error");
      return;
    }
    const title = document.getElementById("new-entry-title")?.value.trim();
    if (!title) {
      document.getElementById("new-entry-title")?.focus();
      return;
    }
    const id = `custom-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    const posterData = await imageToPoster(
      document.getElementById("new-entry-poster")?.files?.[0]
    );
    const entry = api.normalize({
      id,
      title,
      original: document.getElementById("new-entry-original")?.value,
      year: document.getElementById("new-entry-year")?.value,
      medium: document.getElementById("new-entry-medium")?.value,
      genres: parseGenres(document.getElementById("new-entry-genres")?.value),
      creator: document.getElementById("new-entry-creator")?.value,
      region: document.getElementById("new-entry-region")?.value,
      blurb: document.getElementById("new-entry-blurb")?.value,
      posterData,
      createdAt: new Date().toISOString()
    });
    if (!entry) return;
    const customEntries = api.read();
    customEntries.push(entry);
    api.write(customEntries);
    window.location.href = `./editor.html?id=${encodeURIComponent(entry.id)}`;
  }

  function deleteSelectedCustomEntry() {
    const entry = byId.get(selectedId);
    const api = window.SilverCustomEntries;
    if (!entry || !entry.custom || !api) return;
    if (!window.confirm(`确定从片单中删除《${entry.title}》吗？这条影片的本地影评也会删除。`)) return;
    api.write(api.read().filter((item) => item.id !== entry.id));
    delete store[entry.id];
    writeStore(store);
    window.location.href = "./editor.html";
  }

  function sanitize(value) {
    const ratingNumber = value && value.rating !== "" && value.rating != null ? Number(value.rating) : null;
    const rating = Number.isFinite(ratingNumber) && ratingNumber >= 0 && ratingNumber <= 10
      ? Math.round(ratingNumber * 10) / 10
      : null;
    const legacyDate = /^\d{4}-\d{2}-\d{2}$/.test(String(value?.watchedAt || "")) ? String(value.watchedAt) : "";
    const watchDates = Array.isArray(value?.watchDates)
      ? [...new Set(value.watchDates.filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(String(date))).map(String))].sort()
      : (legacyDate ? [legacyDate] : []);
    const status = STATUS_OPTIONS.includes(value?.status) ? value.status : "看过";
    const clean = {
      status,
      rating,
      watchDates,
      favoriteShot: String(value?.favoriteShot || "").trim(),
      review: String(value?.review || "").trim(),
      updatedAt: typeof value?.updatedAt === "string" ? value.updatedAt : new Date().toISOString()
    };
    if (Array.isArray(value?.tags)) {
      clean.tags = [...new Set(value.tags.map((tag) => String(tag).trim()).filter(Boolean))].slice(0, 50);
    }
    return clean;
  }

  function isMeaningful(value) {
    return Boolean(
      value.review ||
      value.rating != null ||
      (Array.isArray(value.watchDates) && value.watchDates.length > 0) ||
      value.watchedAt ||
      value.favoriteShot ||
      Array.isArray(value.tags) ||
      (value.status && value.status !== "看过")
    );
  }

  function hasLocalReview(entry) {
    const value = store[entry.id];
    return Boolean(value && isMeaningful(value));
  }

  function setStatus(message, kind) {
    if (!saveStatus) return;
    saveStatus.textContent = message;
    saveStatus.dataset.kind = kind || "";
  }

  function setDirty(value) {
    dirty = value;
    if (value) setStatus("有未保存的修改，按 Ctrl/Cmd + S 或点“保存”。", "dirty");
  }

  function formatSaved(value) {
    if (!value) return "尚无本地修改";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "已有本地修改";
    return `上次保存：${date.toLocaleString("zh-CN", { hour12: false })}`;
  }

  function renderList(query) {
    const q = normalize(query);
    const list = q ? entries.filter((entry) => searchText(entry).includes(q)) : entries;
    if (!list.length) {
      listNode.innerHTML = `<div class="editor-list-empty">没有找到匹配的条目。</div>`;
      return;
    }

    listNode.innerHTML = list.map((entry) => {
      const local = store[entry.id];
      const localRating = local && local.rating != null && Number.isFinite(Number(local.rating))
        ? Number(local.rating)
        : null;
      const localTags = local && Array.isArray(local.tags) ? local.tags : null;
      const displayTags = localTags || entry.genres || [];
      const poster = posterFor(entry);
      const thumb = poster
        ? `<img class="mini-mark editor-thumb" src="${poster}" alt="" loading="lazy">`
        : `<span class="mini-mark" style="background:${escapeHtml(entry.cover || "linear-gradient(135deg,#38566b,#17242e)")}">${escapeHtml(entry.mark)}</span>`;
      const badge = local && isMeaningful(local)
        ? `<span class="editor-item-rating">${localRating != null ? `★ ${localRating.toFixed(1)}` : local.favoriteShot ? "镜头" : local.review ? "已写" : (Array.isArray(local.watchDates) && local.watchDates.length) ? "记录" : localTags ? "标签" : "已改"}</span>`
        : `<span class="editor-item-year">${entry.year || "—"}</span>`;
      return `<button class="editor-item${entry.id === selectedId ? " active" : ""}" type="button" data-id="${escapeHtml(entry.id)}">
        ${thumb}
        <span class="editor-item-text">
          <strong>${escapeHtml(entry.title)}</strong>
          <small>${escapeHtml(entry.medium)} · ${escapeHtml(displayTags.slice(0, 2).join(" / "))}</small>
        </span>
        ${badge}
      </button>`;
    }).join("");
  }

  function renderCount() {
    if (localCount) {
      localCount.textContent = String(
        Object.keys(store).filter((id) => byId.has(id) && store[id] && isMeaningful(store[id])).length
      );
    }
  }

  function renderTags() {
    if (!tagChips) return;
    tagChips.innerHTML = tagValues.map((tag, index) =>
      `<span class="edit-tag">${escapeHtml(tag)}<button type="button" data-tag-index="${index}" aria-label="删除标签 ${escapeHtml(tag)}">×</button></span>`
    ).join("");
  }

  function addTags(raw) {
    const parts = String(raw || "").split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
    let changed = false;
    parts.forEach((rawTag) => {
      const tag = rawTag.slice(0, 30);
      const duplicate = tagValues.some((item) => item.toLowerCase() === tag.toLowerCase());
      if (tag && !duplicate && tagValues.length < 50) {
        tagValues.push(tag);
        changed = true;
      }
    });
    if (tagInput) tagInput.value = "";
    if (changed) {
      renderTags();
      setDirty(true);
    }
  }

  function renderWatchDates() {
    if (watchChips) {
      watchChips.innerHTML = watchDates.map((date, index) =>
        `<span class="edit-tag watch-chip">${escapeHtml(date)}<button type="button" data-watch-index="${index}" aria-label="删除观看日期 ${escapeHtml(date)}">×</button></span>`
      ).join("");
    }
    if (watchSummary) {
      watchSummary.textContent = watchDates.length
        ? `共 ${watchDates.length} 次 · 重看 ${Math.max(0, watchDates.length - 1)} 次`
        : "还没记录观看日期";
    }
  }

  function initializeFilmWatchSelectors() {
    const currentYear = new Date().getFullYear();
    watchYearSelect.min = "2016";
    watchYearSelect.max = String(currentYear);
    const updateDays = () => {
      const year = Number(watchYearSelect.value) || currentYear;
      const month = Math.max(1, Math.min(12, Number(watchMonthSelect.value) || 1));
      const total = new Date(year, month, 0).getDate();
      const selected = Math.max(1, Math.min(total, Number(watchDaySelect.value) || 1));
      watchMonthSelect.value = String(month);
      watchDaySelect.max = String(total);
      watchDaySelect.value = String(selected);
    };
    watchYearSelect.addEventListener("input", updateDays);
    watchYearSelect.addEventListener("change", updateDays);
    watchMonthSelect.addEventListener("change", updateDays);
    watchDaySelect.addEventListener("change", updateDays);
    resetFilmWatchSelection();
  }

  function resetFilmWatchSelection() {
    const today = new Date();
    watchYearSelect.value = String(today.getFullYear());
    watchMonthSelect.value = String(today.getMonth() + 1);
    watchMonthSelect.dispatchEvent(new Event("change"));
    watchDaySelect.value = String(today.getDate());
  }

  function selectedFilmWatchDate() {
    const year = watchYearSelect.value;
    const month = Math.max(1, Math.min(12, Number(watchMonthSelect.value) || 0));
    const day = Number(watchDaySelect.value) || 0;
    const numericYear = Number(year);
    const currentYear = new Date().getFullYear();
    if (!year || numericYear < 2016 || numericYear > currentYear || !month || !day) return "";
    const maxDay = new Date(Number(year), month, 0).getDate();
    if (day > maxDay) return "";
    return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  function addWatchDate(raw) {
    const value = String(raw || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      setStatus("请选择一个有效的观看日期。", "error");
      return;
    }
    if (!watchDates.includes(value)) {
      watchDates.push(value);
      watchDates.sort();
      renderWatchDates();
      setDirty(true);
    }
    resetFilmWatchSelection();
  }

  function selectEntry(id, updateUrl) {
    const entry = byId.get(id);
    if (!entry) return;
    selectedId = id;
    if (deleteCustomEntryButton) deleteCustomEntryButton.hidden = !entry.custom;
    const local = store[id] ? sanitize(store[id]) : null;

    if (empty) empty.hidden = true;
    if (form) form.hidden = false;
    const localTags = local && Array.isArray(local.tags) ? local.tags : null;
    const displayTags = localTags || entry.genres;
    if (titleNode) titleNode.textContent = entry.title;
    if (metaNode) metaNode.textContent = [entry.original, entry.year || "年份待补", entry.medium, displayTags.join(" / ")].filter(Boolean).join(" · ");
    if (markNode) {
      const poster = posterFor(entry);
      markNode.style.background = entry.cover || "linear-gradient(135deg,#38566b,#17242e)";
      markNode.classList.toggle("has-poster", Boolean(poster));
      markNode.innerHTML = poster
        ? `<img src="${poster}" alt="">`
        : escapeHtml(entry.mark);
    }
    if (viewLink) viewLink.href = entryUrl(entry.id);
    if (statusInput) statusInput.value = local?.status || entry.status || "看过";
    if (ratingInput) ratingInput.value = local?.rating != null ? String(local.rating) : "";
    watchDates = local && Array.isArray(local.watchDates) ? [...local.watchDates] : [];
    resetFilmWatchSelection();
    renderWatchDates();
    if (favoriteShotInput) favoriteShotInput.value = local?.favoriteShot || "";
    if (reviewInput) reviewInput.value = local?.review || "";
    tagValues = localTags ? [...localTags] : [...entry.genres];
    renderTags();
    if (countNode) countNode.textContent = `${Array.from(reviewInput?.value || "").length} 字`;
    setStatus(local ? formatSaved(local.updatedAt) : "尚无本地修改", "");
    dirty = false;

    if (updateUrl !== false && window.history && window.history.replaceState) {
      try {
        const next = new URL(window.location.href);
        next.searchParams.set("id", entry.id);
        window.history.replaceState({}, "", next);
      } catch (error) { /* file:// 环境下忽略 */ }
    }

    renderList(searchInput ? searchInput.value : "");
    if (updateUrl !== false && window.innerWidth <= 760 && form) form.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function currentValue() {
    return sanitize({
      status: statusInput?.value,
      rating: ratingInput?.value,
      watchDates: [...watchDates],
      favoriteShot: favoriteShotInput?.value,
      review: reviewInput?.value,
      tags: tagValues,
      updatedAt: new Date().toISOString()
    });
  }

  function saveSelected() {
    const entry = byId.get(selectedId);
    if (!entry) return;
    if (tagInput && tagInput.value.trim()) addTags(tagInput.value);
    const value = currentValue();
    const sameTags = value.tags.length === entry.genres.length &&
      value.tags.every((tag) => entry.genres.includes(tag));
    if (sameTags) delete value.tags;
    if (isMeaningful(value)) store[entry.id] = value;
    else delete store[entry.id];
    if (!writeStore(store)) return;
    store = readStore();
    renderCount();
    selectEntry(entry.id, false);
    setStatus("已保存到当前浏览器。", "success");
    dirty = false;
    if (window.innerWidth <= 760) setStatus("已保存到当前浏览器，点“查看条目”可以看到效果。", "success");
  }

  function resetSelected() {
    const entry = byId.get(selectedId);
    if (!entry) return;
    if (store[entry.id] && !window.confirm(`确定删除《${entry.title}》的本地评分、观看记录、标签、镜头和影评吗？`)) return;
    delete store[entry.id];
    if (!writeStore(store)) return;
    store = readStore();
    selectEntry(entry.id, false);
    renderCount();
    renderList(searchInput ? searchInput.value : "");
    setStatus("已恢复此条的初始状态。", "success");
  }

  function downloadBackup() {
    const payload = {
      version: 2,
      exportedAt: new Date().toISOString(),
      reviews: store,
      customEntries: window.SilverCustomEntries ? window.SilverCustomEntries.read() : []
    };
    const nativeBridge = window.AndroidFilmBridge;
    if (nativeBridge && typeof nativeBridge.exportFilmBackup === "function") {
      nativeBridge.exportFilmBackup(JSON.stringify(payload, null, 2), "");
      return;
    }
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const date = new Date();
    const stamp = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    link.href = url;
    link.download = `银幕档案-影评备份-${stamp}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus(`已导出 ${Object.keys(store).length} 条本地修改。`, "success");
  }

  window.onFilmBackupExportResult = function (success, message) {
    setStatus(message || String(success), success ? "success" : "error");
  };

  async function importBackup(file) {
    if (!file) return;
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      const source = parsed && parsed.reviews && typeof parsed.reviews === "object" ? parsed.reviews : parsed;
      if (!source || typeof source !== "object" || Array.isArray(source)) throw new Error("格式不正确");

      let imported = 0;
      Object.entries(source).forEach(([id, value]) => {
        if (!byId.has(id)) return;
        const clean = sanitize(value);
        if (isMeaningful(clean)) {
          store[id] = clean;
          imported += 1;
        }
      });

      if (!writeStore(store)) return;
      store = readStore();
      const customApi = window.SilverCustomEntries;
      const customSource = Array.isArray(parsed.customEntries) ? parsed.customEntries : [];
      let importedCustom = 0;
      if (customApi && customSource.length) {
        const merged = new Map(customApi.read().map((entry) => [entry.id, entry]));
        customSource.forEach((value) => {
          const clean = customApi.normalize(value);
          if (clean) {
            merged.set(clean.id, clean);
            importedCustom += 1;
          }
        });
        customApi.write([...merged.values()]);
      }
      renderCount();
      renderList(searchInput ? searchInput.value : "");
      if (selectedId) selectEntry(selectedId, false);
      if (importedCustom > 0) {
        setTimeout(() => window.location.reload(), 450);
      }
      setStatus(`已导入 ${imported} 条本地记录，并与现有记录合并。`, "success");
    } catch (error) {
      setStatus("导入失败：请选择由本编辑器导出的 JSON 备份文件。", "error");
    } finally {
      if (importInput) importInput.value = "";
    }
  }

  function clearAll() {
    const total = Object.keys(store).length;
    if (!total) {
      setStatus("当前没有本地修改。", "");
      return;
    }
    if (!window.confirm(`确定清空全部 ${total} 条本地修改吗？此操作不可撤销，建议先导出备份。`)) return;
    store = {};
    if (!writeStore(store)) return;
    renderCount();
    renderList(searchInput ? searchInput.value : "");
    if (selectedId) selectEntry(selectedId, false);
    setStatus("已清空全部本地修改。", "success");
  }

  searchInput?.addEventListener("input", () => renderList(searchInput.value));

  listNode.addEventListener("click", (event) => {
    const item = event.target.closest("[data-id]");
    if (item) selectEntry(item.dataset.id);
  });

  form?.addEventListener("submit", (event) => {
    event.preventDefault();
    saveSelected();
  });

  [statusInput, ratingInput, reviewInput, favoriteShotInput].forEach((input) => {
    input?.addEventListener("input", () => setDirty(true));
    input?.addEventListener("change", () => setDirty(true));
  });

  addWatchDateButton?.addEventListener("click", () => {
    addWatchDate(selectedFilmWatchDate());
  });

  watchChips?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-watch-index]");
    if (!button) return;
    const index = Number(button.dataset.watchIndex);
    if (Number.isInteger(index) && index >= 0 && index < watchDates.length) {
      watchDates.splice(index, 1);
      renderWatchDates();
      setDirty(true);
    }
  });

  reviewInput?.addEventListener("input", () => {
    if (countNode) countNode.textContent = `${Array.from(reviewInput.value).length} 字`;
  });

  tagChips?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-tag-index]");
    if (!button) return;
    const index = Number(button.dataset.tagIndex);
    if (Number.isInteger(index) && index >= 0 && index < tagValues.length) {
      tagValues.splice(index, 1);
      renderTags();
      setDirty(true);
    }
  });

  tagInput?.addEventListener("keydown", (event) => {
    if (event.isComposing) return;
    if (event.key === "Enter" || event.key === "," || event.key === "，") {
      event.preventDefault();
      addTags(tagInput.value);
    }
    if (event.key === "Backspace" && !tagInput.value && tagValues.length) {
      tagValues.pop();
      renderTags();
      setDirty(true);
    }
  });

  tagInput?.addEventListener("blur", () => {
    if (tagInput.value.trim()) addTags(tagInput.value);
  });

  resetTagsButton?.addEventListener("click", () => {
    const entry = byId.get(selectedId);
    if (!entry) return;
    tagValues = [...entry.genres];
    renderTags();
    setDirty(true);
    setStatus("已恢复默认标签，记得保存。", "dirty");
  });

  resetButton?.addEventListener("click", resetSelected);
  addEntryButton?.addEventListener("click", openAddEntry);
  addEntryForm?.addEventListener("submit", addCustomEntry);
  addEntryModal?.querySelectorAll("[data-close-add-entry]").forEach((button) => {
    button.addEventListener("click", closeAddEntry);
  });
  deleteCustomEntryButton?.addEventListener("click", deleteSelectedCustomEntry);
  exportButton?.addEventListener("click", downloadBackup);
  importInput?.addEventListener("change", () => importBackup(importInput.files?.[0]));
  clearButton?.addEventListener("click", clearAll);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && addEntryModal && !addEntryModal.hidden) {
      closeAddEntry();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
      event.preventDefault();
      saveSelected();
    }
  });

  window.addEventListener("beforeunload", (event) => {
    if (!dirty) return;
    event.preventDefault();
    event.returnValue = "";
  });

  if ("storage" in window) {
    window.addEventListener("storage", (event) => {
      if (event.key !== STORAGE_KEY) return;
      store = readStore();
      renderCount();
      renderList(searchInput ? searchInput.value : "");
      if (selectedId) selectEntry(selectedId, false);
    });
  }

  initializeFilmWatchSelectors();
  renderCount();
  renderList("");
  if (selectedId) selectEntry(selectedId, false);
})();
