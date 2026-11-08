//! Find a suitable Java, or download a Temurin JRE into the launcher's runtime folder.
use crate::download::{Downloader, Progress, ProgressFn};
use crate::error::{Error, Result};
use crate::paths::Dirs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, serde::Serialize)]
pub struct JavaInstall {
    pub path: PathBuf,
    pub major: u32,
    pub version: String,
}

const EXE: &str = if cfg!(windows) { "java.exe" } else { "java" };

/// `openjdk version "21.0.12.1" ...` / `java version "1.8.0_351"` → (8, "1.8.0_351")
pub fn parse_version_output(out: &str) -> Option<(u32, String)> {
    let start = out.find('"')? + 1;
    let end = start + out[start..].find('"')?;
    let v = &out[start..end];
    let mut parts = v.split(|c: char| c == '.' || c == '_' || c == '-' || c == '+');
    let first: u32 = parts.next()?.parse().ok()?;
    let major = if first == 1 { parts.next()?.parse().ok()? } else { first };
    Some((major, v.to_string()))
}

pub fn probe(java: &Path) -> Option<JavaInstall> {
    let out = std::process::Command::new(java).arg("-version").output().ok()?;
    let text = String::from_utf8_lossy(&out.stderr).to_string() + &String::from_utf8_lossy(&out.stdout);
    let (major, version) = parse_version_output(&text)?;
    Some(JavaInstall { path: java.to_path_buf(), major, version })
}

fn find_bin_java(dir: &Path, depth: u32) -> Option<PathBuf> {
    let direct = dir.join("bin").join(EXE);
    if direct.is_file() {
        return Some(direct);
    }
    if depth == 0 {
        return None;
    }
    for e in std::fs::read_dir(dir).ok()?.flatten() {
        let p = e.path();
        if p.is_dir() {
            if let Some(f) = find_bin_java(&p, depth - 1) {
                return Some(f);
            }
        }
    }
    None
}

fn candidate_paths(dirs: &Dirs) -> Vec<PathBuf> {
    let mut c: Vec<PathBuf> = Vec::new();
    // launcher-managed runtimes first
    if let Ok(rd) = std::fs::read_dir(dirs.runtimes()) {
        for e in rd.flatten() {
            if let Some(j) = find_bin_java(&e.path(), 4) {
                c.push(j);
            }
        }
    }
    if let Ok(h) = std::env::var("JAVA_HOME") {
        c.push(Path::new(&h).join("bin").join(EXE));
    }
    let roots: &[&str] = if cfg!(windows) {
        &[
            r"C:\Program Files\Java",
            r"C:\Program Files\Eclipse Adoptium",
            r"C:\Program Files\Microsoft",
            r"C:\Program Files\Zulu",
            r"C:\Program Files\BellSoft",
            r"C:\Program Files\Amazon Corretto",
        ]
    } else if cfg!(target_os = "macos") {
        &["/Library/Java/JavaVirtualMachines"]
    } else {
        &["/usr/lib/jvm"]
    };
    for r in roots {
        if let Ok(rd) = std::fs::read_dir(r) {
            for e in rd.flatten() {
                let p = e.path();
                let mac = p.join("Contents").join("Home");
                let base = if mac.is_dir() { mac } else { p };
                let j = base.join("bin").join(EXE);
                if j.is_file() {
                    c.push(j);
                }
            }
        }
    }
    c.push(PathBuf::from(EXE)); // whatever is on PATH
    c
}

pub fn detect_all(dirs: &Dirs) -> Vec<JavaInstall> {
    let mut seen = std::collections::HashSet::new();
    let mut out = Vec::new();
    for p in candidate_paths(dirs) {
        if let Some(j) = probe(&p) {
            if seen.insert(j.path.canonicalize().unwrap_or(j.path.clone())) {
                out.push(j);
            }
        }
    }
    out
}

/// Pick the best install for a required major. Exact match wins. Newer majors are only
/// accepted for 17+ (Java 8-era versions and their loaders break on newer JVMs).
pub fn choose(installs: &[JavaInstall], required: u32) -> Option<&JavaInstall> {
    if let Some(j) = installs.iter().find(|j| j.major == required) {
        return Some(j);
    }
    if required >= 17 {
        return installs.iter().filter(|j| j.major > required).min_by_key(|j| j.major);
    }
    None
}

fn adoptium_url(major: u32) -> Result<String> {
    let os = if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "mac" } else { "linux" };
    let arch = if cfg!(target_arch = "aarch64") { "aarch64" } else if cfg!(target_arch = "x86") { "x32" } else { "x64" };
    Ok(format!("https://api.adoptium.net/v3/binary/latest/{major}/ga/{os}/{arch}/jre/hotspot/normal/eclipse"))
}

/// Returns a usable java for `required`, downloading a Temurin JRE if none is installed.
pub async fn ensure(dirs: &Dirs, dl: &Downloader, required: u32, on: &ProgressFn) -> Result<JavaInstall> {
    let installs = detect_all(dirs);
    if let Some(j) = choose(&installs, required) {
        return Ok(j.clone());
    }
    on(Progress { stage: format!("Downloading Java {required}"), done: 0, total: 1 });
    let target = dirs.runtimes().join(required.to_string());
    std::fs::create_dir_all(&target)?;
    let archive = target.join(if cfg!(windows) { "jre.zip" } else { "jre.tar.gz" });
    let url = adoptium_url(required)?;
    // no checksum available from this redirect endpoint; the archive is still extracted defensively
    dl.fetch(&crate::download::Job { url, dest: archive.clone(), sha1: None, size: None }).await?;
    on(Progress { stage: format!("Installing Java {required}"), done: 0, total: 1 });
    let a2 = archive.clone();
    let t2 = target.clone();
    tokio::task::spawn_blocking(move || -> Result<()> {
        if cfg!(windows) {
            let mut z = zip::ZipArchive::new(std::fs::File::open(&a2)?)?;
            z.extract(&t2)?; // zip crate rejects path traversal
        } else {
            let gz = flate2::read::GzDecoder::new(std::fs::File::open(&a2)?);
            tar::Archive::new(gz).unpack(&t2)?; // tar crate rejects ".." entries
        }
        Ok(())
    })
    .await
    .map_err(|e| Error::msg(e.to_string()))??;
    let _ = std::fs::remove_file(&archive);
    let java = find_bin_java(&target, 4).ok_or_else(|| Error::msg("downloaded Java runtime has no bin/java"))?;
    let j = probe(&java).ok_or_else(|| Error::msg("downloaded Java runtime failed to start"))?;
    on(Progress { stage: format!("Installing Java {required}"), done: 1, total: 1 });
    Ok(j)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parses_versions() {
        assert_eq!(parse_version_output("openjdk version \"21.0.12.1\" 2026-08-18").unwrap().0, 21);
        assert_eq!(parse_version_output("java version \"1.8.0_351\"").unwrap().0, 8);
        assert_eq!(parse_version_output("openjdk version \"17-ea\" 2021").unwrap().0, 17);
        assert!(parse_version_output("garbage").is_none());
    }
    fn ji(m: u32) -> JavaInstall {
        JavaInstall { path: format!("/j{m}").into(), major: m, version: m.to_string() }
    }
    #[test]
    fn choose_rules() {
        let v = vec![ji(8), ji(21), ji(17)];
        assert_eq!(choose(&v, 17).unwrap().major, 17);
        assert_eq!(choose(&v, 8).unwrap().major, 8);
        let only21 = vec![ji(21)];
        assert_eq!(choose(&only21, 17).unwrap().major, 21); // newer ok for 17+
        assert!(choose(&only21, 8).is_none()); // not for Java 8-era versions
    }
    #[test]
    fn detects_system_java_if_present() {
        // Smoke test: just must not panic
        let _ = detect_all(&Dirs::new(std::env::temp_dir().join("vyro-test-none")));
    }
}
