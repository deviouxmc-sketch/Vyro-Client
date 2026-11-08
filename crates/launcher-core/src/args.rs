//! Builds the final `java ...` command line from a merged version + launch options.
use crate::rules::{allowed, Env};
use crate::version::{Arg, Merged};
use std::collections::HashMap;
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct Session {
    pub username: String,
    pub uuid: String,
    pub access_token: String,
}

#[derive(Debug, Clone)]
pub struct LaunchOptions {
    pub java: PathBuf,
    pub min_mem_mb: u32,
    pub max_mem_mb: u32,
    pub extra_jvm_args: Vec<String>,
    pub extra_game_args: Vec<String>,
    pub resolution: Option<(u32, u32)>,
    pub fullscreen: bool,
    pub session: Session,
    pub game_dir: PathBuf,
    pub assets_root: PathBuf,
    pub natives_dir: PathBuf,
    pub libraries_dir: PathBuf,
    /// Library jars (without the client jar).
    pub classpath: Vec<PathBuf>,
    pub client_jar: PathBuf,
}

#[derive(Debug, Clone)]
pub struct Command {
    pub program: PathBuf,
    pub args: Vec<String>,
    pub cwd: PathBuf,
}

impl Command {
    /// Command line suitable for logs – the access token is masked.
    pub fn redacted(&self, token: &str) -> String {
        let mut s = self.program.display().to_string();
        for a in &self.args {
            s.push(' ');
            s.push_str(&if !token.is_empty() && a == token { "***".to_string() } else { a.clone() });
        }
        s
    }
}

fn expand(s: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(start) = rest.find("${") {
        out.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        match after.find('}') {
            Some(end) => {
                let key = &after[..end];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => out.push_str(&rest[start..start + 2 + end + 1]), // leave unknown vars untouched
                }
                rest = &after[end + 1..];
            }
            None => {
                out.push_str(&rest[start..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

fn flatten(args: &[Arg], env: &Env) -> Vec<String> {
    let mut out = Vec::new();
    for a in args {
        match a {
            Arg::Plain(s) => out.push(s.clone()),
            Arg::Conditional { rules, value } => {
                if allowed(rules, env) {
                    out.extend(value.clone().into_vec());
                }
            }
        }
    }
    out
}

pub fn build(v: &Merged, o: &LaunchOptions, version_type_override: Option<&str>) -> Command {
    let sep = if cfg!(windows) { ";" } else { ":" };
    let mut cp: Vec<PathBuf> = o.classpath.clone();
    cp.push(o.client_jar.clone());
    let classpath = cp.iter().map(|p| p.display().to_string()).collect::<Vec<_>>().join(sep);

    let mut env = Env::current();
    if o.resolution.is_some() {
        env = env.with_feature("has_custom_resolution", true);
    }
    let (w, h) = o.resolution.unwrap_or((854, 480));

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", o.session.username.clone());
    vars.insert("auth_uuid", o.session.uuid.clone());
    vars.insert("auth_access_token", o.session.access_token.clone());
    vars.insert("auth_session", format!("token:{}:{}", o.session.access_token, o.session.uuid));
    vars.insert("auth_xuid", "0".into());
    vars.insert("clientid", "".into());
    vars.insert("user_type", "msa".into());
    vars.insert("user_properties", "{}".into());
    vars.insert("version_name", v.id.clone());
    vars.insert("version_type", version_type_override.unwrap_or(&v.kind).to_string());
    vars.insert("game_directory", o.game_dir.display().to_string());
    vars.insert("assets_root", o.assets_root.display().to_string());
    vars.insert("game_assets", o.assets_root.display().to_string());
    vars.insert("assets_index_name", v.asset_index.id.clone());
    vars.insert("natives_directory", o.natives_dir.display().to_string());
    vars.insert("library_directory", o.libraries_dir.display().to_string());
    vars.insert("classpath_separator", sep.into());
    vars.insert("launcher_name", "VYRO".into());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").into());
    vars.insert("classpath", classpath);
    vars.insert("resolution_width", w.to_string());
    vars.insert("resolution_height", h.to_string());

    let mut args: Vec<String> = Vec::new();
    // memory first so user JVM args can override it
    args.push(format!("-Xms{}M", o.min_mem_mb.min(o.max_mem_mb)));
    args.push(format!("-Xmx{}M", o.max_mem_mb));
    args.extend(o.extra_jvm_args.iter().cloned());

    if v.jvm.is_empty() {
        // legacy versions: Mojang's launcher supplied these itself
        args.push("-Djava.library.path=${natives_directory}".into());
        args.push("-cp".into());
        args.push("${classpath}".into());
    } else {
        args.extend(flatten(&v.jvm, &env));
    }
    args.push(v.main_class.clone());
    if let Some(l) = &v.legacy_args {
        args.extend(l.split_whitespace().map(String::from));
        if o.resolution.is_some() {
            args.extend(["--width".into(), "${resolution_width}".into(), "--height".into(), "${resolution_height}".into()]);
        }
    } else {
        args.extend(flatten(&v.game, &env));
    }
    if o.fullscreen {
        args.push("--fullscreen".into());
    }
    args.extend(o.extra_game_args.iter().cloned());

    let args = args.into_iter().map(|a| expand(&a, &vars)).collect();
    Command { program: o.java.clone(), args, cwd: o.game_dir.clone() }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::version::{fixtures, merge, to_merged, VersionJson};

    fn opts() -> LaunchOptions {
        LaunchOptions {
            java: "/usr/bin/java".into(),
            min_mem_mb: 512,
            max_mem_mb: 4096,
            extra_jvm_args: vec!["-XX:+UseG1GC".into()],
            extra_game_args: vec![],
            resolution: None,
            fullscreen: false,
            session: Session { username: "Steve".into(), uuid: "uuid-1".into(), access_token: "SECRET".into() },
            game_dir: "/g".into(),
            assets_root: "/a".into(),
            natives_dir: "/n".into(),
            libraries_dir: "/l".into(),
            classpath: vec!["/l/a.jar".into(), "/l/b.jar".into()],
            client_jar: "/v/1.20.4.jar".into(),
        }
    }

    fn modern() -> Merged {
        to_merged(serde_json::from_str::<VersionJson>(fixtures::MODERN).unwrap(), "1.20.4".into()).unwrap()
    }

    #[test]
    fn expand_vars() {
        let mut v = HashMap::new();
        v.insert("a", "1".to_string());
        assert_eq!(expand("x${a}y${b}z${", &v), "x1y${b}z${");
    }

    #[test]
    fn modern_command() {
        let c = build(&modern(), &opts(), None);
        let a = &c.args;
        assert_eq!(a[0], "-Xms512M");
        assert_eq!(a[1], "-Xmx4096M");
        assert_eq!(a[2], "-XX:+UseG1GC");
        assert!(a.contains(&"-Djava.library.path=/n".to_string()));
        let cp_i = a.iter().position(|x| x == "-cp").unwrap();
        assert!(a[cp_i + 1].ends_with("/v/1.20.4.jar"));
        let main_i = a.iter().position(|x| x == "net.minecraft.client.main.Main").unwrap();
        assert!(main_i > cp_i);
        let after = &a[main_i + 1..];
        assert!(after.windows(2).any(|w| w[0] == "--username" && w[1] == "Steve"));
        assert!(after.windows(2).any(|w| w[0] == "--accessToken" && w[1] == "SECRET"));
        // feature-gated args absent
        assert!(!after.contains(&"--demo".to_string()));
        assert!(!after.contains(&"--width".to_string()));
        // os-gated mac flag absent on linux
        if cfg!(target_os = "linux") {
            assert!(!a.contains(&"-XstartOnFirstThread".to_string()));
        }
        assert!(!c.redacted("SECRET").contains("SECRET"));
    }

    #[test]
    fn resolution_and_fullscreen() {
        let mut o = opts();
        o.resolution = Some((1280, 720));
        o.fullscreen = true;
        let c = build(&modern(), &o, None);
        assert!(c.args.windows(2).any(|w| w[0] == "--width" && w[1] == "1280"));
        assert!(c.args.contains(&"--fullscreen".to_string()));
    }

    #[test]
    fn legacy_command() {
        let v = to_merged(serde_json::from_str::<VersionJson>(fixtures::LEGACY).unwrap(), "1.8.9".into()).unwrap();
        let c = build(&v, &opts(), None);
        assert!(c.args.contains(&"-cp".to_string()));
        assert!(c.args.windows(2).any(|w| w[0] == "--userProperties" && w[1] == "{}"));
        assert!(c.args.windows(2).any(|w| w[0] == "--assetIndex" && w[1] == "1.8"));
    }

    #[test]
    fn fabric_uses_loader_main_class() {
        let p: VersionJson = serde_json::from_str(fixtures::MODERN).unwrap();
        let ch: VersionJson = serde_json::from_str(fixtures::FABRIC).unwrap();
        let m = to_merged(merge(&p, &ch), "1.20.4".into()).unwrap();
        let c = build(&m, &opts(), None);
        assert!(c.args.contains(&"net.fabricmc.loader.impl.launch.knot.KnotClient".to_string()));
        assert!(!c.args.contains(&"net.minecraft.client.main.Main".to_string()));
    }
}
