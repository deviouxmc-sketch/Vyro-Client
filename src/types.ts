export type Loader = "vanilla" | "fabric" | "quilt" | "forge" | "neoforge";

export interface Profile {
  id: string;
  name: string;
  mc_version: string;
  loader: Loader;
  loader_version: string | null;
  ram_mb: number;
  jvm_args: string[];
  java_path: string | null;
  resolution: [number, number] | null;
  fullscreen: boolean;
}

export interface AccountMeta {
  uuid: string;
  username: string;
}
export interface AccountList {
  accounts: AccountMeta[];
  active: string | null;
}

export interface DeviceCode {
  user_code: string;
  verification_uri: string;
  expires_in: number;
  message: string;
}

export interface McVersion {
  id: string;
  kind: string;
}

export interface JavaInstall {
  path: string;
  major: number;
  version: string;
}

export interface SystemInfo {
  total_mem_mb: number;
  cpu_threads: number;
  os: string;
  data_dir: string;
}

export interface Settings {
  client_id: string;
  java_path: string | null;
}

export interface Progress {
  stage: string;
  done: number;
  total: number;
}
export interface LogLine {
  stream: "stdout" | "stderr";
  line: string;
}
export interface GameExit {
  profile_id: string;
  code: number | null;
}

export const LOADERS: { id: Loader; label: string; supported: boolean }[] = [
  { id: "vanilla", label: "Vanilla", supported: true },
  { id: "fabric", label: "Fabric", supported: true },
  { id: "quilt", label: "Quilt", supported: true },
  { id: "forge", label: "Forge (coming soon)", supported: false },
  { id: "neoforge", label: "NeoForge (coming soon)", supported: false },
];

// ---- mods (Modrinth) ----
export type Sort = "relevance" | "downloads" | "follows" | "newest" | "updated";

export interface SearchHit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  downloads: number;
  icon_url: string | null;
  categories: string[];
}
export interface SearchResult {
  hits: SearchHit[];
  total_hits: number;
}
export interface ProjectDetail {
  id: string;
  slug: string;
  title: string;
  description: string;
  body: string;
  icon_url: string | null;
  gallery: { url: string; title: string | null; featured: boolean }[];
  downloads: number;
  categories: string[];
}
export interface InstalledMod {
  project_id: string;
  version_id: string;
  version_number: string;
  title: string;
  icon_url: string | null;
  filename: string;
  as_dependency: boolean;
  enabled: boolean;
  requires: string[];
  incompatible: string[];
}
export interface ModsView {
  managed: InstalledMod[];
  unmanaged: string[];
  orphans: string[];
  conflicts: [string, string][];
}
export interface Collection {
  id: string;
  name: string;
  description: string;
  kind: "collection" | "category";
  slugs: string[];
}
export interface Skipped {
  project: string;
  reason: string;
}
export interface InstallReport {
  installed: string[];
  skipped: Skipped[];
}
export interface UpdateInfo {
  project_id: string;
  title: string;
  current: string;
  latest_version_id: string;
  latest_number: string;
}
export interface UpdateCheck {
  updates: UpdateInfo[];
  no_compatible: string[];
}
