let PARCELLE_INFO = {}; // nome -> ParcellaDTO, popolato al bootstrap da /api/parcelle
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'pioggia', 'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca'];

const FINESTRE_TEMPORALI = {
  breve: { ms: null },
  giorno: { ms: 24 * 60 * 60 * 1000 },
  tre_giorni: { ms: 3 * 24 * 60 * 60 * 1000 },
  settimana: { ms: 7 * 24 * 60 * 60 * 1000 },
};

async function popolaFiltri() {
  const selParcella = document.getElementById('filtroParcella');
  const selParametro = document.getElementById('filtroParametro');
  const selRisolteParcella = document.getElementById('filtroRisolteParcella');

  try {
    const parcelle = await GrapeHealthAPI.getParcelle();
    parcelle.forEach(p => { PARCELLE_INFO[p.nome] = p; });
  } catch (err) {
    console.error('Impossibile caricare il catalogo parcelle da /api/parcelle', err);
    // I filtri restano utilizzabili sul solo parametro; nessuna opzione parcella oltre a "Tutte".
  }

  Object.keys(PARCELLE_INFO).sort().forEach(nome => {
    const opt = document.createElement('option');
    opt.value = nome; opt.textContent = nome;
    selParcella.appendChild(opt);

    const optRisolte = document.createElement('option');
    optRisolte.value = nome; optRisolte.textContent = nome;
    selRisolteParcella.appendChild(optRisolte);
  });

  PARAMETRI.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p;
    opt.textContent = p.replaceAll('_', ' ');
    selParametro.appendChild(opt);
  });
}

// Contatore di generazione: ogni invocazione di caricaGraficoMisurazioni()
// incrementa questo valore e ne cattura una copia locale. Se, al termine di
// un await, la copia locale non e' piu' la piu' recente, significa che nel
// frattempo e' partita un'altra invocazione (es. l'auto-refresh a 30s che si
// sovrappone a un click su "Aggiorna grafico", o a se stessa quando una fetch
// impiega piu' di 30s con --time-scale elevati): in tal caso la risposta e'
// scartata invece di essere renderizzata. Senza questo codice, due chiamate
// concorrenti possono entrambe arrivare a renderTrendChart() e finire per
// creare due istanze Chart.js sullo stesso canvas (una delle due "vince" la
// destroy() dell'altra nell'ordine sbagliato), con conseguente doppia scala e
// dataset residuo sovrapposti.
let generazioneGraficoMisurazioni = 0;

async function caricaGraficoMisurazioni() {
  const generazioneCorrente = ++generazioneGraficoMisurazioni;
  const erroreEl = document.getElementById('measurementError');
  erroreEl.textContent = '';

  const parcella = document.getElementById('filtroParcella').value || undefined;
  const parametro = document.getElementById('filtroParametro').value || 'temperatura_aria';
  const finestra = FINESTRE_TEMPORALI[document.getElementById('filtroFinestra').value];
  const finestraAttiva = Boolean(finestra.ms);

  try {
    let dal, al, size;

    if (finestraAttiva) {
      const ultima = await GrapeHealthAPI.getMisurazioni({ parcella, parametro, size: 1 });
      if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente

      const ultimeMisurazioni = estraiContenuto(ultima);
      if (ultimeMisurazioni.length === 0) {
        erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
        renderTrendChart('chartMisurazioni', [], parametro, parcella, undefined, finestraAttiva);
        aggiornaKpiMisurazioni(0, 0, null);
        return;
      }
      al = new Date(ultimeMisurazioni[0].rilevatoIl);
      dal = new Date(al.getTime() - finestra.ms);
      size = 2000; // tetto di sicurezza (limite di default di Spring Data)
    } else {
      size = parcella ? 100 : 300;
    }

    const risposta = await GrapeHealthAPI.getMisurazioni({
      parcella, parametro, size,
      dal: dal ? dal.toISOString() : undefined,
      al: al ? al.toISOString() : undefined,
    });
    if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente

    const misurazioni = estraiContenuto(risposta);

    if (misurazioni.length === 0) {
      erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
    }

    const unitaMisura = misurazioni[0]?.unitaMisura;
    const nodiDistinti = new Set(misurazioni.map(m => m.nodoCodice)).size;
    const ultimaRicevutaIl = misurazioni.reduce((max, m) => {
      const t = new Date(m.ricevutoIl).getTime();
      return t > max ? t : max;
    }, 0);

    renderTrendChart('chartMisurazioni', misurazioni, parametro, parcella, unitaMisura, finestraAttiva);
    aggiornaKpiMisurazioni(misurazioni.length, nodiDistinti, ultimaRicevutaIl || null);
  } catch (err) {
    if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente
    console.error('Errore nel recupero delle misurazioni', err);
    erroreEl.textContent = `Impossibile caricare i dati: ${err.message}`;
  }
}

// A differenza del pannello Allerte, il grafico delle misurazioni veniva caricato solo all'avvio o al click su
// "Aggiorna grafico": i KPI derivati (misurazioni caricate, nodi attivi,
// ultima ricevuta) restavano quindi fermi allo snapshot iniziale anche con
// dati nuovi in arrivo. Per risolvere ho introdotto un setInterval che richiama la funzione gia'
// esistente, rispettando i filtri correnti selezionati dall'utente.
const INTERVALLO_AGGIORNAMENTO_GRAFICO_MS = 30000;
let timerGraficoMisurazioni = null;

function avviaAggiornamentoGraficoMisurazioni() {
  timerGraficoMisurazioni = setInterval(caricaGraficoMisurazioni, INTERVALLO_AGGIORNAMENTO_GRAFICO_MS);
}

document.addEventListener('DOMContentLoaded', async () => {
  await popolaFiltri();
  inizializzaTabAllerte();

  document.getElementById('filtroForm').addEventListener('submit', e => {
    e.preventDefault();
    caricaGraficoMisurazioni();
  });

  caricaGraficoMisurazioni();
  avviaAggiornamentoGraficoMisurazioni();
  startPolling();
  avviaAggiornamentoTempoRisposta();
});