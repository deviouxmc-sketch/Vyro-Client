import { getCurrentWindow } from "@tauri-apps/api/window";
import { Logo } from "./Logo";

export function TitleBar() {
  const w = getCurrentWindow();
  return (
    <header className="titlebar" data-tauri-drag-region>
      <div className="brand" data-tauri-drag-region>
        <Logo size={20} />
        <span data-tauri-drag-region>VYRO <b>CLIENT</b></span>
      </div>
      <div className="wc">
        <button aria-label="Minimize" onClick={() => void w.minimize()}>–</button>
        <button aria-label="Maximize" onClick={() => void w.toggleMaximize()}>▢</button>
        <button aria-label="Close" className="close" onClick={() => void w.close()}>✕</button>
      </div>
    </header>
  );
}
