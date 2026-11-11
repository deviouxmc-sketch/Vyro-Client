use std::{env, fs, path::Path};

fn main() {
    // `npm run sync-mod` builds vyro-mod and copies the jar to src-tauri/resources/.
    // It is embedded into the launcher binary so installs work in dev and in release builds.
    let out = env::var("OUT_DIR").expect("OUT_DIR");
    let jar = Path::new("resources/vyro-client-mod.jar");
    let ver = Path::new("resources/vyro-client-mod.version");
    println!("cargo:rerun-if-changed=resources/vyro-client-mod.jar");
    println!("cargo:rerun-if-changed=resources/vyro-client-mod.version");
    let dest = Path::new(&out).join("vyro-client-mod.jar");
    if jar.is_file() {
        fs::copy(jar, &dest).expect("copy vyro mod jar");
    } else {
        fs::write(&dest, b"").expect("write placeholder");
    }
    let version = fs::read_to_string(ver).map(|v| v.trim().to_string()).unwrap_or_default();
    println!("cargo:rustc-env=VYRO_MOD_VERSION={version}");
    tauri_build::build()
}
