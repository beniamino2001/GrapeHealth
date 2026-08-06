async function aggiornaStatistiche(allerteAttive) {
  document.getElementById('kpiAllerteAttive').textContent = allerteAttive.length;
  renderAlertParcellaChart('chartAllerteParcella', allerteAttive);
  renderAlertLivelloChart('chartAllerteLivello', allerteAttive);

  try {
    const raccomandazioni = await GrapeHealthAPI.getRaccomandazioni({});
    const lista = estraiContenuto(raccomandazioni);
    const eseguite = lista.filter(r => r.basedOnSimulatedExecution);

    renderAzioniChart('chartAzioni', lista);

    const percentuale = lista.length > 0 ? Math.round((eseguite.length / lista.length) * 100) : 0;
    document.getElementById('kpiTassoEsecuzione').textContent = `${percentuale}%`;
  } catch (err) {
    console.error('Errore nel recupero delle statistiche sulle raccomandazioni', err);
    document.getElementById('kpiTassoEsecuzione').textContent = '—';
  }
}

function aggiornaKpiMisurazioni(count, nodiDistinti) {
  document.getElementById('kpiMisurazioniCaricate').textContent = count;
  document.getElementById('kpiNodiAttivi').textContent = nodiDistinti ?? '—';
}

const NUMERO_ALLERTE_PER_TEMPO_RISPOSTA = 20;
const INTERVALLO_TEMPO_RISPOSTA_MS = 60000;

let timerTempoRisposta = null;

async function aggiornaTempoRisposta() {
  const kpiTempo = document.getElementById('kpiTempoRisposta');
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'risolta', size: NUMERO_ALLERTE_PER_TEMPO_RISPOSTA });
    const risolte = estraiContenuto(risposta);

    if (risolte.length === 0) {
      kpiTempo.textContent = '—';
      return;
    }

    // /api/raccomandazioni senza allertaId copre solo le allerte ATTIVE, quindi
    // per lo storico serve una chiamata per singola allerta (funziona a
    // prescindere dallo stato, v. RaccomandazioniService.perAllerta).
    const raccomandazioni = await Promise.all(
      risolte.map(a => GrapeHealthAPI.getRaccomandazioni({ allertaId: a.id }).catch(() => null))
    );

    const durate = risolte
      .map((allerta, indice) => {
        const lista = raccomandazioni[indice] ? estraiContenuto(raccomandazioni[indice]) : [];
        const r = lista[0];
        if (!r || !r.basedOnSimulatedExecution || !r.eseguitaIl || !allerta.risoltaIl) return null;
        return new Date(allerta.risoltaIl) - new Date(r.eseguitaIl);
      })
      .filter(ms => ms !== null && ms >= 0);

    kpiTempo.textContent = durate.length > 0
      ? formattaDurata(durate.reduce((somma, ms) => somma + ms, 0) / durate.length)
      : '—';
  } catch (err) {
    console.error('Errore nel calcolo del tempo medio di risposta', err);
    kpiTempo.textContent = '—';
  }
}

function avviaAggiornamentoTempoRisposta() {
  aggiornaTempoRisposta();
  timerTempoRisposta = setInterval(aggiornaTempoRisposta, INTERVALLO_TEMPO_RISPOSTA_MS);
}