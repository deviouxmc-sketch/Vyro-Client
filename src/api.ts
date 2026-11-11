import { invoke } from "@tauri-apps/api/core";
import { listen, UnlistenFn } from "@tauri-apps/api/event";
import type {
  AccountList, AccountMeta, Collection, DeviceCode, GameExit, InstallReport, JavaInstall, LogLine, McVersion,
  ModsView, Profile, Progress, ProjectDetail, SearchResult, Settings, Sort, SystemInfo, UpdateCheck, VyroModStatus,
} from "./types";

// Every function below maps 1:1 to a #[tauri::command] in src-tauri/src/main.rs.
export const api = {
  mcVersions: (snapshots: boolean) => invoke<McVersion[]>("list_mc_versions", { snapshots }),
  profiles: () => invoke<Profile[]>("list_profiles"),
  createProfile: (profile: Profile) => invoke<Profile>("create_profile", { profile }),
  updateProfile: (profile: Profile) => invoke<Profile>("update_profile", { profile }),
  deleteProfile: (id: string) => invoke<void>("delete_profile", { id }),
  duplicateProfile: (id: string) => invoke<Profile>("duplicate_profile", { id }),
  countMods: (id: string) => invoke<number>("count_mods", { id }),
  accounts: () => invoke<AccountList>("list_accounts"),
  setActiveAccount: (uuid: string) => invoke<void>("set_active_account", { uuid }),
  removeAccount: (uuid: string) => invoke<void>("remove_account", { uuid }),
  loginStart: () => invoke<DeviceCode>("login_start"),
  loginFinish: () => invoke<AccountMeta>("login_finish"),
  loginCancel: () => invoke<void>("login_cancel"),
  detectJava: () => invoke<JavaInstall[]>("detect_java"),
  systemInfo: () => invoke<SystemInfo>("system_info"),
  getSettings: () => invoke<Settings>("get_settings"),
  saveSettings: (settings: Settings) => invoke<void>("save_settings", { settings }),
  launch: (profileId: string) => invoke<void>("launch_profile", { profileId }),
  modsSearch: (profileId: string, query: string, sort: Sort, offset: number) =>
    invoke<SearchResult>("mods_search", { profileId, query, sort, offset }),
  modsProject: (id: string) => invoke<ProjectDetail>("mods_project", { id }),
  modsList: (profileId: string) => invoke<ModsView>("mods_list", { profileId }),
  modsCollections: () => invoke<Collection[]>("mods_collections"),
  modsInstall: (profileId: string, refs: string[]) => invoke<InstallReport>("mods_install", { profileId, refs }),
  modsInstallCollection: (profileId: string, collectionId: string) =>
    invoke<InstallReport>("mods_install_collection", { profileId, collectionId }),
  modsSetEnabled: (profileId: string, projectId: string, enabled: boolean) =>
    invoke<string[]>("mods_set_enabled", { profileId, projectId, enabled }),
  modsRemove: (profileId: string, projectId: string) => invoke<string[]>("mods_remove", { profileId, projectId }),
  modsCheckUpdates: (profileId: string) => invoke<UpdateCheck>("mods_check_updates", { profileId }),
  vyroModStatus: (profileId: string) => invoke<VyroModStatus>("vyro_mod_status", { profileId }),
  vyroModInstall: (profileId: string) => invoke<InstallReport>("vyro_mod_install", { profileId }),
  modsUpdate: (profileId: string, selection: { project_id: string; version_id: string }[]) =>
    invoke<InstallReport>("mods_update", { profileId, selection }),
};

export const events = {
  progress: (f: (p: Progress) => void): Promise<UnlistenFn> => listen<Progress>("launch-progress", (e) => f(e.payload)),
  log: (f: (l: LogLine) => void): Promise<UnlistenFn> => listen<LogLine>("game-log", (e) => f(e.payload)),
  modsProgress: (f: (p: Progress) => void): Promise<UnlistenFn> => listen<Progress>("mods-progress", (e) => f(e.payload)),
  exit: (f: (x: GameExit) => void): Promise<UnlistenFn> => listen<GameExit>("game-exit", (e) => f(e.payload)),
};

export const errText = (e: unknown): string => (typeof e === "string" ? e : e instanceof Error ? e.message : JSON.stringify(e));
