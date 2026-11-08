#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use launcher_core::accounts::{AccountMeta, Accounts, SecretStore};
use launcher_core::args::{self, LaunchOptions, Session};
use launcher_core::auth::{self, DeviceCode};
use launcher_core::download::{Progress, ProgressFn};
use launcher_core::install::Installer;
use launcher_core::java::{self, JavaInstall};
use launcher_core::launch::{self, LogFn, LogLine};
use launcher_core::loaders::{self, Loader};
use launcher_core::paths::Dirs;
use launcher_core::profile::{Profile, ProfileStore};
use launcher_core::version::Manifest;
use launcher_core::{Error, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::path::Path;
use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Emitter, Manager, State};

// ---------------------------------------------------------------- secrets (OS keychain)

struct Keyring;

fn kerr(e: keyring::Error) -> Error {
    Error::msg(format!("secure credential storage error: {e}"))
}

impl SecretStore for Keyring {
    fn get(&self, key: &str) -> Result<Option<String>> {
        match keyring::Entry::new("vyro-client", key).map_err(kerr)?.get_password() {
            Ok(v) => Ok(Some(v)),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(kerr(e)),
        }
    }
    fn set(&self, key: &str, value: &str) -> Result<()> {
        keyring::Entry::new("vyro-client", key).map_err(kerr)?.set_password(value).map_err(kerr)
    }
    fn delete(&self, key: &str) -> Result<()> {
        match keyring::Entry::new("vyro-client", key).map_err(kerr)?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(kerr(e)),
        }
    }
}

// ---------------------------------------------------------------- state

#[derive(Serialize, Deserialize, Clone, Default)]
struct Settings {
    client_id: String,
    java_path: Option<String>,
}

struct AppState {
    dirs: Dirs,
    installer: Installer,
    accounts: Accounts,
    profiles: ProfileStore,
    settings: Mutex<Settings>,
    manifest: tokio::sync::Mutex<Option<Manifest>>,
    running: Mutex<HashSet<String>>,
    pending_login: tokio::sync::Mutex<Option<DeviceCode>>,
    cancel_login: tokio::sync::Notify,
}

type St<'a> = State<'a, Arc<AppState>>;

fn load_settings(dirs: &Dirs) -> Settings {
    let mut s: Settings = std::fs::read(dirs.settings_file())
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default();
    if s.client_id.is_empty() {
        // allows shipping a build with the project's own Azure app id baked in
        s.client_id = option_env!("VYRO_MS_CLIENT_ID").unwrap_or("").to_string();
    }
    s
}

// ---------------------------------------------------------------- simple commands

#[derive(Serialize)]
struct McVersion {
    id: String,
    kind: String,
}

#[tauri::command]
async fn list_mc_versions(state: St<'_>, snapshots: bool) -> Result<Vec<McVersion>> {
    let mut cache = state.manifest.lock().await;
    if cache.is_none() {
        *cache = Some(state.installer.manifest().await?);
    }
    let m = cache.as_ref().expect("manifest just loaded");
    Ok(m.versions
        .iter()
        .filter(|v| snapshots || v.kind == "release")
        .map(|v| McVersion { id: v.id.clone(), kind: v.kind.clone() })
        .collect())
}

#[tauri::command]
fn list_profiles(state: St<'_>) -> Result<Vec<Profile>> {
    state.profiles.list()
}
#[tauri::command]
fn create_profile(state: St<'_>, profile: Profile) -> Result<Profile> {
    state.profiles.create(profile)
}
#[tauri::command]
fn update_profile(state: St<'_>, profile: Profile) -> Result<Profile> {
    state.profiles.update(profile)
}
#[tauri::command]
fn delete_profile(state: St<'_>, id: String) -> Result<()> {
    // Only the profile entry is removed; the game folder (worlds!) is intentionally kept.
    state.profiles.delete(&id)
}
#[tauri::command]
fn duplicate_profile(state: St<'_>, id: String) -> Result<Profile> {
    state.profiles.duplicate(&id)
}

#[tauri::command]
fn count_mods(state: St<'_>, id: String) -> Result<u32> {
    // only ids of existing profiles are accepted, so no path traversal through `id`
    if !state.profiles.list()?.iter().any(|p| p.id == id) {
        return Err(Error::msg("profile not found"));
    }
    let dir = state.dirs.instance(&id).join("mods");
    let Ok(rd) = std::fs::read_dir(dir) else { return Ok(0) };
    Ok(rd
        .flatten()
        .filter(|e| e.path().extension().map(|x| x.eq_ignore_ascii_case("jar")).unwrap_or(false))
        .count() as u32)
}

#[derive(Serialize)]
struct AccountList {
    accounts: Vec<AccountMeta>,
    active: Option<String>,
}

#[tauri::command]
fn list_accounts(state: St<'_>) -> AccountList {
    let (accounts, active) = state.accounts.list();
    AccountList { accounts, active }
}
#[tauri::command]
fn set_active_account(state: St<'_>, uuid: String) -> Result<()> {
    state.accounts.set_active(&uuid)
}
#[tauri::command]
fn remove_account(state: St<'_>, uuid: String) -> Result<()> {
    state.accounts.remove(&uuid)
}

#[derive(Serialize)]
struct DeviceCodeView {
    user_code: String,
    verification_uri: String,
    expires_in: u64,
    message: String,
}

#[tauri::command]
async fn login_start(state: St<'_>) -> Result<DeviceCodeView> {
    let cid = state.settings.lock().unwrap().client_id.clone();
    if cid.trim().is_empty() {
        return Err(Error::msg("Set your Microsoft application (client) ID in Settings first."));
    }
    let dc = auth::start_device_flow(state.installer.dl.http(), cid.trim()).await?;
    let view = DeviceCodeView {
        user_code: dc.user_code.clone(),
        verification_uri: dc.verification_uri.clone(),
        expires_in: dc.expires_in,
        message: dc.message.clone(),
    };
    *state.pending_login.lock().await = Some(dc);
    Ok(view)
}

#[tauri::command]
async fn login_finish(state: St<'_>) -> Result<AccountMeta> {
    let cid = state.settings.lock().unwrap().client_id.trim().to_string();
    let dc = state.pending_login.lock().await.take().ok_or_else(|| Error::msg("no sign-in in progress"))?;
    let http = state.installer.dl.http().clone();
    let tokens = tokio::select! {
        r = auth::poll_device_flow(&http, &cid, &dc) => r?,
        _ = state.cancel_login.notified() => return Err(Error::msg("sign-in cancelled")),
    };
    let session = auth::minecraft_session(&http, &tokens.access_token).await?;
    state.accounts.add(&tokens.refresh_token, session)
}

#[tauri::command]
fn login_cancel(state: St<'_>) {
    state.cancel_login.notify_waiters();
}

#[tauri::command]
async fn detect_java(state: St<'_>) -> Result<Vec<JavaInstall>> {
    let dirs = state.dirs.clone();
    tokio::task::spawn_blocking(move || java::detect_all(&dirs)).await.map_err(|e| Error::msg(e.to_string()))
}

#[derive(Serialize)]
struct SystemInfo {
    total_mem_mb: u64,
    cpu_threads: usize,
    os: String,
    data_dir: String,
}

#[tauri::command]
fn system_info(state: St<'_>) -> SystemInfo {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    SystemInfo {
        total_mem_mb: sys.total_memory() / 1024 / 1024,
        cpu_threads: std::thread::available_parallelism().map(|n| n.get()).unwrap_or(1),
        os: std::env::consts::OS.to_string(),
        data_dir: state.dirs.root.display().to_string(),
    }
}

#[tauri::command]
fn get_settings(state: St<'_>) -> Settings {
    state.settings.lock().unwrap().clone()
}

#[tauri::command]
fn save_settings(state: St<'_>, settings: Settings) -> Result<()> {
    std::fs::create_dir_all(&state.dirs.root)?;
    std::fs::write(state.dirs.settings_file(), serde_json::to_vec_pretty(&settings)?)?;
    *state.settings.lock().unwrap() = settings;
    Ok(())
}

// ---------------------------------------------------------------- launching

#[derive(Serialize, Clone)]
struct GameExit {
    profile_id: String,
    code: Option<i32>,
}

async fn start_game(app: &AppHandle, st: &Arc<AppState>, profile: &Profile) -> Result<tokio::process::Child> {
    let on: ProgressFn = {
        let app = app.clone();
        Arc::new(move |p: Progress| {
            let _ = app.emit("launch-progress", p);
        })
    };
    let log: LogFn = {
        let app = app.clone();
        Arc::new(move |l: LogLine| {
            let _ = app.emit("game-log", l);
        })
    };
    let settings = st.settings.lock().unwrap().clone();

    let uuid = st.accounts.list().1.ok_or_else(|| Error::msg("Add a Microsoft account first."))?;
    on(Progress { stage: "Signing in".into(), done: 0, total: 1 });
    let session = st.accounts.session(st.installer.dl.http(), settings.client_id.trim(), &uuid).await?;

    on(Progress { stage: "Preparing mod loader".into(), done: 0, total: 1 });
    let version_id =
        loaders::install(&st.dirs, &st.installer.dl, profile.loader, &profile.mc_version, profile.loader_version.as_deref()).await?;

    let game_dir = st.dirs.instance(&profile.id);
    let prepared = st.installer.prepare(&version_id, &game_dir, &on).await?;
    if profile.loader != Loader::Vanilla {
        std::fs::create_dir_all(game_dir.join("mods"))?;
    }

    let required = prepared.merged.java_major;
    let chosen = profile.java_path.clone().filter(|p| !p.trim().is_empty()).or(settings.java_path.filter(|p| !p.trim().is_empty()));
    let java = match chosen {
        Some(p) => {
            let j = java::probe(Path::new(p.trim()))
                .ok_or_else(|| Error::msg(format!("Java could not be started from '{p}'. Check the path in Settings.")))?;
            if j.major < required {
                return Err(Error::msg(format!(
                    "Minecraft {} needs Java {required} or newer, but the configured Java is version {}.",
                    profile.mc_version, j.major
                )));
            }
            j
        }
        None => java::ensure(&st.dirs, &st.installer.dl, required, &on).await?,
    };

    let opts = LaunchOptions {
        java: java.path.clone(),
        min_mem_mb: (profile.ram_mb / 4).clamp(512, 2048),
        max_mem_mb: profile.ram_mb,
        extra_jvm_args: profile.jvm_args.clone(),
        extra_game_args: vec![],
        resolution: profile.resolution,
        fullscreen: profile.fullscreen,
        session: Session { username: session.username.clone(), uuid: session.uuid.clone(), access_token: session.access_token.clone() },
        game_dir: game_dir.clone(),
        assets_root: prepared.assets_root.clone(),
        natives_dir: prepared.natives_dir.clone(),
        libraries_dir: st.dirs.libraries(),
        classpath: prepared.classpath.clone(),
        client_jar: prepared.client_jar.clone(),
    };
    let cmd = args::build(&prepared.merged, &opts, None);
    log(LogLine { stream: "stdout", line: format!("[VYRO] {}", cmd.redacted(&session.access_token)) });
    on(Progress { stage: "Starting Minecraft".into(), done: 1, total: 1 });
    launch::spawn(&cmd, log)
}

#[tauri::command]
async fn launch_profile(app: AppHandle, state: St<'_>, profile_id: String) -> Result<()> {
    let st = state.inner().clone();
    let profile = st
        .profiles
        .list()?
        .into_iter()
        .find(|p| p.id == profile_id)
        .ok_or_else(|| Error::msg("profile not found"))?;
    if !st.running.lock().unwrap().insert(profile.id.clone()) {
        return Err(Error::msg("This profile is already running."));
    }
    match start_game(&app, &st, &profile).await {
        Ok(mut child) => {
            let id = profile.id.clone();
            tauri::async_runtime::spawn(async move {
                let code = child.wait().await.ok().and_then(|s| s.code());
                st.running.lock().unwrap().remove(&id);
                let _ = app.emit("game-exit", GameExit { profile_id: id, code });
            });
            Ok(())
        }
        Err(e) => {
            st.running.lock().unwrap().remove(&profile.id);
            Err(e)
        }
    }
}

// ---------------------------------------------------------------- bootstrap

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let root = app.path().app_data_dir()?.join("vyro");
            let dirs = Dirs::new(root);
            std::fs::create_dir_all(&dirs.root)?;
            let state = AppState {
                installer: Installer::new(dirs.clone()).map_err(|e| e.to_string())?,
                accounts: Accounts::load(dirs.accounts_file(), Box::new(Keyring)).map_err(|e| e.to_string())?,
                profiles: ProfileStore::new(dirs.profiles_file()),
                settings: Mutex::new(load_settings(&dirs)),
                manifest: tokio::sync::Mutex::new(None),
                running: Mutex::new(HashSet::new()),
                pending_login: tokio::sync::Mutex::new(None),
                cancel_login: tokio::sync::Notify::new(),
                dirs,
            };
            app.manage(Arc::new(state));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            list_mc_versions,
            list_profiles,
            create_profile,
            update_profile,
            delete_profile,
            duplicate_profile,
            count_mods,
            list_accounts,
            set_active_account,
            remove_account,
            login_start,
            login_finish,
            login_cancel,
            detect_java,
            system_info,
            get_settings,
            save_settings,
            launch_profile,
        ])
        .run(tauri::generate_context!())
        .expect("error while running VYRO Client");
}
