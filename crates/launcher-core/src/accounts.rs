//! Multi-account storage. Non-secret metadata lives in `accounts.json`; refresh and
//! access tokens go through a `SecretStore` (the desktop shell backs this with the OS
//! keychain: Windows Credential Manager / macOS Keychain / Secret Service).
use crate::auth::{self, McSession};
use crate::error::{Error, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;

pub trait SecretStore: Send + Sync {
    fn get(&self, key: &str) -> Result<Option<String>>;
    fn set(&self, key: &str, value: &str) -> Result<()>;
    fn delete(&self, key: &str) -> Result<()>;
}

#[derive(Default)]
pub struct MemorySecrets(Mutex<HashMap<String, String>>);

impl SecretStore for MemorySecrets {
    fn get(&self, key: &str) -> Result<Option<String>> {
        Ok(self.0.lock().unwrap().get(key).cloned())
    }
    fn set(&self, key: &str, value: &str) -> Result<()> {
        self.0.lock().unwrap().insert(key.into(), value.into());
        Ok(())
    }
    fn delete(&self, key: &str) -> Result<()> {
        self.0.lock().unwrap().remove(key);
        Ok(())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AccountMeta {
    pub uuid: String,
    pub username: String,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct File {
    accounts: Vec<AccountMeta>,
    active: Option<String>,
}


pub struct Accounts {
    path: PathBuf,
    secrets: Box<dyn SecretStore>,
    file: Mutex<File>,
}

// Two separate entries: Windows Credential Manager caps a single credential at ~2.5 KB and a
// Microsoft refresh token plus a Minecraft JWT can exceed that together.
fn refresh_key(uuid: &str) -> String {
    format!("vyro-refresh-{uuid}")
}
fn session_key(uuid: &str) -> String {
    format!("vyro-session-{uuid}")
}

impl Accounts {
    pub fn load(path: PathBuf, secrets: Box<dyn SecretStore>) -> Result<Self> {
        let file = if path.exists() { serde_json::from_slice(&std::fs::read(&path)?)? } else { File::default() };
        Ok(Self { path, secrets, file: Mutex::new(file) })
    }

    fn save(&self, f: &File) -> Result<()> {
        if let Some(p) = self.path.parent() {
            std::fs::create_dir_all(p)?;
        }
        std::fs::write(&self.path, serde_json::to_vec_pretty(f)?)?;
        Ok(())
    }

    pub fn list(&self) -> (Vec<AccountMeta>, Option<String>) {
        let f = self.file.lock().unwrap();
        (f.accounts.clone(), f.active.clone())
    }

    /// Store a freshly authenticated account and make it active.
    pub fn add(&self, refresh_token: &str, session: McSession) -> Result<AccountMeta> {
        let meta = AccountMeta { uuid: session.uuid.clone(), username: session.username.clone() };
        self.secrets.set(&refresh_key(&meta.uuid), refresh_token)?;
        self.secrets.set(&session_key(&meta.uuid), &serde_json::to_string(&session)?)?;
        let mut f = self.file.lock().unwrap();
        match f.accounts.iter_mut().find(|a| a.uuid == meta.uuid) {
            Some(a) => a.username = meta.username.clone(), // renamed accounts keep one entry
            None => f.accounts.push(meta.clone()),
        }
        f.active = Some(meta.uuid.clone());
        self.save(&f)?;
        Ok(meta)
    }

    pub fn set_active(&self, uuid: &str) -> Result<()> {
        let mut f = self.file.lock().unwrap();
        if !f.accounts.iter().any(|a| a.uuid == uuid) {
            return Err(Error::msg("unknown account"));
        }
        f.active = Some(uuid.into());
        self.save(&f)
    }

    pub fn remove(&self, uuid: &str) -> Result<()> {
        self.secrets.delete(&refresh_key(uuid))?;
        self.secrets.delete(&session_key(uuid))?;
        let mut f = self.file.lock().unwrap();
        f.accounts.retain(|a| a.uuid != uuid);
        if f.active.as_deref() == Some(uuid) {
            f.active = f.accounts.first().map(|a| a.uuid.clone());
        }
        self.save(&f)
    }

    /// A valid Minecraft session for `uuid`, refreshing through Microsoft when the cached one is about to expire.
    pub async fn session(&self, http: &reqwest::Client, client_id: &str, uuid: &str) -> Result<McSession> {
        let refresh = self
            .secrets
            .get(&refresh_key(uuid))?
            .ok_or_else(|| Error::Auth("account credentials missing, please sign in again".into()))?;
        if let Some(raw) = self.secrets.get(&session_key(uuid))? {
            if let Ok(cached) = serde_json::from_str::<McSession>(&raw) {
                if cached.expires_at > auth::now() + 120 {
                    return Ok(cached);
                }
            }
        }
        let ms = auth::refresh_ms(http, client_id, &refresh).await?;
        let session = auth::minecraft_session(http, &ms.access_token).await?;
        // Microsoft rotates refresh tokens: always keep the newest one
        self.secrets.set(&refresh_key(uuid), &ms.refresh_token)?;
        self.secrets.set(&session_key(uuid), &serde_json::to_string(&session)?)?;
        Ok(session)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sess(uuid: &str, name: &str, exp: u64) -> McSession {
        McSession { uuid: uuid.into(), username: name.into(), access_token: "tok".into(), expires_at: exp }
    }

    #[tokio::test]
    async fn add_switch_remove_and_cached_session() {
        let td = tempfile::tempdir().unwrap();
        let acc = Accounts::load(td.path().join("accounts.json"), Box::new(MemorySecrets::default())).unwrap();
        acc.add("r1", sess("u1", "Alex", auth::now() + 3600)).unwrap();
        acc.add("r2", sess("u2", "Sam", auth::now() + 3600)).unwrap();
        let (list, active) = acc.list();
        assert_eq!(list.len(), 2);
        assert_eq!(active.as_deref(), Some("u2"));
        acc.set_active("u1").unwrap();
        assert!(acc.set_active("nope").is_err());

        // cached, unexpired session is returned without any network call
        let http = reqwest::Client::new();
        let s = acc.session(&http, "client", "u1").await.unwrap();
        assert_eq!(s.username, "Alex");

        // metadata persisted, tokens are NOT in the json file
        let on_disk = std::fs::read_to_string(td.path().join("accounts.json")).unwrap();
        assert!(on_disk.contains("Alex") && !on_disk.contains("tok") && !on_disk.contains("r1"));

        acc.remove("u1").unwrap();
        let (list, active) = acc.list();
        assert_eq!(list.len(), 1);
        assert_eq!(active.as_deref(), Some("u2"));
        assert!(acc.session(&http, "client", "u1").await.is_err());
    }

    #[test]
    fn re_adding_same_uuid_does_not_duplicate() {
        let td = tempfile::tempdir().unwrap();
        let acc = Accounts::load(td.path().join("a.json"), Box::new(MemorySecrets::default())).unwrap();
        acc.add("r", sess("u1", "Old", 1)).unwrap();
        acc.add("r", sess("u1", "New", 1)).unwrap();
        let (list, _) = acc.list();
        assert_eq!(list, vec![AccountMeta { uuid: "u1".into(), username: "New".into() }]);
    }
}
