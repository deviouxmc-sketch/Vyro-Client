//! Mod loader installation. Fabric and Quilt publish ready-made version profiles, so
//! installing them is "download one json". Forge/NeoForge need their installer run and are
//! not implemented yet – we say so instead of pretending.
use crate::download::Downloader;
use crate::error::{Error, Result};
use crate::paths::{ensure_parent, Dirs};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Vanilla,
    Fabric,
    Quilt,
    Forge,
    #[serde(rename = "neoforge")]
    NeoForge,
}

impl Loader {
    pub fn is_supported(self) -> bool {
        matches!(self, Loader::Vanilla | Loader::Fabric | Loader::Quilt)
    }
}

#[derive(Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}
#[derive(Deserialize)]
struct LoaderInfo {
    version: String,
    stable: Option<bool>,
}

fn meta_base(l: Loader) -> Result<&'static str> {
    match l {
        Loader::Fabric => Ok("https://meta.fabricmc.net/v2/versions/loader"),
        Loader::Quilt => Ok("https://meta.quiltmc.org/v3/versions/loader"),
        _ => Err(Error::msg(format!("{l:?} installation is not implemented yet"))),
    }
}

pub async fn latest_loader_version(dl: &Downloader, l: Loader, mc: &str) -> Result<String> {
    let url = format!("{}/{mc}", meta_base(l)?);
    let list: Vec<LoaderEntry> = dl.json(&url).await?;
    if list.is_empty() {
        return Err(Error::msg(format!("{l:?} does not support Minecraft {mc}")));
    }
    // Quilt does not flag `stable`; first entry is newest.
    let pick = list.iter().find(|e| e.loader.stable == Some(true)).unwrap_or(&list[0]);
    Ok(pick.loader.version.clone())
}

pub fn version_id(l: Loader, mc: &str, loader_version: &str) -> String {
    match l {
        Loader::Vanilla => mc.to_string(),
        Loader::Fabric => format!("fabric-loader-{loader_version}-{mc}"),
        Loader::Quilt => format!("quilt-loader-{loader_version}-{mc}"),
        _ => format!("{l:?}-{loader_version}-{mc}").to_lowercase(),
    }
}

/// Installs the loader's version json and returns the version id to launch.
pub async fn install(dirs: &Dirs, dl: &Downloader, l: Loader, mc: &str, loader_version: Option<&str>) -> Result<String> {
    if l == Loader::Vanilla {
        return Ok(mc.to_string());
    }
    let base = meta_base(l)?;
    let lv = match loader_version {
        Some(v) => v.to_string(),
        None => latest_loader_version(dl, l, mc).await?,
    };
    let id = version_id(l, mc, &lv);
    let path = dirs.version_json(&id);
    if !path.exists() {
        let url = format!("{base}/{mc}/{lv}/profile/json");
        let body = dl.text(&url).await?;
        // validate before writing so a bad response never poisons the cache
        let _: crate::version::VersionJson = serde_json::from_str(&body)?;
        ensure_parent(&path)?;
        std::fs::write(&path, body)?;
    }
    Ok(id)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn ids() {
        assert_eq!(version_id(Loader::Fabric, "1.20.4", "0.16.9"), "fabric-loader-0.16.9-1.20.4");
        assert_eq!(version_id(Loader::Vanilla, "1.20.4", ""), "1.20.4");
        assert!(!Loader::Forge.is_supported());
        assert!(meta_base(Loader::Forge).is_err());
    }
    #[test]
    fn loader_serde() {
        assert_eq!(serde_json::to_string(&Loader::NeoForge).unwrap(), "\"neoforge\"");
        let l: Loader = serde_json::from_str("\"fabric\"").unwrap();
        assert_eq!(l, Loader::Fabric);
    }
}
