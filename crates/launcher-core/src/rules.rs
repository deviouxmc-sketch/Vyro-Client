use serde::Deserialize;
use std::collections::HashMap;

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
    pub version: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
    pub features: Option<HashMap<String, bool>>,
}

/// Describes the machine and launch features that Mojang's rule syntax is evaluated against.
#[derive(Debug, Clone)]
pub struct Env {
    pub os: &'static str,   // windows | osx | linux
    pub arch: &'static str, // x86 | arm64 | x86_64
    pub features: HashMap<String, bool>,
}

impl Env {
    pub fn current() -> Self {
        let os = if cfg!(target_os = "windows") {
            "windows"
        } else if cfg!(target_os = "macos") {
            "osx"
        } else {
            "linux"
        };
        let arch = if cfg!(target_arch = "x86") {
            "x86"
        } else if cfg!(target_arch = "aarch64") {
            "arm64"
        } else {
            "x86_64"
        };
        Self { os, arch, features: HashMap::new() }
    }
    pub fn with_feature(mut self, k: &str, v: bool) -> Self {
        self.features.insert(k.to_string(), v);
        self
    }
}

fn rule_matches(rule: &Rule, env: &Env) -> bool {
    if let Some(os) = &rule.os {
        if let Some(n) = &os.name {
            if n != env.os {
                return false;
            }
        }
        if let Some(a) = &os.arch {
            if a != env.arch {
                return false;
            }
        }
        // OS version regexes only gate a Windows-10 specific JVM flag; we never match them.
        if os.version.is_some() {
            return false;
        }
    }
    if let Some(feats) = &rule.features {
        for (k, want) in feats {
            if env.features.get(k).copied().unwrap_or(false) != *want {
                return false;
            }
        }
    }
    true
}

/// Mojang semantics: no rules = allowed; otherwise last matching rule wins, default deny.
pub fn allowed(rules: &Option<Vec<Rule>>, env: &Env) -> bool {
    let Some(rules) = rules else { return true };
    if rules.is_empty() {
        return true;
    }
    let mut ok = false;
    for r in rules {
        if rule_matches(r, env) {
            ok = r.action == "allow";
        }
    }
    ok
}

#[cfg(test)]
mod tests {
    use super::*;
    fn env() -> Env {
        Env { os: "linux", arch: "x86_64", features: HashMap::new() }
    }
    fn parse(s: &str) -> Option<Vec<Rule>> {
        Some(serde_json::from_str(s).unwrap())
    }
    #[test]
    fn no_rules_allowed() {
        assert!(allowed(&None, &env()));
    }
    #[test]
    fn os_specific() {
        let r = parse(r#"[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]"#);
        assert!(allowed(&r, &env()));
        let r = parse(r#"[{"action":"allow","os":{"name":"windows"}}]"#);
        assert!(!allowed(&r, &env()));
    }
    #[test]
    fn arch_specific() {
        let r = parse(r#"[{"action":"allow","os":{"name":"linux","arch":"arm64"}}]"#);
        assert!(!allowed(&r, &env()));
    }
    #[test]
    fn features_default_false() {
        let r = parse(r#"[{"action":"allow","features":{"is_demo_user":true}}]"#);
        assert!(!allowed(&r, &env()));
        let e = env().with_feature("is_demo_user", true);
        assert!(allowed(&r, &e));
    }
}
