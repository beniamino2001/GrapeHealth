async function aggiornaStatistiche(allerteAttive) {
  document.getElementById('kpiAllerteAttive').textContent = allerteAttive.length;
  renderAlertParcellaChart('chartAllerteParcella', allerteAttive);
  renderAlertLivelloChart('chartAllerteLivello', allerteAttive);

  // Solo per il grafico "Azioni raccomandate (allerte attive)": lo scope e' gia'
  // dichiarato nel titolo, quindi resta corretto restringerlo alle sole attive.
  // Il KPI "Raccomandazioni con azione eseguita" e' calcolato altrove su un campione piu' ampio,
  // proprio per evitare che vada a 0%/— non appena non ci sono piu' allerte attive (caso normale a fine
  // scenario, non un'anomalia) mentre lo storico recente mostra azioni eseguite.
  try {
    const raccomandazioni = await GrapeHealthAPI.getRaccomandazioni({});
    const lista = estraiContenuto(raccomandazioni);
    renderAzioniChart('chartAzioni', lista);
  } catch (err) {
    console.error('Errore nel recupero delle raccomandazioni per il grafico azioni', err);
  }
}

function aggiornaKpiMisurazioni(count, nodiDistinti, ultimaRicevutaIl) {
  document.getElementById('kpiMisurazioniCaricate').textContent = count;
  document.getElementById('kpiNodiAttivi').textContent = nodiDistinti ?? '—';
  document.getElementById('kpiUltimaRicevuta').textContent = ultimaRicevutaIl
    ? new Date(ultimaRicevutaIl).toLocaleTimeString('it-IT')
    : '—';
}

const NUMERO_ALLERTE_PER_TEMPO_RISPOSTA = 20;
const INTERVALLO_TEMPO_RISPOSTA_MS = 60000;

let timerTempoRisposta = null;

async function aggiornaTempoRisposta() {
  const kpiTempo = document.getElementById('kpiTempoRisposta');
  const kpiTasso = document.getElementById('kpiTassoEsecuzione');
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'risolta', size: NUMERO_ALLERTE_PER_TEMPO_RISPOSTA });
    const risolte = estraiContenuto(risposta);

    // Campione per il tasso di esecuzione: le attive gia' in memoria (aggiornate
    // dal polling di alerts.js) piu' le ultime risolte appena recuperate. Cosi'
    // il KPI non crolla a 0%/— appena non ci sono piu' allerte attive (fine
    // scenario), pur restando limitato a un campione recente per lo stesso
    // motivo di contenimento chiamate gia' applicato al tempo di risposta.
    // Deduplicato con un Set: i due elenchi sono aggiornati da cicli di polling
    // indipendenti (12s per le attive, 60s per le risolte), quindi un'allerta
    // appena transitata da attiva a risolta puo' comparire in entrambi finche'
    // il prossimo giro di refreshAllerteAttive() non la rimuove dalle attive. 
    // L'endpoint non ne risentirebbe (l'IN della query SQL deduplica comunque), ma evitare
    // id ripetuti tiene la richiesta pulita e il codice corretto per costruzione.
    const idsCampione = [...new Set([
      ...ultimeAllerteAttive.map(a => a.id),
      ...risolte.map(a => a.id),
    ])];

    if (idsCampione.length === 0) {
      kpiTempo.textContent = '—';
      kpiTasso.textContent = '—';
      return;
    }

    // Una sola chiamata per l'intero lotto invece di una per allerta: l'endpoint
    // ignora silenziosamente gli id non trovati, quindi non serve gestire errori
    // per singola allerta. Funziona indipendentemente dallo stato (RaccomandazioniService.perAllerteMultiple),
    // quindi copre in un colpo solo sia le attive sia le risolte del campione.
    const raccomandazioni = await GrapeHealthAPI.getRaccomandazioni({ allertaIds: idsCampione });
    const perAllerta = new Map(raccomandazioni.map(r => [r.allertaId, r]));

    const eseguite = raccomandazioni.filter(r => r.basedOnSimulatedExecution);
    const percentuale = raccomandazioni.length > 0
      ? Math.round((eseguite.length / raccomandazioni.length) * 100)
      : 0;
    kpiTasso.textContent = raccomandazioni.length > 0 ? `${percentuale}%` : '—';

    const durate = risolte
      .map(allerta => {
        const r = perAllerta.get(allerta.id);
        if (!r || !r.basedOnSimulatedExecution || !r.eseguitaIl || !allerta.risoltaIl) return null;
        return new Date(allerta.risoltaIl) - new Date(r.eseguitaIl);
      })
      .filter(ms => ms !== null && ms >= 0);

    kpiTempo.textContent = durate.length > 0
      ? formattaDurata(durate.reduce((somma, ms) => somma + ms, 0) / durate.length)
      : '—';
  } catch (err) {
    console.error('Errore nel calcolo delle metriche di esecuzione/risposta', err);
    kpiTempo.textContent = '—';
    kpiTasso.textContent = '—';
  }
}

function avviaAggiornamentoTempoRisposta() {
  aggiornaTempoRisposta();
  timerTempoRisposta = setInterval(aggiornaTempoRisposta, INTERVALLO_TEMPO_RISPOSTA_MS);
}