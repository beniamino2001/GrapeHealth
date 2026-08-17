// Load test k6 per la REST API di GrapeHealth.
//
// Uso:
//   k6 run tests/load/grapehealth-load-test.js
//   k6 run --out json=risultati.json tests/load/grapehealth-load-test.js
//   (unit test delle funzioni pure: cd tests/load && npm test)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { randItem, randSample } from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';

// Trend separati per endpoint: la media aggregata di k6 (http_req_duration)
// mescola endpoint dal costo molto diverso tra loro. Allerte non filtrate
// separate da allerte filtrate per tipo/parcella (query non indicizzata),
// raccomandazione singola separata da raccomandazione batch;
// /api/parcelle ha il proprio Trend.
const durataMisurazioni = new Trend('durata_misurazioni', true);
const durataAllerte = new Trend('durata_allerte', true);
const durataAllerteFiltrate = new Trend('durata_allerte_filtrate', true);
const durataRaccomandazioni = new Trend('durata_raccomandazioni', true);
const durataRaccomandazioniBatch = new Trend('durata_raccomandazioni_batch', true);
const durataParcelle = new Trend('durata_parcelle', true);

const PARCELLE = ['parcellaA', 'parcellaB', 'parcellaC'];
// Le stesse tre parcelle del seed, più un nome deliberatamente inesistente:
// usato per esercitare anche il percorso 404 di /api/parcelle/{nome} (GlobalExceptionHandler).
const NOMI_PARCELLA_CON_INESISTENTE = [...PARCELLE, 'parcellaInesistente'];
// I sei parametri realmente pubblicati dai tre tipi di nodo:
// meteo -> temperatura_aria, umidita_aria, pioggia, bagnatura_fogliare; idrico -> psi_stem;
// bacca -> temperatura_bacca.
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'pioggia', 'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca'];
// I quattro tipi di allerta realmente prodotti dal decision engine.
const TIPI_ALLERTA = ['stress_idrico', 'ondata_di_calore', 'sunburn', 'tre_dieci'];
// Dimensione del campione per la chiamata batch /api/raccomandazioni?allertaIds=...
const CAMPIONE_BATCH_RACCOMANDAZIONI = 5;

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '30s',
      exec: 'default',
      startTime: '0s',
    },
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
      ],
      exec: 'default',
      startTime: '35s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
    durata_misurazioni: ['p(95)<2000'],
    durata_allerte: ['p(95)<1000'],
    durata_allerte_filtrate: ['p(95)<1000'],
    durata_raccomandazioni: ['p(95)<1000'],
    durata_raccomandazioni_batch: ['p(95)<1500'],
    durata_parcelle: ['p(95)<1000'],
  },
};

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
      'probabilmente vuota) e i check su questo endpoint falliranno per l\'intera ' +
      'durata del test: prima di rilanciare k6, fai girare il simulatore con uno ' +
      'scenario di stress (stress_idrico / ondata_di_calore) per qualche minuto.'
    );
  } else {
    console.log(`setup(): trovate ${idAllerteRisolte.length} allerte risolte, id campione: ${idAllerteRisolte.slice(0, 5).join(', ')}...`);
  }

  // /api/parcelle interrogato una sola volta, mirroring esatto del pattern reale della dashboard
  // (dashboard/js/main.js carica PARCELLE_INFO una volta al bootstrap, non a
  // ogni render).
  const resParcelle = http.get(`${BASE_URL}/api/parcelle`);
  check(resParcelle, {
    'setup: /api/parcelle status 200': (r) => r.status === 200,
    'setup: tutte le parcelle del seed hanno varieta/colore bacca/germoglio': (r) => {
      try {
        const lista = JSON.parse(r.body);
        return lista.length === PARCELLE.length && lista.every(
          (p) => Boolean(p.varieta) && Boolean(p.coloreBacca) && p.lunghezzaGermoglioCm != null
        );
      } catch (e) {
        return false;
      }
    },
  });

  return { idAllerteRisolte };
}

export default function (data) {
  // --- GET /api/misurazioni ---
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
    'misurazioni: arricchimento nodo/parcella presente': (r) => {
      try {
        const primo = JSON.parse(r.body).content[0];
        return Boolean(primo) && Boolean(primo.nodoCodice) && Boolean(primo.parcella);
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.3);

  // --- GET /api/allerte (stato=risolta) ---
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
    'allerte: regolaScatenante presente': (r) => {
      try {
        const primo = JSON.parse(r.body).content[0];
        return Boolean(primo) && Boolean(primo.regolaScatenante);
      } catch (e) {
        return false;
      }
    },
  });

  // --- GET /api/allerte (stato=risolta, tipo/parcella filtrati) ---
  const tipoAllerta = randItem(TIPI_ALLERTA);
  const parcellaAllerta = randItem(PARCELLE);
  res = http.get(
    `${BASE_URL}/api/allerte?stato=risolta&tipo=${tipoAllerta}&parcella=${parcellaAllerta}&size=50`,
    { tags: { endpoint: 'allerte_filtrate' } }
  );
  durataAllerteFiltrate.add(res.timings.duration);
  check(res, {
    'allerte (filtrate per tipo/parcella): status 200': (r) => r.status === 200,
  });

  // --- GET /api/allerte (stato=attiva) ---
  res = http.get(`${BASE_URL}/api/allerte?stato=attiva&size=50`, {
    tags: { endpoint: 'allerte' },
  });
  durataAllerte.add(res.timings.duration);
  check(res, {
    'allerte (attive): status 200': (r) => r.status === 200,
  });

  sleep(0.3);

  // --- GET /api/raccomandazioni (allertaId singolo) ---
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
    'raccomandazioni: arricchimento bibliografico presente': (r) => {
      try {
        const prima = JSON.parse(r.body)[0];
        return Boolean(prima) && Boolean(prima.fonteBibliograficaRegola) &&
               Array.isArray(prima.azioniAlternative) && prima.azioniAlternative.length > 0;
      } catch (e) {
        return false;
      }
    },
    'raccomandazioni: esito simulato presente': (r) => {
      try {
        const prima = JSON.parse(r.body)[0];
        return Boolean(prima) && prima.basedOnSimulatedExecution === true &&
               Boolean(prima.azioneEseguita) && Boolean(prima.esitoSimulato) &&
               Boolean(prima.eseguitaIl);
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.3);

  // --- GET /api/raccomandazioni (allertaIds batch) ---
  const campioneBatch = randSample(ids, CAMPIONE_BATCH_RACCOMANDAZIONI);
  if (campioneBatch.length > 0) {
    const queryBatch = campioneBatch.map((id) => `allertaIds=${id}`).join('&');
    res = http.get(`${BASE_URL}/api/raccomandazioni?${queryBatch}`, {
      tags: { endpoint: 'raccomandazioni_batch' },
    });
    durataRaccomandazioniBatch.add(res.timings.duration);
    check(res, {
      'raccomandazioni (batch): status 200': (r) => r.status === 200,
      'raccomandazioni (batch): numero elementi coerente con l\'input': (r) => {
        try {
          return JSON.parse(r.body).length === campioneBatch.length;
        } catch (e) {
          return false;
        }
      },
    });
  }

  // --- GET /api/parcelle/{nome}.
  // Un nome su quattro è deliberatamente inesistente ('parcellaInesistente'):
  // testa sia il percorso 200 (dato reale, consumato da alerts.js) sia il
  // percorso 404 di GlobalExceptionHandler. 
  // expectedStatuses(200, 404) evita che i 404 voluti sporchino la soglia globale http_req_failed.
  const nomeParcella = randItem(NOMI_PARCELLA_CON_INESISTENTE);
  res = http.get(`${BASE_URL}/api/parcelle/${nomeParcella}`, {
    tags: { endpoint: 'parcelle' },
    responseCallback: http.expectedStatuses(200, 404),
  });
  durataParcelle.add(res.timings.duration);
  check(res, {
    'parcelle: status 200 o 404 coerente con il nome richiesto': (r) => {
      const esisteDavvero = PARCELLE.includes(nomeParcella);
      return esisteDavvero ? r.status === 200 : r.status === 404;
    },
    'parcelle: corpo coerente con lo status': (r) => {
      try {
        const body = JSON.parse(r.body);
        if (r.status === 200) {
          return body.nome === nomeParcella && Boolean(body.varieta) && Boolean(body.coloreBacca);
        }
        return r.status === 404 && body.status === 404 && Boolean(body.messaggio);
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.5);
}