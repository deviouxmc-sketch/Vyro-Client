import { useEffect, useState } from "react";
import { api, errText } from "../api";
import { useStore } from "../store";
import type { JavaInstall, SystemInfo } from "../types";

export function Settings() {
  const s = useStore();
  const [clientId, setClientId] = useState(s.settings.client_id);
  const [java, setJava] = useState(s.settings.java_path ?? "");
  const [found, setFound] = useState<JavaInstall[] | null>(null);
  const [sys, setSys] = useState<SystemInfo | null>(null);

  useEffect(() => { setClientId(s.settings.client_id); setJava(s.settings.java_path ?? ""); }, [s.settings]);
  useEffect(() => { api.systemInfo().then(setSys).catch(() => undefined); }, []);

  const scan = async () => {
    try { setFound(await api.detectJava()); } catch (e) { s.toast(errText(e), "error"); }
  };

  const save = async () => {
    try {
      await api.saveSettings({ client_id: clientId.trim(), java_path: java.trim() || null });
      await s.reload();
      s.toast("Settings saved");
    } catch (e) {
      s.toast(errText(e), "error");
    }
  };

  return (
    <section className="page fade">
      <h1>Settings</h1>

      <div className="panel form">
        <h3>Microsoft sign-in</h3>
        <label>Application (client) ID
          <input className="input" value={clientId} onChange={(e) => setClientId(e.target.value)} placeholder="00000000-0000-0000-0000-000000000000" />
        </label>
        <p className="muted small">Register an app at entra.microsoft.com → App registrations: supported accounts "Personal Microsoft accounts only", then Authentication → "Allow public client flows" = Yes. Minecraft services additionally require Mojang's approval of the app before other players can sign in with it.</p>
      </div>

      <div className="panel form">
        <h3>Java</h3>
        <label>Global Java executable (leave empty for automatic)
          <input className="input" value={java} onChange={(e) => setJava(e.target.value)} placeholder="auto – VYRO picks or downloads the right version" />
        </label>
        <div className="row gap-s wrap">
          <button className="btn ghost small" onClick={() => void scan()}>Detect installed Java</button>
          {found?.map((j) => (
            <button key={j.path} className="chip btn-chip" title={j.path} onClick={() => setJava(j.path)}>Java {j.major} · {j.version}</button>
          ))}
          {found && found.length === 0 && <span className="muted small">No Java found – VYRO will download one when needed.</span>}
        </div>
      </div>

      <div className="panel form">
        <h3>System</h3>
        {sys ? (
          <ul className="kv">
            <li><span>Operating system</span><b>{sys.os}</b></li>
            <li><span>Memory</span><b>{(sys.total_mem_mb / 1024).toFixed(1)} GB</b></li>
            <li><span>CPU threads</span><b>{sys.cpu_threads}</b></li>
            <li><span>Data folder</span><b className="path">{sys.data_dir}</b></li>
          </ul>
        ) : <p className="muted">Loading…</p>}
      </div>

      <div className="row end"><button className="btn primary" onClick={() => void save()}>Save settings</button></div>
    </section>
  );
}
