//! Orchestrates everything needed before launch: version json chain, client jar,
//! libraries, natives and assets.
use crate::assets::{self, AssetIndex};
use crate::download::{Downloader, Job, Progress, ProgressFn};
use crate::error::{Error, Result};
use crate::libs;
use crate::paths::Dirs;
use crate::rules::Env;
use crate::version::{self, Manifest, Merged, VersionJson, MANIFEST_URL};
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct Prepared {
    pub merged: Merged,
    pub classpath: Vec<PathBuf>,
    pub client_jar: PathBuf,
    pub natives_dir: PathBuf,
    pub assets_root: PathBuf,
}

#[derive(Clone)]
pub struct Installer {
    pub dirs: Dirs,
    pub dl: Downloader,
}

impl Installer {
    pub fn new(dirs: Dirs) -> Result<Self> {
        Ok(Self { dirs, dl: Downloader::new()? })
    }

    pub async fn manifest(&self) -> Result<Manifest> {
        self.dl.json(MANIFEST_URL).await
    }

    async fn load_json(&self, id: &str, manifest: &mut Option<Manifest>) -> Result<VersionJson> {
        if let Some(v) = version::read_local(&self.dirs, id)? {
            return Ok(v);
        }
        if manifest.is_none() {
            *manifest = Some(self.manifest().await?);
        }
        let entry = manifest
            .as_ref()
            .and_then(|m| m.versions.iter().find(|e| e.id == id))
            .ok_or_else(|| Error::msg(format!("unknown Minecraft version '{id}'")))?
            .clone();
        let dest = self.dirs.version_json(id);
        self.dl.fetch(&Job { url: entry.url, dest: dest.clone(), sha1: entry.sha1, size: None }).await?;
        Ok(serde_json::from_slice(&std::fs::read(dest)?)?)
    }

    /// Resolve `id` and everything it inherits from into one flat description.
    pub async fn resolve(&self, id: &str) -> Result<Merged> {
        let mut manifest = None;
        let mut chain = vec![self.load_json(id, &mut manifest).await?];
        while let Some(parent) = chain.last().and_then(|v| v.inherits_from.clone()) {
            if chain.len() > 8 {
                return Err(Error::msg("version inheritance chain too deep"));
            }
            chain.push(self.load_json(&parent, &mut manifest).await?);
        }
        let base_id = chain.last().map(|v| v.id.clone()).unwrap_or_else(|| id.to_string());
        let mut it = chain.into_iter().rev();
        let mut acc = it.next().ok_or_else(|| Error::msg("empty version chain"))?;
        for child in it {
            acc = version::merge(&acc, &child);
        }
        version::to_merged(acc, base_id)
    }

    /// Download and unpack everything required to start `id`.
    pub async fn prepare(&self, id: &str, game_dir: &std::path::Path, on: &ProgressFn) -> Result<Prepared> {
        on(Progress { stage: "Resolving version".into(), done: 0, total: 1 });
        let merged = self.resolve(id).await?;
        let env = Env::current();

        // client jar (of the vanilla base version)
        let client_jar = self.dirs.version_jar(&merged.base_id);
        let dl_info = merged
            .client_download
            .clone()
            .ok_or_else(|| Error::msg("version has no client download"))?;
        self.dl
            .fetch_all(
                "Minecraft client",
                vec![Job { url: dl_info.url, dest: client_jar.clone(), sha1: dl_info.sha1, size: dl_info.size }],
                1,
                on,
            )
            .await?;

        // libraries
        let plan = libs::plan(&merged.libraries, &self.dirs, &env);
        self.dl.fetch_all("Libraries", plan.downloads.clone(), 12, on).await?;

        // natives (legacy classifier jars)
        let natives_dir = self.dirs.natives(&merged.id);
        std::fs::create_dir_all(&natives_dir)?;
        let marker = natives_dir.join(".vyro-natives-ok");
        if !plan.natives.is_empty() && !marker.exists() {
            for e in std::fs::read_dir(&natives_dir)?.flatten() {
                if e.path().is_file() {
                    let _ = std::fs::remove_file(e.path());
                }
            }
            for (jar, excl) in &plan.natives {
                libs::extract_natives(jar, excl, &natives_dir)?;
            }
            std::fs::write(&marker, b"ok")?;
        }

        // assets
        let ai = &merged.asset_index;
        let index_path = self.dirs.assets().join("indexes").join(format!("{}.json", ai.id));
        self.dl
            .fetch_all(
                "Asset index",
                vec![Job { url: ai.url.clone(), dest: index_path.clone(), sha1: ai.sha1.clone(), size: ai.size }],
                1,
                on,
            )
            .await?;
        let index: AssetIndex = serde_json::from_slice(&std::fs::read(&index_path)?)?;
        self.dl.fetch_all("Game assets", assets::jobs(&index, &self.dirs), 24, on).await?;
        std::fs::create_dir_all(game_dir)?;
        let assets_root = assets::materialize_legacy(&index, &self.dirs, &ai.id, game_dir)?
            .unwrap_or_else(|| self.dirs.assets());

        Ok(Prepared { merged, classpath: plan.classpath, client_jar, natives_dir, assets_root })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::download::noop_progress;
    use std::io::Write;

    /// Everything is pre-seeded with correct sizes, so `prepare` must succeed with no network.
    #[tokio::test]
    async fn prepare_offline_when_cache_is_complete() {
        let td = tempfile::tempdir().unwrap();
        let dirs = Dirs::new(td.path());
        let w = |p: PathBuf, b: &[u8]| {
            std::fs::create_dir_all(p.parent().unwrap()).unwrap();
            std::fs::write(p, b).unwrap();
        };
        let idx = br#"{"objects":{"icons/a.png":{"hash":"abcdef","size":3}}}"#;
        w(dirs.assets().join("indexes/x.json"), idx);
        w(dirs.assets().join("objects/ab/abcdef"), b"123");
        w(dirs.version_jar("t1"), b"JAR!");
        w(dirs.libraries().join("a/b.jar"), b"ab");
        // legacy natives jar
        let nat = dirs.libraries().join("n/n.jar");
        std::fs::create_dir_all(nat.parent().unwrap()).unwrap();
        {
            let mut z = zip::ZipWriter::new(std::fs::File::create(&nat).unwrap());
            z.start_file("libfoo.so", zip::write::FileOptions::default()).unwrap();
            z.write_all(b"so").unwrap();
            z.finish().unwrap();
        }
        let nat_size = std::fs::metadata(&nat).unwrap().len();
        let os = Env::current().os;
        let json = format!(
            r#"{{"id":"t1","mainClass":"M","assets":"x",
            "assetIndex":{{"id":"x","url":"http://127.0.0.1:1/x","size":{}}},
            "downloads":{{"client":{{"url":"http://127.0.0.1:1/c","size":4}}}},
            "javaVersion":{{"majorVersion":17}},
            "libraries":[
              {{"name":"a:b:1","downloads":{{"artifact":{{"path":"a/b.jar","url":"http://127.0.0.1:1/b","size":2}}}}}},
              {{"name":"n:n:1","natives":{{"{os}":"natives"}},
                "downloads":{{"classifiers":{{"natives":{{"path":"n/n.jar","url":"http://127.0.0.1:1/n","size":{}}}}}}}}}
            ]}}"#,
            idx.len(),
            nat_size
        );
        w(dirs.version_json("t1"), json.as_bytes());

        let inst = Installer::new(dirs.clone()).unwrap();
        let game = td.path().join("game");
        let p = inst.prepare("t1", &game, &noop_progress()).await.unwrap();
        assert_eq!(p.merged.java_major, 17);
        assert_eq!(p.classpath.len(), 1);
        assert_eq!(p.client_jar, dirs.version_jar("t1"));
        assert!(p.natives_dir.join("libfoo.so").exists());
        assert_eq!(p.assets_root, dirs.assets());
    }

    #[tokio::test]
    async fn resolve_follows_inheritance_from_local_files() {
        let td = tempfile::tempdir().unwrap();
        let dirs = Dirs::new(td.path());
        for (id, body) in [("1.20.4", version::fixtures::MODERN), ("fabric-loader-0.16.9-1.20.4", version::fixtures::FABRIC)] {
            std::fs::create_dir_all(dirs.version_dir(id)).unwrap();
            std::fs::write(dirs.version_json(id), body).unwrap();
        }
        let inst = Installer::new(dirs).unwrap();
        let m = inst.resolve("fabric-loader-0.16.9-1.20.4").await.unwrap();
        assert_eq!(m.base_id, "1.20.4");
        assert_eq!(m.java_major, 17);
        assert!(m.main_class.contains("Knot"));
        assert!(m.client_download.is_some());
    }
}
