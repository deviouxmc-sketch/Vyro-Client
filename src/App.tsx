import { useState } from "react";
import { Sidebar, Page } from "./components/Sidebar";
import { TitleBar } from "./components/TitleBar";
import { Accounts } from "./pages/Accounts";
import { Home } from "./pages/Home";
import { Mods } from "./pages/Mods";
import { Profiles } from "./pages/Profiles";
import { Settings } from "./pages/Settings";
import { StoreProvider, useStore } from "./store";

function Shell() {
  const [page, setPage] = useState<Page>("home");
  const { toasts } = useStore();
  return (
    <div className="app">
      <TitleBar />
      <div className="body">
        <Sidebar page={page} onChange={setPage} />
        <main className="content">
          {page === "home" && <Home go={setPage} />}
          {page === "mods" && <Mods go={setPage} />}
          {page === "profiles" && <Profiles />}
          {page === "accounts" && <Accounts />}
          {page === "settings" && <Settings />}
        </main>
      </div>
      <div className="toasts">
        {toasts.map((t) => <div key={t.id} className={`toast ${t.kind}`}>{t.text}</div>)}
      </div>
    </div>
  );
}

export default function App() {
  return <StoreProvider><Shell /></StoreProvider>;
}
