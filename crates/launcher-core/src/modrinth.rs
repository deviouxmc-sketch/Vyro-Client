//! Modrinth API client and the dependency resolver used by the mod manager.
use crate::error::{Error, Result};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet, VecDeque};

pub const API: &str = "https://api.modrinth.com/v2";
/// Downloads are only accepted from Modrinth's own CDN.
pub const CDN_PREFIX: &str = "https://cdn.modrinth.com/";

// ------------------------------------------------------------------ data types

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchHit {
    pub project_id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub author: String,
    pub downloads: u64,
    pub icon_url: Option<String>,
    #[serde(default)]
    pub categories: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResult {
    pub hits: Vec<SearchHit>,
    pub total_hits: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GalleryImage {
    pub url: String,
    pub title: Option<String>,
    #[serde(default)]
    pub featured: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Project {
    pub id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    #[serde(default)]
    pub body: String,
    pub icon_url: Option<String>,
    #[serde(default)]
    pub gallery: Vec<GalleryImage>,
    #[serde(default)]
    pub downloads: u64,
    #[serde(default)]
    pub categories: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Hashes {
    pub sha1: String,
    #[serde(default)]
    pub sha512: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModFile {
    pub url: String,
    pub filename: String,
    #[serde(default)]
    pub primary: bool,
    #[serde(default)]
    pub size: u64,
    pub hashes: Hashes,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Dependency {
    pub version_id: Option<String>,
    pub project_id: Option<String>,
    /// required | optional | incompatible | embedded
    pub dependency_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModVersion {
    pub id: String,
    pub project_id: String,
    #[serde(default)]
    pub name: String,
    pub version_number: String,
    /// release | beta | alpha
    pub version_type: String,
    #[serde(default)]
    pub game_versions: Vec<String>,
    #[serde(default)]
    pub loaders: Vec<String>,
    #[serde(default)]
    pub files: Vec<ModFile>,
    #[serde(default)]
    pub dependencies: Vec<Dependency>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Sort {
    Relevance,
    Downloads,
    Follows,
    Newest,
    Updated,
}

impl Sort {
    fn index(self) -> &'static str {
        match self {
            Sort::Relevance => "relevance",
            Sort::Downloads => "downloads",
            Sort::Follows => "follows",
            Sort::Newest => "newest",
            Sort::Updated => "updated",
        }
    }
}

/// What the target profile can run.
#[derive(Debug, Clone, PartialEq)]
pub struct Target {
    pub mc_version: String,
    pub loaders: Vec<String>,
}

impl Target {
    /// Quilt can load Fabric mods, so it accepts both.
    pub fn for_loader(mc_version: &str, loader: crate::loaders::Loader) -> Result<Self> {
        use crate::loaders::Loader::*;
        let loaders: Vec<&str> = match loader {
            Vanilla => return Err(Error::msg("Vanilla profiles don't use mods. Switch the profile's loader to Fabric or Quilt first.")),
            Fabric => vec!["fabric"],
            Quilt => vec!["quilt", "fabric"],
            Forge => vec!["forge"],
            NeoForge => vec!["neoforge"],
        };
        Ok(Self { mc_version: mc_version.to_string(), loaders: loaders.into_iter().map(String::from).collect() })
    }

    pub fn accepts(&self, v: &ModVersion) -> bool {
        v.game_versions.iter().any(|g| *g == self.mc_version) && v.loaders.iter().any(|l| self.loaders.contains(l))
    }
}

// ------------------------------------------------------------------ HTTP client

#[derive(Clone)]
pub struct Modrinth {
    http: reqwest::Client,
    base: String,
}

impl Modrinth {
    pub fn new(http: reqwest::Client) -> Self {
        Self { http, base: API.to_string() }
    }

    fn json_list(items: &[String]) -> String {
        serde_json::to_string(items).unwrap_or_else(|_| "[]".into())
    }

    pub async fn search(&self, query: &str, t: &Target, sort: Sort, offset: u32, limit: u32) -> Result<SearchResult> {
        let loader_facets: Vec<String> = t.loaders.iter().map(|l| format!("categories:{l}")).collect();
        let facets = serde_json::json!([
            ["project_type:mod"],
            [format!("versions:{}", t.mc_version)],
            loader_facets,
            ["client_side:required", "client_side:optional"] // server-only mods are useless in a client launcher
        ]);
        let r = self
            .http
            .get(format!("{}/search", self.base))
            .query(&[
                ("query", query),
                ("facets", &facets.to_string()),
                ("index", sort.index()),
                ("offset", &offset.to_string()),
                ("limit", &limit.min(50).to_string()),
            ])
            .send()
            .await?
            .error_for_status()?;
        Ok(r.json().await?)
    }

    pub async fn project(&self, id_or_slug: &str) -> Result<Project> {
        let r = self.http.get(format!("{}/project/{}", self.base, urlenc(id_or_slug))).send().await?.error_for_status()?;
        Ok(r.json().await?)
    }

    /// Titles/icons for many projects in one call. Unknown ids are simply missing from the result.
    pub async fn projects(&self, ids: &[String]) -> Result<Vec<Project>> {
        if ids.is_empty() {
            return Ok(vec![]);
        }
        let r = self
            .http
            .get(format!("{}/projects", self.base))
            .query(&[("ids", Self::json_list(ids))])
            .send()
            .await?
            .error_for_status()?;
        Ok(r.json().await?)
    }

    pub async fn version(&self, id: &str) -> Result<ModVersion> {
        let r = self.http.get(format!("{}/version/{}", self.base, urlenc(id))).send().await?.error_for_status()?;
        Ok(r.json().await?)
    }

    /// Latest version per installed file (keyed by sha1). Files with no compatible version are absent.
    pub async fn latest_for_hashes(&self, hashes: &[String], t: &Target) -> Result<HashMap<String, ModVersion>> {
        if hashes.is_empty() {
            return Ok(HashMap::new());
        }
        let r = self
            .http
            .post(format!("{}/version_files/update", self.base))
            .json(&serde_json::json!({
                "hashes": hashes, "algorithm": "sha1",
                "loaders": t.loaders, "game_versions": [t.mc_version]
            }))
            .send()
            .await?
            .error_for_status()?;
        Ok(r.json().await?)
    }
}

fn urlenc(s: &str) -> String {
    // slugs/ids are [A-Za-z0-9_-]; anything else is percent-encoded so it cannot alter the path
    s.bytes()
        .map(|b| if b.is_ascii_alphanumeric() || b == b'-' || b == b'_' { (b as char).to_string() } else { format!("%{b:02X}") })
        .collect()
}

// ------------------------------------------------------------------ resolver

#[allow(async_fn_in_trait)]
pub trait VersionSource {
    /// Newest first. `None` = the project does not exist.
    async fn project_versions(&self, project: &str, t: &Target) -> Result<Option<Vec<ModVersion>>>;
    async fn version(&self, id: &str) -> Result<ModVersion>;
}

impl VersionSource for Modrinth {
    async fn project_versions(&self, project: &str, t: &Target) -> Result<Option<Vec<ModVersion>>> {
        let r = self
            .http
            .get(format!("{}/project/{}/version", self.base, urlenc(project)))
            .query(&[("loaders", Self::json_list(&t.loaders)), ("game_versions", Self::json_list(&[t.mc_version.clone()]))])
            .send()
            .await?;
        if r.status().as_u16() == 404 {
            return Ok(None);
        }
        Ok(Some(r.error_for_status()?.json().await?))
    }
    async fn version(&self, id: &str) -> Result<ModVersion> {
        Modrinth::version(self, id).await
    }
}

#[derive(Debug, Clone)]
pub struct Request {
    /// Project id or slug.
    pub reference: String,
    pub pinned_version: Option<String>,
}

impl Request {
    pub fn project(reference: impl Into<String>) -> Self {
        Self { reference: reference.into(), pinned_version: None }
    }
}

#[derive(Debug, Clone)]
pub struct PlanItem {
    pub version: ModVersion,
    pub file: ModFile,
    pub as_dependency: bool,
    pub required_by: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct Skipped {
    pub project: String,
    pub reason: String,
}

#[derive(Debug, Default)]
pub struct Plan {
    pub install: Vec<PlanItem>,
    pub skipped: Vec<Skipped>,
}

fn pick_best(list: &[ModVersion], t: &Target) -> Option<ModVersion> {
    let ok: Vec<&ModVersion> = list.iter().filter(|v| t.accepts(v)).collect();
    for kind in ["release", "beta", "alpha"] {
        if let Some(v) = ok.iter().find(|v| v.version_type == kind) {
            return Some((*v).clone());
        }
    }
    None
}

fn pick_file(v: &ModVersion) -> Option<ModFile> {
    let jars = v.files.iter().filter(|f| f.filename.to_lowercase().ends_with(".jar"));
    jars.clone().find(|f| f.primary).or_else(|| jars.clone().next()).cloned()
}

/// Work out exactly which files to install: the requested mods plus every required dependency,
/// each checked against the target Minecraft version and loader. Anything that cannot be
/// satisfied is reported in `skipped` and never installed.
pub async fn resolve<S: VersionSource>(
    src: &S,
    t: &Target,
    requests: Vec<Request>,
    installed: &HashSet<String>,
) -> Result<Plan> {
    let mut queue: VecDeque<(Request, bool, Option<String>)> = requests.into_iter().map(|r| (r, false, None)).collect();
    let mut seen_refs = HashSet::new();
    let mut chosen: Vec<PlanItem> = Vec::new();
    let mut chosen_ids: HashSet<String> = HashSet::new();
    let mut skipped: Vec<Skipped> = Vec::new();
    let mut unmet: Vec<(String, String)> = Vec::new(); // (parent project, missing dep ref)
    let mut incompat: Vec<(String, String)> = Vec::new(); // (owner project, other project)

    while let Some((req, as_dep, parent)) = queue.pop_front() {
        if !seen_refs.insert(req.reference.clone()) {
            continue;
        }
        let fail = |skipped: &mut Vec<Skipped>, unmet: &mut Vec<(String, String)>, reason: String| {
            skipped.push(Skipped { project: req.reference.clone(), reason });
            if let Some(p) = &parent {
                unmet.push((p.clone(), req.reference.clone()));
            }
        };

        let mut version: Option<ModVersion> = None;
        if let Some(vid) = &req.pinned_version {
            if let Ok(v) = src.version(vid).await {
                if t.accepts(&v) {
                    version = Some(v);
                }
            }
        }
        if version.is_none() {
            match src.project_versions(&req.reference, t).await? {
                None => {
                    fail(&mut skipped, &mut unmet, "not found on Modrinth".into());
                    continue;
                }
                Some(list) => version = pick_best(&list, t),
            }
        }
        let Some(v) = version else {
            fail(&mut skipped, &mut unmet, format!("no version for Minecraft {} with {}", t.mc_version, t.loaders.join("/")));
            continue;
        };
        if installed.contains(&v.project_id) {
            if !as_dep {
                skipped.push(Skipped { project: req.reference.clone(), reason: "already installed".into() });
            }
            continue;
        }
        if chosen_ids.contains(&v.project_id) {
            continue;
        }
        let Some(file) = pick_file(&v) else {
            fail(&mut skipped, &mut unmet, "this version has no downloadable .jar".into());
            continue;
        };

        for d in &v.dependencies {
            match d.dependency_type.as_str() {
                "required" => {
                    let pid = match (&d.project_id, &d.version_id) {
                        (Some(p), _) => Some(p.clone()),
                        (None, Some(vid)) => src.version(vid).await.ok().map(|x| x.project_id),
                        _ => None,
                    };
                    if let Some(pid) = pid {
                        queue.push_back((Request { reference: pid, pinned_version: d.version_id.clone() }, true, Some(v.project_id.clone())));
                    }
                }
                "incompatible" => {
                    if let Some(p) = &d.project_id {
                        incompat.push((v.project_id.clone(), p.clone()));
                    }
                }
                _ => {} // optional / embedded need no action
            }
        }
        chosen_ids.insert(v.project_id.clone());
        chosen.push(PlanItem { version: v, file, as_dependency: as_dep, required_by: parent });
    }

    let mut remove: Vec<(String, String)> = Vec::new(); // (project, reason)
    for (owner, other) in &incompat {
        if chosen_ids.contains(owner) && (installed.contains(other) || chosen_ids.contains(other)) {
            remove.push((owner.clone(), format!("incompatible with another mod ({other})")));
        }
    }
    for (parent, dep) in &unmet {
        remove.push((parent.clone(), format!("requires {dep}, which isn't available for this Minecraft version/loader")));
    }
    // drop removed items, then dependencies that were only there for them
    let mut removed: HashSet<String> = HashSet::new();
    for (p, reason) in remove {
        if removed.insert(p.clone()) {
            skipped.push(Skipped { project: p, reason });
        }
    }
    loop {
        let before = removed.len();
        for item in &chosen {
            if let Some(parent) = &item.required_by {
                if removed.contains(parent) && item.as_dependency && removed.insert(item.version.project_id.clone()) {
                    skipped.push(Skipped { project: item.version.project_id.clone(), reason: "only needed by a mod that could not be installed".into() });
                }
            }
        }
        if removed.len() == before {
            break;
        }
    }
    chosen.retain(|i| !removed.contains(&i.version.project_id));
    Ok(Plan { install: chosen, skipped })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Default)]
    struct Fake {
        by_project: HashMap<String, Vec<ModVersion>>, // key: id or slug
        by_id: HashMap<String, ModVersion>,
    }
    impl VersionSource for Fake {
        async fn project_versions(&self, p: &str, t: &Target) -> Result<Option<Vec<ModVersion>>> {
            Ok(self.by_project.get(p).map(|l| l.iter().filter(|v| t.accepts(v)).cloned().collect()))
        }
        async fn version(&self, id: &str) -> Result<ModVersion> {
            self.by_id.get(id).cloned().ok_or_else(|| Error::msg("nope"))
        }
    }

    fn v(id: &str, project: &str, kind: &str, mc: &str, deps: Vec<(&str, &str)>) -> ModVersion {
        ModVersion {
            id: id.into(),
            project_id: project.into(),
            name: id.into(),
            version_number: "1.0".into(),
            version_type: kind.into(),
            game_versions: vec![mc.into()],
            loaders: vec!["fabric".into()],
            files: vec![ModFile {
                url: format!("{CDN_PREFIX}data/{project}/{id}.jar"),
                filename: format!("{project}-{id}.jar"),
                primary: true,
                size: 10,
                hashes: Hashes { sha1: format!("h-{id}"), sha512: String::new() },
            }],
            dependencies: deps
                .into_iter()
                .map(|(p, t)| Dependency { version_id: None, project_id: Some(p.into()), dependency_type: t.into() })
                .collect(),
        }
    }

    fn fake(vs: Vec<ModVersion>) -> Fake {
        let mut f = Fake::default();
        for x in vs {
            f.by_project.entry(x.project_id.clone()).or_default().push(x.clone());
            f.by_id.insert(x.id.clone(), x);
        }
        f
    }

    fn target() -> Target {
        Target::for_loader("1.20.4", crate::loaders::Loader::Fabric).unwrap()
    }

    #[tokio::test]
    async fn pulls_in_required_dependency() {
        let f = fake(vec![v("s1", "sodium", "release", "1.20.4", vec![("fapi", "required")]), v("f1", "fapi", "release", "1.20.4", vec![])]);
        let p = resolve(&f, &target(), vec![Request::project("sodium")], &HashSet::new()).await.unwrap();
        let names: Vec<_> = p.install.iter().map(|i| (i.version.project_id.as_str(), i.as_dependency)).collect();
        assert_eq!(names, vec![("sodium", false), ("fapi", true)]);
        assert!(p.skipped.is_empty());
    }

    #[tokio::test]
    async fn skips_installed_dependency_silently() {
        let f = fake(vec![v("s1", "sodium", "release", "1.20.4", vec![("fapi", "required")]), v("f1", "fapi", "release", "1.20.4", vec![])]);
        let inst: HashSet<String> = ["fapi".to_string()].into();
        let p = resolve(&f, &target(), vec![Request::project("sodium")], &inst).await.unwrap();
        assert_eq!(p.install.len(), 1);
        assert!(p.skipped.is_empty());
    }

    #[tokio::test]
    async fn unmet_required_dependency_blocks_parent() {
        // dependency only exists for another Minecraft version
        let f = fake(vec![v("a1", "a", "release", "1.20.4", vec![("b", "required")]), v("b1", "b", "release", "1.19.2", vec![])]);
        let p = resolve(&f, &target(), vec![Request::project("a")], &HashSet::new()).await.unwrap();
        assert!(p.install.is_empty());
        assert!(p.skipped.iter().any(|s| s.project == "a" && s.reason.contains("requires b")));
    }

    #[tokio::test]
    async fn incompatible_with_installed_is_refused() {
        let f = fake(vec![v("o1", "optifine-ish", "release", "1.20.4", vec![("sodium", "incompatible")])]);
        let inst: HashSet<String> = ["sodium".to_string()].into();
        let p = resolve(&f, &target(), vec![Request::project("optifine-ish")], &inst).await.unwrap();
        assert!(p.install.is_empty());
        assert!(p.skipped[0].reason.contains("incompatible"));
    }

    #[tokio::test]
    async fn prefers_release_over_newer_beta() {
        let f = fake(vec![v("new-beta", "m", "beta", "1.20.4", vec![]), v("old-rel", "m", "release", "1.20.4", vec![])]);
        let p = resolve(&f, &target(), vec![Request::project("m")], &HashSet::new()).await.unwrap();
        assert_eq!(p.install[0].version.id, "old-rel");
    }

    #[tokio::test]
    async fn cycles_terminate_and_unknown_projects_are_reported() {
        let f = fake(vec![v("a1", "a", "release", "1.20.4", vec![("b", "required")]), v("b1", "b", "release", "1.20.4", vec![("a", "required")])]);
        let p = resolve(&f, &target(), vec![Request::project("a"), Request::project("ghost")], &HashSet::new()).await.unwrap();
        assert_eq!(p.install.len(), 2);
        assert!(p.skipped.iter().any(|s| s.project == "ghost" && s.reason.contains("not found")));
    }

    #[tokio::test]
    async fn pinned_version_is_honoured() {
        let f = fake(vec![v("latest", "m", "release", "1.20.4", vec![]), v("pinned", "m", "release", "1.20.4", vec![])]);
        let req = Request { reference: "m".into(), pinned_version: Some("pinned".into()) };
        let p = resolve(&f, &target(), vec![req], &HashSet::new()).await.unwrap();
        assert_eq!(p.install[0].version.id, "pinned");
    }

    #[test]
    fn target_rules() {
        assert!(Target::for_loader("1.20.4", crate::loaders::Loader::Vanilla).is_err());
        let q = Target::for_loader("1.20.4", crate::loaders::Loader::Quilt).unwrap();
        assert!(q.loaders.contains(&"fabric".to_string()));
        assert_eq!(urlenc("sod/../ium"), "sod%2F%2E%2E%2Fium".replace("%2E", "%2E"));
    }
}
