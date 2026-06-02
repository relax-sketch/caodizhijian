(function () {
  const app = document.getElementById("app");
  let state = {};
  let ignoreDraft = null;

  function call(action, payload = {}) {
    if (window.QualityBridge) {
      window.QualityBridge.perform(action, JSON.stringify(payload));
    }
  }

  function esc(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function icon(name, filled = false) {
    const names = {
      menu: "menu",
      back: "arrow_back",
      account: "account_circle",
      folder: "folder_open",
      upload: "drive_folder_upload",
      refresh: "refresh",
      check: "check_circle",
      database: "data_check",
      storage: "database",
      warning: "warning",
      plant: "potted_plant",
      fact: "fact_check",
      chart: "analytics",
      settings: "settings",
      error: "error",
      hidden: "visibility_off",
      arrow: "arrow_forward",
      close: "close",
      filter: "filter_list",
      restore: "restore",
      expand: "expand_more",
      chevron: "chevron_right",
      skip: "skip_next",
    };
    return `<span class="material-symbols-outlined" aria-hidden="true" style="font-variation-settings:'FILL' ${filled ? 1 : 0}, 'wght' 400, 'GRAD' 0, 'opsz' 24">${names[name] || "check_circle"}</span>`;
  }

  function shell(title, body, options = {}) {
    const back = options.back ? icon("back") : icon("menu");
    const topbar = options.noTopbar ? "" : `
      <header class="topbar">
        <button class="icon-button" data-action="${options.back ? "back" : "noop"}">${back}</button>
        <h1>${esc(title)}</h1>
        <button class="icon-button" data-action="noop">${icon("account")}</button>
      </header>`;
    return `
      <div class="app-shell screen-${options.screen || "source"}">
        ${topbar}
        ${body}
        ${options.action || ""}
        ${options.dock ? dock(options.dock) : ""}
        ${ignoreDialog()}
      </div>`;
  }

  function ignoreDialog() {
    if (!ignoreDraft) return "";
    return `
      <div class="modal-backdrop" role="presentation">
        <section class="glass-card liquid-glass-card ignore-dialog" role="dialog" aria-modal="true" aria-labelledby="ignoreReasonTitle">
          <h2 id="ignoreReasonTitle" class="title">填写忽略理由</h2>
          <p class="small">忽略前必须记录原因，后续可在已忽略卡片中查看。</p>
          <textarea class="field ignore-reason-field" data-input="ignoreReason" maxlength="240" placeholder="请输入忽略理由">${esc(ignoreDraft.reason || "")}</textarea>
          ${ignoreDraft.error ? `<p class="ignore-error">${esc(ignoreDraft.error)}</p>` : ""}
          <div class="button-row ignore-dialog-actions">
            <button class="button secondary" data-ignore-cancel="true">取消</button>
            <button class="button primary" data-ignore-confirm="true">确认忽略</button>
          </div>
        </section>
      </div>`;
  }

  function dock(active) {
    return `
      <nav class="dock">
        ${dockButton("data", "Data", icon("storage", active === "data"), active, "showSource")}
        ${dockButton("plot", "Plot", icon("plant", active === "plot"), active, "showScope")}
        ${dockButton("status", "Status", icon("chart", active === "status"), active, "showSummary", !(state.scope || {}).checkAllMode)}
        ${dockButton("settings", "Settings", icon("settings", active === "settings"), active, "showSettings")}
      </nav>`;
  }

  function dockButton(key, label, symbol, active, action, disabled = false) {
    return `<button class="${active === key ? "active" : ""}" data-action="${action}" ${disabled ? "disabled" : ""}>
      <span class="dock-icon">${symbol}</span><span>${label}</span>
    </button>`;
  }

  function sourceScreen() {
    const scan = state.scan || {};
    const directory = state.directory;
    return shell("草地监测", `
      <main class="page stack">
        <section class="hero stack">
          <h2 class="source-hero-title">质量控制检查</h2>
          <p>配置本地工作空间，开始野外监测数据质检。</p>
        </section>
        ${error()}
        <section class="glass-card liquid-glass-card card-pad-lg stack">
          <div class="row">
            <div class="icon-tile">${icon("folder", true)}</div>
            <div>
              <h3 class="title">数据目录选择</h3>
              <p class="small">${esc(directory ? directory.name : "选择本地 .zdb 数据目录")}</p>
            </div>
          </div>
          <div class="button-row source-button-row">
            <button class="button primary" data-action="pickDirectory">${icon("upload")} 选择目录</button>
            <button class="button secondary" data-action="showScope" ${(scan.indexedCount || 0) > 0 ? "" : "disabled"}>选择范围 ${icon("arrow")}</button>
          </div>
        </section>
        <section class="glass-card liquid-glass-card card-pad-lg stack">
          <div class="row space-between">
            <h3 class="title">${icon("check", true)} 扫描状态</h3>
            <span class="chip">${state.isScanning ? "扫描中" : "就绪"}</span>
          </div>
          <div class="scan-grid">
            ${metric("已扫描", scan.validCount || 0, "ZDB 文件")}
            ${metric("已索引", scan.indexedCount || 0, "样地")}
          </div>
          ${metric("异常", scan.rejectedCount || 0, "条")}
        </section>
        <section class="stat-grid">
          ${miniStat(icon("folder", true), "目录总数", scan.validCount || 0, "个")}
          ${miniStat(icon("database", true), "索引样地", scan.indexedCount || 0, "个")}
          ${miniStat(icon("warning"), "异常历史", scan.rejectedCount || 0, "条")}
        </section>
        <p class="muted-note">扫描和质检均以只读方式读取 ZDB，不会修改原始数据。</p>
      </main>`,
      {
        screen: "source",
        dock: "data"
      });
  }

  function metric(label, value, unit) {
    return `<div class="metric-box"><p class="small">${esc(label)}</p><span class="metric-value">${esc(value)}</span> <span class="label outline">${esc(unit)}</span></div>`;
  }

  function miniStat(symbol, label, value, unit) {
    return `<article class="glass-card liquid-glass-card mini-stat"><span class="icon-tile">${symbol}</span><span class="title mini-stat-label outline">${esc(label)}</span><span class="mini-stat-count"><strong>${esc(value)}</strong><span>${esc(unit)}</span></span></article>`;
  }

  function scopeScreen() {
    const scope = state.scope || {};
    return shell("草地监测", `
      <main class="page compact-top stack">
        <p class="body-text">配置质检参数和目标样地。</p>
        ${error()}
        <section class="glass-card liquid-glass-card card-pad stack">
          <input class="field" value="${esc(scope.plotQuery || "")}" placeholder="搜索样地编号..." data-input="query" ${scope.checkAllMode ? "disabled" : ""}>
          <select class="field" data-input="county" ${scope.checkAllMode ? "disabled" : ""}>
            <option value="">全部区县</option>
            ${(scope.countyOptions || []).map(c => `<option value="${esc(c)}" ${scope.selectedCounty === c ? "selected" : ""}>${esc(c)}</option>`).join("")}
          </select>
        </section>
        <div class="list-title label"><span>可用样地</span><span>${scope.checkAllMode ? `全量 ${scope.indexedCount || 0} 个` : `匹配 ${scope.filteredCount || 0} 个`}</span></div>
        <section class="stack">
          ${(scope.plots || []).map(plotCard).join("") || `<div class="glass-card empty">当前筛选条件下没有匹配样地。</div>`}
        </section>
      </main>`,
      {
        screen: "scope",
        dock: "plot",
        action: `<div class="fixed-action with-dock"><button class="button primary pill" data-action="startCheck" ${scope.checkAllMode || scope.selectedPlotKey ? "" : "disabled"}>开始质检 ${icon("arrow")}</button></div>`
      });
  }

  function switchRow(title, subtitle, kind, checked) {
    return `<button class="switch-row liquid-toggle ${checked ? "selected" : ""}" data-toggle="${kind}">
      <span><strong class="title">${esc(title)}</strong><br><span class="small">${esc(subtitle)}</span></span>
      <span class="switch ${checked ? "on" : ""}"><span></span></span>
    </button>`;
  }

  function plotCard(plot) {
    return `<button class="glass-card liquid-glass-card plot-card ${plot.selected ? "selected" : ""}" data-plot="${esc(plot.key)}">
      <span class="icon-tile">${icon("plant", true)}</span>
      <span style="min-width:0; flex:1; text-align:left">
        <strong class="title">样地 ${esc(plot.id)}</strong><br>
        <span class="small">${esc(plot.county)}-${esc(plot.source)}</span>
      </span>
      <span class="selection-dot"></span>
    </button>`;
  }

  function progressScreen() {
    const progress = state.progress || { completed: 0, total: 0 };
    const total = Math.max(progress.total || 0, 1);
    const ratio = Math.max(0, Math.min(1, (progress.completed || 0) / total));
    const fill = Math.round(24 + ratio * 68);
    return `<main class="progress-page">
      <section class="progress-stack">
        <div class="progress-title">正在质检样地...</div>
        <div class="liquid-ring" style="--fill:${fill}%">
          <div class="liquid-mask">
            <div class="liquid-wave"></div>
            <div class="liquid-wave fast"></div>
            <div class="progress-content">
              <div><span class="progress-number">${esc(progress.completed || 0)}</span><span class="title outline">/${esc(progress.total || 0)}</span></div>
              <div class="current-pill">${icon("filter", true)} 当前：${esc(progress.currentPlot || "--")}</div>
            </div>
          </div>
          <div class="liquid-glow"></div>
        </div>
        <button class="button secondary pill" style="max-width:190px;color:var(--red)" data-action="cancelCheck">${icon("close")} 取消</button>
      </section>
    </main>`;
  }

  function summaryScreen() {
    const summary = state.summary || {};
    return shell("质检结果汇总", `
      <main class="page summary-page stack">
        ${summary.cancelled ? `<div class="error-card">本次质检已取消，下方展示取消前完成的结果。</div>` : ""}
        <section class="summary-grid">
          ${summaryTile("强制性", summary.pendingMandatory || 0, "问题待处理", "danger", "error")}
          ${summaryTile("提示性", summary.pendingAdvisory || 0, "需要复核", "warn", "warning")}
          ${summaryTile("已忽略", summary.ignored || 0, "已忽略", "outline", "hidden")}
          ${summaryTile("已跳过", summary.skipped || 0, "未检查", "outline", "skip")}
        </section>
        <section class="stack-tight">
          <div class="list-title summary-list-heading"><strong class="title">样地汇总</strong><span class="summary-total">${esc((summary.plots || []).length)} 个样地</span></div>
          ${(summary.plots || []).map(summaryPlot).join("") || `<div class="glass-card empty">暂无质检结果。</div>`}
        </section>
      </main>`,
      {
        screen: "summary",
        dock: "status",
        action: `<div class="fixed-action summary-actions"><div class="button-row">
          <button class="button secondary" data-action="recheck">重新质检</button>
          <button class="button mint" data-action="showScope">改变范围</button>
        </div></div>`
      });
  }

  function summaryTile(label, value, sub, tone, iconName) {
    return `<article class="glass-card liquid-glass-card summary-tile">
      <div class="summary-tile-head"><span class="label outline">${esc(label)}</span><span class="${tone}">${icon(iconName, true)}</span></div>
      <div><strong class="${tone}">${esc(value)}</strong><span class="small">${esc(sub)}</span></div>
    </article>`;
  }

  function summaryPlot(plot) {
    const mandatory = plot.mandatory || 0;
    const advisory = plot.advisory || 0;
    const ignored = plot.ignored || 0;
    const chips = [
      mandatory > 0 ? `<span class="chip mandatory">${esc(mandatory)} 强制</span>` : "",
      advisory > 0 ? `<span class="chip suggested">${esc(advisory)} 提示</span>` : "",
      mandatory === 0 && advisory === 0 ? `<span class="chip clean">通过</span>` : "",
      ignored > 0 ? `<span class="chip">${esc(ignored)} 忽略</span>` : "",
    ].join("");
    return `<button class="glass-card liquid-glass-card plot-card summary-plot-card" data-detail="${esc(plot.key)}">
      <span class="summary-plot-main">
        <span>
          <span class="summary-plot-id">ID: ${esc(plot.id)}</span>
          <span class="summary-plot-title">区县: ${esc(plot.county)}</span>
        </span>
        <span class="title outline">${icon("chevron")}</span>
      </span>
      <span class="chip-row">${chips}</span>
    </button>`;
  }

  function detailScreen() {
    const detail = state.detail || {};
    const filters = state.filters || {};
    return shell(`样地详情 - ${detail.plotId || ""}`, `
      <main class="page compact-top stack">
        <section class="detail-stats">
          ${detailStat("MANDATORY", detail.mandatory || 0, "danger")}
          ${detailStat("SUGGESTED", detail.advisory || 0, "warn")}
          ${detailStat("IGNORED", detail.ignoredCount || 0, "outline")}
        </section>
        <section class="glass-card liquid-glass-card filter-card">
          <div class="chip-row">
            ${(filters.statusOptions || []).filter(option => option.name !== "FAILED").map(option => filterChip(option, filters.status, "status")).join("")}
          </div>
          <div class="chip-row table-filter-row">
            ${tableFilterSelect(filters)}
          </div>
        </section>
        <section class="stack-tight">
          ${[...(detail.issues || []), ...(detail.skippedRules || []), ...(detail.passedRules || [])].map(ruleCard).join("") || `<div class="glass-card empty">当前筛选无结果。</div>`}
        </section>
        <section class="stack detail-actions">
          <button class="button primary" data-action="recheck">${icon("refresh")} 重新质检</button>
          <button class="button secondary" data-action="back">返回结果</button>
        </section>
      </main>`,
      { screen: "detail", back: true });
  }

  function detailStat(label, value, tone) {
    return `<article class="glass-card liquid-glass-card detail-stat"><strong class="${tone}">${esc(value)}</strong><span class="label">${esc(label)}</span></article>`;
  }

  function filterChip(option, selected, kind, mint) {
    const cls = selected === option.name ? (mint ? "chip mint-active" : "chip active") : "chip";
    return `<button class="${cls}" data-filter="${kind}" data-value="${esc(option.name)}">${esc(statusLabel(option))}</button>`;
  }

  function statusLabel(option) {
    const labels = { ALL: "全部", FAILED: "失败", IGNORED: "忽略", MANDATORY: "强制性", ADVISORY: "提示性" };
    return labels[option.name] || option.label;
  }

  function tableFilterSelect(filters) {
    const options = filters.tableOptions || [];
    if (options.length === 0) {
      return `<span class="table-select-shell">${icon("filter")}<span>全部表</span>${icon("expand")}</span>`;
    }
    return `<label class="table-select-shell">${icon("filter")}
      <select data-input="tableFilter">
        ${options.map(option => {
          const label = option.name === "ALL" ? "全部表" : option.label;
          return `<option value="${esc(option.name)}" ${filters.table === option.name ? "selected" : ""}>${esc(label)}</option>`;
        }).join("")}
      </select>
      ${icon("expand")}
    </label>`;
  }

  function ruleCard(rule) {
    const kind = rule.ignored ? "IGNORED" : rule.severityKind === "ADVISORY" ? "ADVISORY" : rule.severityKind === "PASSED" ? "PASSED" : "MANDATORY";
    const actionClass = rule.ignored ? "rule-action restore" : "rule-action";
    const valueTone = kind === "ADVISORY" ? "warn" : kind === "IGNORED" || kind === "PASSED" ? "outline" : "danger";
    const watermark = kind === "ADVISORY" ? "warning" : kind === "IGNORED" ? "hidden" : kind === "PASSED" ? "check" : "error";
    const ignoredReasonBlock = rule.ignored
      ? `<div class="ignore-reason-panel"><span class="label outline">忽略理由</span><p>${esc(rule.ignoredReason || "未记录忽略理由")}</p></div>`
      : "";
    return `<article class="glass-card liquid-glass-card rule-card ${kind}">
      <div class="rule-watermark">${icon(watermark)}</div>
      <div class="rule-meta">
        <div class="row" style="gap:8px"><span class="severity ${kind}">${esc(severityLabel(kind))}</span><span class="small">${esc(rule.ruleId)}</span></div>
        <span class="chip">${esc(rule.tableName)}</span>
      </div>
      <div>
        <h3 style="${rule.ignored ? "text-decoration:line-through" : ""}">${esc(rule.title)}</h3>
        <p class="small">${esc(rule.explanation)}</p>
      </div>
      <div class="found">
        <span><span class="label outline">发现值</span><span class="found-value ${valueTone}">${esc(rule.foundValue)}</span></span>
        ${rule.fingerprint ? `<button class="${actionClass}" data-ignore="${esc(rule.fingerprint)}" data-next="${rule.ignored ? "false" : "true"}">${rule.ignored ? `${icon("restore")} 恢复` : `${icon("hidden")} 忽略`}</button>` : ""}
      </div>
      ${ignoredReasonBlock}
    </article>`;
  }

  function severityLabel(kind) {
    const labels = { MANDATORY: "强制性", ADVISORY: "提示性", IGNORED: "已忽略", PASSED: "通过" };
    return labels[kind] || kind;
  }

  function settingsScreen() {
    const scope = state.scope || {};
    return shell("设置", `
      <main class="page compact-top stack">
        <section class="glass-card liquid-glass-card card-pad-lg stack">
          <span class="icon-tile">${icon("settings", true)}</span>
          <h2 class="hero-title">质检设置</h2>
          <p class="settings-copy">这里集中放置会改变质检范围或结果展示方式的选项，样地页只保留筛选和开始质检。</p>
        </section>
        <section class="glass-card liquid-glass-card card-pad stack">
          ${switchRow("全量模式", "检查全部已索引样地", "all", !!scope.checkAllMode)}
          ${switchRow("国检模式", "执行原始基础规则检查", "national", !!scope.nationalCheckMode)}
          ${switchRow("测试模式", "显示通过规则，不影响原始数据", "test", !!scope.testMode)}
        </section>
        <section class="glass-card liquid-glass-card card-pad stack">
          <h3 class="title">当前数据源</h3>
          <p class="small">${esc(state.directory ? state.directory.name : "未选择目录")}</p>
        </section>
      </main>`,
      { screen: "settings", back: true, dock: "settings" });
  }

  function error() {
    return state.errorMessage ? `<div class="error-card">${esc(state.errorMessage)}</div>` : "";
  }

  function render(nextState) {
    state = nextState || {};
    const screen = state.screen;
    if (screen === "scope") app.innerHTML = scopeScreen();
    else if (screen === "progress") app.innerHTML = progressScreen();
    else if (screen === "summary") app.innerHTML = summaryScreen();
    else if (screen === "detail") app.innerHTML = detailScreen();
    else if (screen === "settings") app.innerHTML = settingsScreen();
    else app.innerHTML = sourceScreen();
  }

  document.addEventListener("click", event => {
    const target = event.target.closest("[data-action],[data-toggle],[data-plot],[data-detail],[data-filter],[data-ignore],[data-ignore-confirm],[data-ignore-cancel]");
    if (!target) return;
    if (target.dataset.ignoreCancel) {
      ignoreDraft = null;
      render(state);
      return;
    }
    if (target.dataset.ignoreConfirm) {
      const reason = (ignoreDraft && ignoreDraft.reason || "").trim();
      if (!reason) {
        ignoreDraft = { ...ignoreDraft, error: "请填写忽略理由" };
        render(state);
        return;
      }
      call("setIgnored", { fingerprint: ignoreDraft.fingerprint, ignored: true, reason });
      ignoreDraft = null;
      render(state);
      return;
    }
    const action = target.dataset.action;
    if (action && action !== "noop") call(action);
    if (target.dataset.toggle === "all") call("toggleAll", { enabled: !(state.scope || {}).checkAllMode });
    if (target.dataset.toggle === "national") call("toggleNational", { enabled: !(state.scope || {}).nationalCheckMode });
    if (target.dataset.toggle === "test") call("toggleTest", { enabled: !(state.scope || {}).testMode });
    if (target.dataset.plot) call("selectPlot", { key: target.dataset.plot });
    if (target.dataset.detail) call("openDetail", { key: target.dataset.detail });
    if (target.dataset.filter === "status") call("setStatusFilter", { value: target.dataset.value });
    if (target.dataset.filter === "table") call("setTableFilter", { value: target.dataset.value });
    if (target.dataset.ignore) {
      const nextIgnored = target.dataset.next === "true";
      if (nextIgnored) {
        ignoreDraft = { fingerprint: target.dataset.ignore, reason: "", error: "" };
        render(state);
      } else {
        call("setIgnored", { fingerprint: target.dataset.ignore, ignored: false });
      }
    }
  });

  document.addEventListener("input", event => {
    if (event.target.dataset.input === "query") call("setQuery", { query: event.target.value });
    if (event.target.dataset.input === "ignoreReason" && ignoreDraft) {
      ignoreDraft = { ...ignoreDraft, reason: event.target.value, error: "" };
    }
  });

  document.addEventListener("change", event => {
    if (event.target.dataset.input === "county") call("setCounty", { county: event.target.value });
    if (event.target.dataset.input === "tableFilter") call("setTableFilter", { value: event.target.value });
  });

  window.QualityApp = { render };
  try {
    if (window.QualityBridge) render(JSON.parse(window.QualityBridge.initialState()));
  } catch (error) {
    render({});
  }
})();
