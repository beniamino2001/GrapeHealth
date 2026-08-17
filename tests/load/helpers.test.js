import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randItem, randSample } from './helpers.js';

// --- randItem ---

test('randItem restituisce sempre un elemento effettivamente presente nell\'array', () => {
  const arr = ['a', 'b', 'c'];
  for (let i = 0; i < 200; i++) {
    assert.ok(arr.includes(randItem(arr)));
  }
});

test('randItem su un array di un solo elemento restituisce sempre quello', () => {
  assert.equal(randItem(['solo']), 'solo');
});

// --- randSample ---

test('randSample restituisce esattamente n elementi quando n <= arr.length', () => {
  const arr = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  assert.equal(randSample(arr, 5).length, 5);
});

test('randSample non restituisce mai duplicati (100 estrazioni)', () => {
  const arr = Array.from({ length: 50 }, (_, i) => i);
  for (let i = 0; i < 100; i++) {
    const campione = randSample(arr, 20);
    assert.equal(new Set(campione).size, campione.length);
  }
});

test('randSample restituisce solo elementi realmente presenti in arr', () => {
  const arr = ['x1', 'x2', 'x3', 'x4'];
  randSample(arr, 3).forEach((el) => assert.ok(arr.includes(el)));
});

test('randSample con n > arr.length si ferma a arr.length invece di andare in loop o duplicare', () => {
  const arr = ['a', 'b', 'c'];
  const campione = randSample(arr, 10);
  assert.equal(campione.length, 3);
  assert.equal(new Set(campione).size, 3);
});

test('randSample con n = 0 restituisce un array vuoto', () => {
  assert.deepEqual(randSample([1, 2, 3], 0), []);
});

test('randSample su un array vuoto restituisce un array vuoto, qualunque n', () => {
  assert.deepEqual(randSample([], 5), []);
});

test('randSample non modifica l\'array originale passato come argomento', () => {
  const arr = [1, 2, 3, 4, 5];
  const copiaOriginale = [...arr];
  randSample(arr, 3);
  assert.deepEqual(arr, copiaOriginale);
});