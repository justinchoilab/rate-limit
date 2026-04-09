import { useEffect, useRef, useState } from "react";
import styles from "./RateLimitPage.module.css";
import { API_BASE_URL } from "../../config";
import LoadingButton from "../../components/LoadingButton";

interface EndpointState {
  limit: number;
  remaining: number;
  resetIn: number;
}

interface LogEntry {
  id: number;
  status: number;
  remaining: number;
  resetIn: number;
  ts: string;
}

interface Stats {
  ok: number;
  limited: number;
  error: number;
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("auth_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function fetchInfo(): Promise<{ state: EndpointState; redisAvailable: boolean }> {
  const res = await fetch(`${API_BASE_URL}/rate-limit/info`, { headers: authHeaders() });
  if (!res.ok) throw new Error();
  const data = await res.json();
  if (!data.redisAvailable) return { state: { limit: 10, remaining: 10, resetIn: 0 }, redisAvailable: false };
  return { state: data.api, redisAvailable: true };
}

async function sendRequest(): Promise<{ status: number; remaining: number; resetIn: number }> {
  const res = await fetch(`${API_BASE_URL}/rate-limit/call`, { headers: authHeaders() });
  const remaining = parseInt(res.headers.get("X-RateLimit-Remaining") ?? "0");
  const resetIn = parseInt(res.headers.get("X-RateLimit-Reset") ?? "0");
  return { status: res.status, remaining, resetIn };
}

let logIdCounter = 0;

function progressColor(remaining: number, limit: number): string {
  if (remaining === 0) return "var(--primary-red)";
  const usedPct = (limit - remaining) / limit;
  if (usedPct < 0.8) return "var(--primary-green)";
  return "var(--accent-amber)";
}

function remainingColor(remaining: number, limit: number): string {
  if (remaining === 0) return "var(--primary-red)";
  const pct = remaining / limit;
  if (pct > 0.2) return "var(--primary-green)";
  return "var(--accent-amber)";
}

function now(): string {
  return new Date().toLocaleTimeString("ko-KR", { hour12: false, hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

const BURST_COUNT = 5;

export default function RateLimitPage() {
  const [state, setState] = useState<EndpointState>({ limit: 10, remaining: 10, resetIn: 0 });
  const [countdown, setCountdown] = useState(0);
  const [resetAt, setResetAt] = useState(0);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [stats, setStats] = useState<Stats>({ ok: 0, limited: 0, error: 0 });
  const [redisAvailable, setRedisAvailable] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);
  const [bursting, setBursting] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const consoleRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  useEffect(() => {
    fetchInfo().then(({ state: s, redisAvailable: ra }) => {
      if (!mountedRef.current) return;
      setState(s);
      setRedisAvailable(ra);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    if (resetAt <= 0) { setCountdown(0); return; }
    const id = setInterval(() => {
      const secs = Math.max(0, Math.ceil((resetAt - Date.now()) / 1000));
      setCountdown(secs);
      if (secs === 0) {
        clearInterval(id);
        setResetAt(0);
        setState(prev => ({ ...prev, remaining: prev.limit }));
      }
    }, 1000);
    setCountdown(Math.max(0, Math.ceil((resetAt - Date.now()) / 1000)));
    return () => clearInterval(id);
  }, [resetAt]);

  useEffect(() => {
    if (consoleRef.current) consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
  }, [logs]);

  const pushLog = (entry: Omit<LogEntry, "id">) => {
    setLogs(prev => [...prev, { ...entry, id: logIdCounter++ }].slice(-300));
  };

  const handleRequest = async () => {
    setLoading(true);
    try {
      const r = await sendRequest();
      if (!mountedRef.current) return;
      setState(prev => ({ ...prev, remaining: r.remaining, resetIn: r.resetIn }));
      if (r.resetIn > 0) setResetAt(Date.now() + r.resetIn * 1000);
      pushLog({ status: r.status, remaining: r.remaining, resetIn: r.resetIn, ts: now() });
      setStats(prev => ({
        ...prev,
        ok: prev.ok + (r.status === 200 ? 1 : 0),
        limited: prev.limited + (r.status === 429 ? 1 : 0),
      }));
    } catch {
      if (!mountedRef.current) return;
      pushLog({ status: 0, remaining: 0, resetIn: 0, ts: now() });
      setStats(prev => ({ ...prev, error: prev.error + 1 }));
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  };

  const handleBurst = async () => {
    setBursting(true);
    const newLogs: LogEntry[] = [];
    const batch = { ok: 0, limited: 0, error: 0 };

    await Promise.all(
      Array.from({ length: BURST_COUNT }, () =>
        sendRequest()
          .then(r => {
            newLogs.push({ id: logIdCounter++, status: r.status, remaining: r.remaining, resetIn: r.resetIn, ts: now() });
            if (r.status === 200) batch.ok++;
            else if (r.status === 429) batch.limited++;
            else batch.error++;
            if (mountedRef.current) {
              setState(prev => ({ ...prev, remaining: r.remaining, resetIn: r.resetIn }));
              if (r.resetIn > 0) setResetAt(Date.now() + r.resetIn * 1000);
            }
          })
          .catch(() => {
            newLogs.push({ id: logIdCounter++, status: 0, remaining: 0, resetIn: 0, ts: now() });
            batch.error++;
          })
      )
    );

    if (!mountedRef.current) return;
    newLogs.sort((a, b) => a.id - b.id);
    setLogs(prev => [...prev, ...newLogs].slice(-300));
    setStats(prev => ({
      ok: prev.ok + batch.ok,
      limited: prev.limited + batch.limited,
      error: prev.error + batch.error,
    }));
    setBursting(false);
  };

  const usedPct = Math.min(100, ((state.limit - state.remaining) / state.limit) * 100);
  const busy = loading || bursting;

  return (
    <div className={styles.page}>

      {showHelp && <div className={styles.overlay} onClick={() => setShowHelp(false)} />}
      {showHelp && (
        <div className={styles.modal}>
          <button className={styles.modalClose} onClick={() => setShowHelp(false)}>✕</button>
          <h3 className={styles.modalTitle}>동작 원리</h3>
          <div className={styles.helpBody}>
            <p>같은 IP에서 10초 안에 요청을 너무 많이 보내면 잠시 차단하는 기능입니다. 서버 과부하나 악의적인 반복 호출을 막기 위해 사용합니다.</p>
            <dl className={styles.helpList}>
              <dt>횟수 계산 방식</dt>
              <dd>첫 요청이 들어오면 서버에 카운터를 만들고 10초 타이머를 시작합니다. 이후 요청마다 카운터를 1씩 올리고, 10번을 넘으면 차단합니다. 타이머가 끝나면 카운터는 자동으로 0으로 돌아갑니다.</dd>
              <dt>차단 시 동작</dt>
              <dd>서버가 <code>429 Too Many Requests</code>로 응답하며, 차단된 시점부터 10초가 지나야 다시 요청할 수 있습니다. 차단 상태에서 계속 요청을 보내면 그때마다 10초 타이머가 다시 시작됩니다.</dd>
              <dt>차단 기준</dt>
              <dd>접속자의 IP 주소를 기준으로 합니다. 카페나 회사처럼 여러 사람이 같은 IP를 공유하는 환경에서는 한 사람이 많이 보내면 다른 사람도 영향을 받을 수 있습니다.</dd>
            </dl>
          </div>
        </div>
      )}

      <header className={styles.header}>
        <div className={styles.titleBlock}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>API 요청 제한</h1>
            <button className={styles.helpBtn} onClick={() => setShowHelp(true)}>?</button>
          </div>
          <p className={styles.subtitle}>같은 접속자가 10초 안에 보낼 수 있는 요청 횟수를 제한합니다</p>
        </div>
      </header>

      {redisAvailable === false && (
        <div className={styles.notReady}>
          <span className={styles.notReadyDot} />
          Redis 연결 대기 중
        </div>
      )}

      {/* 카드 */}
      {redisAvailable === true && <div className={styles.card}>
        <div className={styles.remainingRow}>
          <span className={styles.remainingNum} style={{ color: remainingColor(state.remaining, state.limit) }}>
            {state.remaining}
          </span>
          <span className={styles.remainingOf}>/ {state.limit} 남음</span>
        </div>

        <div className={styles.progressTrack}>
          <div
            className={styles.progressFill}
            style={{ width: `${usedPct}%`, backgroundColor: progressColor(state.remaining, state.limit) }}
          />
        </div>

        {countdown > 0 && (
          <span className={styles.resetLabel}>초기화까지 {countdown}s</span>
        )}

        <div className={styles.btnRow}>
          <LoadingButton className={styles.requestBtn} onClick={handleRequest} disabled={busy} isLoading={loading} loadingText="요청 중">
            요청
          </LoadingButton>
          <LoadingButton className={styles.burstBtn} onClick={handleBurst} disabled={busy} isLoading={bursting} loadingText={`${BURST_COUNT}개 처리 중`}>
            {BURST_COUNT}개 연속
          </LoadingButton>
        </div>
      </div>}

      {/* 로그 */}
      {redisAvailable === true && <div className={styles.logCard}>
        <h2 className={styles.cardTitle}>응답 로그</h2>
        <div className={styles.console} ref={consoleRef}>
          {logs.map(entry => (
            <div
              key={entry.id}
              className={`${styles.logLine} ${
                entry.status === 200 ? styles.logOk :
                entry.status === 429 ? styles.logLimited :
                styles.logErr
              }`}
            >
              {`[${entry.ts}]  ${entry.status || "ERR"}  remaining: ${entry.remaining}  reset: ${entry.resetIn}s`}
            </div>
          ))}
        </div>
        <div className={styles.statsBar}>
          <span className={styles.logOk}>성공 {stats.ok}</span>
          <span className={styles.logLimited}>차단 {stats.limited}</span>
          <span className={styles.logErr}>오류 {stats.error}</span>
        </div>
      </div>}
    </div>
  );
}
