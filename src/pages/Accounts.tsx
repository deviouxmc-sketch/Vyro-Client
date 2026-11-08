import { openUrl } from "@tauri-apps/plugin-opener";
import { useState } from "react";
import { api, errText } from "../api";
import { Avatar } from "../components/Avatar";
import { useStore } from "../store";
import type { DeviceCode } from "../types";

export function Accounts() {
  const s = useStore();
  const [code, setCode] = useState<DeviceCode | null>(null);
  const [starting, setStarting] = useState(false);
  const noClient = !s.settings.client_id.trim();

  const start = async () => {
    setStarting(true);
    try {
      const dc = await api.loginStart();
      setCode(dc);
      setStarting(false);
      void openUrl(dc.verification_uri).catch(() => undefined);
      const acc = await api.loginFinish(); // resolves when the user finishes in the browser
      setCode(null);
      await s.reload();
      s.toast(`Signed in as ${acc.username}`);
    } catch (e) {
      setCode(null);
      setStarting(false);
      const msg = errText(e);
      if (!/cancel/i.test(msg)) s.toast(msg, "error");
    }
  };

  const cancel = async () => {
    await api.loginCancel().catch(() => undefined);
    setCode(null);
  };

  const run = async (f: () => Promise<unknown>) => {
    try { await f(); await s.reload(); } catch (e) { s.toast(errText(e), "error"); }
  };

  return (
    <section className="page fade">
      <div className="row between">
        <h1>Accounts</h1>
        <button className="btn primary" disabled={starting || !!code || noClient} onClick={() => void start()}>+ Add Microsoft account</button>
      </div>
      <p className="muted">Sign-in happens on Microsoft's own page. VYRO never sees your password; only tokens are kept, in your operating system's secure credential store.</p>
      {noClient && <p className="hint warn">Set your Microsoft app (client) ID in Settings before adding an account.</p>}

      {code && (
        <div className="panel code-panel fade">
          <p>Enter this code on the Microsoft page that just opened:</p>
          <div className="code">{code.user_code}</div>
          <p className="muted small">If no page opened, go to <a onClick={() => void openUrl(code.verification_uri)}>{code.verification_uri}</a>. Waiting for you to finish…</p>
          <button className="btn ghost" onClick={() => void cancel()}>Cancel</button>
        </div>
      )}

      <div className="cards">
        {s.accounts.accounts.map((a) => (
          <div key={a.uuid} className={`panel card ${a.uuid === s.accounts.active ? "sel" : ""}`}>
            <div className="row gap">
              <Avatar uuid={a.uuid} name={a.username} size={48} />
              <div>
                <div className="strong">{a.username}</div>
                <div className="muted small">{a.uuid === s.accounts.active ? "Active" : "Signed in"}</div>
              </div>
            </div>
            <div className="row gap-s top">
              {a.uuid !== s.accounts.active && <button className="btn small primary" onClick={() => void run(() => api.setActiveAccount(a.uuid))}>Use this account</button>}
              <button className="btn small ghost" onClick={() => void run(() => api.removeAccount(a.uuid))}>Sign out</button>
            </div>
          </div>
        ))}
        {s.accounts.accounts.length === 0 && !code && <div className="panel empty"><p>No accounts yet.</p></div>}
      </div>
    </section>
  );
}
