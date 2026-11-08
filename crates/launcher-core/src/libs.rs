//! Turns a merged version into: a classpath, a list of files to download, and natives to unpack.
use crate::download::Job;
use crate::paths::{maven_path, Dirs};
use crate::rules::{allowed, Env};
use crate::version::{Artifact, Library};
use std::collections::HashSet;
use std::path::PathBuf;

pub const DEFAULT_MAVEN: &str = "https://libraries.minecraft.net/";

#[derive(Debug, Default)]
pub struct LibPlan {
    pub classpath: Vec<PathBuf>,
    pub downloads: Vec<Job>,
    /// (jar path, exclude prefixes) – legacy "natives" classifiers that must be unpacked.
    pub natives: Vec<(PathBuf, Vec<String>)>,
}

fn job_for(art: &Artifact, dirs: &Dirs, fallback_path: Option<String>) -> Option<(Job, PathBuf)> {
    let rel = art.path.clone().or(fallback_path)?;
    let dest = dirs.libraries().join(&rel);
    Some((
        Job { url: art.url.clone(), dest: dest.clone(), sha1: art.sha1.clone(), size: art.size },
        dest,
    ))
}

fn native_classifier(lib: &Library, env: &Env) -> Option<String> {
    let n = lib.natives.as_ref()?.get(env.os)?;
    let bits = if env.arch == "x86" { "32" } else { "64" };
    Some(n.replace("${arch}", bits))
}

pub fn plan(libs: &[Library], dirs: &Dirs, env: &Env) -> LibPlan {
    let mut out = LibPlan::default();
    let mut seen = HashSet::new();
    for lib in libs {
        if !allowed(&lib.rules, env) {
            continue;
        }
        // 1) Legacy natives: unpack, never on the classpath.
        if let Some(classifier) = native_classifier(lib, env) {
            if let Some(art) = lib.downloads.as_ref().and_then(|d| d.classifiers.as_ref()).and_then(|c| c.get(&classifier)) {
                if let Some((job, dest)) = job_for(art, dirs, None) {
                    out.downloads.push(job);
                    let excl = lib.extract.as_ref().map(|e| e.exclude.clone()).unwrap_or_default();
                    out.natives.push((dest, excl));
                }
            }
            // The "main" artifact of such a library (if any) is still a normal classpath entry below.
        } else if lib.natives.is_some() {
            // natives map exists but not for this OS → nothing to do for the native part
        }

        // 2) Main artifact (explicit downloads block, or Maven coordinates + repo url).
        let explicit = lib.downloads.as_ref().and_then(|d| d.artifact.clone());
        let art = match explicit {
            Some(a) => Some(a),
            None if lib.downloads.is_none() => maven_path(&lib.name).map(|p| {
                let base = lib.url.clone().unwrap_or_else(|| DEFAULT_MAVEN.to_string());
                let base = if base.ends_with('/') { base } else { format!("{base}/") };
                Artifact { path: Some(p.clone()), url: format!("{base}{p}"), sha1: None, size: None }
            }),
            None => None,
        };
        if let Some(art) = art {
            if let Some((job, dest)) = job_for(&art, dirs, maven_path(&lib.name)) {
                if seen.insert(dest.clone()) {
                    out.classpath.push(dest);
                    out.downloads.push(job);
                }
            }
        }
    }
    out
}

/// Unpack native libraries (`.so/.dll/.dylib`) into `dest`, skipping excluded entries.
pub fn extract_natives(jar: &std::path::Path, excludes: &[String], dest: &std::path::Path) -> crate::Result<()> {
    std::fs::create_dir_all(dest)?;
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar)?)?;
    for i in 0..zip.len() {
        let mut f = zip.by_index(i)?;
        let name = f.name().to_string();
        if f.is_dir() || excludes.iter().any(|e| name.starts_with(e)) {
            continue;
        }
        // enclosed_name protects against path traversal
        let Some(rel) = f.enclosed_name().map(|p| p.to_owned()) else { continue };
        let Some(file_name) = rel.file_name() else { continue };
        let mut out = std::fs::File::create(dest.join(file_name))?;
        std::io::copy(&mut f, &mut out)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::version::{fixtures, VersionJson};
    use std::collections::HashMap;

    fn env(os: &'static str) -> Env {
        Env { os, arch: "x86_64", features: HashMap::new() }
    }

    #[test]
    fn modern_linux_includes_only_linux_natives_on_classpath() {
        let v: VersionJson = serde_json::from_str(fixtures::MODERN).unwrap();
        let dirs = Dirs::new("/tmp/vyro");
        let p = plan(&v.libraries, &dirs, &env("linux"));
        assert_eq!(p.classpath.len(), 2);
        assert!(p.classpath.iter().any(|c| c.to_string_lossy().ends_with("natives-linux.jar")));
        assert!(!p.classpath.iter().any(|c| c.to_string_lossy().contains("windows")));
        assert!(p.natives.is_empty());
        assert_eq!(p.downloads.len(), 2);
    }

    #[test]
    fn legacy_natives_are_extracted_not_classpathed() {
        let v: VersionJson = serde_json::from_str(fixtures::LEGACY).unwrap();
        let dirs = Dirs::new("/tmp/vyro");
        let p = plan(&v.libraries, &dirs, &env("linux"));
        assert!(p.classpath.is_empty());
        assert_eq!(p.natives.len(), 1);
        assert_eq!(p.natives[0].1, vec!["META-INF/".to_string()]);
        // wrong OS → nothing
        let p = plan(&v.libraries, &dirs, &env("windows"));
        assert!(p.natives.is_empty());
    }

    #[test]
    fn loader_libs_without_downloads_use_maven_url() {
        let v: VersionJson = serde_json::from_str(fixtures::FABRIC).unwrap();
        let dirs = Dirs::new("/tmp/vyro");
        let p = plan(&v.libraries, &dirs, &env("linux"));
        assert_eq!(p.downloads[0].url, "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar");
        assert!(p.downloads[0].sha1.is_none());
    }

    #[test]
    fn extract_skips_excluded_and_blocks_traversal() {
        use std::io::Write;
        let td = tempfile::tempdir().unwrap();
        let jar = td.path().join("n.jar");
        {
            let f = std::fs::File::create(&jar).unwrap();
            let mut z = zip::ZipWriter::new(f);
            let o = zip::write::FileOptions::default();
            z.start_file("liblwjgl.so", o).unwrap();
            z.write_all(b"x").unwrap();
            z.start_file("META-INF/MANIFEST.MF", o).unwrap();
            z.write_all(b"m").unwrap();
            z.start_file("../evil.so", o).unwrap();
            z.write_all(b"e").unwrap();
            z.finish().unwrap();
        }
        let dest = td.path().join("natives");
        extract_natives(&jar, &["META-INF/".into()], &dest).unwrap();
        assert!(dest.join("liblwjgl.so").exists());
        assert!(!dest.join("MANIFEST.MF").exists());
        assert!(!td.path().join("evil.so").exists());
        assert!(!dest.join("evil.so").exists());
    }
}
