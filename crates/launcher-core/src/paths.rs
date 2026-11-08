use std::path::{Path, PathBuf};

/// All launcher data lives under one root. Libraries, assets and runtimes are shared
/// between profiles; every profile gets its own instance directory.
#[derive(Clone, Debug)]
pub struct Dirs {
    pub root: PathBuf,
}

impl Dirs {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }
    pub fn versions(&self) -> PathBuf { self.root.join("versions") }
    pub fn version_dir(&self, id: &str) -> PathBuf { self.versions().join(id) }
    pub fn version_json(&self, id: &str) -> PathBuf { self.version_dir(id).join(format!("{id}.json")) }
    pub fn version_jar(&self, id: &str) -> PathBuf { self.version_dir(id).join(format!("{id}.jar")) }
    pub fn natives(&self, id: &str) -> PathBuf { self.version_dir(id).join("natives") }
    pub fn libraries(&self) -> PathBuf { self.root.join("libraries") }
    pub fn assets(&self) -> PathBuf { self.root.join("assets") }
    pub fn runtimes(&self) -> PathBuf { self.root.join("runtimes") }
    pub fn instances(&self) -> PathBuf { self.root.join("instances") }
    pub fn instance(&self, profile_id: &str) -> PathBuf { self.instances().join(profile_id) }
    pub fn profiles_file(&self) -> PathBuf { self.root.join("profiles.json") }
    pub fn accounts_file(&self) -> PathBuf { self.root.join("accounts.json") }
    pub fn settings_file(&self) -> PathBuf { self.root.join("settings.json") }
}

/// Turn a Maven coordinate (`group:artifact:version[:classifier][@ext]`) into a repo-relative path.
pub fn maven_path(coord: &str) -> Option<String> {
    let (coord, ext) = match coord.split_once('@') {
        Some((c, e)) => (c, e),
        None => (coord, "jar"),
    };
    let mut it = coord.split(':');
    let group = it.next()?;
    let artifact = it.next()?;
    let version = it.next()?;
    let classifier = it.next();
    let file = match classifier {
        Some(c) => format!("{artifact}-{version}-{c}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!("{}/{}/{}/{}", group.replace('.', "/"), artifact, version, file))
}

/// `group:artifact[:classifier]` – identity used to de-duplicate libraries when merging versions.
pub fn maven_key(coord: &str) -> String {
    let c = coord.split('@').next().unwrap_or(coord);
    let mut it = c.split(':');
    let g = it.next().unwrap_or("");
    let a = it.next().unwrap_or("");
    let _v = it.next();
    match it.next() {
        Some(cl) => format!("{g}:{a}:{cl}"),
        None => format!("{g}:{a}"),
    }
}

pub fn ensure_parent(p: &Path) -> std::io::Result<()> {
    if let Some(parent) = p.parent() {
        std::fs::create_dir_all(parent)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn maven() {
        assert_eq!(
            maven_path("net.fabricmc:fabric-loader:0.16.0").unwrap(),
            "net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar"
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-linux").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"
        );
        assert_eq!(maven_key("org.ow2.asm:asm:9.6"), "org.ow2.asm:asm");
        assert_eq!(maven_key("org.lwjgl:lwjgl:3.3.3:natives-linux"), "org.lwjgl:lwjgl:natives-linux");
    }
}
