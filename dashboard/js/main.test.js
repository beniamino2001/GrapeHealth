const { test } = require('node:test');
const assert = require('node:assert/strict');

// main.js registra document.addEventListener('DOMContentLoaded', ...) a livello
// di modulo, non dentro una funzione: richiederlo in Node lo esegue subito, e
// senza questo stub minimo fallirebbe con "document is not defined" prima ancora
// di poter testare formattaData, che con il DOM non ha nulla a che fare. Lo
// stub non deve fare altro che esistere: il listener non verra' mai invocato
// in un ambiente senza un vero evento DOMContentLoaded.
global.document = { addEventListener: () => {} };

const { formattaData } = require('./main.js');

test('formattaData converte una data ISO (LocalDate lato Java) in formato italiano gg/mm/aaaa', () => {
  assert.equal(formattaData('2025-03-12'), '12/03/2025');
});

test('formattaData su un singolo giorno/mese non aggiunge o toglie zeri iniziali già presenti', () => {
  assert.equal(formattaData('2026-01-05'), '05/01/2026');
});

test('formattaData su input vuoto o assente restituisce "—"', () => {
  assert.equal(formattaData(''), '—');
  assert.equal(formattaData(null), '—');
  assert.equal(formattaData(undefined), '—');
});

test('formattaData non passa mai da un oggetto Date, quindi nessun fuso orario può spostare il giorno', () => {
  // Una data vicina al cambio di mese è il caso in cui un errore di fuso
  // orario (introdotto passando da new Date(...) e poi formattando in locale)
  // sarebbe più facile da notare, spostando il giorno al mese sbagliato.
  assert.equal(formattaData('2025-12-31'), '31/12/2025');
  assert.equal(formattaData('2026-01-01'), '01/01/2026');
});