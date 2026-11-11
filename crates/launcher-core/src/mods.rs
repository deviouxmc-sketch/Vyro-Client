//! Per-profile mod registry: installs verified files, tracks what VYRO installed and why,
//! and enables/disables/removes mods by renaming or deleting files in `<instance>/mods`.
use crate::download::{Downloader, Job};
use crate::error::{Error, Result};
use crate::modrinth::{PlanItem, CDN_PREFIX};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

/// Registry id used for VYRO's own in-game mod (it is not a Modrinth project).
pub const VYRO_MOD_ID: &str = "vyro-client-mod";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct InstalledMod {
    pub project_id: String,
    pub version_id: String,
    pub version_number: String,
    pub title: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    /// File name when enabled (a disabled mod has `.disabled` appended on disk).
    pub filename: String,
    pub sha1: String,
    pub as_dependency: bool,
    pub enabled: bool,
    #[serde(default)]
    pub requires: Vec<String>,
    #[serde(default)]
    pub incompatible: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ModsView {
    pub managed: Vec<InstalledMod>,
    /// Jar files in the mods folder that VYRO did not install (left untouched).
    pub unmanaged: Vec<String>,
    /// Auto-installed dependencies that nothing needs any more.
    pub orphans: Vec<String>,
    /// Pairs of enabled mods that declare each other incompatible.
    pub conflicts: Vec<(String, String)>,
}

pub struct ModStore {
    instance: PathBuf,
}

pub fn safe_filename(name: &str) -> Result<String> {
    let bad = name.is_empty()
        || name.len() > 180
        || name == "."
        || name == ".."
        || name.contains(['/', '\\', '\0', ':'])
        || !name.to_lowercase().ends_with(".jar");
    if bad {
        return Err(Error::msg(format!("refusing unsafe mod file name '{name}'")));
    }
    Ok(name.to_string())
}

impl ModStore {
    pub fn new(instance: PathBuf) -> Self {
        Self { instance }
    }
    pub fn mods_dir(&self) -> PathBuf {
        self.instance.join("mods")
    }
    fn manifest(&self) -> PathBuf {
        self.instance.join("vyro-mods.json")
    }
    fn on_disk(&self, m: &InstalledMod) -> PathBuf {
        self.mods_dir().join(&m.filename)
    }
    fn disabled_path(&self, m: &InstalledMod) -> PathBuf {
        self.mods_dir().join(format!("{}.disabled", m.filename))
    }

    fn read_raw(&self) -> Result<Vec<InstalledMod>> {
        let p = self.manifest();
        if !p.exists() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_slice(&std::fs::read(p)?)?)
    }

    fn write(&self, list: &[InstalledMod]) -> Result<()> {
        std::fs::create_dir_all(&self.instance)?;
        let tmp = self.manifest().with_extension("tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(list)?)?;
        std::fs::rename(tmp, self.manifest())?;
        Ok(())
    }

    /// Manifest reconciled with what is really on disk (users may delete/rename files by hand).
    pub fn list(&self) -> Result<Vec<InstalledMod>> {
        let raw = self.read_raw()?;
        let mut out = Vec::with_capacity(raw.len());
        let mut changed = false;
        for mut m in raw {
            let en = self.on_disk(&m).is_file();
            let dis = self.disabled_path(&m).is_file();
            if !en && !dis {
                changed = true; // file vanished → forget the entry
                continue;
            }
            if m.enabled != en {
                m.enabled = en;
                changed = true;
            }
            out.push(m);
        }
        if changed {
            self.write(&out)?;
        }
        Ok(out)
    }

    pub fn view(&self) -> Result<ModsView> {
        let managed = self.list()?;
        let known: HashSet<String> = managed.iter().flat_map(|m| [m.filename.clone(), format!("{}.disabled", m.filename)]).collect();
        let mut unmanaged = vec![];
        if let Ok(rd) = std::fs::read_dir(self.mods_dir()) {
            for e in rd.flatten() {
                let n = e.file_name().to_string_lossy().to_string();
                let l = n.to_lowercase();
                if (l.ends_with(".jar") || l.ends_with(".jar.disabled")) && !known.contains(&n) {
                    unmanaged.push(n);
                }
            }
        }
        unmanaged.sort();
        let orphans = Self::orphans_of(&managed);
        let mut conflicts = vec![];
        let enabled: Vec<&InstalledMod> = managed.iter().filter(|m| m.enabled).collect();
        for a in &enabled {
            for b in &enabled {
                if a.project_id < b.project_id && (a.incompatible.contains(&b.project_id) || b.incompatible.contains(&a.project_id)) {
                    conflicts.push((a.title.clone(), b.title.clone()));
                }
            }
        }
        Ok(ModsView { managed, unmanaged, orphans, conflicts })
    }

    fn orphans_of(list: &[InstalledMod]) -> Vec<String> {
        let needed: HashSet<&String> = list.iter().flat_map(|m| m.requires.iter()).collect();
        list.iter().filter(|m| m.as_dependency && !needed.contains(&m.project_id)).map(|m| m.project_id.clone()).collect()
    }

    /// Names of enabled mods that require `project_id` (shown as a warning before disabling/removing).
    pub fn dependents(&self, project_id: &str) -> Result<Vec<String>> {
        Ok(self.list()?.into_iter().filter(|m| m.enabled && m.requires.iter().any(|r| r == project_id)).map(|m| m.title).collect())
    }

    /// Download every planned file (verified), then record them. All-or-nothing: if any
    /// download fails, files fetched in this call are removed and the registry is untouched.
    pub async fn install(&self, dl: &Downloader, items: &[PlanItem], meta: &HashMap<String, (String, Option<String>)>) -> Result<Vec<InstalledMod>> {
        std::fs::create_dir_all(self.mods_dir())?;
        let mut fresh: Vec<PathBuf> = vec![];
        let mut entries: Vec<InstalledMod> = vec![];
        for it in items {
            let name = safe_filename(&it.file.filename)?;
            if !it.file.url.starts_with(CDN_PREFIX) {
                return Err(Error::msg("refusing to download a mod from outside Modrinth's CDN"));
            }
            let dest = self.mods_dir().join(&name);
            let existing_same = dest.is_file() && crate::download::file_sha1(&dest)? == it.file.hashes.sha1;
            if dest.is_file() && !existing_same {
                // an unrelated/older file with this name; only OK if it is the previous version of this very mod
                let is_prev = self.list()?.iter().any(|m| m.project_id == it.version.project_id && m.filename == name);
                if !is_prev {
                    return Err(Error::msg(format!("a different file named '{name}' already exists in the mods folder")));
                }
            }
            if !existing_same {
                let job = Job { url: it.file.url.clone(), dest: dest.clone(), sha1: Some(it.file.hashes.sha1.clone()), size: None };
                // stale same-name file of the previous version must not satisfy the "already up to date" check
                if dest.is_file() {
                    let _ = std::fs::remove_file(&dest);
                }
                if let Err(e) = dl.fetch(&job).await {
                    for f in &fresh {
                        let _ = std::fs::remove_file(f);
                    }
                    return Err(e);
                }
                fresh.push(dest);
            }
            let (title, icon) = meta.get(&it.version.project_id).cloned().unwrap_or_else(|| (it.version.project_id.clone(), None));
            entries.push(InstalledMod {
                project_id: it.version.project_id.clone(),
                version_id: it.version.id.clone(),
                version_number: it.version.version_number.clone(),
                title,
                icon_url: icon,
                filename: name,
                sha1: it.file.hashes.sha1.clone(),
                as_dependency: it.as_dependency,
                enabled: true,
                requires: it.version.dependencies.iter().filter(|d| d.dependency_type == "required").filter_map(|d| d.project_id.clone()).collect(),
                incompatible: it.version.dependencies.iter().filter(|d| d.dependency_type == "incompatible").filter_map(|d| d.project_id.clone()).collect(),
            });
        }
        self.commit(entries.clone())?;
        Ok(entries)
    }

    /// Record installed entries. Replacing a mod removes its previous file and keeps its enabled state.
    pub fn commit(&self, entries: Vec<InstalledMod>) -> Result<()> {
        let mut list = self.list()?;
        for mut e in entries {
            if let Some(pos) = list.iter().position(|m| m.project_id == e.project_id) {
                let old = list.remove(pos);
                e.as_dependency = old.as_dependency && e.as_dependency; // explicitly installed once stays explicit
                if old.filename != e.filename {
                    let _ = std::fs::remove_file(self.on_disk(&old));
                    let _ = std::fs::remove_file(self.disabled_path(&old));
                }
                if !old.enabled {
                    let en = self.on_disk(&e);
                    if en.is_file() {
                        std::fs::rename(&en, self.disabled_path(&e))?;
                    }
                    e.enabled = false;
                }
            }
            list.push(e);
        }
        self.write(&list)
    }

    pub fn set_enabled(&self, project_id: &str, enabled: bool) -> Result<()> {
        let mut list = self.list()?;
        let m = list.iter_mut().find(|m| m.project_id == project_id).ok_or_else(|| Error::msg("mod is not installed"))?;
        if m.enabled == enabled {
            return Ok(());
        }
        let (from, to) = if enabled {
            (self.mods_dir().join(format!("{}.disabled", m.filename)), self.mods_dir().join(&m.filename))
        } else {
            (self.mods_dir().join(&m.filename), self.mods_dir().join(format!("{}.disabled", m.filename)))
        };
        std::fs::rename(from, to)?;
        m.enabled = enabled;
        self.write(&list)
    }

    /// Delete a mod. Returns the project ids of dependencies that are now unused.
    pub fn remove(&self, project_id: &str) -> Result<Vec<String>> {
        let mut list = self.list()?;
        let pos = list.iter().position(|m| m.project_id == project_id).ok_or_else(|| Error::msg("mod is not installed"))?;
        let m = list.remove(pos);
        let _ = std::fs::remove_file(self.on_disk(&m));
        let _ = std::fs::remove_file(self.disabled_path(&m));
        self.write(&list)?;
        Ok(Self::orphans_of(&list))
    }
}

#[allow(dead_code)]
fn _unused(_: &Path) {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::modrinth::{Dependency, Hashes, ModFile, ModVersion};

    fn entry(id: &str, dep: bool, requires: &[&str]) -> InstalledMod {
        InstalledMod {
            project_id: id.into(),
            version_id: format!("v-{id}"),
            version_number: "1".into(),
            title: id.to_uppercase(),
            icon_url: None,
            filename: format!("{id}.jar"),
            sha1: "x".into(),
            as_dependency: dep,
            enabled: true,
            requires: requires.iter().map(|s| s.to_string()).collect(),
            incompatible: vec![],
        }
    }
    fn touch(s: &ModStore, name: &str) {
        std::fs::create_dir_all(s.mods_dir()).unwrap();
        std::fs::write(s.mods_dir().join(name), b"jar").unwrap();
    }

    #[test]
    fn filename_safety() {
        assert!(safe_filename("sodium-1.0.jar").is_ok());
        for bad in ["", "../x.jar", "a/b.jar", "a\\b.jar", "x.exe", "..", "C:evil.jar"] {
            assert!(safe_filename(bad).is_err(), "{bad}");
        }
    }

    #[test]
    fn enable_disable_remove_and_orphans() {
        let td = tempfile::tempdir().unwrap();
        let s = ModStore::new(td.path().to_path_buf());
        for f in ["sodium.jar", "fapi.jar"] {
            touch(&s, f);
        }
        s.commit(vec![entry("sodium", false, &["fapi"]), entry("fapi", true, &[])]).unwrap();
        assert_eq!(s.dependents("fapi").unwrap(), vec!["SODIUM".to_string()]);

        s.set_enabled("sodium", false).unwrap();
        assert!(s.mods_dir().join("sodium.jar.disabled").is_file());
        assert!(!s.mods_dir().join("sodium.jar").exists());
        assert!(!s.list().unwrap().iter().find(|m| m.project_id == "sodium").unwrap().enabled);
        s.set_enabled("sodium", true).unwrap();
        assert!(s.mods_dir().join("sodium.jar").is_file());

        // removing the only mod that needs fapi leaves fapi orphaned
        let orphans = s.remove("sodium").unwrap();
        assert_eq!(orphans, vec!["fapi".to_string()]);
        assert!(!s.mods_dir().join("sodium.jar").exists());
        assert!(s.remove("sodium").is_err());
    }

    #[test]
    fn reconciles_with_disk_and_lists_unmanaged() {
        let td = tempfile::tempdir().unwrap();
        let s = ModStore::new(td.path().to_path_buf());
        touch(&s, "a.jar");
        touch(&s, "mine.jar");
        s.commit(vec![entry("a", false, &[]), entry("gone", false, &[])]).unwrap(); // "gone" has no file
        let v = s.view().unwrap();
        assert_eq!(v.managed.len(), 1);
        assert_eq!(v.unmanaged, vec!["mine.jar".to_string()]);
    }

    #[test]
    fn conflicts_detected_between_enabled_mods() {
        let td = tempfile::tempdir().unwrap();
        let s = ModStore::new(td.path().to_path_buf());
        touch(&s, "a.jar");
        touch(&s, "b.jar");
        let mut a = entry("a", false, &[]);
        a.incompatible = vec!["b".into()];
        s.commit(vec![a, entry("b", false, &[])]).unwrap();
        assert_eq!(s.view().unwrap().conflicts, vec![("A".to_string(), "B".to_string())]);
        s.set_enabled("b", false).unwrap();
        assert!(s.view().unwrap().conflicts.is_empty());
    }

    #[test]
    fn replacing_keeps_disabled_state_and_deletes_old_file() {
        let td = tempfile::tempdir().unwrap();
        let s = ModStore::new(td.path().to_path_buf());
        touch(&s, "a-old.jar");
        let mut old = entry("a", false, &[]);
        old.filename = "a-old.jar".into();
        s.commit(vec![old]).unwrap();
        s.set_enabled("a", false).unwrap();
        touch(&s, "a-new.jar"); // as if just downloaded
        let mut new = entry("a", false, &[]);
        new.filename = "a-new.jar".into();
        new.version_id = "v2".into();
        s.commit(vec![new]).unwrap();
        assert!(!s.mods_dir().join("a-old.jar.disabled").exists());
        assert!(s.mods_dir().join("a-new.jar.disabled").is_file());
        let l = s.list().unwrap();
        assert_eq!((l[0].version_id.as_str(), l[0].enabled), ("v2", false));
    }

    #[tokio::test]
    async fn install_rejects_foreign_urls_and_skips_download_when_file_matches() {
        let td = tempfile::tempdir().unwrap();
        let s = ModStore::new(td.path().to_path_buf());
        let dl = Downloader::new().unwrap();
        let mk = |url: &str, sha: &str| PlanItem {
            version: ModVersion {
                id: "v1".into(), project_id: "p".into(), name: "".into(), version_number: "1".into(), version_type: "release".into(),
                game_versions: vec![], loaders: vec![], files: vec![],
                dependencies: vec![Dependency { version_id: None, project_id: Some("dep".into()), dependency_type: "required".into() }],
            },
            file: ModFile { url: url.into(), filename: "p.jar".into(), primary: true, size: 3, hashes: Hashes { sha1: sha.into(), sha512: String::new() } },
            as_dependency: false,
            required_by: None,
        };
        let evil = mk("https://evil.example/p.jar", "x");
        assert!(s.install(&dl, &[evil], &HashMap::new()).await.is_err());

        // file already present with the right hash → nothing is downloaded, entry is recorded
        touch(&s, "p.jar");
        let sha = crate::download::file_sha1(&s.mods_dir().join("p.jar")).unwrap();
        let ok = mk(&format!("{CDN_PREFIX}data/p/p.jar"), &sha);
        let done = s.install(&dl, &[ok], &HashMap::new()).await.unwrap();
        assert_eq!(done[0].requires, vec!["dep".to_string()]);
        assert_eq!(s.list().unwrap().len(), 1);

        // a different file with the same name is never overwritten
        let s2 = ModStore::new(td.path().join("other"));
        touch(&s2, "p.jar");
        let clash = mk(&format!("{CDN_PREFIX}data/p/p.jar"), "deadbeef");
        assert!(s2.install(&dl, &[clash], &HashMap::new()).await.is_err());
        assert!(s2.mods_dir().join("p.jar").is_file());
    }
}
