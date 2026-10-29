(function () {
  "use strict";

  const body = document.body;
  const root = (body.dataset.root || ".").replace(/\/$/, "");
  const rawEntries = Array.isArray(window.WIKI_ENTRIES) ? window.WIKI_ENTRIES : [];

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
    for (const char of String(value)) {
      hash = (hash * 31 + char.codePointAt(0)) % 1000000007;
    }
    return hash;
  }

  function coverFor(entry) {
    const palette = palettes[hashString(entry.title) % palettes.length];
    return `linear-gradient(135deg, ${palette[0]} 0%, ${palette[1]} 100%)`;
  }

  const REVIEW_STORAGE_KEY = "silver-screen:reviews:v1";

  function readReviewStore() {
    try {
      const raw = localStorage.getItem(REVIEW_STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
    } catch (error) {
      return {};
    }
  }

  const localReviews = readReviewStore();

  const entries = rawEntries.map((item, index) => {
    const id = item.id || item.title;
    const local = localReviews[id];
    const hasLocalTags = Boolean(local && Array.isArray(local.tags));
    const localTags = hasLocalTags
      ? [...new Set(local.tags.map((tag) => String(tag).trim()).filter(Boolean))]
      : [];
    const baseGenres = Array.isArray(item.genres) ? item.genres : [];
    return {
      ...item,
      id,
      index,
      year: Number(item.year) > 0 ? Number(item.year) : 0,
      genres: hasLocalTags ? localTags : baseGenres,
      baseGenres,
      tags: Array.isArray(item.tags) ? item.tags : [],
      mark: item.mark || Array.from(item.title || "?")[0],
      cover: item.cover || coverFor(item),
      status: item.status || "看过"
    };
  });

  const byId = new Map(entries.map((entry) => [entry.id, entry]));

  function localReviewFor(entry) {
    const value = localReviews[entry.id];
    return value && typeof value === "object" ? value : null;
  }

  function formatDateTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleString("zh-CN", { hour12: false });
  }

  const normalize = (value) => String(value || "").toLowerCase().replace(/\s+/g, " ").trim();
  const yearLabel = (entry, empty) => (entry.year ? String(entry.year) : (empty || "待补充"));
  const entryUrl = (id) => `${root}/entry.html?id=${encodeURIComponent(id)}`;
  const posterMap = window.WIKI_POSTERS && typeof window.WIKI_POSTERS === "object" ? window.WIKI_POSTERS : {};
  const posterFor = (entry) => {
    if (entry.posterData) return entry.posterData;
    const value = posterMap[entry.id] || posterMap[entry.title];
    if (!value) return "";
    if (/^(https?:|data:)/.test(value)) return value;
    return `${root}/${value.replace(/^\.?\//, "")}`;
  };
  const catalogUrl = (params) => {
    const query = new URLSearchParams();
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "" && value !== "全部") query.set(key, value);
    });
    const suffix = query.toString();
    return `${root}/entries/index.html${suffix ? `?${suffix}` : ""}`;
  };

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function searchText(entry, includeSecondary = true) {
    const fields = [
      entry.title,
      entry.original,
      ...entry.genres,
      ...entry.tags
    ];
    if (includeSecondary) {
      fields.push(entry.year, entry.medium, entry.creator, entry.region);
    }
    return normalize(fields.join(" "));
  }

  function searchEntries(query, limit) {
    const q = normalize(query);
    if (!q) {
      return entries.slice(0, limit || entries.length);
    }

    const terms = q.split(" ").filter(Boolean);
    const includeSecondary = q.length > 1;
    return entries
      .map((entry) => {
        const text = searchText(entry, includeSecondary);
        const title = normalize(entry.title);
        let score = 0;
        terms.forEach((term) => {
          if (title === term) score += 20;
          if (title.startsWith(term)) score += 10;
          if (title.includes(term)) score += 6;
          if (text.includes(term)) score += 1;
        });
        return { entry, score };
      })
      .filter((item) => item.score > 0)
      .sort((a, b) => b.score - a.score || a.entry.index - b.entry.index)
      .slice(0, limit || entries.length)
      .map((item) => item.entry);
  }

  function mediumCounts() {
    return entries.reduce((counts, entry) => {
      counts[entry.medium] = (counts[entry.medium] || 0) + 1;
      return counts;
    }, {});
  }

  function genreCounts() {
    const counts = new Map();
    entries.forEach((entry) => {
      entry.genres.forEach((genre) => counts.set(genre, (counts.get(genre) || 0) + 1));
    });
    return counts;
  }

  function entryCell(entry) {
    const local = localReviewFor(entry);
    const localRating = local && local.rating != null && Number.isFinite(Number(local.rating))
      ? Number(local.rating)
      : null;
    const localBadge = local && (String(local.review || "").trim() || local.rating != null)
      ? `<span class="entry-review-badge">${localRating != null ? `★ ${localRating.toFixed(1)}` : "已写"}</span>`
      : "";
    const poster = posterFor(entry);
    const mark = poster
      ? `<img class="entry-mark entry-thumb" src="${poster}" alt="" loading="lazy">`
      : `<span class="entry-mark" style="background:${entry.cover}">${escapeHtml(entry.mark)}</span>`;
    return `<div class="entry-cell">
      ${mark}
      <div>
        <a class="entry-link" href="${entryUrl(entry.id)}">${escapeHtml(entry.title)}</a>${localBadge}
        <span class="entry-original">${escapeHtml(entry.original || " ")}</span>
      </div>
    </div>`;
  }

  function resultMarkup(entry) {
    return `<a class="search-result" href="${entryUrl(entry.id)}">
      <span class="search-result-mark" style="background:${entry.cover}">${escapeHtml(entry.mark)}</span>
      <span>
        <span class="search-result-title">${escapeHtml(entry.title)}</span>
        <span class="search-result-meta">${escapeHtml(yearLabel(entry, "年份待补"))} · ${escapeHtml(entry.medium)} · ${escapeHtml(entry.genres.join(" / "))}</span>
      </span>
      <span class="search-result-type">${escapeHtml(entry.medium)}</span>
    </a>`;
  }

  function initSearch() {
    const input = document.getElementById("wiki-search");
    const results = document.getElementById("search-results");
    if (!input || !results) return;

    const render = (query) => {
      const normalizedQuery = normalize(query);
      const list = searchEntries(query, normalizedQuery ? entries.length : 6);
      results.innerHTML = list.length
        ? (normalize(query) ? "" : `<div class="search-empty" style="padding:9px 12px 7px;text-align:left">最近加入</div>`) + list.map(resultMarkup).join("")
        : `<div class="search-empty">没有找到对应条目，换个片名、类型或年份试试。</div>`;
      results.classList.add("open");
      input.setAttribute("aria-expanded", "true");
    };

    const close = () => {
      results.classList.remove("open");
      input.setAttribute("aria-expanded", "false");
    };

    input.addEventListener("focus", () => render(input.value));
    input.addEventListener("input", () => render(input.value));
    input.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        close();
        input.blur();
      }
      if (event.key === "Enter") {
        if (input.value.trim()) {
          event.preventDefault();
          window.location.href = catalogUrl({ q: input.value.trim() });
        }
      }
    });

    document.addEventListener("click", (event) => {
      if (!event.target.closest(".search-wrap")) close();
    });

    document.addEventListener("keydown", (event) => {
      const tag = (event.target.tagName || "").toLowerCase();
      const typing = tag === "input" || tag === "textarea" || tag === "select" || event.target.isContentEditable;
      if (event.key === "/" && !typing) {
        event.preventDefault();
        input.focus();
        input.select();
      }
    });
  }

  function initSidebar() {
    const toggle = document.getElementById("menu-toggle");
    const sidebar = document.getElementById("sidebar");
    const backdrop = document.getElementById("sidebar-backdrop");
    if (!toggle || !sidebar) return;

    const close = () => {
      sidebar.classList.remove("open");
      if (backdrop) backdrop.classList.remove("open");
      body.classList.remove("no-scroll");
      toggle.setAttribute("aria-expanded", "false");
    };

    toggle.addEventListener("click", () => {
      const open = !sidebar.classList.contains("open");
      sidebar.classList.toggle("open", open);
      if (backdrop) backdrop.classList.toggle("open", open);
      body.classList.toggle("no-scroll", open);
      toggle.setAttribute("aria-expanded", String(open));
    });

    if (backdrop) backdrop.addEventListener("click", close);
    sidebar.querySelectorAll("a").forEach((link) => link.addEventListener("click", close));
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") close();
    });
  }

  function initRandomEntry() {
    const button = document.getElementById("random-entry");
    if (!button || !entries.length) return;
    button.addEventListener("click", () => {
      const next = entries[Math.floor(Math.random() * entries.length)];
      window.location.href = entryUrl(next.id);
    });
  }

  function renderSideRecent() {
    const target = document.getElementById("side-recent");
    if (!target) return;
    target.innerHTML = entries.slice(0, 6).map((entry) => `
      <a href="${entryUrl(entry.id)}">${escapeHtml(entry.title)}<time>${escapeHtml(yearLabel(entry, "年份待补"))} · ${escapeHtml(entry.medium)}</time></a>
    `).join("");

    const total = document.getElementById("nav-total");
    if (total) total.textContent = String(entries.length);
    const aboutTotal = document.getElementById("about-total");
    if (aboutTotal) aboutTotal.textContent = String(entries.length);

    const counts = mediumCounts();
    const ids = { "电影": "nav-movie", "剧集": "nav-series", "动画": "nav-animation" };
    Object.entries(ids).forEach(([medium, id]) => {
      const node = document.getElementById(id);
      if (node) node.textContent = String(counts[medium] || 0);
    });
  }

  function renderHome() {
    const featured = document.getElementById("featured-entry");
    if (!featured) return;
    document.getElementById("category-grid")?.closest("section")?.classList.add("home-category-section");

    const total = document.getElementById("home-total");
    const totalStat = document.getElementById("home-total-stat");
    const aboutTotal = document.getElementById("about-total");
    const counts = mediumCounts();
    if (total) total.textContent = String(entries.length);
    if (totalStat) totalStat.textContent = String(entries.length);
    if (aboutTotal) aboutTotal.textContent = String(entries.length);
    ["home-movies", "home-series", "home-animation"].forEach((id) => {
      const node = document.getElementById(id);
      if (!node) return;
      const medium = id === "home-movies" ? "电影" : id === "home-series" ? "剧集" : "动画";
      node.textContent = String(counts[medium] || 0);
    });

    const pick = entries.find((entry) => entry.featured) || entries[0];
    if (pick) {
      const poster = posterFor(pick);
      featured.innerHTML = `<div class="feature-card">
        <div class="feature-poster" style="background:${pick.cover}">
          ${poster ? `<img class="feature-poster-img" src="${poster}" alt="${escapeHtml(pick.title)} 海报">` : `<span class="poster-mark">${escapeHtml(pick.mark)}</span>`}
          <span class="poster-caption">${escapeHtml(pick.medium)} · ${escapeHtml(yearLabel(pick))}</span>
        </div>
        <div class="feature-body">
          <span class="feature-meta">${escapeHtml(pick.medium)} · ${escapeHtml(pick.genres.join(" / "))}</span>
          <h3 class="feature-title">${escapeHtml(pick.title)}</h3>
          <p class="feature-en">${escapeHtml(pick.original || "")}</p>
          <p class="feature-excerpt">${escapeHtml(pick.blurb || "这条目还没有写简介。")}</p>
          <a class="button" href="${entryUrl(pick.id)}">阅读条目 <span aria-hidden="true">→</span></a>
        </div>
      </div>`;
    } else {
      featured.innerHTML = `<div class="empty-state">
        <p>影视片单还是空的。</p>
        <p><a class="button secondary" href="${root}/editor.html">＋ 添加第一部影视</a></p>
      </div>`;
    }

    const recent = document.getElementById("recent-entries");
    if (recent) {
      recent.innerHTML = entries.length ? entries.slice(0, 6).map((entry) => `
        <a class="recent-row" href="${entryUrl(entry.id)}">
          <span class="recent-date">${escapeHtml(yearLabel(entry, "——"))}</span>
          <span class="recent-title">${escapeHtml(entry.title)}<small>${escapeHtml(entry.original || "")}</small></span>
          <span class="recent-tag">${escapeHtml(entry.medium)} · ${escapeHtml(entry.genres.slice(0, 2).join(" / "))}</span>
        </a>
      `).join("") : `<div class="empty-state">还没有手动添加的影视记录。</div>`;
    }

    const categories = document.getElementById("category-grid");
    if (categories) {
      const genreMap = genreCounts();
      const cards = [
        { name: "电影", desc: "完整长片与院线电影", mark: "影", href: catalogUrl({ medium: "电影" }), count: counts["电影"] || 0, type: "medium" },
        { name: "剧集", desc: "连续剧、迷你剧与季播剧", mark: "剧", href: catalogUrl({ medium: "剧集" }), count: counts["剧集"] || 0, type: "medium" },
        { name: "动画", desc: "动画电影与剧场版", mark: "动", href: catalogUrl({ medium: "动画" }), count: counts["动画"] || 0, type: "medium" },
        ...[...genreMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([genre, count]) => ({
          name: genre,
          desc: `按「${genre}」题材浏览`,
          mark: Array.from(genre)[0],
          href: catalogUrl({ genre }),
          count,
          type: "genre"
        }))
      ];
      categories.innerHTML = cards.map((card) => `
        <a class="category-card" href="${card.href}" data-mark="${escapeHtml(card.mark)}">
          <span class="category-name">${escapeHtml(card.name)}</span>
          <span class="category-desc">${escapeHtml(card.desc)}</span>
          <span class="category-count">${card.count} 条</span>
        </a>
      `).join("");
    }
  }

  function renderDailyQuote() {
    const box = document.getElementById("home-quote");
    if (!box) return;
    const quotes = Array.isArray(window.WIKI_QUOTES) ? window.WIKI_QUOTES.filter((quote) => quote && quote.text) : [];
    if (!quotes.length) return;

    const textNode = document.getElementById("daily-quote-text");
    const filmNode = document.getElementById("daily-quote-film");
    const noteNode = document.getElementById("daily-quote-note");
    const refresh = document.getElementById("quote-refresh");
    const today = new Date();
    const seed = today.getFullYear() * 10000 + (today.getMonth() + 1) * 100 + today.getDate();
    let index = seed % quotes.length;

    const paint = (nextIndex) => {
      index = nextIndex;
      const quote = quotes[index];
      if (textNode) textNode.textContent = quote.text;
      if (noteNode) noteNode.textContent = quote.note || "";
      if (filmNode) {
        const entry = byId.get(quote.entryId || quote.film);
        filmNode.textContent = `—— ${quote.film}`;
        if (entry) filmNode.href = entryUrl(entry.id);
        else filmNode.removeAttribute("href");
      }
      box.dataset.index = String(index);
    };

    paint(index);
    box.hidden = false;
    refresh?.addEventListener("click", () => {
      if (quotes.length < 2) return;
      let next = index;
      while (next === index) next = Math.floor(Math.random() * quotes.length);
      paint(next);
    });
  }

  function renderCatalog() {
    const tbody = document.getElementById("catalog-body");
    if (!tbody) return;

    const filter = document.getElementById("entry-filter");
    const yearSelect = document.getElementById("year-filter");
    const genreSelect = document.getElementById("genre-filter");
    const sortSelect = document.getElementById("sort-order");
    const chips = Array.from(document.querySelectorAll("[data-medium]"));
    const empty = document.getElementById("table-empty");
    const count = document.getElementById("visible-count");
    const loadMore = document.getElementById("catalog-load-more");
    const pageSize = 30;
    const params = new URLSearchParams(window.location.search);
    let medium = params.get("medium") || "全部";
    let year = params.get("year") || "全部";
    let genre = params.get("genre") || "全部";
    let sort = params.get("sort") || "order";

    if (filter && params.get("q")) filter.value = params.get("q");
    if (yearSelect) {
      const yearMap = entries.reduce((map, entry) => {
        const key = entry.year ? String(entry.year) : "0";
        map.set(key, (map.get(key) || 0) + 1);
        return map;
      }, new Map());
      const years = [...yearMap.entries()]
        .filter(([key]) => key !== "0")
        .sort((a, b) => Number(b[0]) - Number(a[0]));
      yearSelect.innerHTML = `<option value="全部">全部年份</option>` +
        years.map(([name, total]) => `<option value="${name}">${name} · ${total}</option>`).join("") +
        (yearMap.has("0") ? `<option value="0">年份待补 · ${yearMap.get("0")}</option>` : "");
      if (year !== "全部") yearSelect.value = year;
    }
    if (genreSelect) {
      const genres = [...genreCounts().entries()].sort((a, b) => a[0].localeCompare(b[0], "zh"));
      genreSelect.innerHTML = `<option value="全部">全部题材</option>` +
        genres.map(([name, total]) => `<option value="${escapeHtml(name)}">${escapeHtml(name)} · ${total}</option>`).join("");
      if (genre !== "全部") genreSelect.value = genre;
    }
    if (sortSelect) sortSelect.value = sort;

    let currentRows = [];
    let visibleRows = pageSize;

    const paintRows = () => {
      const visible = currentRows.slice(0, visibleRows);
      tbody.innerHTML = visible.map((entry) => `
        <tr data-entry-row data-medium="${escapeHtml(entry.medium)}">
          <td>${entryCell(entry)}</td>
          <td class="col-medium"><span class="table-muted">${escapeHtml(entry.medium)}</span></td>
          <td class="col-year"><span class="table-muted">${entry.year ? `<a class="table-filter-link" href="${catalogUrl({ year: entry.year })}">${entry.year}</a>` : "—"}</span></td>
          <td class="col-genre"><span class="table-muted">${entry.genres.map((item) => `<a class="table-filter-link" href="${catalogUrl({ genre: item })}">${escapeHtml(item)}</a>`).join(" / ")}</span></td>
          <td class="col-note"><span class="table-muted">${escapeHtml(entry.note || "—")}</span></td>
        </tr>
      `).join("");
      if (count) count.textContent = String(currentRows.length);
      if (empty) empty.hidden = currentRows.length !== 0;
      if (loadMore) {
        const remaining = Math.max(0, currentRows.length - visible.length);
        loadMore.hidden = remaining === 0;
        loadMore.textContent = remaining ? `显示更多（还有 ${remaining} 条）` : "";
      }
    };

    const apply = () => {
      const q = normalize(filter ? filter.value : "");
      let list = entries.filter((entry) => {
        const byMedium = medium === "全部" || entry.medium === medium;
        const byYear = year === "全部" || (year === "0" ? !entry.year : entry.year === Number(year));
        const byGenre = genre === "全部" || entry.genres.includes(genre);
        const byQuery = !q || searchText(entry, q.length > 1).includes(q);
        return byMedium && byYear && byGenre && byQuery;
      });

      if (sort === "year") {
        list = list.slice().sort((a, b) => (b.year || 0) - (a.year || 0) || a.index - b.index);
      } else if (sort === "title") {
        list = list.slice().sort((a, b) => a.title.localeCompare(b.title, "zh"));
      }

      chips.forEach((chip) => {
        const active = chip.dataset.medium === medium;
        chip.classList.toggle("active", active);
        chip.setAttribute("aria-pressed", String(active));
      });

      tbody.innerHTML = list.map((entry) => `
        <tr data-entry-row data-medium="${escapeHtml(entry.medium)}">
          <td>${entryCell(entry)}</td>
          <td class="col-medium"><span class="table-muted">${escapeHtml(entry.medium)}</span></td>
          <td class="col-year"><span class="table-muted">${entry.year ? `<a class="table-filter-link" href="${catalogUrl({ year: entry.year })}">${entry.year}</a>` : "—"}</span></td>
          <td class="col-genre"><span class="table-muted">${entry.genres.map((item) => `<a class="table-filter-link" href="${catalogUrl({ genre: item })}">${escapeHtml(item)}</a>`).join(" / ")}</span></td>
          <td class="col-note"><span class="table-muted">${escapeHtml(entry.note || "—")}</span></td>
        </tr>
      `).join("");

      if (count) count.textContent = String(list.length);
      if (empty) empty.hidden = list.length !== 0;
      currentRows = list;
      visibleRows = pageSize;
      paintRows();

      if (window.history && window.history.replaceState) {
        const next = new URL(window.location.href);
        if (q) next.searchParams.set("q", filter.value.trim());
        else next.searchParams.delete("q");
        if (medium !== "全部") next.searchParams.set("medium", medium);
        else next.searchParams.delete("medium");
        if (year !== "全部") next.searchParams.set("year", year);
        else next.searchParams.delete("year");
        if (genre !== "全部") next.searchParams.set("genre", genre);
        else next.searchParams.delete("genre");
        if (sort !== "order") next.searchParams.set("sort", sort);
        else next.searchParams.delete("sort");
        try { window.history.replaceState({}, "", next); } catch (error) { /* file:// 环境下忽略 */ }
      }
    };

    if (loadMore) {
      loadMore.addEventListener("click", () => {
        visibleRows += pageSize;
        paintRows();
      });
    }

    chips.forEach((chip) => {
      chip.addEventListener("click", () => {
        medium = chip.dataset.medium;
        apply();
      });
    });
    if (filter) filter.addEventListener("input", apply);
    if (yearSelect) {
      yearSelect.addEventListener("change", () => {
        year = yearSelect.value;
        apply();
      });
    }
    if (genreSelect) {
      genreSelect.addEventListener("change", () => {
        genre = genreSelect.value;
        apply();
      });
    }
    if (sortSelect) {
      sortSelect.addEventListener("change", () => {
        sort = sortSelect.value;
        apply();
      });
    }

    apply();
  }

  function miniMark(entry) {
    const poster = posterFor(entry);
    return poster
      ? `<img class="mini-mark mini-thumb" src="${poster}" alt="" loading="lazy">`
      : `<span class="mini-mark" style="background:${entry.cover}">${escapeHtml(entry.mark)}</span>`;
  }

  function renderCategories() {
    const target = document.getElementById("categories-root");
    if (!target) return;

    const counts = mediumCounts();
    const mediumOrder = ["电影", "剧集", "动画"];
    const mediumDesc = {
      "电影": "完整长片、院线片和个人反复重看的电影。",
      "剧集": "连续剧、迷你剧和按季观看的剧集。",
      "动画": "动画电影、剧场版和动画长片。"
    };

    const yearMap = entries.reduce((map, entry) => {
      const key = entry.year ? String(entry.year) : "0";
      map.set(key, (map.get(key) || 0) + 1);
      return map;
    }, new Map());
    const years = [...yearMap.entries()]
      .filter(([key]) => key !== "0")
      .sort((a, b) => Number(b[0]) - Number(a[0]));
    const genres = [...genreCounts().entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "zh"));
    const mediumMarks = { "电影": "影", "剧集": "剧", "动画": "动" };

    target.innerHTML = `
      <section class="year-index" id="year-index">
        <h2 class="section-title">按年份</h2>
        <p class="category-desc-text">点年份进入该年份的全部条目；同一年的电影、剧集和动画会一起列出。</p>
        <div class="year-grid">
          ${years.map(([year, total]) => `<a class="year-chip" href="${catalogUrl({ year })}"><strong>${year}</strong><span>${total} 条</span></a>`).join("")}
          ${yearMap.has("0") ? `<a class="year-chip unknown" href="${catalogUrl({ year: 0 })}"><strong>待补</strong><span>${yearMap.get("0")} 条</span></a>` : ""}
        </div>
      </section>

      <section class="section" id="type-index">
        <h2 class="section-title">按类型</h2>
        <div class="category-grid">
          ${mediumOrder.map((medium) => `<a class="category-card" href="${catalogUrl({ medium })}" data-mark="${escapeHtml(mediumMarks[medium] || "影")}">
            <span class="category-name">${escapeHtml(medium)}</span>
            <span class="category-desc">${escapeHtml(mediumDesc[medium] || "")}</span>
            <span class="category-count">${counts[medium] || 0} 条</span>
          </a>`).join("")}
        </div>
      </section>

      <section class="section" id="genre-index">
        <h2 class="section-title">按题材</h2>
        <p class="category-desc-text">全部题材标签，包括你在本地编辑器里新增的标签。点标签进入对应作品列表。</p>
        <div class="tag-cloud">
          ${genres.map(([genre, total]) => `<a class="tag-chip" href="${catalogUrl({ genre })}">${escapeHtml(genre)}<span>${total}</span></a>`).join("")}
        </div>
      </section>`;
  }

  function relatedEntries(entry) {
    return entries
      .filter((item) => item.id !== entry.id)
      .map((item) => {
        const shared = item.genres.filter((genre) => entry.genres.includes(genre)).length;
        return { item, score: shared * 4 + (item.medium === entry.medium ? 2 : 0) };
      })
      .filter((item) => item.score > 0)
      .sort((a, b) => b.score - a.score || a.item.index - b.item.index)
      .slice(0, 4)
      .map((item) => item.item);
  }

  function renderEntryPage() {
    const titleNode = document.getElementById("entry-title");
    if (!titleNode) return;

    const id = new URLSearchParams(window.location.search).get("id");
    const entry = id ? byId.get(id) : null;

    if (!entry) {
      titleNode.textContent = "条目不存在";
      const original = document.getElementById("entry-original");
      const content = document.getElementById("entry-content");
      const crumb = document.getElementById("entry-crumb");
      if (original) original.textContent = "";
      if (crumb) crumb.textContent = "条目不存在";
      if (content) {
        content.innerHTML = `<h2 id="missing">没有找到这个条目</h2>
          <p>链接里的条目 ID 不在当前数据中。你可以回到 <a href="${root}/entries/index.html">全部条目</a> 重新选择。</p>`;
      }
      document.title = "条目不存在 · 我的影视维基";
      return;
    }

    document.title = `${entry.title} · 我的影视维基`;
    const description = document.querySelector('meta[name="description"]');
    if (description) description.setAttribute("content", entry.blurb || `${entry.title}的个人观影条目`);

    const setText = (idName, value) => {
      const node = document.getElementById(idName);
      if (node) node.textContent = value;
    };

    setText("entry-crumb", entry.title);
    setText("entry-medium", `${entry.medium} · 个人记录`);
    setText("entry-title", entry.title);
    setText("entry-original", entry.original || "");

    const local = localReviewFor(entry);
    const localRating = local && local.rating != null && Number.isFinite(Number(local.rating))
      ? Number(local.rating)
      : null;
    const baseRating = entry.score != null && Number.isFinite(Number(entry.score)) ? Number(entry.score) : null;
    const displayRating = localRating != null ? localRating : baseRating;
    const displayStatus = (local && local.status) || entry.status;
    const reviewText = String(local?.review || "").trim();
    const favoriteShot = String(local?.favoriteShot || "").trim();
    const watchDates = (() => {
      const raw = Array.isArray(local?.watchDates) ? local.watchDates : (local?.watchedAt ? [local.watchedAt] : []);
      return [...new Set(raw.filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(String(date))).map(String))].sort();
    })();
    const lastWatched = watchDates[watchDates.length - 1] || "";
    const rewatchCount = Math.max(0, watchDates.length - 1);

    const tools = document.getElementById("entry-tools");
    if (tools) {
      tools.innerHTML = `<a class="button secondary" href="${root}/editor.html?id=${encodeURIComponent(entry.id)}">✎ 编辑这条</a>`;
    }

    const personal = document.getElementById("entry-personal");
    if (personal) {
      personal.innerHTML = `
        <div class="personal-stat">
          <span class="personal-label">我的评分</span>
          <span class="personal-value">${displayRating != null ? `${displayRating.toFixed(1)} / 10` : "未评分"}</span>
        </div>
        <div class="personal-stat">
          <span class="personal-label">观看</span>
          <span class="personal-value">${watchDates.length ? `${watchDates.length} 次` : "未记录"}</span>
          <span class="personal-sub">${watchDates.length ? `${rewatchCount ? `重看 ${rewatchCount} 次 · ` : ""}最近 ${lastWatched}` : "还没有观看日期"}</span>
        </div>
        <div class="personal-tags">
          <span class="personal-label">我的标签</span>
          <div class="personal-tag-row">
            ${entry.genres.map((genre) => `<a class="tag" href="${catalogUrl({ genre })}">${escapeHtml(genre)}</a>`).join("") || `<span class="personal-sub">还没有标签</span>`}
          </div>
        </div>`;
    }

    const infobox = document.getElementById("entry-infobox");
    if (infobox) {
      const poster = posterFor(entry);
      infobox.innerHTML = `<div class="infobox-cover${poster ? " has-poster" : ""}" style="background:${entry.cover}">
          <span class="infobox-type">${escapeHtml(entry.medium)}</span>
          ${poster ? `<img class="infobox-img" src="${poster}" alt="${escapeHtml(entry.title)} 海报">` : `<span class="infobox-mark">${escapeHtml(entry.mark)}</span>`}
        </div>`;
    }

    const content = document.getElementById("entry-content");
    if (content) {
      const related = relatedEntries(entry);
      const watchList = watchDates.length
        ? `<ol class="watch-list">${watchDates.map((date, index) => `<li class="watch-item"><time>${date}</time><span>第 ${index + 1} 次观看${index === watchDates.length - 1 && watchDates.length > 1 ? " · 最近一次" : ""}</span></li>`).join("")}</ol>`
        : `<p class="personal-empty">还没有记录观看日期。点上方“编辑这条”，添加第一次观看或重看的日期。</p>`;
      const memory = favoriteShot
        ? `<blockquote class="memory-quote">${escapeHtml(favoriteShot).replace(/\n/g, "<br>")}</blockquote>`
        : `<p class="personal-empty">还没有写下最记得的镜头。可以是一个画面、一句台词，或者一个具体的时间点。</p>`;
      const reviewBlock = reviewText
        ? `<div class="review-card"><div class="review-body">${escapeHtml(reviewText).replace(/\n/g, "<br>")}</div></div>`
        : `<p class="personal-empty">还没有写影评。点上方“编辑这条”，写下当时的感受。</p>`;
      const originalGenres = (entry.baseGenres || []).map((genre) => `<span>${escapeHtml(genre)}</span>`).join(" / ") || "待补充";
      const relatedInfo = `<details class="related-info">
        <summary>相关资料</summary>
        <div class="related-info-body">
          <dl class="info-grid">
            <dt>年份</dt><dd>${entry.year ? `<a class="table-filter-link" href="${catalogUrl({ year: entry.year })}">${entry.year}</a>` : escapeHtml(yearLabel(entry))}</dd>
            <dt>原始题材</dt><dd>${originalGenres}</dd>
            <dt>主创</dt><dd>${escapeHtml(entry.creator || "待补充")}</dd>
            <dt>地区</dt><dd>${escapeHtml(entry.region || "待补充")}</dd>
            <dt>状态</dt><dd>${escapeHtml(displayStatus)}</dd>
            ${entry.note ? `<dt>备注</dt><dd>${escapeHtml(entry.note)}</dd>` : ""}
          </dl>
          ${entry.blurb ? `<p class="related-synopsis">${escapeHtml(entry.blurb)}</p>` : ""}
        </div>
      </details>`;
      content.innerHTML = `
        <h2 id="record">我的观看记录</h2>
        ${watchList}
        <h2 id="shot">最记得的一个镜头</h2>
        ${memory}
        <h2 id="review">我的影评</h2>
        ${reviewBlock}
        ${relatedInfo}
        <h2 id="related">相关条目</h2>
        <div class="related-grid">
          ${related.map((item) => `<a class="related-card" href="${entryUrl(item.id)}">${miniMark(item)}<span>${escapeHtml(item.title)}<small>${escapeHtml(yearLabel(item, "年份待补"))} · ${escapeHtml(item.medium)}</small></span></a>`).join("")}
        </div>
      `;
    }

    const pager = document.getElementById("entry-pager");
    if (pager) {
      const prev = entries[entry.index - 1];
      const next = entries[entry.index + 1];
      pager.innerHTML = `${prev ? `<a class="pager" href="${entryUrl(prev.id)}"><small>上一条</small><strong>${escapeHtml(prev.title)}</strong></a>` : `<span></span>`}
        ${next ? `<a class="pager next" href="${entryUrl(next.id)}"><small>下一条</small><strong>${escapeHtml(next.title)}</strong></a>` : `<span></span>`}`;
    }
  }

  function initToc() {
    const toc = document.getElementById("toc");
    const article = document.getElementById("entry-content");
    if (!toc || !article) return;

    const headings = Array.from(article.querySelectorAll("h2[id]"));
    if (!headings.length) {
      toc.hidden = true;
      return;
    }

    const list = document.createElement("ol");
    headings.forEach((heading) => {
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = `#${heading.id}`;
      link.textContent = heading.textContent;
      item.appendChild(link);
      list.appendChild(item);
    });
    toc.appendChild(list);

    const links = Array.from(toc.querySelectorAll("a"));
    const setActive = (id) => {
      links.forEach((link) => link.classList.toggle("active", link.getAttribute("href") === `#${id}`));
    };

    if ("IntersectionObserver" in window) {
      const observer = new IntersectionObserver((items) => {
        const visible = items
          .filter((item) => item.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible.length) setActive(visible[0].target.id);
      }, { rootMargin: "-90px 0px -68% 0px", threshold: 0 });
      headings.forEach((heading) => observer.observe(heading));
    }
  }

  function initActiveNav() {
    const page = body.dataset.page;
    document.querySelectorAll(".side-link[data-nav]").forEach((link) => {
      const target = link.dataset.nav;
      const match = target === page || (page === "entry" && target === "entries");
      link.classList.toggle("active", match);
      if (match) link.setAttribute("aria-current", "page");
    });
  }

  function initYear() {
    document.querySelectorAll("[data-year]").forEach((node) => {
      node.textContent = String(new Date().getFullYear());
    });
  }

  function initMobileTopbarOffset() {
    const topbar = document.querySelector(".topbar");
    if (!topbar) return;
    const update = () => {
      document.documentElement.style.setProperty(
        "--mobile-topbar-height",
        `${topbar.getBoundingClientRect().height}px`
      );
    };
    update();
    window.addEventListener("resize", update, { passive: true });
    if ("ResizeObserver" in window) {
      new ResizeObserver(update).observe(topbar);
    }
  }

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

  initViewportHeight();
  initMobileTopbarOffset();
  renderHome();
  renderDailyQuote();
  renderCatalog();
  renderCategories();
  renderEntryPage();
  renderSideRecent();
  initSearch();
  initSidebar();
  initRandomEntry();
  initToc();
  initActiveNav();
  initYear();
})();
