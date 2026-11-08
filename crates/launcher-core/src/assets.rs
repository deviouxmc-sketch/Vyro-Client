use crate::download::Job;
use crate::paths::Dirs;
use serde::Deserialize;
use std::collections::HashMap;
use std::path::PathBuf;

#[derive(Debug, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

#[derive(Debug, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
    #[serde(default, rename = "virtual")]
    pub is_virtual: bool,
    #[serde(default)]
    pub map_to_resources: bool,
}

pub const RESOURCES_URL: &str = "https://resources.download.minecraft.net";

pub fn object_path(dirs: &Dirs, hash: &str) -> PathBuf {
    dirs.assets().join("objects").join(&hash[..2]).join(hash)
}

pub fn jobs(index: &AssetIndex, dirs: &Dirs) -> Vec<Job> {
    index
        .objects
        .values()
        .map(|o| Job {
            url: format!("{RESOURCES_URL}/{}/{}", &o.hash[..2], o.hash),
            dest: object_path(dirs, &o.hash),
            sha1: Some(o.hash.clone()),
            size: Some(o.size),
        })
        .collect()
}

/// Pre-1.6 versions read assets by their real names instead of hashed object paths.
/// Returns the directory to hand to the game as `game_assets` / `assets_root` replacement.
pub fn materialize_legacy(
    index: &AssetIndex,
    dirs: &Dirs,
    index_id: &str,
    game_dir: &std::path::Path,
) -> crate::Result<Option<PathBuf>> {
    if !index.is_virtual && !index.map_to_resources {
        return Ok(None);
    }
    let target = if index.map_to_resources {
        game_dir.join("resources")
    } else {
        dirs.assets().join("virtual").join(index_id)
    };
    for (name, obj) in &index.objects {
        let dest = target.join(name);
        if dest.exists() {
            continue;
        }
        if let Some(p) = dest.parent() {
            std::fs::create_dir_all(p)?;
        }
        std::fs::copy(object_path(dirs, &obj.hash), &dest)?;
    }
    Ok(Some(target))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn builds_jobs() {
        let idx: AssetIndex = serde_json::from_str(
            r#"{"objects":{"icons/a.png":{"hash":"aabbcc","size":3}}}"#,
        )
        .unwrap();
        let j = jobs(&idx, &Dirs::new("/r"));
        assert_eq!(j[0].url, "https://resources.download.minecraft.net/aa/aabbcc");
        assert!(j[0].dest.ends_with("assets/objects/aa/aabbcc"));
        assert!(!idx.is_virtual);
    }
}
