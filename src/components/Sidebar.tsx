export type Page = "home" | "mods" | "profiles" | "accounts" | "settings";

const ITEMS: { id: Page; label: string; icon: string }[] = [
  { id: "home", label: "Play", icon: "▶" },
  { id: "mods", label: "Mods", icon: "✦" },
  { id: "profiles", label: "Profiles", icon: "◧" },
  { id: "accounts", label: "Accounts", icon: "◉" },
  { id: "settings", label: "Settings", icon: "⚙" },
];

export function Sidebar({ page, onChange }: { page: Page; onChange: (p: Page) => void }) {
  return (
    <nav className="sidebar">
      {ITEMS.map((i) => (
        <button key={i.id} className={page === i.id ? "nav active" : "nav"} onClick={() => onChange(i.id)}>
          <span className="ico">{i.icon}</span>
          <span>{i.label}</span>
        </button>
      ))}
      <div className="grow" />
      <p className="legal">Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</p>
    </nav>
  );
}
