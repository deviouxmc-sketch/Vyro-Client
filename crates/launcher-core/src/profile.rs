use crate::error::{Error, Result};
use crate::loaders::Loader;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    pub loader: Loader,
    #[serde(default)]
    pub loader_version: Option<String>,
    pub ram_mb: u32,
    #[serde(default)]
    pub jvm_args: Vec<String>,
    /// Overrides the global Java path when set.
    #[serde(default)]
    pub java_path: Option<String>,
    #[serde(default)]
    pub resolution: Option<(u32, u32)>,
    #[serde(default)]
    pub fullscreen: bool,
}

pub fn slug(name: &str) -> String {
    let s: String = name
        .to_lowercase()
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c } else { '-' })
        .collect();
    let s = s.split('-').filter(|p| !p.is_empty()).collect::<Vec<_>>().join("-");
    if s.is_empty() { "profile".into() } else { s }
}

pub struct ProfileStore {
    path: PathBuf,
}

impl ProfileStore {
    pub fn new(path: PathBuf) -> Self {
        Self { path }
    }

    pub fn list(&self) -> Result<Vec<Profile>> {
        if !self.path.exists() {
            return Ok(vec![]);
        }
        Ok(serde_json::from_slice(&std::fs::read(&self.path)?)?)
    }

    fn write(&self, p: &[Profile]) -> Result<()> {
        if let Some(d) = self.path.parent() {
            std::fs::create_dir_all(d)?;
        }
        let tmp = self.path.with_extension("tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(p)?)?;
        std::fs::rename(tmp, &self.path)?; // atomic replace: a crash never leaves half a file
        Ok(())
    }

    fn unique_id(existing: &[Profile], name: &str) -> String {
        let base = slug(name);
        let mut id = base.clone();
        let mut n = 2;
        while existing.iter().any(|p| p.id == id) {
            id = format!("{base}-{n}");
            n += 1;
        }
        id
    }

    pub fn create(&self, mut p: Profile) -> Result<Profile> {
        let mut all = self.list()?;
        validate(&p)?;
        p.id = Self::unique_id(&all, &p.name);
        all.push(p.clone());
        self.write(&all)?;
        Ok(p)
    }

    pub fn update(&self, p: Profile) -> Result<Profile> {
        validate(&p)?;
        let mut all = self.list()?;
        let slot = all.iter_mut().find(|x| x.id == p.id).ok_or_else(|| Error::msg("profile not found"))?;
        *slot = p.clone();
        self.write(&all)?;
        Ok(p)
    }

    pub fn delete(&self, id: &str) -> Result<()> {
        let mut all = self.list()?;
        let before = all.len();
        all.retain(|p| p.id != id);
        if all.len() == before {
            return Err(Error::msg("profile not found"));
        }
        self.write(&all)
    }

    /// Duplicates settings only; the caller copies the instance folder if it wants the files too.
    pub fn duplicate(&self, id: &str) -> Result<Profile> {
        let all = self.list()?;
        let src = all.iter().find(|p| p.id == id).ok_or_else(|| Error::msg("profile not found"))?.clone();
        let mut copy = src;
        copy.name = format!("{} copy", copy.name);
        self.create(copy)
    }
}

fn validate(p: &Profile) -> Result<()> {
    if p.name.trim().is_empty() {
        return Err(Error::msg("profile name cannot be empty"));
    }
    if p.mc_version.trim().is_empty() {
        return Err(Error::msg("choose a Minecraft version"));
    }
    if p.ram_mb < 512 {
        return Err(Error::msg("at least 512 MB of RAM is required"));
    }
    if !p.loader.is_supported() {
        return Err(Error::msg("this mod loader is not supported yet"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn p(name: &str) -> Profile {
        Profile {
            id: String::new(),
            name: name.into(),
            mc_version: "1.20.4".into(),
            loader: Loader::Fabric,
            loader_version: None,
            ram_mb: 4096,
            jvm_args: vec![],
            java_path: None,
            resolution: None,
            fullscreen: false,
        }
    }
    #[test]
    fn slugs() {
        assert_eq!(slug("My PvP  Profile!"), "my-pvp-profile");
        assert_eq!(slug("!!!"), "profile");
    }
    #[test]
    fn crud() {
        let td = tempfile::tempdir().unwrap();
        let s = ProfileStore::new(td.path().join("profiles.json"));
        let a = s.create(p("PvP")).unwrap();
        let b = s.create(p("PvP")).unwrap();
        assert_eq!((a.id.as_str(), b.id.as_str()), ("pvp", "pvp-2"));
        let c = s.duplicate("pvp").unwrap();
        assert_eq!(c.name, "PvP copy");
        let mut a2 = a.clone();
        a2.ram_mb = 6144;
        s.update(a2).unwrap();
        assert_eq!(s.list().unwrap().iter().find(|x| x.id == "pvp").unwrap().ram_mb, 6144);
        s.delete("pvp-2").unwrap();
        assert_eq!(s.list().unwrap().len(), 2);
        assert!(s.delete("zzz").is_err());
    }
    #[test]
    fn validation() {
        let td = tempfile::tempdir().unwrap();
        let s = ProfileStore::new(td.path().join("p.json"));
        assert!(s.create(p("  ")).is_err());
        let mut low = p("x");
        low.ram_mb = 100;
        assert!(s.create(low).is_err());
        let mut forge = p("x");
        forge.loader = Loader::Forge;
        assert!(s.create(forge).is_err());
    }
}
