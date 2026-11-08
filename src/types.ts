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
