// Lista e dettaglio delle allerte: tab Attive/Risolte con paginazione,
// polling periodico delle attive, filtro per tipo dal grafico a ciambella, e
// il pannello di dettaglio con la raccomandazione arricchita (soglie
// bibliografiche, azioni alternative, esito dell'esecuzione simulata).
const POLL_INTERVAL_MS = 12000;
let pollTimer = null;

let ultimeAllerteAttive = [];
let filtroTipoAttuale = null;

let tabAllerteAttuale = 'attiva';
let paginaRisolteCorrente = 0;
let paginaRisolteInfo = { totalPages: 1, totalElements: 0 };

async function refreshAllerteAttive() {
  const erroreEl = document.getElementById('alertError');
  erroreEl.textContent = '';
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'attiva', size: 100 });
    const allerte = estraiContenuto(risposta);
    ultimeAllerteAttive = allerte;

    renderAlertTypeChart('chartAllerteTipo', allerte);

    if (tabAllerteAttuale === 'attiva') {
      if (filtroTipoAttuale && ultimeAllertePerTipo[filtroTipoAttuale]) {
        renderAlertList(ultimeAllertePerTipo[filtroTipoAttuale], filtroTipoAttuale);
      } else {
        filtroTipoAttuale = null;
        renderAlertList(allerte);
      }
    }

    document.getElementById('alertCount').textContent = allerte.length;
    document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString('it-IT');

    aggiornaStatistiche(allerte);

    return allerte;
  } catch (err) {
    console.error('Errore nel recupero delle allerte attive', err);
    erroreEl.textContent = `Impossibile aggiornare le allerte: ${err.message}`;
  }
}

function filtraPerTipoAllerta(tipo) {
  filtroTipoAttuale = tipo;
  const allerteDelTipo = ultimeAllertePerTipo[tipo] || [];
  renderAlertList(allerteDelTipo, tipo);
  if (allerteDelTipo.length > 0) {
    mostraRaccomandazione(allerteDelTipo[0]);
  }
}

function resetFiltroTipo() {
  filtroTipoAttuale = null;
  renderAlertList(ultimeAllerteAttive);
}

// --- Tab Attive / Risolte -------------------------------------------------

function inizializzaTabAllerte() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => cambiaTabAllerte(btn.dataset.stato));
  });
  document.getElementById('filtroRisolteTipo').addEventListener('change', () => caricaAllerteRisolte(0));
  document.getElementById('filtroRisolteParcella').addEventListener('change', () => caricaAllerteRisolte(0));
}

function cambiaTabAllerte(stato) {
  tabAllerteAttuale = stato;
  filtroTipoAttuale = null;
  paginaRisolteCorrente = 0;

  document.querySelectorAll('.tab-btn').forEach(btn =>
    btn.classList.toggle('active', btn.dataset.stato === stato)
  );

  const filtriRisolte = document.getElementById('filtriRisolte');

  if (stato === 'attiva') {
    filtriRisolte.hidden = true;
    document.getElementById('paginazioneAllerte').innerHTML = '';
    renderAlertList(ultimeAllerteAttive);
  } else {
    filtriRisolte.hidden = false;
    caricaAllerteRisolte(0);
  }
}

async function caricaAllerteRisolte(page) {
  const container = document.getElementById('alertList');
  container.innerHTML = '<p class="empty-state">Caricamento…</p>';
  const tipo = document.getElementById('filtroRisolteTipo').value || undefined;
  const parcella = document.getElementById('filtroRisolteParcella').value || undefined;
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'risolta', tipo, parcella, page, size: 10 });
    const allerte = estraiContenuto(risposta);
    paginaRisolteCorrente = risposta.page?.number ?? page;
    paginaRisolteInfo = {
      totalPages: risposta.page?.totalPages ?? 1,
      totalElements: risposta.page?.totalElements ?? allerte.length,
    };
    renderAlertList(allerte);
    renderPaginazione();
  } catch (err) {
    console.error('Errore nel recupero delle allerte risolte', err);
    container.innerHTML = `<p class="error-text">Impossibile caricare lo storico: ${err.message}</p>`;
  }
}

function renderPaginazione() {
  const el = document.getElementById('paginazioneAllerte');
  if (tabAllerteAttuale !== 'risolta' || paginaRisolteInfo.totalPages <= 1) {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = `
    <div class="paginazione-bottoni">
      <button type="button" id="pagPrec" ${paginaRisolteCorrente === 0 ? 'disabled' : ''}>&larr; Precedenti</button>
      <button type="button" id="pagSucc" ${paginaRisolteCorrente + 1 >= paginaRisolteInfo.totalPages ? 'disabled' : ''}>Successive &rarr;</button>
    </div>
    <span class="paginazione-info">Pagina ${paginaRisolteCorrente + 1} di ${paginaRisolteInfo.totalPages} (${paginaRisolteInfo.totalElements} totali)</span>
  `;
  document.getElementById('pagPrec')?.addEventListener('click', () => caricaAllerteRisolte(paginaRisolteCorrente - 1));
  document.getElementById('pagSucc')?.addEventListener('click', () => caricaAllerteRisolte(paginaRisolteCorrente + 1));
}

// --- Lista e dettaglio ------------------------------------------------------

function renderAlertList(allerte, filtroTipoAttivo = null) {
  const container = document.getElementById('alertList');
  container.innerHTML = '';

  if (filtroTipoAttivo) {
    const banner = document.createElement('div');
    banner.className = 'filtro-attivo';
    banner.innerHTML = `Filtro: <strong>${formatTipo(filtroTipoAttivo)}</strong> · <button type="button" class="reset-filtro">mostra tutte</button>`;
    banner.querySelector('.reset-filtro').addEventListener('click', resetFiltroTipo);
    container.appendChild(banner);
  }

  if (allerte.length === 0) {
    container.insertAdjacentHTML('beforeend', '<p class="empty-state">Nessuna allerta in questa categoria.</p>');
    return;
  }

  allerte.forEach(a => {
    const card = document.createElement('div');
    const livello = (a.livelloRischio || '').toLowerCase();
    const risolta = a.stato === 'risolta';
    card.className = `alert-card livello-${livello}${risolta ? ' risolta' : ''}`;
    card.innerHTML = `
      <div class="alert-header">
        <span>${formatTipo(a.tipo)}${risolta ? ' <span class="badge badge-risolta">risolta</span>' : ''}</span>
        <span>${a.livelloRischio || ''}</span>
      </div>
      <div class="alert-body">
        <span>${a.parcella} · ${a.nodoCodice}</span>
        <span>${a.generataIl ? new Date(a.generataIl).toLocaleString('it-IT') : ''}</span>
      </div>
      ${risolta && a.risoltaIl ? `
        <div class="alert-risolta-info">
          Risolta il ${new Date(a.risoltaIl).toLocaleString('it-IT')}
          · dopo ${formattaDurata(new Date(a.risoltaIl) - new Date(a.generataIl))}
        </div>
      ` : ''}
      ${!risolta && a.risoluzionePianificataIl ? `
        <div class="alert-pianificata-info">
          ${testoRisoluzionePrevista(a.risoluzionePianificataIl)}
        </div>
      ` : ''}
    `;
    card.addEventListener('click', () => mostraRaccomandazione(a));
    container.appendChild(card);
  });
}

// Entrambi gli operandi sono istanti reali (risoluzionePianificataIl e' scritto
// da SchedulerRisoluzioneAllerte come Instant.now() + ritardo scalato, non un
// timestamp nel dominio simulato): confrontarlo con "adesso" del browser e'
// corretto a qualunque --time-scale, a differenza del confronto con generataIl.
function testoRisoluzionePrevista(risoluzionePianificataIl) {
  const restante = new Date(risoluzionePianificataIl) - new Date();
  const dataFormattata = new Date(risoluzionePianificataIl).toLocaleString('it-IT');
  return restante > 0
    ? `Risoluzione prevista: ${dataFormattata} (tra circa ${formattaDurata(restante)})`
    : `Risoluzione prevista: ${dataFormattata} (a breve)`;
}

async function mostraRaccomandazione(allerta) {
  const panel = document.getElementById('recommendationPanel');
  panel.innerHTML = '<p class="empty-state">Caricamento…</p>';
  try {
    const risposta = await GrapeHealthAPI.getRaccomandazioni({ allertaId: allerta.id });
    const lista = estraiContenuto(risposta);
    renderRaccomandazione(lista[0], allerta);
  } catch (err) {
    console.error('Errore nel recupero della raccomandazione', err);
    panel.innerHTML = `<p class="error-text">Impossibile caricare la raccomandazione: ${err.message}</p>`;
  }
}

function formattaOperatore(operatore) {
  const mappa = { '<=': '≤', '>=': '≥', '<': '<', '>': '>', '=': '=' };
  return mappa[operatore] || operatore;
}

function renderRaccomandazione(r, allerta) {
  const panel = document.getElementById('recommendationPanel');
  if (!r) {
    panel.innerHTML = '<p class="empty-state">Nessuna raccomandazione disponibile per questa allerta.</p>';
    return;
  }

  const badge = r.basedOnSimulatedExecution
    ? '<span class="badge badge-eseguita">Azione simulata eseguita</span>'
    : '<span class="badge badge-teorica">Raccomandazione teorica</span>';

  const dettaglioAllerta = allerta ? `
    <p class="dettaglio-allerta">
      <strong>Regola scatenante:</strong> ${allerta.regolaScatenante || '—'}<br>
      <strong>Descrizione dell'evento:</strong> ${allerta.descrizione || '—'}
    </p>
    ${allerta.stato === 'risolta' && allerta.risoltaIl ? `
      <p><strong>Risolta il:</strong> ${new Date(allerta.risoltaIl).toLocaleString('it-IT')}
      ${r.basedOnSimulatedExecution && r.eseguitaIl
        ? `(${formattaDurata(new Date(allerta.risoltaIl) - new Date(r.eseguitaIl))} dopo l'esecuzione)`
        : ''}</p>
    ` : ''}
    ${allerta.stato === 'attiva' && allerta.risoluzionePianificataIl ? `
      <p>${testoRisoluzionePrevista(allerta.risoluzionePianificataIl)}</p>
    ` : ''}
  ` : '';

  const fonteRegola = (r.descrizioneRegola || r.fonteBibliograficaRegola) ? `
    <p class="dettaglio-regola">
      ${r.descrizioneRegola ? `<strong>Descrizione della regola:</strong> ${r.descrizioneRegola}<br>` : ''}
      ${r.fonteBibliograficaRegola ? `<strong>Fonte bibliografica:</strong> ${r.fonteBibliograficaRegola}` : ''}
    </p>
  ` : '';

  // Soglie numeriche codificate per la regola: il dato bibliografico "grezzo" dietro la descrizione discorsiva mostrata sopra.
  const soglie = (r.soglieRegola || []);
  const sezioneSoglie = soglie.length > 0 ? `
    <div class="soglie-regola">
      <h4>Soglie bibliografiche della regola</h4>
      <table class="tabella-soglie">
        <thead>
          <tr><th>Parametro</th><th>Livello</th><th>Condizione</th><th>Durata min.</th><th>Note</th></tr>
        </thead>
        <tbody>
          ${soglie.map(s => `
            <tr>
              <td>${s.parametro}</td>
              <td>${s.livelloRischio || '—'}</td>
              <td>${formattaOperatore(s.operatore)} ${s.valoreSoglia.toLocaleString('it-IT')} ${s.unitaMisura || ''}</td>
              <td>${s.durataMinimaMinuti ? `${s.durataMinimaMinuti} min` : '—'}</td>
              <td>${s.note || '—'}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  ` : '';

  // Solo per tre_dieci: la soglia "germogli" in tabella indica cosa richiede la regola,
  // ma non il valore effettivamente rilevato per la parcella. Quel dato vive in
  // parcella.lunghezza_germoglio_cm, esposto da /api/parcelle e già cache-ato in
  // PARCELLE_INFO (main.js) al bootstrap: nessuna chiamata aggiuntiva necessaria qui.
  const infoGermoglio = (() => {
    if (r.tipoAllerta !== 'tre_dieci' || !allerta) return '';
    const p = PARCELLE_INFO[allerta.parcella];
    if (!p || p.lunghezzaGermoglioCm == null) return '';
    const dataAgg = p.germoglioAggiornatoIl
      ? new Date(p.germoglioAggiornatoIl).toLocaleDateString('it-IT')
      : 'data non registrata';
    return `
    <p class="dettaglio-regola">
      <strong>Lunghezza germoglio rilevata su ${allerta.parcella}:</strong> ${p.lunghezzaGermoglioCm} cm
      (sopralluogo del ${dataAgg})
    </p>
  `;
  })();

  // L'ordine con cui /api/raccomandazioni restituisce azioniAlternative non e' garantito da un
  // ORDER BY esplicito lato api (verificato: CacheAzioniMitigazione.azioniPerRegola() restituisce
  // l'ordine di lettura di regola_azione, non un ordine dichiarato) - oggi coincide con l'ordine
  // di inserimento del seed, ma non e' un contratto su cui questo file possa fare affidamento.
  // Il testo sotto dichiara che l'azione consigliata compare sempre per prima: per essere vero a
  // prescindere da come arrivano i dati, l'ordine e' imposto qui, non presunto dalla risposta.
  const alternative = (r.azioniAlternative || []).slice().sort((a, b) => {
    if (a.codice === r.azioneConsigliata) return -1;
    if (b.codice === r.azioneConsigliata) return 1;
    return 0;
  });
  const sezioneAlternative = alternative.length > 1 ? `
    <div class="azioni-alternative">
      <h4>Strategie alternative documentate in letteratura</h4>
      <p class="hint">L'azione consigliata è sempre la prima in alto in quanto la bibliografia non indica un criterio per scegliere automaticamente tra le alternative presenti.</p>
      ${alternative.map(alt => `
        <div class="azione-alternativa${alt.codice === r.azioneConsigliata ? ' azione-corrente' : ''}">
          <strong>${alt.descrizione}</strong>
          ${alt.codice === r.azioneConsigliata ? '<span class="badge badge-corrente">azione applicata</span>' : ''}
          ${alt.nota ? `<p class="azione-nota">${alt.nota}</p>` : ''}
          ${alt.fonteBibliografica ? `<p class="azione-fonte">Fonte: ${alt.fonteBibliografica}</p>` : ''}
        </div>
      `).join('')}
    </div>
  ` : '';

  panel.innerHTML = `
    <h3>Allerta #${r.allertaId ?? '—'} — ${formatTipo(r.tipoAllerta)} <span class="livello-inline">${r.livelloRischio || ''}</span></h3>
    ${badge}
    ${dettaglioAllerta}
    ${fonteRegola}
    ${sezioneSoglie}
    ${infoGermoglio}
    <p><strong>Azione consigliata:</strong> ${r.azioneConsigliata || '—'}</p>
    <p>${r.testoRaccomandazione || ''}</p>
    ${r.basedOnSimulatedExecution ? `
      <p><strong>Azione eseguita (simulata):</strong> ${r.azioneEseguita || '—'}</p>
      <p><strong>Esito:</strong> ${r.esitoSimulato || '—'}</p>
      <p><strong>Eseguita il:</strong> ${r.eseguitaIl ? new Date(r.eseguitaIl).toLocaleString('it-IT') : '—'}</p>
    ` : ''}
    ${sezioneAlternative}
  `;
}

function startPolling() {
  refreshAllerteAttive();
  pollTimer = setInterval(refreshAllerteAttive, POLL_INTERVAL_MS);
}

// V. il commento equivalente in api.js per il perche' di questo blocco: nel
// browser e' un no-op (nessun `module`), in Node espone ad alerts.test.js le
// due sole funzioni di questo file che non toccano il DOM. testoRisoluzionePrevista
// usa a sua volta formattaDurata (api.js): chi importa questo file nei test deve
// prima richiedere api.js, cosi' come nel browser api.js e' caricato per primo.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { formattaOperatore, testoRisoluzionePrevista };
}