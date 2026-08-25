const { test } = require('node:test');
const assert = require('node:assert/strict');

// charts.js referenzia Chart.defaults.* al primo caricamento (configurazione
// globale di Chart.js): nel browser lo script chart.umd.min.js lo definisce
// prima che charts.js sia eseguito. Qui basta uno stub minimo con la sola
// forma che quelle tre righe si aspettano, cosi' il file si carica senza
// errori senza dover caricare la libreria vera, non necessaria per testare
// decima() e formatTipo() che non toccano Chart.js in alcun modo.
global.Chart = { defaults: { font: {} } };

const { decima, formatTipo } = require('./charts.js');

// --- decima -------------------------------------------------------------------

test('decima restituisce l\'array originale invariato quando e\' gia\' entro il limite', () => {
  const punti = [1, 2, 3];
  assert.equal(decima(punti, 5), punti);
  assert.equal(decima(punti, 3), punti);
});

test('decima riduce la lunghezza quando i punti superano il limite', () => {
  const punti = Array.from({ length: 1000 }, (_, i) => i);
  const risultato = decima(punti, 150);
  assert.ok(risultato.length <= 151); // 150 + eventuale ultimo punto forzato
  assert.ok(risultato.length < punti.length);
});

test('decima include sempre l\'ultimo punto della serie originale, anche se il passo lo salterebbe', () => {
  const punti = Array.from({ length: 101 }, (_, i) => i); // passo = ceil(101/10) = 11, indice 100 non e' multiplo di 11
  const risultato = decima(punti, 10);
  assert.equal(risultato[risultato.length - 1], 100);
});

test('decima con massimo=1 su piu\' punti restituisce primo ed ultimo, non un solo punto', () => {
  // Comportamento non ovvio dalla sola firma della funzione: richiedere un
  // massimo di 1 punto non garantisce un array di lunghezza 1, perche'
  // l'ultimo punto viene sempre aggiunto se non gia' incluso dal campionamento.
  const punti = [10, 20, 30, 40, 50];
  const risultato = decima(punti, 1);
  assert.deepEqual(risultato, [10, 50]);
});

test('decima campiona a passo costante (ogni k-esimo elemento), non le prime N voci', () => {
  const punti = Array.from({ length: 20 }, (_, i) => i);
  const risultato = decima(punti, 5); // passo = ceil(20/5) = 4
  assert.deepEqual(risultato, [0, 4, 8, 12, 16, 19]); // 19 aggiunto perche' non multiplo di 4
});

// --- formatTipo -----------------------------------------------------------------

test('formatTipo traduce ciascuno dei sette tipi di allerta nella relativa etichetta leggibile', () => {
  assert.equal(formatTipo('stress_idrico'), 'Stress idrico');
  assert.equal(formatTipo('ondata_di_calore'), 'Ondata di calore');
  assert.equal(formatTipo('sunburn'), 'Sunburn');
  assert.equal(formatTipo('tre_dieci'), 'Regola del tre-dieci');
  assert.equal(formatTipo('svernamento_oospore'), 'Svernamento oospore');
  assert.equal(formatTipo('infezione_secondaria'), 'Infezione secondaria');
  assert.equal(formatTipo('danno_radicale'), 'Danno radicale');
});

test('formatTipo su un valore non mappato restituisce il valore stesso invariato', () => {
  assert.equal(formatTipo('tipo_sconosciuto'), 'tipo_sconosciuto');
});