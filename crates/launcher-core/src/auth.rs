//! Microsoft → Xbox Live → XSTS → Minecraft authentication using the official OAuth
//! device-code flow. No passwords are ever seen or stored by VYRO.
//!
//! You must register your own Azure app ("Personal Microsoft accounts only", enable
//! "Allow public client flows") and use its Application (client) ID. For public
//! distribution Mojang also requires approval of that app for the Minecraft API.
use crate::error::{Error, Result};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::time::{SystemTime, UNIX_EPOCH};

const SCOPE: &str = "XboxLive.signin offline_access";
const DEVICE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

pub fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceCode {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    #[serde(default = "five")]
    pub interval: u64,
    #[serde(default)]
    pub message: String,
}
fn five() -> u64 { 5 }

#[derive(Debug, Clone, Deserialize)]
struct TokenResp {
    access_token: Option<String>,
    refresh_token: Option<String>,
    error: Option<String>,
    error_description: Option<String>,
}

#[derive(Debug, Clone)]
pub struct MsTokens {
    pub access_token: String,
    pub refresh_token: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct McSession {
    pub uuid: String,
    pub username: String,
    pub access_token: String,
    pub expires_at: u64,
}

pub async fn start_device_flow(http: &reqwest::Client, client_id: &str) -> Result<DeviceCode> {
    let r = http.post(DEVICE_URL).form(&[("client_id", client_id), ("scope", SCOPE)]).send().await?;
    if !r.status().is_success() {
        let body = r.text().await.unwrap_or_default();
        return Err(Error::Auth(format!("could not start sign-in: {body}")));
    }
    Ok(r.json().await?)
}

/// Poll until the user finishes signing in in their browser (or the code expires).
pub async fn poll_device_flow(http: &reqwest::Client, client_id: &str, dc: &DeviceCode) -> Result<MsTokens> {
    let deadline = now() + dc.expires_in;
    let mut interval = dc.interval.max(1);
    loop {
        if now() > deadline {
            return Err(Error::Auth("the sign-in code expired, please try again".into()));
        }
        tokio::time::sleep(std::time::Duration::from_secs(interval)).await;
        let r = http
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", dc.device_code.as_str()),
            ])
            .send()
            .await?;
        let t: TokenResp = r.json().await?;
        if t.error.as_deref() == Some("slow_down") {
            interval += 5; // RFC 8628: back off when asked
        }
        if let Some(tokens) = interpret_token(t)? {
            return Ok(tokens);
        }
    }
}

/// `Ok(None)` = keep polling.
fn interpret_token(t: TokenResp) -> Result<Option<MsTokens>> {
    if let (Some(a), Some(r)) = (t.access_token.clone(), t.refresh_token.clone()) {
        return Ok(Some(MsTokens { access_token: a, refresh_token: r }));
    }
    match t.error.as_deref() {
        Some("authorization_pending") | Some("slow_down") => Ok(None),
        Some("authorization_declined") => Err(Error::Auth("sign-in was declined".into())),
        Some("expired_token") => Err(Error::Auth("the sign-in code expired, please try again".into())),
        Some(e) => Err(Error::Auth(format!("{e}: {}", t.error_description.unwrap_or_default()))),
        None => Err(Error::Auth("unexpected response from Microsoft".into())),
    }
}

pub async fn refresh_ms(http: &reqwest::Client, client_id: &str, refresh_token: &str) -> Result<MsTokens> {
    let r = http
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", refresh_token),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    let t: TokenResp = r.json().await?;
    match interpret_token(t)? {
        Some(t) => Ok(t),
        None => Err(Error::Auth("session expired, please sign in again".into())),
    }
}

pub fn explain_xerr(code: u64) -> &'static str {
    match code {
        2148916233 => "This Microsoft account has no Xbox profile. Sign in at xbox.com once to create one, then try again.",
        2148916235 => "Xbox Live is not available in your country/region.",
        2148916236 | 2148916237 => "This account needs adult verification on Xbox.",
        2148916238 => "This is a child account. An adult must add it to a Microsoft family before it can play online.",
        _ => "Xbox Live refused the request.",
    }
}

#[derive(Deserialize)]
struct XblResp {
    #[serde(rename = "Token")]
    token: String,
}
#[derive(Deserialize)]
struct XstsResp {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    claims: Claims,
}
#[derive(Deserialize)]
struct Claims {
    xui: Vec<Xui>,
}
#[derive(Deserialize)]
struct Xui {
    uhs: String,
}
#[derive(Deserialize)]
struct McLoginResp {
    access_token: String,
    expires_in: u64,
}
#[derive(Deserialize)]
struct ProfileResp {
    id: String,
    name: String,
}

/// Exchange a Microsoft access token for a Minecraft session. Also verifies that the
/// account owns Minecraft Java (the profile endpoint returns 404 otherwise).
pub async fn minecraft_session(http: &reqwest::Client, ms_access_token: &str) -> Result<McSession> {
    let xbl = http
        .post(XBL_URL)
        .json(&json!({
            "Properties": {"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket": format!("d={ms_access_token}")},
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send()
        .await?
        .error_for_status()
        .map_err(|e| Error::Auth(format!("Xbox Live sign-in failed: {e}")))?
        .json::<XblResp>()
        .await?;

    let r = http
        .post(XSTS_URL)
        .json(&json!({
            "Properties": {"SandboxId":"RETAIL","UserTokens":[xbl.token]},
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await?;
    if r.status().as_u16() == 401 {
        let v: serde_json::Value = r.json().await.unwrap_or_default();
        let code = v.get("XErr").and_then(|x| x.as_u64()).unwrap_or(0);
        return Err(Error::Auth(explain_xerr(code).into()));
    }
    let xsts: XstsResp = r.error_for_status().map_err(|e| Error::Auth(e.to_string()))?.json().await?;
    let uhs = xsts.claims.xui.first().map(|x| x.uhs.clone()).ok_or_else(|| Error::Auth("XSTS returned no user hash".into()))?;

    let mc_resp = http
        .post(MC_LOGIN_URL)
        .json(&json!({"identityToken": format!("XBL3.0 x={uhs};{}", xsts.token)}))
        .send()
        .await?;
    if mc_resp.status().as_u16() == 403 {
        // Mojang rejects every Azure app that has not been approved for Minecraft services.
        return Err(Error::Auth(
            "Mojang has not approved this app registration for Minecraft sign-in yet (HTTP 403). \
             Submit the client ID at aka.ms/mce-reviewappid and try again once it is approved."
                .into(),
        ));
    }
    let mc: McLoginResp = mc_resp
        .error_for_status()
        .map_err(|e| Error::Auth(format!("Minecraft sign-in failed: {e}")))?
        .json()
        .await?;

    let pr = http.get(MC_PROFILE_URL).bearer_auth(&mc.access_token).send().await?;
    if pr.status().as_u16() == 404 {
        return Err(Error::Auth("This Microsoft account does not own Minecraft: Java Edition.".into()));
    }
    let p: ProfileResp = pr.error_for_status().map_err(|e| Error::Auth(e.to_string()))?.json().await?;
    Ok(McSession { uuid: p.id, username: p.name, access_token: mc.access_token, expires_at: now() + mc.expires_in })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tr(json: &str) -> TokenResp {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn pending_means_keep_polling() {
        assert!(interpret_token(tr(r#"{"error":"authorization_pending"}"#)).unwrap().is_none());
        assert!(interpret_token(tr(r#"{"error":"slow_down"}"#)).unwrap().is_none());
    }
    #[test]
    fn success_and_failures() {
        let t = interpret_token(tr(r#"{"access_token":"a","refresh_token":"r"}"#)).unwrap().unwrap();
        assert_eq!((t.access_token.as_str(), t.refresh_token.as_str()), ("a", "r"));
        assert!(interpret_token(tr(r#"{"error":"authorization_declined"}"#)).is_err());
        assert!(interpret_token(tr(r#"{"error":"expired_token"}"#)).is_err());
        assert!(interpret_token(tr(r#"{}"#)).is_err());
    }
    #[test]
    fn xerr_messages() {
        assert!(explain_xerr(2148916233).contains("no Xbox profile"));
        assert!(explain_xerr(2148916238).contains("child"));
        assert_eq!(explain_xerr(1), "Xbox Live refused the request.");
    }
    #[test]
    fn device_code_defaults() {
        let d: DeviceCode = serde_json::from_str(
            r#"{"device_code":"d","user_code":"U","verification_uri":"https://microsoft.com/link","expires_in":900}"#,
        )
        .unwrap();
        assert_eq!(d.interval, 5);
    }
}
