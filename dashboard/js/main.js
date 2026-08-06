const PARCELLE = ['parcellaA', 'parcellaB', 'parcellaC'];
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca'];

const FINESTRE_TEMPORALI = {
  breve: { ms: null },
  giorno: { ms: 24 * 60 * 60 * 1000 },
  tre_giorni: { ms: 3 * 24 * 60 * 60 * 1000 },
  settimana: { ms: 7 * 24 * 60 * 60 * 1000 },
};

function popolaFiltri() {
  const selParcella = document.getElementById('filtroParcella');
  const selParametro = document.getElementById('filtroParametro');

  PARCELLE.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p;
    opt.textContent = p;
    selParcella.appendChild(opt);
  });

  PARAMETRI.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p;
    opt.textContent = p.replaceAll('_', ' ');
    selParametro.appendChild(opt);
  });
}

async function caricaGraficoMisurazioni() {
  const erroreEl = document.getElementById('measurementError');
  erroreEl.textContent = '';

  const parcella = document.getElementById('filtroParcella').value || undefined;
  const parametro = document.getElementById('filtroParametro').value || 'temperatura_aria';
  const finestra = FINESTRE_TEMPORALI[document.getElementById('filtroFinestra').value];

  try {
    let dal, al, size;

    if (finestra.ms) {
      const ultima = await GrapeHealthAPI.getMisurazioni({ parcella, parametro, size: 1 });
      const ultimeMisurazioni = estraiContenuto(ultima);
      if (ultimeMisurazioni.length === 0) {
        erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
        renderTrendChart('chartMisurazioni', [], parametro, parcella, undefined);
        aggiornaKpiMisurazioni(0, 0);
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
    const misurazioni = estraiContenuto(risposta);

    if (misurazioni.length === 0) {
      erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
    } else if (risposta.totalElements > misurazioni.length) {
      erroreEl.textContent = finestra.ms
        ? `Vista campionata: ${misurazioni.length} punti caricati su ${risposta.totalElements} disponibili nella finestra selezionata per questioni di leggibilità`
        : `Stai visualizzando le ${misurazioni.length} misurazioni più recenti (${risposta.totalElements} disponibili in totale per questo parametro).`;
    }

    const unitaMisura = misurazioni[0]?.unitaMisura;
    const nodiDistinti = new Set(misurazioni.map(m => m.nodoCodice)).size;

    renderTrendChart('chartMisurazioni', misurazioni, parametro, parcella, unitaMisura);
    aggiornaKpiMisurazioni(misurazioni.length, nodiDistinti);
  } catch (err) {
    console.error('Errore nel recupero delle misurazioni', err);
    erroreEl.textContent = `Impossibile caricare i dati: ${err.message}`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  popolaFiltri();
  inizializzaTabAllerte();

  document.getElementById('filtroForm').addEventListener('submit', e => {
    e.preventDefault();
    caricaGraficoMisurazioni();
  });

  caricaGraficoMisurazioni();
  startPolling();
  avviaAggiornamentoTempoRisposta();
});