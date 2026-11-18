// =====================================================================
//  chapter3ui.js – Capítulo 3: Palavras cruzadas (Avó), Baú de disfarces,
//  A Dança dos Sentimentos (Avô) e o final "Juntos é melhor"
// =====================================================================

// ---------------- Palavras cruzadas da Avó ----------------
// Gitter: 5 Zeilen × 6 Spalten. Die Wörter kreuzen sich.
const CROSS_WORDS = [
  { n: 1, word: "AMOR",   r: 0, c: 1, down: true,  pic: "🥰", de: "die Liebe" },
  { n: 2, word: "FELIZ",  r: 0, c: 2, down: true,  emo: "feliz",  de: "glücklich" },
  { n: 3, word: "MEDO",   r: 1, c: 1, down: false, emo: "medo",   de: "die Angst" },
  { n: 4, word: "TRISTE", r: 3, c: 0, down: false, emo: "triste", de: "traurig" },
];
const LETTER_NAMES = { A: "á", D: "dê", E: "é", F: "éfe", I: "i", L: "éle", M: "éme", O: "ó", R: "érre", S: "ésse", T: "tê", Z: "zê" };
const CROSS_TEXT = {
  intro: "Escolhe uma imagem e escreve a palavra, letra a letra.",
  word: w => `Muito bem! ${w.word.toLowerCase()}!`,
};
const cross = { cells: {}, sel: null };
const crossEl = document.getElementById("cross");

function openCrossword() {
  ch3.state = "cross";
  crossEl.style.display = "flex";
  const grid = document.getElementById("crossGrid"), clues = document.getElementById("crossClues"), keysEl = document.getElementById("crossKeys");
  grid.innerHTML = ""; clues.innerHTML = ""; keysEl.innerHTML = ""; cross.cells = {};
  // Zellen anlegen
  for (let r = 0; r < 5; r++) for (let c = 0; c < 6; c++) {
    const d = document.createElement("div"); d.className = "cell empty"; grid.appendChild(d);
    cross.cells[`${r},${c}`] = { el: d, letter: null, filled: false };
  }
  CROSS_WORDS.forEach(w => {
    w.cellKeys = [...w.word].map((ch, i) => `${w.down ? w.r + i : w.r},${w.down ? w.c : w.c + i}`);
    w.cellKeys.forEach((k, i) => { const cell = cross.cells[k]; cell.letter = w.word[i]; cell.el.className = "cell"; cell.el.onclick = () => selectWord(w); });
    const first = cross.cells[w.cellKeys[0]].el;
    if (!first.querySelector(".num")) { const n = document.createElement("span"); n.className = "num"; n.textContent = w.n; first.appendChild(n); }
    // Hinweis mit Bild
    const b = document.createElement("button"); b.className = "clue";
    if (w.emo) b.appendChild(faceCanvas(w.emo, { hair: "#ffcc55", style: "pigtails", bg: "#fff" }, 90));
    else { const e = document.createElement("div"); e.className = "bigEmoji"; e.textContent = w.pic; b.appendChild(e); }
    const lab = document.createElement("div"); lab.textContent = `${w.n} ${w.down ? "⬇" : "➡"}`; b.appendChild(lab);
    b.onclick = () => selectWord(w); w.clueEl = b; clues.appendChild(b);
  });
  // Buchstaben-Tastatur
  const letters = [...new Set(CROSS_WORDS.map(w => w.word).join(""))].sort();
  letters.forEach(L => { const k = document.createElement("button"); k.textContent = L; k.onclick = () => typeLetter(L, k); keysEl.appendChild(k); });
  selectWord(CROSS_WORDS[0]);
  Voice.speak({ text: CROSS_TEXT.intro, voice: "avoF" });
}
function wordDone(w) { return w.cellKeys.every(k => cross.cells[k].filled); }
function selectWord(w) {
  if (wordDone(w)) return;
  cross.sel = w;
  Object.values(cross.cells).forEach(c => c.el.classList.remove("sel", "next"));
  CROSS_WORDS.forEach(x => x.clueEl.classList.toggle("on", x === w));
  w.cellKeys.forEach(k => cross.cells[k].el.classList.add("sel"));
  const nk = w.cellKeys.find(k => !cross.cells[k].filled);
  if (nk) cross.cells[nk].el.classList.add("next");
}
function typeLetter(L, keyEl) {
  const w = cross.sel; if (!w) return;
  const nk = w.cellKeys.find(k => !cross.cells[k].filled);
  const cell = cross.cells[nk];
  if (cell.letter === L) {
    cell.filled = true; cell.el.textContent = L; cell.el.classList.add("ok");
    Sound.talk();
    if (wordDone(w)) {
      Sound.correct();
      w.clueEl.classList.add("done");
      Voice.speak([{ text: CROSS_TEXT.word(w), voice: "avoF" }, { text: PT.inGerman, voice: "pt" }, { text: w.de, voice: "de" }]);
      const next = CROSS_WORDS.find(x => !wordDone(x));
      if (next) selectWord(next);
      else setTimeout(() => { crossEl.style.display = "none"; say(PT.crossDone, openDress); }, 2600);
    } else { Voice.speak({ text: LETTER_NAMES[L], voice: "avoF" }); selectWord(w); }
  } else {
    Sound.wrong();
    keyEl.classList.remove("shake"); void keyEl.offsetWidth; keyEl.classList.add("shake");
    Voice.speak({ text: WRONG_TEXT_PT, voice: "avoF" });
  }
}

// ---------------- O baú da Avó: disfarces e maquilhagem ----------------
const DRESS_TEXT = {
  princesa: "Uma princesa linda!", bruxa: "Uma bruxinha mágica!",
  gelo: "Uma rainha do gelo, que brilha como a neve!", trancas: "Uma menina forte e divertida, com tranças!",
  blush: "Bochechas cor-de-rosa!", glitter: "Brilhos mágicos!", estrela: "Uma estrela na cara!", coracao: "Um coração na cara!", batom: "Batom vermelho!",
};
const dressEl = document.getElementById("dress");
const makeupOn = {};
function openDress() {
  ch3.state = "dress";
  alma.root.position.copy(H(10, 6)); alma.root.rotation.set(0, -Math.PI / 2, 0);
  avoF.root.position.copy(H(8.5, 8)); avoM.root.position.copy(H(8.5, 4));
  dressEl.style.display = "flex";
  Sound.play("zauber");
}
document.querySelectorAll("#dress [data-costume]").forEach(b => b.onclick = () => {
  wearCostume(b.dataset.costume);
  document.querySelectorAll("#dress [data-costume]").forEach(x => x.classList.toggle("on", x === b));
  for (let k = 0; k < 20; k++) spawnParticle(alma.root.position.clone().setY(1.2), RAINBOW[k % 7], new THREE.Vector3(rand(-2, 2), rand(1, 3), rand(-2, 2)), 1, 1.2, 3);
  Sound.magic();
  Voice.speak({ text: DRESS_TEXT[b.dataset.costume], voice: "avoF" });
});
document.querySelectorAll("#dress [data-makeup]").forEach(b => b.onclick = () => {
  const id = b.dataset.makeup, on = !makeupOn[id];
  makeupOn[id] = on; b.classList.toggle("on", on);
  if (id === "batom") alma.smile.material.color.set(on ? 0xff1744 : 0xc2185b);
  else makeup.set(id, on);
  Sound.talk();
  if (on) Voice.speak({ text: DRESS_TEXT[id], voice: "avoF" });
});
document.getElementById("dressDone").onclick = () => { dressEl.style.display = "none"; ch3.state = "talk"; say(PT.dressDone, startDance); };

// ---------------- A Dança dos Sentimentos ----------------
const MOVES = {
  palmas: { emoji: "👏", pt: "Bate palmas!",         de: "Klatsch in die Hände" },
  maos:   { emoji: "🙌", pt: "Mãos no ar!",          de: "Hände hoch" },
  roda:   { emoji: "🔄", pt: "Dá uma volta!",        de: "Dreh dich" },
  salta:  { emoji: "🦘", pt: "Salta!",               de: "Spring" },
  feliz:  { emoji: "😊", pt: "Faz uma cara feliz!",  de: "Mach ein fröhliches Gesicht" },
  bracos: { emoji: "💃", pt: "Abana os braços!",     de: "Schwing die Arme" },
};
const DANCE_ORDER = ["palmas", "maos", "roda", "salta", "feliz", "bracos", "roda", "palmas"];
const CHEERS = ["Boa!", "Muito bem!", "Isso mesmo!", "Fantástico!"];
const danceEl = document.getElementById("dance");
(function buildMoveButtons() {
  const box = document.getElementById("moves");
  Object.entries(MOVES).forEach(([id, m]) => {
    const b = document.createElement("button");
    b.innerHTML = `<span class="em">${m.emoji}</span><span class="pt">${m.pt}</span><span class="de">${m.de}</span>`;
    b.onclick = () => pressMove(id, b);
    box.appendChild(b);
  });
})();
function startDance() {
  ch3.state = "dance"; ch3.step = 0; ch3.move = null; ch3.moveT = 0;
  alma.root.position.copy(H(-8, 6)); alma.root.rotation.set(0, 0, 0);
  avoF.root.position.copy(H(-10.2, 6.4)); avoM.root.position.copy(H(-5.8, 6.4));
  [avoF, avoM].forEach(c => { c.pose = "dance"; c.root.rotation.y = 0; c.rig.set("feliz"); });
  danceEl.style.display = "flex";
  Sound.play("portugal");
  setTimeout(callMove, 800);
}
function callMove() {
  if (ch3.step >= DANCE_ORDER.length) return finishDance();
  const id = DANCE_ORDER[ch3.step], m = MOVES[id];
  document.getElementById("danceCall").innerHTML = `${m.emoji} <b>${m.pt}</b><small>${m.de}</small>`;
  document.getElementById("danceDots").textContent = DANCE_ORDER.map((_, i) => i < ch3.step ? "💖" : "🤍").join("");
  Voice.speak({ text: m.pt, voice: "avoM" });
}
function pressMove(id, btn) {
  if (ch3.state !== "dance" || ch3.move) return;
  if (id === DANCE_ORDER[ch3.step]) {
    ch3.move = id; ch3.moveT = 0; ch3.step++;
    Voice.speak({ text: CHEERS[ch3.step % CHEERS.length], voice: "avoF" });
    for (let k = 0; k < 14; k++) spawnParticle(alma.root.position.clone().setY(2), RAINBOW[k % 7], new THREE.Vector3(rand(-2, 2), rand(1, 3), rand(-1, 1)), 1, 1.2, 3);
    setTimeout(() => { ch3.move = null; callMove(); }, 2300);
  } else {
    Sound.wrong();
    btn.classList.remove("shake"); void btn.offsetWidth; btn.classList.add("shake");
    Voice.speak({ text: WRONG_TEXT_PT, voice: "avoM" });
  }
}
function finishDance() {
  danceEl.style.display = "none";
  ch3.move = "hug"; ch3.moveT = 0;
  Voice.speak({ text: PT.danceDone, voice: "avoM" });
  Sound.play("fest");
  setTimeout(() => {
    ch3.state = "end"; ch3.move = "party";
    document.getElementById("end3").style.display = "flex";
    Voice.speak({ text: PT.end, voice: "pt" });
  }, 4500);
}
// Tanzbewegungen für alle drei
function poseArms(arms, sideOf, fn) { arms.forEach((a, i) => { const s = sideOf(a, i); const [x, z] = fn(s); a.rotation.x = x; a.rotation.z = s * z; }); }
function danceUpdate(dt, t) {
  ch3.moveT += dt;
  const k = Math.min(1, ch3.moveT / 2);
  const people = [
    { c: alma, base: H(-8, 6), arms: alma.arms, side: (a, i) => (i ? 1 : -1) },
    { c: avoF, base: H(-10.2, 6.4), arms: avoF.arms, side: a => a.userData.s },
    { c: avoM, base: H(-5.8, 6.4), arms: avoM.arms, side: a => a.userData.s },
  ];
  people.forEach((P, idx) => {
    const r = P.c.root, body = P.c.body;
    r.position.copy(P.base); r.rotation.set(0, 0, 0); body.rotation.z = 0;
    const mv = ch3.move;
    if (mv === "palmas") poseArms(P.arms, P.side, () => [-1.3, 0.12 + 0.35 * Math.abs(Math.sin(t * 9))]);
    else if (mv === "maos") poseArms(P.arms, P.side, () => [0, 2.8 + Math.sin(t * 10) * 0.15]);
    else if (mv === "roda") { r.rotation.y = smooth(k) * Math.PI * 2; poseArms(P.arms, P.side, () => [0, 1.3]); }
    else if (mv === "salta") { r.position.y = Math.abs(Math.sin(k * Math.PI * 2)) * 0.55; poseArms(P.arms, P.side, () => [0, 2.2]); }
    else if (mv === "feliz") { body.rotation.z = Math.sin(t * 6) * 0.12; poseArms(P.arms, P.side, s => [0, 0.9 + Math.sin(t * 6 + s) * 0.4]); }
    else if (mv === "bracos") poseArms(P.arms, P.side, s => [Math.sin(t * 8 + s) * 1.2, 0.7]);
    else if (mv === "hug") {
      const toward = H(-8, 6).sub(P.base).multiplyScalar(smooth(k) * 0.55);
      r.position.add(toward);
      if (idx) r.rotation.y = headingTo(P.base, H(-8, 6)) * smooth(k);
      poseArms(P.arms, P.side, () => [-1.2, 0.35]);
      if (Math.random() < 0.08) floatEmoji("💖", H(-8, 6).setY(2.4).add(new THREE.Vector3(rand(-1, 1), 0, 0)), 0.5);
    } else if (mv === "party") {
      body.rotation.z = Math.sin(t * 5 + idx) * 0.1; r.position.y = Math.abs(Math.sin(t * 5 + idx)) * 0.15;
      poseArms(P.arms, P.side, s => [0, 1.8 + Math.sin(t * 8 + s + idx) * 0.6]);
      if (Math.random() < 0.02) floatEmoji(["💖", "🎉", "⭐"][idx], P.base.clone().setY(2.3), 0.5);
    }
    if (P.c.rig && (mv === "feliz" || mv === "party" || mv === "hug")) P.c.rig.set("feliz");
  });
  if (ch3.move === "party" && Math.random() < dt * 1.5) firework(H(-8, 6).setY(-4));
  camera.position.lerp(H(-8, 0, 0).add(new THREE.Vector3(0, 3.2, 11.5)), Math.min(1, dt * 2));
  camera.lookAt(H(-8, 6).setY(1.2));
}
document.getElementById("danceAgain").onclick = () => { document.getElementById("end3").style.display = "none"; startDance(); };
document.getElementById("paint3").onclick = () => { document.getElementById("end3").style.display = "none"; openPaint(false); };

// Sätze für die Aufnahmen
function CH3UI_VOICE_LINES() {
  const L = [{ text: CROSS_TEXT.intro, voice: "avoF" }, { text: WRONG_TEXT_PT, voice: "avoF" }, { text: WRONG_TEXT_PT, voice: "avoM" }];
  CROSS_WORDS.forEach(w => L.push({ text: CROSS_TEXT.word(w), voice: "avoF" }, { text: w.de, voice: "de" }));
  Object.values(LETTER_NAMES).forEach(t => L.push({ text: t, voice: "avoF" }));
  Object.values(DRESS_TEXT).forEach(t => L.push({ text: t, voice: "avoF" }));
  Object.values(MOVES).forEach(m => L.push({ text: m.pt, voice: "avoM" }));
  CHEERS.forEach(t => L.push({ text: t, voice: "avoF" }));
  return L;
}
