// Load test k6 per la REST API di GrapeHealth.
//
// Prerequisito operativo: prima di lanciare questo test 
// la sessione del simulatore deve aver già generato almeno
// una allerta (qualunque scenario diverso da "normale"
// per qualche minuto e' sufficiente) in quanto se il database non contiene
// nessuna allerta, ne' vecchia ne' nuova, il metodo setup() non trova
// id da usare e il test degrada automaticamente sulla sola verifica di
// /api/misurazioni.
//
// Uso:
//   k6 run tests/load/grapehealth-load-test.js
//   k6 run --out json=risultati.json tests/load/grapehealth-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';

// Trend separati per endpoint: la media aggregata di k6 (http_req_duration)
// mescola i tre endpoint, che hanno costi molto diversi tra loro.
const durataMisurazioni = new Trend('durata_misurazioni', true);
const durataAllerte = new Trend('durata_allerte', true);
const durataRaccomandazioni = new Trend('durata_raccomandazioni', true);

const PARCELLE = ['parcellaA', 'parcellaB', 'parcellaC'];
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'psi_stem', 'temperatura_bacca', 'bagnatura_fogliare'];

export const options = {
  scenarios: {
    // Fase 1: smoke test, pochi VU, solo per validare che lo script sia corretto
    // e che il sistema risponda prima di salire di carico.
    smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '30s',
      exec: 'default',
      startTime: '0s',
    },
    // Fase 2: load test vero e proprio, rampa fino a decine di VU concorrenti.
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '2m', target: 30 },   // stato stazionario a 30 VU
        { duration: '30s', target: 0 },
      ],
      exec: 'default',
      startTime: '35s', // parte subito dopo lo smoke test
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],       // meno dell'1% di errori
    http_req_duration: ['p(95)<1500'],    // soglia indicativa, da tarare sui risultati osservati
    durata_misurazioni: ['p(95)<2000'],
    durata_allerte: ['p(95)<1000'],
    durata_raccomandazioni: ['p(95)<1000'],
  },
};

function randItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// setup() gira una sola volta, prima dell'avvio di tutti gli scenari, e il
// suo valore di ritorno viene passato a default(data) su ogni singola
// iterazione di ogni VU. Qui recupera fino a 100 allerte risolte già
// presenti a DB (qualunque sia stata la loro origine nella sessione), per
// poter interrogare /api/raccomandazioni?allertaId=X su id realmente
// esistenti invece che sulla sola lista delle allerte attive nell'istante del test.
export function setup() {
  const res = http.get(`${BASE_URL}/api/allerte?stato=risolta&size=100`);

  let idAllerteRisolte = [];
  try {
    const body = JSON.parse(res.body);
    idAllerteRisolte = (body.content || []).map((a) => a.id);
  } catch (e) {
    idAllerteRisolte = [];
  }

  if (idAllerteRisolte.length === 0) {
    console.warn(
      'setup(): nessuna allerta risolta trovata a DB. ' +
      '/api/raccomandazioni verra\' testato senza allertaId (lista delle attive, ' +
      'probabilmente vuota) e il check "raccomandazioni: contenuto non vuoto" fallira\' ' +
      'per l\'intera durata del test: prima di rilanciare k6, fai girare il simulatore ' +
      'con uno scenario di stress (stress_idrico / ondata_di_calore) per qualche minuto.'
    );
  } else {
    console.log(`setup(): trovate ${idAllerteRisolte.length} allerte risolte, id campione: ${idAllerteRisolte.slice(0, 5).join(', ')}...`);
  }

  return { idAllerteRisolte };
}

export default function (data) {
  // --- GET /api/misurazioni ---
  // Filtro solo su parcella + parametro: si lascia che il
  // PageableDefault lato server (size=50, sort=rilevatoIl DESC) restituisca
  // sempre gli ultimi record disponibili, indipendentemente dal disallineamento
  // tra orologio reale (k6) e orologio simulato (simulator/clock.py).
  const parcella = randItem(PARCELLE);
  const parametro = randItem(PARAMETRI);

  let res = http.get(
    `${BASE_URL}/api/misurazioni?parcella=${parcella}&parametro=${parametro}&size=50`,
    { tags: { endpoint: 'misurazioni' } }
  );
  durataMisurazioni.add(res.timings.duration);
  check(res, {
    'misurazioni: status 200': (r) => r.status === 200,
    'misurazioni: contenuto non vuoto': (r) => {
      try {
        return JSON.parse(r.body).content.length > 0;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.3);

  // --- GET /api/allerte ---
  // stato=risolta invece di stato=attiva: le allerte attive si risolvono in
  // pochi secondi/minuti reali (risoluzione accelerata dalla time-scale) e
  // possono benissimo essere zero nell'istante esatto in cui k6 gira, mentre
  // le risolte restano a DB indefinitamente e danno un test riproducibile.
  res = http.get(`${BASE_URL}/api/allerte?stato=risolta&size=50`, {
    tags: { endpoint: 'allerte' },
  });
  durataAllerte.add(res.timings.duration);
  check(res, {
    'allerte: status 200': (r) => r.status === 200,
    'allerte: contenuto non vuoto': (r) => {
      try {
        return JSON.parse(r.body).content.length > 0;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.3);

  // --- GET /api/raccomandazioni ---
  // Con allertaId pescato dal pool recuperato in setup(): esercita
  // RaccomandazioniService.perAllerta(id), che funziona indipendentemente
  // dallo stato dell'allerta, invece della sola perAllerteAttive() (lista
  // delle attive, quasi certamente vuota nell'istante del test). Se il
  // pool e' vuoto (nessuna allerta mai generata nella sessione) ripiega
  // sulla query senza filtro, comunque valida ma prevedibilmente vuota.
  const ids = data.idAllerteRisolte;
  const url = ids.length > 0
    ? `${BASE_URL}/api/raccomandazioni?allertaId=${randItem(ids)}`
    : `${BASE_URL}/api/raccomandazioni`;

  res = http.get(url, { tags: { endpoint: 'raccomandazioni' } });
  durataRaccomandazioni.add(res.timings.duration);
  check(res, {
    'raccomandazioni: status 200': (r) => r.status === 200,
    'raccomandazioni: contenuto non vuoto': (r) => {
      try {
        return JSON.parse(r.body).length > 0;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.5);
}