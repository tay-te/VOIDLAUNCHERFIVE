//! A tiny HTTP/1.1 mock server for the tests: routes by path, records every request.
//!
//! Hand-rolled on a `TcpListener` instead of pulling in wiremock and its server stack: the
//! client only ever sends `POST` with a `Content-Length` JSON body, so a parser for exactly that
//! is a few dozen lines and keeps the dev-dependency tree empty.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use serde_json::Value;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

#[derive(Debug, Clone)]
pub(crate) struct Req {
    pub method: String,
    pub path: String,
    pub query: String,
    pub headers: HashMap<String, String>,
    pub body: Value,
}

impl Req {
    pub(crate) fn bearer(&self) -> Option<&str> {
        self.headers.get("authorization").and_then(|v| v.strip_prefix("Bearer "))
    }
}

pub(crate) struct Reply {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: String,
}

impl Reply {
    pub(crate) fn json(status: u16, v: Value) -> Reply {
        Reply { status, headers: Vec::new(), body: v.to_string() }
    }

    pub(crate) fn ok(v: Value) -> Reply {
        Reply::json(200, v)
    }

    pub(crate) fn empty(status: u16) -> Reply {
        Reply { status, headers: Vec::new(), body: String::new() }
    }

    pub(crate) fn header(mut self, k: &str, v: &str) -> Reply {
        self.headers.push((k.to_string(), v.to_string()));
        self
    }
}

type Handler = Arc<dyn Fn(&Req) -> Reply + Send + Sync>;

#[derive(Default)]
struct Shared {
    routes: Mutex<HashMap<String, Handler>>,
    log: Mutex<Vec<Req>>,
}

pub(crate) struct Mock {
    pub url: String,
    shared: Arc<Shared>,
}

impl Mock {
    pub(crate) async fn start() -> Mock {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let shared = Arc::new(Shared::default());
        let s = shared.clone();
        tokio::spawn(async move {
            while let Ok((sock, _)) = listener.accept().await {
                tokio::spawn(serve(sock, s.clone()));
            }
        });
        Mock { url, shared }
    }

    /// Answer requests to `path` (no query string) with `f`, replacing any earlier route.
    pub(crate) fn on(&self, path: &str, f: impl Fn(&Req) -> Reply + Send + Sync + 'static) {
        self.shared.routes.lock().unwrap().insert(path.to_string(), Arc::new(f));
    }

    pub(crate) fn requests(&self) -> Vec<Req> {
        self.shared.log.lock().unwrap().clone()
    }

    pub(crate) fn to(&self, path: &str) -> Vec<Req> {
        self.requests().into_iter().filter(|r| r.path == path).collect()
    }

    pub(crate) fn count(&self, path: &str) -> usize {
        self.to(path).len()
    }
}

fn find(hay: &[u8], needle: &[u8]) -> Option<usize> {
    hay.windows(needle.len()).position(|w| w == needle)
}

async fn serve(mut sock: TcpStream, shared: Arc<Shared>) {
    let mut buf: Vec<u8> = Vec::new();
    let mut tmp = [0u8; 8192];
    loop {
        let head_end = loop {
            if let Some(i) = find(&buf, b"\r\n\r\n") {
                break i;
            }
            match sock.read(&mut tmp).await {
                Ok(0) | Err(_) => return,
                Ok(n) => buf.extend_from_slice(&tmp[..n]),
            }
        };
        let head = String::from_utf8_lossy(&buf[..head_end]).to_string();
        let mut lines = head.split("\r\n");
        let mut request_line = lines.next().unwrap_or_default().split(' ');
        let method = request_line.next().unwrap_or_default().to_string();
        let target = request_line.next().unwrap_or_default().to_string();
        let headers: HashMap<String, String> = lines
            .filter_map(|l| l.split_once(':'))
            .map(|(k, v)| (k.trim().to_ascii_lowercase(), v.trim().to_string()))
            .collect();
        let len: usize = headers.get("content-length").and_then(|v| v.parse().ok()).unwrap_or(0);
        let start = head_end + 4;
        while buf.len() < start + len {
            match sock.read(&mut tmp).await {
                Ok(0) | Err(_) => return,
                Ok(n) => buf.extend_from_slice(&tmp[..n]),
            }
        }
        let body = serde_json::from_slice(&buf[start..start + len]).unwrap_or(Value::Null);
        buf.drain(..start + len);

        let (path, query) = target.split_once('?').map(|(p, q)| (p.to_string(), q.to_string())).unwrap_or((target, String::new()));
        let req = Req { method, path, query, headers, body };
        shared.log.lock().unwrap().push(req.clone());
        let handler = shared.routes.lock().unwrap().get(&req.path).cloned();
        let reply = match handler {
            Some(h) => h(&req),
            None => Reply::json(404, serde_json::json!({"error":"not_found","message":"No such route."})),
        };

        let mut out = format!("HTTP/1.1 {} Mock\r\n", reply.status);
        for (k, v) in &reply.headers {
            out.push_str(&format!("{k}: {v}\r\n"));
        }
        if reply.status != 204 && reply.status != 304 {
            out.push_str(&format!("content-type: application/json\r\ncontent-length: {}\r\n", reply.body.len()));
        }
        out.push_str("\r\n");
        out.push_str(&reply.body);
        if sock.write_all(out.as_bytes()).await.is_err() {
            return;
        }
    }
}
