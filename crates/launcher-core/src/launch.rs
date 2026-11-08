use crate::args::Command;
use crate::error::Result;
use std::process::Stdio;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, BufReader};

#[derive(Debug, Clone, serde::Serialize)]
pub struct LogLine {
    pub stream: &'static str, // "stdout" | "stderr"
    pub line: String,
}

pub type LogFn = Arc<dyn Fn(LogLine) + Send + Sync>;

/// Start the game. Output is streamed line by line through `on_line`; the caller awaits
/// the returned child to learn the exit code.
pub fn spawn(cmd: &Command, on_line: LogFn) -> Result<tokio::process::Child> {
    std::fs::create_dir_all(&cmd.cwd)?;
    let mut c = tokio::process::Command::new(&cmd.program);
    c.args(&cmd.args)
        .current_dir(&cmd.cwd)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        c.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    let mut child = c.spawn()?;
    if let Some(out) = child.stdout.take() {
        let f = on_line.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(out).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                f(LogLine { stream: "stdout", line });
            }
        });
    }
    if let Some(err) = child.stderr.take() {
        let f = on_line;
        tokio::spawn(async move {
            let mut lines = BufReader::new(err).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                f(LogLine { stream: "stderr", line });
            }
        });
    }
    Ok(child)
}

#[cfg(all(test, unix))]
mod tests {
    use super::*;
    use std::sync::Mutex;

    #[tokio::test]
    async fn streams_output_and_exit_code() {
        let seen: Arc<Mutex<Vec<LogLine>>> = Arc::default();
        let s2 = seen.clone();
        let cmd = Command {
            program: "sh".into(),
            args: vec!["-c".into(), "echo hello; echo oops 1>&2; exit 3".into()],
            cwd: std::env::temp_dir(),
        };
        let mut child = spawn(&cmd, Arc::new(move |l| s2.lock().unwrap().push(l))).unwrap();
        let status = child.wait().await.unwrap();
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        assert_eq!(status.code(), Some(3));
        let lines = seen.lock().unwrap();
        assert!(lines.iter().any(|l| l.stream == "stdout" && l.line == "hello"));
        assert!(lines.iter().any(|l| l.stream == "stderr" && l.line == "oops"));
    }
}
