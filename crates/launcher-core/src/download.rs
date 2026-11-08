use crate::error::{Error, Result};
use crate::paths::ensure_parent;
use futures_util::{stream, StreamExt};
use serde::de::DeserializeOwned;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use tokio::io::AsyncWriteExt;

#[derive(Debug, Clone, serde::Serialize)]
pub struct Progress {
    pub stage: String,
    pub done: u64,
    pub total: u64,
}

pub type ProgressFn = Arc<dyn Fn(Progress) + Send + Sync>;

pub fn noop_progress() -> ProgressFn {
    Arc::new(|_| {})
}

#[derive(Debug, Clone)]
pub struct Job {
    pub url: String,
    pub dest: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

#[derive(Clone)]
pub struct Downloader {
    client: reqwest::Client,
}

impl Downloader {
    pub fn new() -> Result<Self> {
        let client = reqwest::Client::builder()
            .user_agent(concat!("VYRO-Client/", env!("CARGO_PKG_VERSION")))
            .connect_timeout(std::time::Duration::from_secs(15))
            .build()?;
        Ok(Self { client })
    }

    pub fn http(&self) -> &reqwest::Client {
        &self.client
    }

    pub async fn json<T: DeserializeOwned>(&self, url: &str) -> Result<T> {
        let r = self.client.get(url).send().await?.error_for_status()?;
        Ok(r.json::<T>().await?)
    }

    pub async fn text(&self, url: &str) -> Result<String> {
        Ok(self.client.get(url).send().await?.error_for_status()?.text().await?)
    }

    /// True if `dest` already holds what `job` describes. We trust file size when it is
    /// known (fast startup – thousands of assets) and fall back to hashing otherwise.
    fn up_to_date(job: &Job) -> bool {
        let Ok(meta) = std::fs::metadata(&job.dest) else { return false };
        if let Some(sz) = job.size {
            return meta.len() == sz;
        }
        if let Some(want) = &job.sha1 {
            return std::fs::read(&job.dest).map(|b| hex::encode(Sha1::digest(&b)) == *want).unwrap_or(false);
        }
        meta.len() > 0
    }

    pub async fn fetch(&self, job: &Job) -> Result<()> {
        if Self::up_to_date(job) {
            return Ok(());
        }
        ensure_parent(&job.dest)?;
        let mut last = None;
        for attempt in 0..3u32 {
            match self.fetch_once(job).await {
                Ok(()) => return Ok(()),
                Err(e) => {
                    last = Some(e);
                    tokio::time::sleep(std::time::Duration::from_millis(400 * (attempt as u64 + 1))).await;
                }
            }
        }
        Err(last.unwrap_or_else(|| Error::msg("download failed")))
    }

    async fn fetch_once(&self, job: &Job) -> Result<()> {
        let resp = self.client.get(&job.url).send().await?.error_for_status()?;
        let tmp = job.dest.with_extension("part");
        let mut file = tokio::fs::File::create(&tmp).await?;
        let mut hasher = Sha1::new();
        let mut body = resp.bytes_stream();
        while let Some(chunk) = body.next().await {
            let chunk = chunk?;
            hasher.update(&chunk);
            file.write_all(&chunk).await?;
        }
        file.flush().await?;
        drop(file);
        if let Some(want) = &job.sha1 {
            if hex::encode(hasher.finalize()) != *want {
                let _ = tokio::fs::remove_file(&tmp).await;
                return Err(Error::Checksum(job.url.clone()));
            }
        }
        tokio::fs::rename(&tmp, &job.dest).await?;
        Ok(())
    }

    /// Download many files concurrently, reporting progress per finished file.
    pub async fn fetch_all(&self, stage: &str, jobs: Vec<Job>, concurrency: usize, on: &ProgressFn) -> Result<()> {
        let total = jobs.len() as u64;
        let done = Arc::new(AtomicU64::new(0));
        on(Progress { stage: stage.into(), done: 0, total });
        let mut results = stream::iter(jobs.into_iter().map(|job| {
            let this = self.clone();
            let done = done.clone();
            let on = on.clone();
            let stage = stage.to_string();
            async move {
                let r = this.fetch(&job).await;
                let d = done.fetch_add(1, Ordering::Relaxed) + 1;
                on(Progress { stage, done: d, total });
                r
            }
        }))
        .buffer_unordered(concurrency.max(1));
        while let Some(r) = results.next().await {
            r?; // first error aborts the batch
        }
        Ok(())
    }
}

pub fn file_sha1(p: &Path) -> Result<String> {
    Ok(hex::encode(Sha1::digest(std::fs::read(p)?)))
}
