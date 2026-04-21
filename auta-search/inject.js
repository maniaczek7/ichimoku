(function () {
  "use strict";

  if (document.getElementById("auta-filter-panel")) {
    document.getElementById("auta-filter-panel").remove();
    document.getElementById("auta-filter-results")?.remove();
    document.getElementById("auta-filter-style")?.remove();
    document.getElementById("auta-preview-dialog")?.remove();
    return;
  }

  const API = "/api/v1/auctions/";
  let allAuctions = [];
  let filteredAuctions = [];
  let activeBrands = new Set();
  let currentSort = "endTime";
  let currentPage = 1;
  let pageSize = 300;

  let enrichedData = {};
  let advancedFilters = {};
  let advancedPanelOpen = false;

  const ADV_FIELDS = [
    { key: "fuel", section: "Technische Spezifikationen", field: "Kraftstoffart", label: "Paliwo",
      translate: { "Diesel": "Diesel", "Benzin": "Benzyna", "Elektro": "Elektryczny", "Hybrid": "Hybryda",
        "Plug-in-Hybrid": "Plug-in Hybryda", "Erdgas (CNG)": "Gaz CNG", "Flüssiggas (LPG)": "LPG" } },
    { key: "gearbox", section: "Technische Spezifikationen", field: "Automatik", label: "Skrzynia",
      translate: { "Ja": "Automatyczna", "Nein": "Manualna" } },
    { key: "drive", section: "Technische Spezifikationen", field: "Antriebsart", label: "Naped",
      translate: { "Allrad": "4x4", "Frontantrieb": "Przod", "Hinterradantrieb": "Tyl" } },
    { key: "hail", section: "Aussehen und Zustand", field: "Hagelschaden", label: "Grad",
      translate: { "Ja": "Tak", "Nein": "Nie" } },
    { key: "rust", section: "Aussehen und Zustand", field: "Rost vorhanden", label: "Rdza",
      translate: { "Ja": "Tak", "Nein": "Nie" } },
    { key: "lights", section: "Aussehen und Zustand", field: "Kontrollleuchten an", label: "Kontrolki",
      translate: { "Ja": "Tak", "Nein": "Nie" } },
    { key: "perfect", section: "Aussehen und Zustand", field: "Perfekter Zustand", label: "Idealny stan",
      translate: { "Ja": "Tak", "Nein": "Nie" } },
    { key: "swiss", section: "Fahrzeuginformationen", field: "Schweizer Auto", label: "Auto CH",
      translate: { "Ja": "Tak", "Nein": "Nie" } },
    { key: "status", section: "Fahrzeugstatus", label: "Status", isArray: true,
      translate: { "Schweizer Fahrzeug": "Auto CH", "Importfahrzeug": "Import" } },
    { key: "tires", section: "Reifeninformationen", label: "Opony", isArray: true,
      translate: { "Sommer- und Winterreifen mit Felgen": "Letnie+zimowe z felgami",
        "Sommer- und Winterreifen ohne Felgen": "Letnie+zimowe bez felg",
        "Sommerreifen mit Felgen": "Letnie z felgami", "Winterreifen mit Felgen": "Zimowe z felgami",
        "Sommerreifen": "Letnie", "Winterreifen": "Zimowe" } },
  ];

  function getAdvValue(detail, field) {
    const d = detail.data || detail._advData || {};
    if (field.isArray) {
      const arr = d[field.section] || [];
      return arr.filter(v => v && v !== "Brak danych");
    }
    const sec = d[field.section] || {};
    const v = sec[field.field];
    if (!v || v === "" || v === "Brak danych" || v === null) return [];
    return [v];
  }

  function translateVal(field, v) {
    return (field.translate && field.translate[v]) || v;
  }

  function hasActiveAdvanced() {
    return Object.values(advancedFilters).some(s => s && s.size > 0);
  }

  function matchesAdvanced(auctionId) {
    const detail = enrichedData[auctionId];
    if (!detail) return false;
    for (const f of ADV_FIELDS) {
      const sel = advancedFilters[f.key];
      if (!sel || sel.size === 0) continue;
      const vals = getAdvValue(detail, f);
      if (!vals.some(v => sel.has(v))) return false;
    }
    return true;
  }

  // ── CSS ──────────────────────────────────────────
  const style = document.createElement("style");
  style.id = "auta-filter-style";
  style.textContent = `
    #auta-filter-panel {
      position: relative; z-index: 999999; background: #12141d; color: #e4e6ef;
      font-family: 'Segoe UI', system-ui, sans-serif; box-shadow: 0 4px 30px rgba(0,0,0,.6);
    }
    #auta-filter-panel * { box-sizing: border-box; margin: 0; padding: 0; }

    .afp-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 10px 20px; gap: 12px; background: #1a1d27;
      border-bottom: 1px solid #2e3346; position: sticky; top: 0; z-index: 1000000;
    }
    .afp-header h2 { font-size: 15px; color: #4f8cff; font-weight: 600; }
    .afp-header h2 span { color: #8b90a5; font-weight: 400; }
    .afp-status { font-size: 13px; color: #8b90a5; }
    .afp-status b { color: #2ecc71; }
    .afp-header-btns { display: flex; gap: 6px; }
    .afp-clear {
      background: none; border: 1px solid #3a3f55; color: #f39c12;
      padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 12px;
    }
    .afp-clear:hover { border-color: #f39c12; background: rgba(243,156,18,.1); }
    .afp-close {
      background: none; border: 1px solid #3a3f55; color: #e4e6ef;
      padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px;
    }
    .afp-close:hover { border-color: #e74c3c; color: #e74c3c; }

    .afp-filters {
      padding: 10px 20px; display: flex; flex-wrap: wrap; gap: 8px; align-items: flex-end;
      border-bottom: 1px solid #2e3346;
    }
    .afp-fg { display: flex; flex-direction: column; gap: 3px; }
    .afp-fg label {
      font-size: 10px; text-transform: uppercase; letter-spacing: .5px; color: #8b90a5; font-weight: 600;
    }
    .afp-fg input, .afp-fg select {
      background: #242836; border: 1px solid #2e3346; color: #e4e6ef;
      padding: 6px 8px; border-radius: 5px; font-size: 13px; outline: none;
    }
    .afp-fg input:focus, .afp-fg select:focus { border-color: #4f8cff; }
    .afp-fg input::placeholder { color: #555a70; }
    .afp-search { flex: 1; min-width: 160px; }
    .afp-search input { width: 100%; font-size: 14px; padding: 7px 10px; }
    .afp-range { display: flex; gap: 4px; align-items: center; }
    .afp-range input { width: 70px; text-align: center; }
    .afp-range span { color: #555; font-size: 11px; }
    .afp-help {
      width: 100%; padding: 4px 0 0; font-size: 10px; color: #555; line-height: 1.5;
    }
    .afp-help code {
      background: #242836; padding: 1px 4px; border-radius: 3px; color: #4f8cff; font-size: 10px;
    }

    .afp-brands {
      padding: 8px 20px; display: flex; flex-wrap: wrap; gap: 6px;
      border-bottom: 1px solid #2e3346; max-height: 90px; overflow-y: auto;
    }
    .afp-chip {
      background: #1e2538; border: 1px solid #2a3a5a; color: #a0b4d8;
      padding: 5px 14px; border-radius: 14px; font-size: 12px;
      cursor: pointer; user-select: none; transition: all .15s;
      white-space: nowrap; letter-spacing: .2px;
    }
    .afp-chip:hover { border-color: #4f8cff; color: #e4e6ef; }
    .afp-chip.active { background: #4f8cff; color: #fff; border-color: #4f8cff; }
    .afp-chip.active .cnt { background: rgba(255,255,255,.2); color: #fff; }
    .afp-chip .cnt {
      margin-left: 6px; font-weight: 700;
      background: rgba(79,140,255,.15); color: #6ea8ff;
      padding: 1px 7px; border-radius: 8px; font-size: 11px;
    }
    .afp-chip.zero { opacity: .3; }

    /* ── Advanced panel ── */
    .afp-adv {
      border-bottom: 1px solid #2e3346; background: #141722;
    }
    .afp-adv-bar {
      padding: 6px 20px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
    }
    .afp-adv-btn {
      background: linear-gradient(135deg, #1a3a2a, #1e4535); border: 1px solid #2a6a4a;
      color: #5ddb8e; padding: 5px 16px; border-radius: 5px; cursor: pointer;
      font-size: 12px; font-weight: 600; transition: all .2s;
    }
    .afp-adv-btn:hover { background: linear-gradient(135deg, #1e4535, #257050); border-color: #3a9a6a; }
    .afp-adv-btn:disabled { opacity: .5; cursor: wait; }
    .afp-adv-status { font-size: 11px; color: #8b90a5; flex: 1; }
    .afp-adv-status b { color: #5ddb8e; }
    .afp-adv-progress {
      height: 4px; background: #242836; border-radius: 2px; margin-top: 4px; overflow: hidden;
    }
    .afp-adv-progress-bar {
      height: 100%; background: #5ddb8e; border-radius: 2px; transition: width .3s;
    }
    .afp-adv-toggle {
      background: none; border: 1px solid #2e3346; color: #8b90a5;
      padding: 3px 10px; border-radius: 4px; cursor: pointer; font-size: 11px;
    }
    .afp-adv-toggle:hover { border-color: #4f8cff; color: #e4e6ef; }
    .afp-adv-body {
      padding: 6px 20px 10px; display: none;
    }
    .afp-adv-body.open { display: block; }
    .afp-adv-group {
      display: flex; align-items: center; gap: 6px; margin-bottom: 4px; flex-wrap: wrap;
    }
    .afp-adv-label {
      font-size: 10px; text-transform: uppercase; color: #6b7394; font-weight: 700;
      min-width: 85px; letter-spacing: .3px;
    }
    .afp-adv-chip {
      background: #1c2535; border: 1px solid #2a3a55; color: #8ba0c8;
      padding: 2px 10px; border-radius: 10px; font-size: 11px;
      cursor: pointer; user-select: none; transition: all .15s; white-space: nowrap;
    }
    .afp-adv-chip:hover { border-color: #5ddb8e; color: #e4e6ef; }
    .afp-adv-chip.active { background: #1a4535; color: #5ddb8e; border-color: #3a8a5a; }
    .afp-adv-chip .acnt {
      margin-left: 4px; opacity: .6; font-size: 10px;
    }
    .afp-adv-chip.zero { opacity: .3; }

    .afp-toolbar {
      padding: 6px 20px; display: flex; align-items: center; gap: 8px;
      font-size: 12px; color: #8b90a5; flex-wrap: wrap; border-bottom: 1px solid #2e3346;
    }
    .afp-sbtn {
      background: none; border: 1px solid #2e3346; color: #8b90a5;
      padding: 3px 8px; border-radius: 3px; cursor: pointer; font-size: 11px;
    }
    .afp-sbtn:hover { border-color: #4f8cff; color: #e4e6ef; }
    .afp-sbtn.active { background: #4f8cff; color: #fff; border-color: #4f8cff; }
    .afp-toolbar-sep { flex: 1; }
    .afp-pager { display: flex; align-items: center; gap: 4px; font-size: 11px; }
    .afp-page-info { color: #8b90a5; margin-right: 6px; white-space: nowrap; }
    .afp-page-btn {
      background: #242836; border: 1px solid #2e3346; color: #8b90a5;
      padding: 2px 8px; border-radius: 3px; cursor: pointer; font-size: 11px;
      min-width: 28px; text-align: center;
    }
    .afp-page-btn:hover { border-color: #4f8cff; color: #e4e6ef; }
    .afp-page-btn.active { background: #4f8cff; color: #fff; border-color: #4f8cff; }
    .afp-page-dots { color: #555; }
    .afp-page-size {
      background: #242836; border: 1px solid #2e3346; color: #8b90a5;
      padding: 2px 4px; border-radius: 3px; font-size: 11px; margin-left: 4px;
    }

    #auta-filter-results { background: #0f1117; min-height: 100vh; padding: 16px 20px; }
    .afr-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px;
    }
    .afr-card {
      background: #1a1d27; border: 1px solid #2e3346; border-radius: 8px;
      overflow: hidden; color: inherit; display: block;
      transition: border-color .2s, transform .15s; cursor: pointer;
    }
    .afr-card:hover { border-color: #4f8cff; transform: translateY(-2px); }
    .afr-img-wrap { position: relative; }
    .afr-card img {
      width: 100%; height: 190px; object-fit: cover; background: #242836; display: block;
    }
    .afr-card-body { padding: 10px 14px; }
    .afr-title {
      font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #e4e6ef;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .afr-meta { display: flex; flex-wrap: wrap; gap: 4px 12px; font-size: 11px; color: #8b90a5; }
    .afr-meta .v { color: #e4e6ef; font-weight: 500; }
    .afr-footer {
      margin-top: 6px; padding-top: 6px; border-top: 1px solid #2e3346;
      display: flex; justify-content: space-between; align-items: center; font-size: 11px;
    }
    .afr-time { color: #f39c12; font-weight: 600; font-size: 12px; }
    .afr-bets { color: #8b90a5; }
    .afr-prov {
      display: inline-block; padding: 1px 6px; border-radius: 3px;
      font-size: 10px; font-weight: 600; text-transform: uppercase;
    }
    .afr-prov.bcp { background: #1e3a5f; color: #5dade2; }
    .afr-prov.axa { background: #3e2723; color: #f39c12; }
    .afr-prov.w   { background: #2d3a2d; color: #7dcea0; }
    .afr-preview-btn {
      background: #242836; border: 1px solid #3a3f55; color: #8b90a5;
      padding: 2px 10px; border-radius: 4px; cursor: pointer; font-size: 11px;
      transition: all .15s; white-space: nowrap; text-decoration: none;
    }
    .afr-preview-btn:hover { border-color: #4f8cff; color: #4f8cff; background: #1a2a4a; }
    .afr-empty {
      grid-column: 1/-1; padding: 50px; text-align: center; color: #555; font-size: 14px;
    }
    .afr-loader {
      grid-column: 1/-1; padding: 60px; text-align: center; color: #8b90a5; font-size: 14px;
    }
    .afr-bottom-pager {
      grid-column: 1/-1; display: flex; align-items: center; justify-content: center;
      gap: 4px; padding: 16px 0; font-size: 11px;
    }

    /* ── Preview dialog ── */
    .afp-dialog-overlay {
      position: fixed; inset: 0; z-index: 10000000;
      background: rgba(0,0,0,.8); display: flex; align-items: center; justify-content: center;
      padding: 20px;
    }
    .afp-dialog {
      background: #1a1d27; border: 1px solid #2e3346; border-radius: 12px;
      max-width: 880px; width: 100%; max-height: 92vh; overflow-y: auto;
      position: relative; color: #e4e6ef; font-family: 'Segoe UI', system-ui, sans-serif;
    }
    .afp-dialog * { box-sizing: border-box; }
    .afp-dialog-close {
      position: absolute; top: 10px; right: 14px; z-index: 10;
      background: rgba(0,0,0,.6); border: none; color: #e4e6ef;
      font-size: 22px; width: 36px; height: 36px; border-radius: 50%;
      cursor: pointer; display: flex; align-items: center; justify-content: center;
      transition: background .2s;
    }
    .afp-dialog-close:hover { background: #e74c3c; }
    .afp-gallery-main {
      position: relative; background: #0f1117; display: flex; align-items: center;
      justify-content: center; min-height: 300px; max-height: 450px; overflow: hidden;
      border-radius: 12px 12px 0 0;
    }
    .afp-gallery-main img { width: 100%; height: 430px; object-fit: contain; display: block; }
    .afp-gallery-nav {
      position: absolute; top: 50%; transform: translateY(-50%);
      background: rgba(0,0,0,.55); border: none; color: #fff;
      font-size: 28px; width: 44px; height: 44px; border-radius: 50%;
      cursor: pointer; display: flex; align-items: center; justify-content: center;
      transition: background .2s;
    }
    .afp-gallery-nav:hover { background: rgba(79,140,255,.7); }
    .afp-gallery-nav.prev { left: 12px; }
    .afp-gallery-nav.next { right: 12px; }
    .afp-gallery-counter {
      text-align: center; padding: 6px; font-size: 12px; color: #8b90a5; background: #12141d;
    }
    .afp-gallery-thumbs {
      display: flex; gap: 4px; padding: 6px 12px; overflow-x: auto; background: #12141d;
    }
    .afp-gallery-thumbs img {
      width: 64px; height: 48px; object-fit: cover; border-radius: 4px;
      cursor: pointer; border: 2px solid transparent; opacity: .6;
      transition: all .15s; flex-shrink: 0;
    }
    .afp-gallery-thumbs img:hover { opacity: 1; }
    .afp-gallery-thumbs img.active { border-color: #4f8cff; opacity: 1; }
    .afp-gallery-empty {
      padding: 60px; text-align: center; color: #555; background: #0f1117;
      border-radius: 12px 12px 0 0;
    }
    .afp-dialog-body { padding: 16px 20px; }
    .afp-dialog-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: #e4e6ef; }
    .afp-dialog-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 20px; }
    .afp-detail-row {
      display: flex; justify-content: space-between; padding: 4px 0;
      border-bottom: 1px solid #242836; font-size: 13px;
    }
    .afp-detail-label { color: #8b90a5; }
    .afp-detail-value { color: #e4e6ef; font-weight: 500; text-align: right; }
    .afp-detail-value.price { color: #2ecc71; font-weight: 700; font-size: 15px; }
    .afp-detail-value.warn { color: #e74c3c; font-weight: 700; }
    .afp-dialog-desc {
      margin-top: 12px; padding: 10px 14px;
      border: 1px solid #2e3346; border-radius: 6px; font-size: 13px;
      color: #c8cad7; line-height: 1.6; white-space: pre-wrap; position: relative;
    }
    .afp-dialog-desc strong { color: #8b90a5; }
    .afp-translate-btn {
      background: #1e3a5f; border: 1px solid #2a5a9a; color: #7ab8ff;
      padding: 3px 10px; border-radius: 4px; cursor: pointer; font-size: 11px;
      margin-left: 8px; transition: all .15s; vertical-align: middle;
    }
    .afp-translate-btn:hover { background: #2a5a9a; color: #fff; }
    .afp-translate-btn:disabled { opacity: .5; cursor: wait; }
    .afp-dialog-actions { margin-top: 16px; display: flex; gap: 10px; justify-content: center; }
    .afp-dialog-open {
      display: inline-block; background: #4f8cff; color: #fff;
      text-decoration: none; padding: 10px 28px; border-radius: 6px;
      font-size: 14px; font-weight: 600; transition: background .2s;
    }
    .afp-dialog-open:hover { background: #3a6fd4; }
    .afp-dialog-loading, .afp-dialog-error {
      padding: 60px 20px; text-align: center; color: #8b90a5; font-size: 14px;
    }
    .afp-dialog-error { color: #e74c3c; }
    @media (max-width: 600px) {
      .afp-dialog-grid { grid-template-columns: 1fr; }
      .afp-gallery-main img { height: 260px; }
    }
  `;
  document.head.appendChild(style);

  // ── Panel HTML ───────────────────────────────────
  const panel = document.createElement("div");
  panel.id = "auta-filter-panel";
  panel.innerHTML = `
    <div class="afp-header">
      <h2>AUTA.CH <span>- zaawansowane filtry</span></h2>
      <span class="afp-status" id="afpStatus">Ladowanie...</span>
      <div class="afp-header-btns">
        <button class="afp-clear" id="afpClear">Wyczysc filtry</button>
        <button class="afp-close" id="afpClose">X Zamknij</button>
      </div>
    </div>
    <div class="afp-filters">
      <div class="afp-fg afp-search">
        <label>Szukaj (przecinek = LUB)</label>
        <input type="text" id="afpSearch" placeholder="np. skoda octavia, porsche cabrio">
      </div>
      <div class="afp-fg">
        <label>Rok</label>
        <div class="afp-range">
          <input type="number" id="afpYearFrom" placeholder="od" min="1970" max="2030">
          <span>-</span>
          <input type="number" id="afpYearTo" placeholder="do" min="1970" max="2030">
        </div>
      </div>
      <div class="afp-fg">
        <label>Przebieg max</label>
        <input type="number" id="afpKmMax" placeholder="km" style="width:90px">
      </div>
      <div class="afp-fg">
        <label>Typ</label>
        <select id="afpType">
          <option value="">Wszystkie</option>
          <option value="car">Samochody</option>
          <option value="motorcycle">Motocykle</option>
          <option value="truck">Ciezarowe</option>
          <option value="other">Inne</option>
        </select>
      </div>
      <div class="afp-fg">
        <label>Dostawca</label>
        <select id="afpProvider">
          <option value="">Wszyscy</option>
          <option value="bcp">BCP</option>
          <option value="axa">AXA</option>
        </select>
      </div>
      <div class="afp-fg">
        <label>Konczy za max (h)</label>
        <input type="number" id="afpMaxHours" placeholder="np. 24" style="width:72px" min="0" step="1">
      </div>
      <div class="afp-fg">
        <label>Max licytacji</label>
        <input type="number" id="afpMaxBets" placeholder="np. 5" style="width:60px" min="0">
      </div>
      <div class="afp-fg">
        <label>Max obserwujacych</label>
        <input type="number" id="afpMaxObservers" placeholder="np. 10" style="width:72px" min="0">
      </div>
      <div class="afp-help">
        <code>skoda octavia</code> = AND &nbsp;|&nbsp;
        <code>skoda, porsche</code> = OR &nbsp;|&nbsp;
        <code>skoda octavia, porsche cabrio</code> = (skoda AND octavia) OR (porsche AND cabrio)
      </div>
    </div>
    <div class="afp-brands" id="afpBrands"></div>
    <div class="afp-adv" id="afpAdv">
      <div class="afp-adv-bar">
        <button class="afp-adv-btn" id="afpEnrich">Rozszerz dane</button>
        <span class="afp-adv-status" id="afpAdvStatus"></span>
        <button class="afp-adv-toggle" id="afpAdvToggle" style="display:none">Pokaz/ukryj filtry</button>
      </div>
      <div class="afp-adv-body" id="afpAdvBody"></div>
    </div>
    <div class="afp-toolbar">
      <span>Sortuj:</span>
      <button class="afp-sbtn active" data-sort="endTime">Koniec &#8593;</button>
      <button class="afp-sbtn" data-sort="endTimeDesc">Koniec &#8595;</button>
      <button class="afp-sbtn" data-sort="yearDesc">Najnowsze</button>
      <button class="afp-sbtn" data-sort="yearAsc">Najstarsze</button>
      <button class="afp-sbtn" data-sort="kmAsc">Km &#8593;</button>
      <button class="afp-sbtn" data-sort="kmDesc">Km &#8595;</button>
      <button class="afp-sbtn" data-sort="betsDesc">Licytacje &#8595;</button>
      <button class="afp-sbtn" data-sort="observedDesc">Obserwowane &#8595;</button>
      <div class="afp-toolbar-sep"></div>
      <div class="afp-pager" id="afpPager"></div>
    </div>
  `;
  document.body.prepend(panel);

  const resultsDiv = document.createElement("div");
  resultsDiv.id = "auta-filter-results";
  resultsDiv.innerHTML = '<div class="afr-grid"><div class="afr-loader">Pobieram wszystkie aukcje z API...</div></div>';

  Array.from(document.body.children).forEach(el => {
    if (el.id !== "auta-filter-panel" && el.id !== "auta-filter-results" && el.id !== "auta-filter-style") {
      el.dataset.afpHidden = "1";
      el.style.setProperty("display", "none", "important");
    }
  });
  document.body.appendChild(resultsDiv);

  // ── Data fetching ────────────────────────────────
  const KNOWN_BRANDS = [
    "ALFA ROMEO","ASTON MARTIN","AUDI","BMW","CHEVROLET","CHRYSLER","CITROEN","CITROËN",
    "CUPRA","DACIA","DAEWOO","DAIHATSU","DS","FIAT","FORD","HONDA","HYUNDAI","INFINITI",
    "ISUZU","IVECO","JAGUAR","JEEP","KIA","KYMCO","LANCIA","LAND ROVER","LEXUS","MASERATI",
    "MAZDA","MERCEDES-BENZ","MERCEDES","MINI","MITSUBISHI","NISSAN","OPEL","PEUGEOT","PIAGGIO",
    "PORSCHE","RENAULT","ROLLS-ROYCE","SAAB","SEAT","SKODA","SMART","SSANGYONG","SUBARU",
    "SUZUKI","TESLA","TOYOTA","TRIUMPH","VESPA","VOLVO","VW","VOLKSWAGEN","WOTTAN","YAMAHA"
  ];

  function parseBrand(title) {
    if (!title) return "?";
    const t = title.toUpperCase();
    for (const b of KNOWN_BRANDS) { if (t.startsWith(b + " ") || t === b) return b; }
    return title.split(/[\s,]+/)[0].toUpperCase();
  }

  function enrichAuction(a) {
    a._brand = parseBrand(a.title);
    a._year = a.production_date ? parseInt(a.production_date.substring(0, 4)) : 0;
    a._titleLower = (a.title || "").toLowerCase();
    a._endDate = new Date(a.end_date);
    a._km = a.run || 0;
  }

  async function fetchAll() {
    const status = document.getElementById("afpStatus");
    let page = 1, all = [], totalPages = 1;
    while (page <= totalPages) {
      status.innerHTML = `Strona ${page}/${totalPages}...`;
      try {
        const r = await fetch(
          `${API}?page=${page}&recommended=false&popular=false&recent=false&size=1000&sort=asc`,
          { headers: { Accept: "application/json" } }
        );
        const d = await r.json();
        totalPages = d.total_pages || 1;
        all = all.concat(d.auctions || []);
        status.innerHTML = `<b>${all.length}</b> / ${d.total_items || "?"}`;
        page++;
      } catch (e) { status.textContent = "Blad: " + e.message; break; }
    }
    return all;
  }

  // ── Brand chips ──────────────────────────────────
  let chipElements = {};

  function buildBrandChips() {
    const counts = {};
    for (const a of allAuctions) counts[a._brand] = (counts[a._brand] || 0) + 1;
    const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    const container = document.getElementById("afpBrands");
    container.innerHTML = "";
    chipElements = {};
    for (const [brand] of sorted) {
      const chip = document.createElement("span");
      chip.className = "afp-chip";
      chip.innerHTML = `${brand} <span class="cnt">0</span>`;
      chip.onclick = () => {
        if (activeBrands.has(brand)) { activeBrands.delete(brand); chip.classList.remove("active"); }
        else { activeBrands.add(brand); chip.classList.add("active"); }
        applyFilters();
      };
      container.appendChild(chip);
      chipElements[brand] = chip;
    }
    updateBrandChipCounts();
  }

  function updateBrandChipCounts() {
    const noBrandFiltered = getFilteredAuctions(true, true);
    const counts = {};
    for (const a of noBrandFiltered) counts[a._brand] = (counts[a._brand] || 0) + 1;
    for (const [brand, chip] of Object.entries(chipElements)) {
      const cnt = counts[brand] || 0;
      chip.querySelector(".cnt").textContent = cnt;
      chip.classList.toggle("zero", cnt === 0 && !activeBrands.has(brand));
    }
  }

  // ── Filtering ────────────────────────────────────
  function matchesSearch(titleLower, query) {
    if (!query) return true;
    const groups = query.split(",").map(g => g.trim().toLowerCase()).filter(Boolean);
    if (!groups.length) return true;
    return groups.some(group => {
      const words = group.split(/\s+/).filter(Boolean);
      return words.every(w => titleLower.includes(w));
    });
  }

  function getFilteredAuctions(skipBrandFilter, skipAdvancedFilter) {
    const query = document.getElementById("afpSearch").value;
    const yearFrom = parseInt(document.getElementById("afpYearFrom").value) || 0;
    const yearTo = parseInt(document.getElementById("afpYearTo").value) || 9999;
    const kmMax = parseInt(document.getElementById("afpKmMax").value) || Infinity;
    const type = document.getElementById("afpType").value;
    const provider = document.getElementById("afpProvider").value;
    const maxBetsRaw = document.getElementById("afpMaxBets").value;
    const maxBets = maxBetsRaw !== "" ? parseInt(maxBetsRaw) : null;
    const maxHours = parseFloat(document.getElementById("afpMaxHours").value) || 0;
    const maxObsRaw = document.getElementById("afpMaxObservers").value;
    const maxObservers = maxObsRaw !== "" ? parseInt(maxObsRaw) : null;
    const now = Date.now();
    const checkAdv = !skipAdvancedFilter && hasActiveAdvanced();

    return allAuctions.filter(a => {
      if (!matchesSearch(a._titleLower, query)) return false;
      if (!skipBrandFilter && activeBrands.size > 0 && !activeBrands.has(a._brand)) return false;
      if (a._year < yearFrom || a._year > yearTo) return false;
      if (a._km > kmMax) return false;
      if (type && a.vehicle_type !== type) return false;
      if (provider && a.provider_name !== provider) return false;
      if (maxBets !== null && !isNaN(maxBets) && (a.bet_count || 0) > maxBets) return false;
      if (maxHours > 0) {
        const hoursLeft = (a._endDate - now) / 36e5;
        if (hoursLeft < 0 || hoursLeft > maxHours) return false;
      }
      if (maxObservers !== null && !isNaN(maxObservers) && (a.observed_count || 0) > maxObservers) return false;
      if (checkAdv) {
        if (!enrichedData[a.id]) return false;
        if (!matchesAdvanced(a.id)) return false;
      }
      return true;
    });
  }

  function clearFilters() {
    document.getElementById("afpSearch").value = "";
    document.getElementById("afpYearFrom").value = "";
    document.getElementById("afpYearTo").value = "";
    document.getElementById("afpKmMax").value = "";
    document.getElementById("afpType").value = "";
    document.getElementById("afpProvider").value = "";
    document.getElementById("afpMaxBets").value = "";
    document.getElementById("afpMaxHours").value = "";
    document.getElementById("afpMaxObservers").value = "";
    activeBrands.clear();
    Object.values(chipElements).forEach(c => c.classList.remove("active"));
    advancedFilters = {};
    applyFilters();
    if (advancedPanelOpen) rebuildAdvancedPanel();
  }

  function applyFilters() {
    filteredAuctions = sortAuctions(getFilteredAuctions(false, false), currentSort);
    currentPage = 1;
    updateBrandChipCounts();
    updateEnrichButton();
    if (advancedPanelOpen) rebuildAdvancedPanel();
    document.getElementById("afpStatus").innerHTML =
      `Wyniki: <b>${filteredAuctions.length}</b> / ${allAuctions.length}`;
    renderResults();
    renderPagination();
  }

  function sortAuctions(arr, mode) {
    const c = [...arr];
    switch (mode) {
      case "endTime":      return c.sort((a, b) => a._endDate - b._endDate);
      case "endTimeDesc":  return c.sort((a, b) => b._endDate - a._endDate);
      case "yearDesc":     return c.sort((a, b) => b._year - a._year);
      case "yearAsc":      return c.sort((a, b) => a._year - b._year);
      case "kmAsc":        return c.sort((a, b) => a._km - b._km);
      case "kmDesc":       return c.sort((a, b) => b._km - a._km);
      case "betsDesc":     return c.sort((a, b) => (b.bet_count||0) - (a.bet_count||0));
      case "observedDesc": return c.sort((a, b) => (b.observed_count||0) - (a.observed_count||0));
      default: return c;
    }
  }

  // ── Enriched data / Advanced filters ────────────
  function updateEnrichButton() {
    const basicFiltered = getFilteredAuctions(false, true);
    const uncached = basicFiltered.filter(a => !enrichedData[a.id]).length;
    const btn = document.getElementById("afpEnrich");
    const total = basicFiltered.length;
    const cached = total - uncached;
    if (uncached > 0) {
      btn.textContent = `Rozszerz dane (pobierz ${uncached} z ${total})`;
      btn.disabled = false;
    } else if (total > 0 && cached === total) {
      btn.textContent = `Dane pobrane (${total})`;
      btn.disabled = true;
    } else {
      btn.textContent = "Rozszerz dane";
      btn.disabled = total === 0;
    }
  }

  async function fetchEnrichedBatch() {
    const basicFiltered = getFilteredAuctions(false, true);
    const toFetch = basicFiltered.filter(a => !enrichedData[a.id]);
    if (!toFetch.length) {
      advancedPanelOpen = true;
      rebuildAdvancedPanel();
      document.getElementById("afpAdvBody").classList.add("open");
      document.getElementById("afpAdvToggle").style.display = "";
      return;
    }

    const btn = document.getElementById("afpEnrich");
    const statusEl = document.getElementById("afpAdvStatus");
    btn.disabled = true;
    let done = 0;
    const total = toFetch.length;
    let delay = 300;

    statusEl.innerHTML = `Pobieranie: <b>0</b>/${total}<div class="afp-adv-progress"><div class="afp-adv-progress-bar" style="width:0%"></div></div>`;
    const updateProgress = (extra) => {
      const pct = Math.round((done / total) * 100);
      statusEl.innerHTML = `Pobieranie: <b>${done}</b>/${total}${extra || ""}<div class="afp-adv-progress"><div class="afp-adv-progress-bar" style="width:${pct}%"></div></div>`;
    };

    async function fetchOne(a, retries) {
      try {
        const r = await fetch(`${API}${a.id}`, { headers: { Accept: "application/json" } });
        if (r.status === 429) {
          const wait = Math.min(delay * Math.pow(2, 3 - retries), 15000);
          updateProgress(` | 429 - czekam ${Math.round(wait / 1000)}s...`);
          await new Promise(ok => setTimeout(ok, wait));
          if (retries > 0) return fetchOne(a, retries - 1);
          enrichedData[a.id] = { data: {} };
          return;
        }
        const json = await r.json();
        enrichedData[a.id] = json.auction || json;
      } catch { enrichedData[a.id] = { data: {} }; }
    }

    const CONCURRENT = 3;
    for (let i = 0; i < total; i += CONCURRENT) {
      const batch = toFetch.slice(i, i + CONCURRENT);
      await Promise.all(batch.map(async a => {
        await fetchOne(a, 3);
        done++;
        updateProgress();
      }));
      if (i + CONCURRENT < total) await new Promise(ok => setTimeout(ok, delay));
    }

    statusEl.innerHTML = `Pobrano <b>${total}</b> aukcji`;
    advancedPanelOpen = true;
    updateEnrichButton();
    rebuildAdvancedPanel();
    document.getElementById("afpAdvBody").classList.add("open");
    document.getElementById("afpAdvToggle").style.display = "";
    applyFilters();
  }

  function rebuildAdvancedPanel() {
    const body = document.getElementById("afpAdvBody");
    const basicFiltered = getFilteredAuctions(false, true);
    const enrichedSet = basicFiltered.filter(a => enrichedData[a.id]);

    let html = "";
    for (const f of ADV_FIELDS) {
      const counts = {};
      for (const a of enrichedSet) {
        const vals = getAdvValue(enrichedData[a.id], f);
        for (const v of vals) counts[v] = (counts[v] || 0) + 1;
      }
      const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);
      if (!entries.length) continue;

      const selected = advancedFilters[f.key] || new Set();
      html += `<div class="afp-adv-group" data-field="${f.key}">`;
      html += `<span class="afp-adv-label">${f.label}:</span>`;
      for (const [val, cnt] of entries) {
        const isActive = selected.has(val);
        const label = translateVal(f, val);
        html += `<span class="afp-adv-chip${isActive ? " active" : ""}${cnt === 0 && !isActive ? " zero" : ""}" data-field="${f.key}" data-val="${val.replace(/"/g, "&quot;")}">${label}<span class="acnt">${cnt}</span></span>`;
      }
      html += `</div>`;
    }

    if (!html) {
      body.innerHTML = '<div style="padding:6px;color:#555;font-size:12px">Brak danych do filtrow - kliknij Rozszerz</div>';
      return;
    }
    body.innerHTML = html;

    body.querySelectorAll(".afp-adv-chip").forEach(chip => {
      chip.onclick = () => {
        const key = chip.dataset.field;
        const val = chip.dataset.val;
        if (!advancedFilters[key]) advancedFilters[key] = new Set();
        if (advancedFilters[key].has(val)) {
          advancedFilters[key].delete(val);
          chip.classList.remove("active");
        } else {
          advancedFilters[key].add(val);
          chip.classList.add("active");
        }
        applyFilters();
      };
    });
  }

  // ── Pagination ──────────────────────────────────
  function renderPagination() {
    const totalPages = Math.ceil(filteredAuctions.length / pageSize) || 1;
    renderPagerInto(document.getElementById("afpPager"), totalPages);
    const bottomPager = resultsDiv.querySelector(".afr-bottom-pager");
    if (bottomPager) renderPagerInto(bottomPager, totalPages);
  }

  function renderPagerInto(container, totalPages) {
    if (!container) return;
    if (filteredAuctions.length <= pageSize) {
      container.innerHTML = `<span class="afp-page-info">${filteredAuctions.length} wynikow</span>`;
      return;
    }
    let html = `<span class="afp-page-info">${filteredAuctions.length} | str. ${currentPage}/${totalPages}</span>`;
    if (currentPage > 1)
      html += `<button class="afp-page-btn" data-page="${currentPage - 1}">&laquo;</button>`;
    const maxBtns = 5;
    let start = Math.max(1, currentPage - Math.floor(maxBtns / 2));
    let end = Math.min(totalPages, start + maxBtns - 1);
    start = Math.max(1, end - maxBtns + 1);
    if (start > 1) {
      html += `<button class="afp-page-btn" data-page="1">1</button>`;
      if (start > 2) html += `<span class="afp-page-dots">..</span>`;
    }
    for (let i = start; i <= end; i++)
      html += `<button class="afp-page-btn${i === currentPage ? ' active' : ''}" data-page="${i}">${i}</button>`;
    if (end < totalPages) {
      if (end < totalPages - 1) html += `<span class="afp-page-dots">..</span>`;
      html += `<button class="afp-page-btn" data-page="${totalPages}">${totalPages}</button>`;
    }
    if (currentPage < totalPages)
      html += `<button class="afp-page-btn" data-page="${currentPage + 1}">&raquo;</button>`;
    html += `<select class="afp-page-size">${
      [25, 50, 100, 200, 300].map(s => `<option value="${s}"${s === pageSize ? " selected" : ""}>${s}/str</option>`).join("")
    }</select>`;
    container.innerHTML = html;
    container.querySelectorAll(".afp-page-btn").forEach(btn => {
      btn.onclick = () => {
        currentPage = parseInt(btn.dataset.page);
        renderResults(); renderPagination();
        resultsDiv.scrollIntoView({ behavior: "smooth" });
      };
    });
    const sel = container.querySelector(".afp-page-size");
    if (sel) sel.onchange = (e) => { pageSize = parseInt(e.target.value); currentPage = 1; renderResults(); renderPagination(); };
  }

  // ── Rendering ────────────────────────────────────
  function timeRemaining(endDate) {
    const diff = endDate - Date.now();
    if (diff <= 0) return "Zakonczona";
    const d = Math.floor(diff / 864e5), h = Math.floor((diff % 864e5) / 36e5), m = Math.floor((diff % 36e5) / 6e4);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }
  function fmtKm(km) { return km ? km.toLocaleString("pl-PL") + " km" : "-"; }
  function fmtPrice(v) { if (!v && v !== 0) return ""; return Number(v).toLocaleString("pl-PL") + " CHF"; }
  function imgUrl(path) {
    if (!path) return "";
    if (path.startsWith("http")) return path;
    return `https://auta.ch/${path.replace(/^\//, "")}`;
  }

  function renderResults() {
    if (!resultsDiv.querySelector(".afr-grid")) resultsDiv.innerHTML = '<div class="afr-grid"></div>';
    const g = resultsDiv.querySelector(".afr-grid");
    if (!filteredAuctions.length) { g.innerHTML = '<div class="afr-empty">Brak wynikow - zmien kryteria</div>'; return; }
    const startIdx = (currentPage - 1) * pageSize;
    const show = filteredAuctions.slice(startIdx, startIdx + pageSize);
    g.innerHTML = show.map(a => {
      const img = a.min_image ? imgUrl(a.min_image) : "";
      const prov = a.provider_name || "bcp";
      const url = `https://auta.ch/auctions/${a.id}`;
      return `
        <div class="afr-card" data-id="${a.id}" data-url="${url}">
          <div class="afr-img-wrap">
            ${img ? `<img src="${img}" alt="" loading="lazy" onerror="this.style.display='none'">` : ""}
          </div>
          <div class="afr-card-body">
            <div class="afr-title" title="${(a.title || "").replace(/"/g, '&quot;')}">${a.title || "?"}</div>
            <div class="afr-meta">
              <span>Rok: <span class="v">${a._year || "?"}</span></span>
              <span>Przebieg: <span class="v">${fmtKm(a._km)}</span></span>
              <span><span class="afr-prov ${prov}">${prov.toUpperCase()}</span></span>
            </div>
            <div class="afr-footer">
              <span class="afr-time">${timeRemaining(a._endDate)}</span>
              <span class="afr-bets">${a.bet_count||0} lic. / ${a.observed_count||0} obs.</span>
              <a class="afr-preview-btn" href="${url}" target="_blank" data-link>Otworz <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 1H1v12h12V9"/><path d="M8 1h5v5"/><path d="M13 1L6 8"/></svg></a>
            </div>
          </div>
        </div>`;
    }).join("");
    const totalPages = Math.ceil(filteredAuctions.length / pageSize) || 1;
    if (totalPages > 1) {
      g.innerHTML += '<div class="afr-bottom-pager"></div>';
      renderPagerInto(g.querySelector(".afr-bottom-pager"), totalPages);
    }
  }

  // ── Preview dialog ──────────────────────────────
  async function openPreview(auctionId) {
    const auction = allAuctions.find(a => a.id === auctionId || a.id === parseInt(auctionId));
    if (!auction) return;
    document.getElementById("auta-preview-dialog")?.remove();

    const overlay = document.createElement("div");
    overlay.className = "afp-dialog-overlay";
    overlay.id = "auta-preview-dialog";
    overlay.innerHTML = `<div class="afp-dialog"><button class="afp-dialog-close">&times;</button><div class="afp-dialog-loading">Ladowanie szczegolow...</div></div>`;
    document.body.appendChild(overlay);

    const closeDialog = () => { overlay.remove(); document.removeEventListener("keydown", onKey); };
    overlay.querySelector(".afp-dialog-close").onclick = closeDialog;
    overlay.onclick = (e) => { if (e.target === overlay) closeDialog(); };
    const onKey = (e) => { if (e.key === "Escape") closeDialog(); };
    document.addEventListener("keydown", onKey);

    let detail, photos = [], aData = {};
    if (enrichedData[auctionId]) {
      detail = enrichedData[auctionId];
      photos = detail.photos || [];
      aData = detail.data || {};
    } else {
      try {
        const r = await fetch(`${API}${auctionId}`, { headers: { Accept: "application/json" } });
        const json = await r.json();
        detail = json.auction || json;
        photos = detail.photos || [];
        aData = detail.data || {};
        enrichedData[auctionId] = detail;
      } catch { detail = auction; photos = auction.min_image ? [auction.min_image] : []; }
    }

    if (!photos.length && auction.min_image) photos = [auction.min_image];
    let imgIdx = 0;

    const specs = aData["Technische Spezifikationen"] || {};
    const info = aData["Fahrzeuginformationen"] || {};
    const extra = aData["Zusätzliche Informationen"] || {};
    const cond = aData["Aussehen und Zustand"] || {};
    const tireInfo = aData["Reifeninformationen"] || [];
    const techProblems = aData["Technische Probleme"] || [];
    const endDate = new Date(detail.end_date || auction.end_date);
    const stepDiff = detail.offer_step_diff || 0;

    function val(v) { if (v === null || v === undefined || v === "" || v === "Brak danych") return ""; return v; }
    function yesNo(v) { if (v === "Ja") return "TAK"; if (v === "Nein") return "Nie"; return val(v); }
    function row(label, value) { if (!value && value !== 0) return ""; return `<div class="afp-detail-row"><span class="afp-detail-label">${label}</span><span class="afp-detail-value">${value}</span></div>`; }
    function rowWarn(label, value) { if (!value) return ""; return `<div class="afp-detail-row"><span class="afp-detail-label">${label}</span><span class="afp-detail-value warn">${value}</span></div>`; }

    const dialog = overlay.querySelector(".afp-dialog");
    dialog.innerHTML = `
      <button class="afp-dialog-close">&times;</button>
      ${photos.length > 0 ? `
        <div class="afp-gallery-main">
          <button class="afp-gallery-nav prev">&#8249;</button>
          <img src="${imgUrl(photos[0])}" alt="">
          <button class="afp-gallery-nav next">&#8250;</button>
        </div>
        <div class="afp-gallery-counter">1 / ${photos.length}</div>
        ${photos.length > 1 ? `<div class="afp-gallery-thumbs">${
          photos.map((im, i) => `<img src="${imgUrl(im)}" data-idx="${i}" class="${i === 0 ? 'active' : ''}" loading="lazy">`).join("")
        }</div>` : ""}
      ` : '<div class="afp-gallery-empty">Brak zdjec</div>'}
      <div class="afp-dialog-body">
        <h3 class="afp-dialog-title">${detail.title || auction.title || "?"}</h3>
        <div class="afp-dialog-grid">
          ${row("Konczy sie za", timeRemaining(endDate))}
          ${row("Licytacji", detail.bet_count ?? auction.bet_count ?? 0)}
          ${row("Obserwujacych", detail.observed_count ?? auction.observed_count ?? 0)}
          ${row("Skok licytacji", stepDiff ? fmtPrice(stepDiff) : "")}
          ${row("Nr referencyjny", val(detail.ref_id))}
          ${row("Marka", val(info["Marke"] || detail.brand_name))}
          ${row("Model", val(info["Modell"]))}
          ${row("Pierwsza rejestracja", val(info["Erstregistrierung"]))}
          ${row("Wiek pojazdu", val(info["Fahrzeugalter"]))}
          ${row("Przebieg", fmtKm(detail.run || auction._km))}
          ${row("Paliwo", val(specs["Kraftstoffart"]))}
          ${row("Skrzynia", specs["Automatik"] === "Ja" ? "Automatyczna" : (specs["Automatik"] === "Nein" ? "Manualna" : val(specs["Getriebetyp"])))}
          ${row("Naped", val(specs["Antriebsart"]))}
          ${row("Moc", val(specs["Motorleistung"]))}
          ${row("Emisja", val(specs["Emissionscode"]))}
          ${row("Miejsca", val(specs["Sitze"]))}
          ${row("Auto CH", yesNo(info["Schweizer Auto"]))}
          ${row("Dostepny od", val(info["Verfügbar ab"]))}
          ${row("Tablice", val(info["Kontrollschilder"]))}
          ${row("Dostawca", (detail.provider_name || auction.provider_name || "").toUpperCase())}
          ${row("VIN", val(extra["Fahrgestellnummer"]))}
          ${row("Stammnummer", val(extra["Stammnummer"]))}
          ${row("Opony", val(tireInfo.length ? tireInfo.join(", ") : ""))}
          ${row("Kolor nadwozia", val(cond["Karosseriefarbe"]))}
          ${row("Kolor wnetrza", val(cond["Innenraumfarbe"]))}
          ${row("Klucze", val(cond["Schlüssel"]))}
          ${row("Ostatnie MFK", val(cond["Letzte MFK"]))}
          ${rowWarn("Grad", yesNo(cond["Hagelschaden"]) === "TAK" ? "TAK" : "")}
          ${rowWarn("Rdza", yesNo(cond["Rost vorhanden"]) === "TAK" ? "TAK" : "")}
          ${rowWarn("Kontrolki aktywne", yesNo(cond["Kontrollleuchten an"]) === "TAK" ? "TAK" : "")}
          ${row("Idealny stan", yesNo(cond["Perfekter Zustand"]))}
          ${row("Zdjec", detail.images_count || photos.length || "")}
        </div>
        ${techProblems.length ? `<div class="afp-dialog-desc" style="border-color:#5a2020;color:#e88"><strong>Problemy techniczne:</strong> <button class="afp-translate-btn" data-translate>Przetlumacz</button><span class="afp-desc-text">${techProblems.join(", ")}</span></div>` : ""}
        ${val(extra["Beschreibung"]) ? `<div class="afp-dialog-desc"><strong>Opis:</strong> <button class="afp-translate-btn" data-translate>Przetlumacz</button><span class="afp-desc-text">\n${extra["Beschreibung"]}</span></div>` : ""}
        <div class="afp-dialog-actions">
          <a href="https://auta.ch/auctions/${auctionId}" target="_blank" class="afp-dialog-open">Otworz <svg width="16" height="16" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-left:4px"><path d="M5 1H1v12h12V9"/><path d="M8 1h5v5"/><path d="M13 1L6 8"/></svg></a>
        </div>
      </div>`;

    dialog.querySelector(".afp-dialog-close").onclick = closeDialog;

    dialog.querySelectorAll(".afp-translate-btn[data-translate]").forEach(btn => {
      btn.onclick = async () => {
        const textEl = btn.parentElement.querySelector(".afp-desc-text");
        if (!textEl) return;
        const original = textEl.textContent.trim();
        if (!original) return;
        btn.disabled = true; btn.textContent = "Tlumacze...";
        try {
          const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=de&tl=pl&dt=t&q=${encodeURIComponent(original)}`;
          const r = await fetch(url);
          const data = await r.json();
          const translated = data[0].map(s => s[0]).join("");
          textEl.textContent = "\n" + translated;
          btn.textContent = "Pokaz oryginal";
          btn.onclick = () => {
            if (btn.textContent === "Pokaz oryginal") { textEl.textContent = "\n" + original; btn.textContent = "Przetlumacz"; }
            else { textEl.textContent = "\n" + translated; btn.textContent = "Pokaz oryginal"; }
          };
        } catch {
          window.open(`https://translate.google.com/?sl=de&tl=pl&text=${encodeURIComponent(original)}`, "_blank");
          btn.textContent = "Przetlumacz"; btn.disabled = false;
        }
      };
    });

    if (photos.length > 1) {
      const mainImg = dialog.querySelector(".afp-gallery-main img");
      const counter = dialog.querySelector(".afp-gallery-counter");
      const thumbs = dialog.querySelectorAll(".afp-gallery-thumbs img");
      const goTo = (idx) => {
        imgIdx = idx;
        mainImg.src = imgUrl(photos[imgIdx]);
        counter.textContent = `${imgIdx + 1} / ${photos.length}`;
        thumbs.forEach((t, i) => t.classList.toggle("active", i === imgIdx));
        const at = thumbs[imgIdx];
        if (at) at.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
      };
      dialog.querySelector(".afp-gallery-nav.prev").onclick = () => goTo((imgIdx - 1 + photos.length) % photos.length);
      dialog.querySelector(".afp-gallery-nav.next").onclick = () => goTo((imgIdx + 1) % photos.length);
      thumbs.forEach(t => { t.onclick = () => goTo(parseInt(t.dataset.idx)); });
      const onArrow = (e) => {
        if (!document.getElementById("auta-preview-dialog")) { document.removeEventListener("keydown", onArrow); return; }
        if (e.key === "ArrowLeft") goTo((imgIdx - 1 + photos.length) % photos.length);
        if (e.key === "ArrowRight") goTo((imgIdx + 1) % photos.length);
      };
      document.addEventListener("keydown", onArrow);
    }
  }

  // ── Events ───────────────────────────────────────
  document.getElementById("afpClose").onclick = () => {
    panel.remove(); resultsDiv.remove(); style.remove();
    document.getElementById("auta-preview-dialog")?.remove();
    document.querySelectorAll("[data-afp-hidden]").forEach(el => {
      el.style.removeProperty("display"); delete el.dataset.afpHidden;
    });
  };

  document.getElementById("afpClear").onclick = clearFilters;
  document.getElementById("afpEnrich").onclick = fetchEnrichedBatch;
  document.getElementById("afpAdvToggle").onclick = () => {
    const body = document.getElementById("afpAdvBody");
    body.classList.toggle("open");
    advancedPanelOpen = body.classList.contains("open");
  };

  panel.querySelectorAll(".afp-sbtn").forEach(btn => {
    btn.onclick = () => {
      panel.querySelectorAll(".afp-sbtn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      currentSort = btn.dataset.sort;
      applyFilters();
    };
  });

  let debounce;
  panel.querySelectorAll("input, select").forEach(el => {
    el.addEventListener("input", () => { clearTimeout(debounce); debounce = setTimeout(applyFilters, 200); });
    el.addEventListener("change", () => { clearTimeout(debounce); debounce = setTimeout(applyFilters, 150); });
  });

  resultsDiv.addEventListener("click", (e) => {
    const link = e.target.closest(".afr-preview-btn[data-link]");
    if (link) { e.stopPropagation(); return; }
    const card = e.target.closest(".afr-card");
    if (card && card.dataset.id) { e.preventDefault(); openPreview(card.dataset.id); }
  });

  document.getElementById("afpSearch").focus();

  // ── Init ─────────────────────────────────────────
  fetchAll().then(data => {
    allAuctions = data;
    allAuctions.forEach(enrichAuction);
    buildBrandChips();
    applyFilters();
  });
})();
