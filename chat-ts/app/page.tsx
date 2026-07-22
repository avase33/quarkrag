"use client";

import { useEffect, useRef, useState } from "react";

const ENGINE = process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8080";

type Context = { id: string; docId: string; text: string; score: number };
type QueryResult = { answer: string; contexts: Context[]; model: string; tookMs: number };
type Stats = {
  documents: number;
  chunks: number;
  embeddingDim: number;
  store: string;
  llm: string;
};
type Turn = { question: string; result: QueryResult | null };

const SAMPLE = `# Time Off Policy

Employees accrue paid time off every month and can carry a limited balance into the next year.

To request time off, open the HR portal and submit a leave request with your dates. Your manager approves or declines within two business days.

Sick leave is separate from vacation and does not require advance notice.`;

export default function Page() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [docId, setDocId] = useState("hr/pto.md");
  const [docText, setDocText] = useState(SAMPLE);
  const [question, setQuestion] = useState("how do I request time off?");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("connecting");
  const feedRef = useRef<HTMLDivElement | null>(null);

  const loadStats = async () => {
    try {
      const res = await fetch(`${ENGINE}/api/stats`);
      setStats((await res.json()) as Stats);
      setStatus("live");
    } catch {
      setStatus("offline");
    }
  };

  const ingest = async () => {
    setBusy(true);
    try {
      await fetch(`${ENGINE}/api/ingest`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ docId, text: docText }),
      });
      await loadStats();
    } catch {
      setStatus("offline");
    } finally {
      setBusy(false);
    }
  };

  const ask = async () => {
    if (!question.trim()) return;
    setBusy(true);
    const asked = question;
    setTurns((t) => [{ question: asked, result: null }, ...t]);
    try {
      const res = await fetch(`${ENGINE}/api/query`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ question: asked, k: 4 }),
      });
      const result = (await res.json()) as QueryResult;
      setTurns((t) => t.map((turn, i) => (i === 0 ? { ...turn, result } : turn)));
    } catch {
      setStatus("offline");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    loadStats();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <main style={{ padding: 24, maxWidth: 980, margin: "0 auto" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h1 style={{ fontSize: 22, margin: 0 }}>quarkrag</h1>
        <span style={{ fontSize: 13, color: status === "live" ? "#4ec9b0" : "#ff7b72" }}>
          {status}
          {stats && ` · ${stats.documents} docs · ${stats.chunks} chunks · ${stats.store} · ${stats.llm}`}
        </span>
      </header>

      <section style={{ marginTop: 18 }}>
        <div style={{ color: "#8b949e", fontSize: 13, marginBottom: 6 }}>1 — index a document</div>
        <input
          value={docId}
          onChange={(e) => setDocId(e.target.value)}
          style={inputStyle}
          placeholder="doc id"
        />
        <textarea
          value={docText}
          onChange={(e) => setDocText(e.target.value)}
          rows={7}
          style={{ ...inputStyle, marginTop: 8, fontFamily: "ui-monospace, monospace", resize: "vertical" }}
        />
        <button onClick={ingest} disabled={busy} style={btn("#1f6feb")}>
          {busy ? "working…" : "ingest"}
        </button>
      </section>

      <section style={{ marginTop: 26 }}>
        <div style={{ color: "#8b949e", fontSize: 13, marginBottom: 6 }}>2 — ask a question</div>
        <div style={{ display: "flex", gap: 8 }}>
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && ask()}
            style={{ ...inputStyle, flex: 1 }}
          />
          <button onClick={ask} disabled={busy} style={btn("#238636")}>
            ask
          </button>
        </div>
      </section>

      <section ref={feedRef} style={{ marginTop: 22 }}>
        {turns.map((turn, i) => (
          <div
            key={i}
            style={{
              border: "1px solid #30363d",
              borderRadius: 10,
              padding: 14,
              marginBottom: 12,
              background: "#0b0f14",
            }}
          >
            <div style={{ color: "#58a6ff", fontWeight: 600 }}>{turn.question}</div>
            {!turn.result && <div style={{ color: "#8b949e", marginTop: 8 }}>thinking…</div>}
            {turn.result && (
              <>
                <div style={{ marginTop: 8, lineHeight: 1.5 }}>{turn.result.answer}</div>
                <div style={{ marginTop: 10, fontSize: 12, color: "#8b949e" }}>
                  {turn.result.model} · {turn.result.tookMs} ms
                </div>
                <details style={{ marginTop: 8 }}>
                  <summary style={{ cursor: "pointer", color: "#8b949e", fontSize: 13 }}>
                    {turn.result.contexts.length} retrieved chunks
                  </summary>
                  {turn.result.contexts.map((c) => (
                    <div
                      key={c.id}
                      style={{ borderTop: "1px solid #21262d", padding: "8px 0", fontSize: 13 }}
                    >
                      <b style={{ color: "#58a6ff" }}>{c.id}</b>{" "}
                      <span style={{ color: "#8b949e" }}>score {c.score}</span>
                      <div style={{ color: "#c9d1d9", marginTop: 4 }}>{c.text}</div>
                    </div>
                  ))}
                </details>
              </>
            )}
          </div>
        ))}
      </section>
    </main>
  );
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "8px 10px",
  background: "#0b0f14",
  border: "1px solid #30363d",
  borderRadius: 8,
  color: "#e6edf3",
  boxSizing: "border-box",
};

function btn(bg: string): React.CSSProperties {
  return {
    marginTop: 8,
    padding: "8px 16px",
    background: bg,
    color: "#fff",
    border: "none",
    borderRadius: 8,
    cursor: "pointer",
    fontSize: 14,
  };
}
