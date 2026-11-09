import { openUrl } from "@tauri-apps/plugin-opener";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, errText, events } from "../api";
import { Page } from "../components/Sidebar";
import { useStore } from "../store";
import type {
  Collection, InstallReport, ModsView, ProjectDetail, SearchHit, Sort, UpdateCheck,
} from "../types";

type Tab = "browse" | "installed" | "collections";

const fmt = (n: number) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e3 ? `${(n / 1e3).toFixed(1)}K` : String(n));

function Icon({ src, name }: { src: string | null; name: string }) {
  const [bad, setBad] = useState(false);
  if (!src || bad) return <div className="mod-icon fallback">{name.slice(0, 1).toUpperCase()}</div>;
  return <img className="mod-icon" src={src} alt="" onError={() => setBad(true)} />;
}

function ReportModal({ report, onClose }: { report: InstallReport; onClose: () => void }) {
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal fade">
        <h2>Install result</h2>
        {report.installed.length > 0 && (
          <>
            <h3>Installed ({report.installed.length})</h3>
            <ul className="plain">{report.installed.map((n) => <li key={n}>✓ {n}</li>)}</ul>
          </>
        )}
        {report.skipped.length > 0 && (
          <>
            <h3>Not installed ({report.skipped.length})</h3>
            <ul className="plain">{report.skipped.map((s, i) => <li key={i}><b>{s.project}</b>: <span className="muted">{s.reason}</span></li>)}</ul>
          </>
        )}
        {report.installed.length === 0 && report.skipped.length === 0 && <p className="muted">Nothing to do.</p>}
        <div className="row end"><button className="btn primary" onClick={onClose}>Done</button></div>
      </div>
    </div>
  );
}

function DetailModal({ id, installed, busy, onInstall, onClose }: {
  id: string; installed: boolean; busy: boolean; onInstall: () => void; onClose: () => void;
}) {
  const [p, setP] = useState<ProjectDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { api.modsProject(id).then(setP).catch((e) => setErr(errText(e))); }, [id]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal wide fade">
        {err && <p className="hint warn">{err}</p>}
        {!p && !err && <p className="muted">Loading…</p>}
        {p && (
          <>
            <div className="row gap">
              <Icon src={p.icon_url} name={p.title} />
              <div className="grow"><h2 style={{ margin: 0 }}>{p.title}</h2><div className="muted small">{fmt(p.downloads)} downloads</div></div>
            </div>
            <p>{p.description}</p>
            {p.gallery.length > 0 && (
              <div className="gallery">{p.gallery.slice(0, 8).map((g) => <img key={g.url} src={g.url} alt={g.title ?? ""} loading="lazy" />)}</div>
            )}
            <div className="row gap-s wrap">{p.categories.map((c) => <span key={c} className="chip">{c}</span>)}</div>
            <div className="row between">
              <button className="btn ghost" onClick={() => void openUrl(`https://modrinth.com/mod/${p.slug}`)}>Full page on Modrinth ↗</button>
              <div className="row gap-s">
                <button className="btn ghost" onClick={onClose}>Close</button>
                <button className="btn primary" disabled={busy || installed} onClick={onInstall}>{installed ? "Installed" : "Install"}</button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export function Mods({ go }: { go: (p: Page) => void }) {
  const s = useStore();
  const p = s.selected;
  const [tab, setTab] = useState<Tab>("browse");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<Sort>("downloads");
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<ModsView | null>(null);
  const [collections, setCollections] = useState<Collection[]>([]);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState<string | null>(null);
  const [report, setReport] = useState<InstallReport | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [updates, setUpdates] = useState<UpdateCheck | null>(null);
  const [confirmRm, setConfirmRm] = useState<string | null>(null);
  const reqId = useRef(0);

  const supported = !!p && p.loader !== "vanilla";
  const installedIds = new Set(view?.managed.map((m) => m.project_id) ?? []);

  const loadInstalled = useCallback(async () => {
    if (!p) return;
    try { setView(await api.modsList(p.id)); } catch (e) { s.toast(errText(e), "error"); }
  }, [p, s]);

  useEffect(() => { void loadInstalled(); setUpdates(null); setPicked(new Set()); /* eslint-disable-next-line */ }, [p?.id, p?.mc_version, p?.loader]);
  useEffect(() => { api.modsCollections().then(setCollections).catch(() => undefined); }, []);
  useEffect(() => { const un = events.modsProgress((x) => setBusy(x.stage)); return () => { un.then((f) => f()); }; }, []);

  const search = useCallback(async (offset: number) => {
    if (!p || !supported) return;
    const my = ++reqId.current;
    setLoading(true);
    try {
      const r = await api.modsSearch(p.id, query, sort, offset);
      if (my !== reqId.current) return; // a newer search superseded this one
      setHits((old) => (offset === 0 ? r.hits : [...old, ...r.hits]));
      setTotal(r.total_hits);
    } catch (e) {
      if (my === reqId.current) s.toast(errText(e), "error");
    } finally {
      if (my === reqId.current) setLoading(false);
    }
  }, [p, supported, query, sort, s]);

  useEffect(() => {
    if (tab !== "browse") return;
    const t = setTimeout(() => void search(0), 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, sort, p?.id, p?.mc_version, p?.loader, tab]);

  const run = async (label: string, f: () => Promise<InstallReport | void>) => {
    setBusy(label);
    try {
      const r = await f();
      if (r) setReport(r);
      await loadInstalled();
    } catch (e) {
      s.toast(errText(e), "error");
    } finally {
      setBusy(null);
    }
  };

  const install = (refs: string[]) => p && run("Installing", () => api.modsInstall(p.id, refs).then((r) => { setPicked(new Set()); return r; }));

  if (!p) {
    return <section className="page fade"><div className="panel empty"><p>Create a profile first.</p><button className="btn primary" onClick={() => go("profiles")}>Go to Profiles</button></div></section>;
  }

  return (
    <section className="page fade">
      <div className="row between wrap gap">
        <div><h1>Mods</h1><p className="muted" style={{ margin: 0 }}>Only mods that work with this profile's Minecraft version and loader are shown or installed.</p></div>
        <select className="select big" value={s.selectedId ?? ""} onChange={(e) => s.setSelectedId(e.target.value)} disabled={!!busy}>
          {s.profiles.map((x) => <option key={x.id} value={x.id}>{x.name} · {x.mc_version} · {x.loader}</option>)}
        </select>
      </div>

      {!supported ? (
        <div className="panel empty">
          <p>This profile is <b>Vanilla</b>, which doesn't use mods.</p>
          <button className="btn primary" onClick={() => go("profiles")}>Change the loader to Fabric or Quilt</button>
        </div>
      ) : (
        <>
          <div className="tabs">
            {(["browse", "installed", "collections"] as Tab[]).map((t) => (
              <button key={t} className={tab === t ? "tab active" : "tab"} onClick={() => setTab(t)}>
                {t === "browse" ? "Browse" : t === "installed" ? `Installed (${view?.managed.length ?? 0})` : "Collections"}
              </button>
            ))}
          </div>
          {busy && <div className="panel busy-bar"><div className="bar"><div className="fill indet" /></div><span className="small muted">{busy}…</span></div>}

          {tab === "browse" && (
            <>
              <div className="row gap-s">
                <input className="input" placeholder="Search mods (e.g. sodium, minimap)…" value={query} onChange={(e) => setQuery(e.target.value)} />
                <select className="select sort" value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
                  <option value="downloads">Most downloaded</option>
                  <option value="relevance">Relevance</option>
                  <option value="follows">Most followed</option>
                  <option value="updated">Recently updated</option>
                  <option value="newest">Newest</option>
                </select>
              </div>
              <div className="list">
                {hits.map((h) => {
                  const isIn = installedIds.has(h.project_id);
                  return (
                    <div key={h.project_id} className="panel mod-row">
                      <input type="checkbox" disabled={isIn || !!busy} checked={picked.has(h.slug)}
                        onChange={(e) => setPicked((old) => { const n = new Set(old); if (e.target.checked) n.add(h.slug); else n.delete(h.slug); return n; })} />
                      <Icon src={h.icon_url} name={h.title} />
                      <div className="grow">
                        <div className="strong">{h.title} <span className="muted small">by {h.author} · {fmt(h.downloads)} downloads</span></div>
                        <div className="muted small clamp">{h.description}</div>
                      </div>
                      <button className="btn small ghost" onClick={() => setDetail(h.project_id)}>Details</button>
                      <button className="btn small primary" disabled={isIn || !!busy} onClick={() => void install([h.slug])}>{isIn ? "Installed" : "Install"}</button>
                    </div>
                  );
                })}
                {!loading && hits.length === 0 && <div className="panel empty"><p className="muted">No mods found for Minecraft {p.mc_version} with {p.loader}.</p></div>}
                {hits.length < total && <button className="btn ghost" disabled={loading} onClick={() => void search(hits.length)}>{loading ? "Loading…" : `Load more (${total - hits.length} left)`}</button>}
                {loading && hits.length === 0 && <p className="muted">Searching…</p>}
              </div>
              {picked.size > 0 && (
                <div className="floatbar fade">
                  <span>{picked.size} selected</span>
                  <button className="btn ghost small" onClick={() => setPicked(new Set())}>Clear</button>
                  <button className="btn primary" disabled={!!busy} onClick={() => void install([...picked])}>Install selected</button>
                </div>
              )}
            </>
          )}

          {tab === "installed" && (
            <>
              <div className="row gap-s wrap">
                <button className="btn ghost small" disabled={!!busy} onClick={() => void run("Checking for updates", async () => { setUpdates(await api.modsCheckUpdates(p.id)); })}>Check for updates</button>
                {updates && updates.updates.length > 0 && (
                  <button className="btn primary small" disabled={!!busy}
                    onClick={() => void run("Updating", () => api.modsUpdate(p.id, updates.updates.map((u) => ({ project_id: u.project_id, version_id: u.latest_version_id }))).then((r) => { setUpdates(null); return r; }))}>
                    Update all ({updates.updates.length})
                  </button>
                )}
                {view && view.orphans.length > 0 && (
                  <button className="btn ghost small" disabled={!!busy}
                    onClick={() => void run("Removing unused dependencies", async () => { for (const id of view.orphans) await api.modsRemove(p.id, id); })}>
                    Remove {view.orphans.length} unused {view.orphans.length === 1 ? "dependency" : "dependencies"}
                  </button>
                )}
              </div>
              {updates && updates.updates.length === 0 && <p className="hint ok">Everything is up to date.</p>}
              {updates && updates.no_compatible.length > 0 && (
                <p className="hint warn">No version for Minecraft {p.mc_version} ({p.loader}): {updates.no_compatible.join(", ")}</p>
              )}
              {view?.conflicts.map(([a, b]) => <p key={a + b} className="hint warn">⚠ {a} and {b} are marked incompatible with each other.</p>)}
              <div className="list">
                {view?.managed.map((m) => {
                  const up = updates?.updates.find((u) => u.project_id === m.project_id);
                  return (
                    <div key={m.project_id} className={`panel mod-row ${m.enabled ? "" : "off"}`}>
                      <label className="switch" title={m.enabled ? "Disable" : "Enable"}>
                        <input type="checkbox" checked={m.enabled} disabled={!!busy}
                          onChange={(e) => void run("Saving", async () => {
                            const warn = await api.modsSetEnabled(p.id, m.project_id, e.target.checked);
                            if (warn.length) s.toast(`${warn.join(", ")} need${warn.length === 1 ? "s" : ""} ${m.title} and may not start.`, "error");
                          })} />
                        <span className="slider" />
                      </label>
                      <Icon src={m.icon_url} name={m.title} />
                      <div className="grow">
                        <div className="strong">{m.title} <span className="muted small">v{m.version_number}</span>
                          {m.as_dependency && <span className="chip tiny">dependency</span>}
                          {view.orphans.includes(m.project_id) && <span className="chip tiny warn">unused</span>}
                        </div>
                        <div className="muted small">{m.filename}{m.enabled ? "" : " · disabled"}</div>
                      </div>
                      {up && (
                        <button className="btn small primary" disabled={!!busy}
                          onClick={() => void run("Updating", () => api.modsUpdate(p.id, [{ project_id: up.project_id, version_id: up.latest_version_id }]).then((r) => { setUpdates(null); return r; }))}>
                          Update → {up.latest_number}
                        </button>
                      )}
                      {confirmRm === m.project_id ? (
                        <button className="btn small danger" disabled={!!busy} onClick={() => { setConfirmRm(null); void run("Removing", async () => { await api.modsRemove(p.id, m.project_id); }); }}>Confirm</button>
                      ) : (
                        <button className="btn small ghost" disabled={!!busy} onClick={() => setConfirmRm(m.project_id)}>Uninstall</button>
                      )}
                    </div>
                  );
                })}
                {view && view.managed.length === 0 && <div className="panel empty"><p className="muted">No mods installed through VYRO yet.</p></div>}
              </div>
              {view && view.unmanaged.length > 0 && (
                <p className="muted small">{view.unmanaged.length} other jar file(s) in this profile's mods folder were not installed by VYRO and are left untouched: {view.unmanaged.join(", ")}</p>
              )}
            </>
          )}

          {tab === "collections" && (
            <div className="cards">
              {collections.map((c) => (
                <div key={c.id} className="panel card">
                  <div className="row between"><h3>{c.name}</h3><span className="chip">{c.kind === "collection" ? "bundle" : "category"}</span></div>
                  <p className="muted small">{c.description}</p>
                  <div className="row gap-s wrap">{c.slugs.map((x) => <span key={x} className="chip tiny">{x}</span>)}</div>
                  <button className="btn primary small top" disabled={!!busy} onClick={() => void run("Installing", () => api.modsInstallCollection(p.id, c.id))}>Install all</button>
                </div>
              ))}
              <p className="muted small" style={{ gridColumn: "1 / -1" }}>Mods that don't exist or don't support this Minecraft version are skipped and listed afterwards. Dependencies such as Fabric API are added automatically.</p>
            </div>
          )}
        </>
      )}

      {report && <ReportModal report={report} onClose={() => setReport(null)} />}
      {detail && <DetailModal id={detail} installed={installedIds.has(detail)} busy={!!busy} onClose={() => setDetail(null)}
        onInstall={() => { const id = detail; setDetail(null); void install([id]); }} />}
    </section>
  );
}
