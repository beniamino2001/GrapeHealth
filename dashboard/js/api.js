// Base URL del modulo `api` su porta 8084 con la config CORS già abilitato per le GET su localhost:*
const API_BASE = 'http://localhost:8084/api';

function buildQuery(params) {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  return qs ? `?${qs}` : '';
}

async function fetchJSON(url) {
  const res = await fetch(url);
  if (!res.ok) {
    let msg = `Errore HTTP ${res.status}`;
    try {
      const body = await res.json();
      msg = body.messaggio || body.errore || msg;
    } catch (_) { /* corpo non JSON, viene restituito il messaggio generico */ }
    throw new Error(msg);
  }
  return res.json();
}

function estraiContenuto(risposta) {
  if (Array.isArray(risposta)) return risposta;
  if (risposta && Array.isArray(risposta.content)) return risposta.content;
  return risposta ? [risposta] : [];
}

const GrapeHealthAPI = {
  getMisurazioni({ parcella, parametro, dal, al, page = 0, size = 50 } = {}) {
    return fetchJSON(`${API_BASE}/misurazioni${buildQuery({ parcella, parametro, dal, al, page, size })}`);
  },
  getAllerte({ stato = 'attiva', tipo, parcella, page = 0, size = 50 } = {}) {
    return fetchJSON(`${API_BASE}/allerte${buildQuery({ stato, tipo, parcella, page, size })}`);
  },
  getRaccomandazioni({ allertaId } = {}) {
    return fetchJSON(`${API_BASE}/raccomandazioni${buildQuery({ allertaId })}`);
  },
};

// Funzione utile a formattare una durata in ms in una stringa leggibile, con granularità decrescente (ore > minuti > secondi).
function formattaDurata(ms) {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  const totaleSecondi = Math.round(ms / 1000);
  const minuti = Math.floor(totaleSecondi / 60);
  const secondi = totaleSecondi % 60;
  if (minuti === 0) return `${secondi}s`;
  const ore = Math.floor(minuti / 60);
  if (ore === 0) return `${minuti}m ${secondi}s`;
  return `${ore}h ${minuti % 60}m`;
}