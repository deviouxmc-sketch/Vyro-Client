import { useEffect, useState } from "react";
import { api, errText } from "../api";
import { useStore } from "../store";
import { LOADERS, Loader, McVersion, Profile, SystemInfo } from "../types";

const TEMPLATES: { name: string; loader: Loader; ram: number }[] = [
  { name: "Vanilla", loader: "vanilla", ram: 3072 },
  { name: "PvP", loader: "fabric", ram: 3072 },
  { name: "Survival", loader: "fabric", ram: 4096 },
  { name: "Performance", loader: "fabric", ram: 2048 },
  { name: "Modded", loader: "fabric", ram: 6144 },
];

function blank(latest: string, total: number): Profile {
  const ram = Math.max(2048, Math.min(4096, Math.floor((total * 0.5) / 512) * 512));
  return { id: "", name: "", mc_version: latest, loader: "fabric", loader_version: null, ram_mb: ram, jvm_args: [], java_path: null, resolution: null, fullscreen: false };
}

function Editor({ initial, onClose }: { initial: Profile; onClose: () => void }) {
  const s = useStore();
  const [p, setP] = useState<Profile>(initial);
  const [versions, setVersions] = useState<McVersion[]>([]);
  const [snaps, setSnaps] = useState(false);
  const [sys, setSys] = useState<SystemInfo | null>(null);
  const [args, setArgs] = useState(initial.jvm_args.join(" "));
  const [saving, setSaving] = useState(false);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const isNew = initial.id === "";

  useEffect(() => { api.systemInfo().then(setSys).catch(() => undefined); }, []);
  useEffect(() => {
    setLoadErr(null);
    api.mcVersions(snaps).then((v) => {
      setVersions(v);
      if (isNew && v.length && !v.some((x) => x.id === p.mc_version)) setP((o) => ({ ...o, mc_version: v[0].id }));
    }).catch((e) => setLoadErr(errText(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [snaps]);

  const maxRam = Math.max(2048, Math.floor((sys?.total_mem_mb ?? 16384) / 512) * 512);

  const save = async () => {
    setSaving(true);
    try {
      const out: Profile = { ...p, name: p.name.trim(), jvm_args: args.split(/\s+/).filter(Boolean) };
      if (isNew) await api.createProfile(out); else await api.updateProfile(out);
      await s.reload();
      onClose();
    } catch (e) {
      s.toast(errText(e), "error");
    } finally {
      setSaving(false);
    }
  };

  const setRes = (w: string, h: string) => {
    const W = parseInt(w, 10), H = parseInt(h, 10);
    setP({ ...p, resolution: W > 0 && H > 0 ? [W, H] : null });
  };

  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal fade">
        <h2>{isNew ? "New profile" : "Edit profile"}</h2>

        {isNew && (
          <div className="row wrap gap-s">
            <span className="muted small">Start from</span>
            {TEMPLATES.map((t) => (
              <button key={t.name} className="chip btn-chip" onClick={() => setP({ ...p, name: t.name, loader: t.loader, ram_mb: Math.min(t.ram, maxRam) })}>{t.name}</button>
            ))}
          </div>
        )}

        <label>Name<input className="input" value={p.name} onChange={(e) => setP({ ...p, name: e.target.value })} placeholder="e.g. PvP" /></label>

        <div className="two">
          <label>Minecraft version
            <select className="select" value={p.mc_version} onChange={(e) => setP({ ...p, mc_version: e.target.value })}>
              {!versions.some((v) => v.id === p.mc_version) && <option value={p.mc_version}>{p.mc_version}</option>}
              {versions.map((v) => <option key={v.id} value={v.id}>{v.id}{v.kind !== "release" ? ` (${v.kind})` : ""}</option>)}
            </select>
          </label>
          <label>Mod loader
            <select className="select" value={p.loader} onChange={(e) => setP({ ...p, loader: e.target.value as Loader })}>
              {LOADERS.map((l) => <option key={l.id} value={l.id} disabled={!l.supported}>{l.label}</option>)}
            </select>
          </label>
        </div>
        {loadErr && <p className="hint warn">Could not load the version list: {loadErr}</p>}
        <label className="check"><input type="checkbox" checked={snaps} onChange={(e) => setSnaps(e.target.checked)} /> Show snapshots and old versions</label>

        <label>RAM: <b>{(p.ram_mb / 1024).toFixed(1)} GB</b>
          <input type="range" min={1024} max={maxRam} step={512} value={Math.min(p.ram_mb, maxRam)} onChange={(e) => setP({ ...p, ram_mb: Number(e.target.value) })} />
        </label>

        <div className="two">
          <label>Window width<input className="input" inputMode="numeric" placeholder="auto" value={p.resolution?.[0] ?? ""} onChange={(e) => setRes(e.target.value, String(p.resolution?.[1] ?? ""))} /></label>
          <label>Window height<input className="input" inputMode="numeric" placeholder="auto" value={p.resolution?.[1] ?? ""} onChange={(e) => setRes(String(p.resolution?.[0] ?? ""), e.target.value)} /></label>
        </div>
        <label className="check"><input type="checkbox" checked={p.fullscreen} onChange={(e) => setP({ ...p, fullscreen: e.target.checked })} /> Start in fullscreen</label>

        <label>Extra JVM arguments
          <textarea className="input" rows={2} value={args} onChange={(e) => setArgs(e.target.value)} placeholder="-XX:+UseG1GC -XX:MaxGCPauseMillis=50" />
        </label>
        <label>Java path (optional, overrides the global setting)
          <input className="input" value={p.java_path ?? ""} onChange={(e) => setP({ ...p, java_path: e.target.value.trim() || null })} placeholder="auto" />
        </label>

        <div className="row end gap">
          <button className="btn ghost" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={saving || !p.name.trim()} onClick={() => void save()}>{saving ? "Saving…" : "Save profile"}</button>
        </div>
      </div>
    </div>
  );
}

export function Profiles() {
  const s = useStore();
  const [editing, setEditing] = useState<Profile | null>(null);
  const [confirm, setConfirm] = useState<string | null>(null);

  const openNew = async () => {
    let total = 16384;
    let latest = "";
    try {
      total = (await api.systemInfo()).total_mem_mb;
      latest = (await api.mcVersions(false))[0]?.id ?? "";
    } catch (e) {
      s.toast(`Could not load versions: ${errText(e)}`, "error");
    }
    setEditing(blank(latest, total));
  };

  const act = async (f: () => Promise<unknown>, ok?: string) => {
    try { await f(); await s.reload(); if (ok) s.toast(ok); } catch (e) { s.toast(errText(e), "error"); }
  };

  return (
    <section className="page fade">
      <div className="row between">
        <h1>Profiles</h1>
        <button className="btn primary" onClick={() => void openNew()}>+ New profile</button>
      </div>
      <p className="muted">Each profile has its own version, loader, memory settings and game folder, so mods and worlds never mix.</p>
      <div className="cards">
        {s.profiles.map((p) => (
          <div key={p.id} className={`panel card ${p.id === s.selectedId ? "sel" : ""}`}>
            <div className="row between">
              <h3>{p.name}</h3>
              <span className="chip accent">{p.loader}</span>
            </div>
            <div className="muted small">Minecraft {p.mc_version} · {(p.ram_mb / 1024).toFixed(1)} GB</div>
            <div className="row gap-s wrap top">
              <button className="btn small primary" onClick={() => s.setSelectedId(p.id)}>{p.id === s.selectedId ? "Selected" : "Select"}</button>
              <button className="btn small ghost" onClick={() => setEditing(p)}>Edit</button>
              <button className="btn small ghost" onClick={() => void act(() => api.duplicateProfile(p.id), "Profile duplicated")}>Duplicate</button>
              {confirm === p.id ? (
                <button className="btn small danger" onClick={() => { setConfirm(null); void act(() => api.deleteProfile(p.id), "Profile deleted"); }}>Confirm delete</button>
              ) : (
                <button className="btn small ghost" onClick={() => setConfirm(p.id)}>Delete</button>
              )}
            </div>
          </div>
        ))}
        {s.profiles.length === 0 && <div className="panel empty"><p>No profiles yet.</p></div>}
      </div>
      {editing && <Editor initial={editing} onClose={() => setEditing(null)} />}
    </section>
  );
}
