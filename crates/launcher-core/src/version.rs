use crate::error::{Error, Result};
use crate::paths::{maven_key, Dirs};
use crate::rules::Rule;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

// ---------- version manifest (list of all versions) ----------

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String, // release | snapshot | old_beta | old_alpha
    pub url: String,
    pub sha1: Option<String>,
    #[serde(rename = "releaseTime")]
    pub release_time: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Manifest {
    pub versions: Vec<ManifestEntry>,
}

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

// ---------- per-version json ----------

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum StrOrVec {
    One(String),
    Many(Vec<String>),
}

impl StrOrVec {
    pub fn into_vec(self) -> Vec<String> {
        match self {
            StrOrVec::One(s) => vec![s],
            StrOrVec::Many(v) => v,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum Arg {
    Plain(String),
    Conditional { rules: Option<Vec<Rule>>, value: StrOrVec },
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Arg>,
    #[serde(default)]
    pub jvm: Vec<Arg>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Artifact {
    pub path: Option<String>,
    pub url: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct LibDownloads {
    pub artifact: Option<Artifact>,
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    pub name: String,
    pub downloads: Option<LibDownloads>,
    pub rules: Option<Vec<Rule>>,
    pub natives: Option<HashMap<String, String>>,
    pub extract: Option<Extract>,
    /// Maven repo base for loader-style libraries that have no `downloads` block.
    pub url: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub url: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DownloadInfo {
    pub url: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct Downloads {
    pub client: Option<DownloadInfo>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct JavaVersion {
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: Option<String>,
    #[serde(rename = "mainClass")]
    pub main_class: Option<String>,
    pub arguments: Option<Arguments>,
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndexRef>,
    pub assets: Option<String>,
    pub downloads: Option<Downloads>,
    #[serde(rename = "javaVersion")]
    pub java_version: Option<JavaVersion>,
    #[serde(rename = "type")]
    pub kind: Option<String>,
}

/// A version with its whole `inheritsFrom` chain flattened.
#[derive(Debug, Clone)]
pub struct Merged {
    pub id: String,
    /// Root of the inheritance chain – the vanilla version whose jar we run.
    pub base_id: String,
    pub main_class: String,
    pub jvm: Vec<Arg>,
    pub game: Vec<Arg>,
    pub legacy_args: Option<String>,
    pub libraries: Vec<Library>,
    pub asset_index: AssetIndexRef,
    pub client_download: Option<DownloadInfo>,
    pub java_major: u32,
    pub kind: String,
}

/// Merge `child` on top of `parent`. Child libraries come first and replace parent ones
/// with the same `group:artifact[:classifier]` (loaders ship newer ASM etc.).
pub fn merge(parent: &VersionJson, child: &VersionJson) -> VersionJson {
    let mut libs = child.libraries.clone();
    let child_keys: std::collections::HashSet<String> =
        child.libraries.iter().map(|l| maven_key(&l.name)).collect();
    for l in &parent.libraries {
        if !child_keys.contains(&maven_key(&l.name)) {
            libs.push(l.clone());
        }
    }
    let args = match (&parent.arguments, &child.arguments) {
        (None, None) => None,
        (p, c) => {
            let mut a = Arguments::default();
            if let Some(p) = p {
                a.jvm.extend(p.jvm.clone());
                a.game.extend(p.game.clone());
            }
            if let Some(c) = c {
                a.jvm.extend(c.jvm.clone());
                a.game.extend(c.game.clone());
            }
            Some(a)
        }
    };
    VersionJson {
        id: child.id.clone(),
        inherits_from: None,
        main_class: child.main_class.clone().or_else(|| parent.main_class.clone()),
        arguments: args,
        minecraft_arguments: child.minecraft_arguments.clone().or_else(|| parent.minecraft_arguments.clone()),
        libraries: libs,
        asset_index: child.asset_index.clone().or_else(|| parent.asset_index.clone()),
        assets: child.assets.clone().or_else(|| parent.assets.clone()),
        downloads: child.downloads.clone().or_else(|| parent.downloads.clone()),
        java_version: child.java_version.clone().or_else(|| parent.java_version.clone()),
        kind: child.kind.clone().or_else(|| parent.kind.clone()),
    }
}

pub fn to_merged(v: VersionJson, base_id: String) -> Result<Merged> {
    Ok(Merged {
        id: v.id,
        base_id,
        main_class: v.main_class.ok_or_else(|| Error::msg("version json has no mainClass"))?,
        jvm: v.arguments.as_ref().map(|a| a.jvm.clone()).unwrap_or_default(),
        game: v.arguments.as_ref().map(|a| a.game.clone()).unwrap_or_default(),
        legacy_args: v.minecraft_arguments,
        libraries: v.libraries,
        asset_index: v.asset_index.ok_or_else(|| Error::msg("version json has no assetIndex"))?,
        client_download: v.downloads.and_then(|d| d.client),
        java_major: v.java_version.map(|j| j.major_version).unwrap_or(8),
        kind: v.kind.unwrap_or_else(|| "release".into()),
    })
}

pub fn read_local(dirs: &Dirs, id: &str) -> Result<Option<VersionJson>> {
    let p = dirs.version_json(id);
    if !p.exists() {
        return Ok(None);
    }
    Ok(Some(serde_json::from_slice(&std::fs::read(p)?)?))
}

#[cfg(test)]
pub mod fixtures {
    /// Trimmed copy of the structure Mojang ships for a modern (1.20.x-style) version.
    pub const MODERN: &str = r#"{
      "id": "1.20.4",
      "type": "release",
      "mainClass": "net.minecraft.client.main.Main",
      "assets": "12",
      "assetIndex": {"id":"12","url":"https://example.invalid/12.json","sha1":"abc","size":10},
      "downloads": {"client": {"url":"https://example.invalid/client.jar","sha1":"def","size":100}},
      "javaVersion": {"majorVersion": 17},
      "arguments": {
        "game": ["--username","${auth_player_name}","--version","${version_name}",
                 "--gameDir","${game_directory}","--assetsDir","${assets_root}",
                 "--assetIndex","${assets_index_name}","--uuid","${auth_uuid}",
                 "--accessToken","${auth_access_token}","--userType","${user_type}",
                 "--versionType","${version_type}",
                 {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],
                  "value":["--width","${resolution_width}","--height","${resolution_height}"]},
                 {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"}],
        "jvm": [
          {"rules":[{"action":"allow","os":{"name":"osx"}}],"value":["-XstartOnFirstThread"]},
          "-Djava.library.path=${natives_directory}",
          "-Dminecraft.launcher.brand=${launcher_name}",
          "-cp","${classpath}"]
      },
      "libraries": [
        {"name":"org.lwjgl:lwjgl:3.3.2",
         "downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.3.2/lwjgl-3.3.2.jar","url":"https://example.invalid/lwjgl.jar","sha1":"1","size":1}}},
        {"name":"org.lwjgl:lwjgl:3.3.2:natives-linux",
         "downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.3.2/lwjgl-3.3.2-natives-linux.jar","url":"https://example.invalid/lwjgl-nl.jar","sha1":"2","size":1}},
         "rules":[{"action":"allow","os":{"name":"linux"}}]},
        {"name":"org.lwjgl:lwjgl:3.3.2:natives-windows",
         "downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.3.2/lwjgl-3.3.2-natives-windows.jar","url":"https://example.invalid/lwjgl-nw.jar","sha1":"3","size":1}},
         "rules":[{"action":"allow","os":{"name":"windows"}}]}
      ]
    }"#;

    pub const LEGACY: &str = r#"{
      "id": "1.8.9",
      "type": "release",
      "mainClass": "net.minecraft.client.main.Main",
      "assets": "1.8",
      "assetIndex": {"id":"1.8","url":"https://example.invalid/1.8.json"},
      "minecraftArguments": "--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}",
      "libraries": [
        {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
         "natives":{"linux":"natives-linux","windows":"natives-windows","osx":"natives-osx"},
         "extract":{"exclude":["META-INF/"]},
         "downloads":{"classifiers":{
            "natives-linux":{"path":"org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar","url":"https://example.invalid/n.jar","sha1":"9","size":5}}}}
      ]
    }"#;

    pub const FABRIC: &str = r#"{
      "id": "fabric-loader-0.16.9-1.20.4",
      "inheritsFrom": "1.20.4",
      "type": "release",
      "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
      "arguments": {"game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "]},
      "libraries": [
        {"name":"net.fabricmc:fabric-loader:0.16.9","url":"https://maven.fabricmc.net/"},
        {"name":"org.lwjgl:lwjgl:3.3.3","url":"https://maven.fabricmc.net/"}
      ]
    }"#;
}

#[cfg(test)]
mod tests {
    use super::fixtures::*;
    use super::*;

    #[test]
    fn parses_modern_and_legacy() {
        let m: VersionJson = serde_json::from_str(MODERN).unwrap();
        assert_eq!(m.libraries.len(), 3);
        assert_eq!(m.arguments.as_ref().unwrap().game.len(), 20);
        let l: VersionJson = serde_json::from_str(LEGACY).unwrap();
        assert!(l.minecraft_arguments.is_some());
        assert!(l.arguments.is_none());
    }

    #[test]
    fn fabric_merge() {
        let p: VersionJson = serde_json::from_str(MODERN).unwrap();
        let c: VersionJson = serde_json::from_str(FABRIC).unwrap();
        let m = merge(&p, &c);
        assert_eq!(m.id, "fabric-loader-0.16.9-1.20.4");
        assert_eq!(m.main_class.as_deref(), Some("net.fabricmc.loader.impl.launch.knot.KnotClient"));
        // child lwjgl 3.3.3 replaces parent 3.3.2 (same key); natives classifiers are different keys and stay
        let names: Vec<_> = m.libraries.iter().map(|l| l.name.as_str()).collect();
        assert!(names.contains(&"org.lwjgl:lwjgl:3.3.3"));
        assert!(!names.contains(&"org.lwjgl:lwjgl:3.3.2"));
        assert!(names.contains(&"net.fabricmc:fabric-loader:0.16.9"));
        // assetIndex + java version inherited
        assert_eq!(m.asset_index.as_ref().unwrap().id, "12");
        let merged = to_merged(m, "1.20.4".into()).unwrap();
        assert_eq!(merged.java_major, 17);
        assert_eq!(merged.base_id, "1.20.4");
    }
}
