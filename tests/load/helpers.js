// Sceglie un elemento a caso da un array non vuoto.
export function randItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// Estrae fino a n elementi distinti da arr, senza rimpiazzo: mai duplicati
// nel campione restituito. Se n supera la lunghezza di arr si ferma quando
// l'array da cui pescare si esaurisce, restituendo meno di n elementi
// invece di andare in loop o generare duplicati. Non modifica l'array
// passato come argomento.
export function randSample(arr, n) {
  const copia = [...arr];
  const campione = [];
  while (campione.length < n && copia.length > 0) {
    const indice = Math.floor(Math.random() * copia.length);
    campione.push(copia.splice(indice, 1)[0]);
  }
  return campione;
}