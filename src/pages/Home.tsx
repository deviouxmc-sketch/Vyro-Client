import { useEffect, useState } from "react";
import { api, errText } from "../api";
import { Avatar } from "../components/Avatar";
import { Page } from "../components/Sidebar";
import { useStore } from "../store";
import type { SystemInfo } from "../types";

export function Home({ go }: { go: (p: Page) => void }) {
  const s = useStore();
  const { selected, profiles, accounts, phase, progress, logs, lastExit } = s;
  const [mods, setMods] = useState<number | null>(null);
  const [sys, setSys] = useState<SystemInfo | null>(null);
  const [ram, setRam] = useState<number | null>(null);
  const [showLog, setShowLog] = useState(false);

  useEffect(() => { api.systemInfo().then(setSys).catch(() => undefined); }, []);
  useEffect(() => {
    setRam(selected?.ram_mb ?? null);
    if (selected) api.countMods(selected.id).then(setMods).catch(() => setMods(null));
  }, [selected, phase]);

  const active = accounts.accounts.find((a) => a.uuid === accounts.active) ?? null;
  const maxRam = Math.max(2048, Math.floor((sys?.total_mem_mb ?? 16384) / 512) * 512);
  const busy = phase !== "idle";
  const canLaunch = !!selected && !!active && !busy;

  const commitRam = async () => {
    if (!selected || ram === null || ram === selected.ram_mb) return;
    try {
      await api.updateProfile({ ...selected, ram_mb: ram });
      await s.reload();
    } catch (e) {
      s.toast(errText(e), "error");
    }
  };

  const pct = progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  if (profiles.length === 0) {
    return (
      <section className="page fade">
        <div className="panel empty">
          <h1>Welcome to VYRO</h1>
          <p>Create your first profile to choose a Minecraft version and mod loader.</p>
          <button className="btn primary" onClick={() => go("profiles")}>Create a profile</button>
        </div>
      </section>
    );
  }

  return (
    <section className="page fade">
      <div className="grid-home">
        <div className="panel hero">
          <div className="row between">
            <select className="select big" value={s.selectedId ?? ""} onChange={(e) => s.setSelectedId(e.target.value)} disabled={busy}>
              {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            <button className="btn ghost" onClick={() => go("profiles")}>Edit profiles</button>
          </div>

          <div className="chips">
            <span className="chip">Minecraft {selected?.mc_version}</span>
            <span className="chip accent">{selected?.loader}</span>
            <span className="chip">{mods ?? "–"} mods</span>
            <span className="chip">{selected ? (selected.ram_mb / 1024).toFixed(1) : "–"} GB RAM</span>
          </div>

          <button className={`launch ${busy ? "busy" : ""}`} disabled={!canLaunch} onClick={() => void s.launch()}>
            {phase === "idle" ? "LAUNCH" : phase === "preparing" ? "PREPARING…" : "RUNNING"}
          </button>
          {!active && <p className="hint warn">Add a Microsoft account to play. <a onClick={() => go("accounts")}>Go to Accounts</a></p>}

          {phase === "preparing" && progress && (
            <div className="progress-wrap">
              <div className="row between small"><span>{progress.stage}</span><span>{progress.total > 1 ? `${progress.done}/${progress.total}` : ""}</span></div>
              <div className="bar"><div className="fill" style={{ width: `${progress.total > 1 ? pct : 100}%` }} /></div>
            </div>
          )}
          {phase === "running" && <p className="hint ok">Minecraft is running.</p>}
          {phase === "idle" && lastExit && (
            <p className={`hint ${lastExit.code === 0 ? "ok" : "warn"}`}>
              {lastExit.code === 0 ? "Game closed normally." : `Game stopped unexpectedly (exit code ${lastExit.code ?? "?"}).`}
            </p>
          )}
        </div>

        <div className="side-col">
          <div className="panel">
            <h3>Account</h3>
            {active ? (
              <div className="row gap">
                <Avatar uuid={active.uuid} name={active.username} size={44} />
                <div><div className="strong">{active.username}</div><div className="muted small">Microsoft account</div></div>
              </div>
            ) : (
              <p className="muted">No account signed in.</p>
            )}
            <button className="btn ghost wide" onClick={() => go("accounts")}>Manage accounts</button>
          </div>

          <div className="panel">
            <h3>Memory</h3>
            <div className="row between small"><span>Allocated</span><b>{ram !== null ? (ram / 1024).toFixed(1) : "–"} GB</b></div>
            <input
              type="range" min={1024} max={maxRam} step={512} value={ram ?? 1024} disabled={!selected || busy}
              onChange={(e) => setRam(Number(e.target.value))}
              onPointerUp={() => void commitRam()} onKeyUp={() => void commitRam()}
            />
            <p className="muted small">{sys ? `System has ${(sys.total_mem_mb / 1024).toFixed(1)} GB total. Leave at least 2 GB free for the OS.` : ""}</p>
          </div>
        </div>
      </div>

      <div className="panel console">
        <div className="row between">
          <h3>Game log</h3>
          <button className="btn ghost" onClick={() => setShowLog((v) => !v)}>{showLog ? "Hide" : `Show (${logs.length})`}</button>
        </div>
        {showLog && (
          <pre className="log">
            {logs.length === 0 ? "No output yet." : logs.map((l, i) => <div key={i} className={l.stream}>{l.line}</div>)}
          </pre>
        )}
      </div>
    </section>
  );
}
