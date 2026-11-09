//! Curated mod sets for one-click install. Slugs are resolved against Modrinth at install
//! time, so anything renamed/removed or not available for the profile's Minecraft version is
//! reported as skipped instead of breaking the install.
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct Collection {
    pub id: &'static str,
    pub name: &'static str,
    pub description: &'static str,
    /// "collection" = ready-made bundle, "category" = browse list
    pub kind: &'static str,
    pub slugs: &'static [&'static str],
}

pub const ALL: &[Collection] = &[
    Collection { id: "pvp-essentials", name: "PvP Essentials", kind: "collection",
        description: "Smooth frames plus the info overlays PvP players want.",
        slugs: &["sodium", "lithium", "entityculling", "modernfix", "appleskin", "betterf3", "zoomify", "mouse-tweaks"] },
    Collection { id: "maximum-fps", name: "Maximum FPS", kind: "collection",
        description: "The standard performance stack for low- and mid-range PCs. Results vary by machine, so compare your own FPS.",
        slugs: &["sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling", "moreculling", "modernfix", "krypton", "noisium", "dynamic-fps", "ebe"] },
    Collection { id: "survival-essentials", name: "Survival Essentials", kind: "collection",
        description: "Quality-of-life for long survival worlds.",
        slugs: &["sodium", "lithium", "appleskin", "xaeros-minimap", "xaeros-world-map", "shulkerboxtooltip", "inventory-profiles-next", "mouse-tweaks", "modmenu"] },
    Collection { id: "vanilla-plus", name: "Vanilla+", kind: "collection",
        description: "Keeps the vanilla feel with better performance and visuals.",
        slugs: &["sodium", "lithium", "iris", "continuity", "lambdynamiclights", "appleskin", "zoomify", "modmenu"] },
    Collection { id: "cat-fps", name: "FPS & Performance", kind: "category",
        description: "Rendering, memory and server-thread optimisations.",
        slugs: &["sodium", "lithium", "ferrite-core", "immediatelyfast", "entityculling", "moreculling", "modernfix", "ebe", "dynamic-fps", "krypton", "noisium", "c2me-fabric"] },
    Collection { id: "cat-hud", name: "HUD & Utility", kind: "category",
        description: "Information overlays, maps and inventory helpers.",
        slugs: &["betterf3", "appleskin", "minihud", "xaeros-minimap", "xaeros-world-map", "shulkerboxtooltip", "inventory-profiles-next", "mouse-tweaks", "zoomify"] },
    Collection { id: "cat-graphics", name: "Graphics", kind: "category",
        description: "Shaders and visual improvements (Iris needs Sodium).",
        slugs: &["iris", "sodium", "entity-texture-features", "entity-model-features", "continuity", "lambdynamiclights"] },
];

pub fn find(id: &str) -> Option<&'static Collection> {
    ALL.iter().find(|c| c.id == id)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn well_formed() {
        let mut ids = std::collections::HashSet::new();
        for c in ALL {
            assert!(ids.insert(c.id), "duplicate id {}", c.id);
            assert!(!c.slugs.is_empty());
            let mut s = std::collections::HashSet::new();
            for slug in c.slugs {
                assert!(s.insert(slug), "duplicate slug {slug} in {}", c.id);
                assert!(slug.chars().all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || ch == '-' || ch == '_'));
            }
        }
        assert!(find("maximum-fps").is_some() && find("nope").is_none());
    }
}
