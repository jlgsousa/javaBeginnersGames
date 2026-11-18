// =====================================================================
//  story.js – die Geschichte: Intro-Film, Kreaturen, Rätsel, Dialoge, Ende
//  Rosi (Einhorn) rettet ihre beste Freundin Prinzessin Alma vor den Trollen.
// =====================================================================

const STARS_NEEDED = 5;

// ---------------- Vorlesen auf Deutsch (eingebaute Sprachausgabe des Browsers) ----------------
const Voice = (() => {
  const synth = window.speechSynthesis;
  let voice = null, muted = false;
  function pickVoice() {
    if (!synth) return;
    const voices = synth.getVoices().filter(v => v.lang && v.lang.toLowerCase().startsWith("de"));
    // möglichst eine freundliche, natürliche Stimme nehmen
    voice = voices.find(v => /anna|petra|helena|katja|hedda|marlene|female|weiblich/i.test(v.name))
         || voices.find(v => /google/i.test(v.name)) || voices[0] || null;
  }
  if (synth) { pickVoice(); synth.onvoiceschanged = pickVoice; }
  function clean(text) {
    return text.replace(/<br\s*\/?>/gi, " ").replace(/<[^>]+>/g, "")
      .replace(/[\u{1F000}-\u{1FFFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}\u{200D}]/gu, "")
      .replace(/[„“"]/g, "").replace(/…/g, ",").replace(/\s+/g, " ").trim();
  }
  function speak(text) {
    if (!synth || muted) return;
    synth.cancel();
    const t = clean(text);
    if (!t) return;
    const u = new SpeechSynthesisUtterance(t);
    u.lang = "de-DE"; if (voice) u.voice = voice;
    u.rate = 0.95; u.pitch = 1.15;            // etwas langsamer und fröhlich für Kinder
    synth.speak(u);
  }
  return {
    speak,
    stop() { synth && synth.cancel(); },
    get speaking() { return !!synth && (synth.speaking || synth.pending); },
    setMuted(m) { muted = m; if (m && synth) synth.cancel(); },
  };
})();
const game = { state: "title", t: 0 };   // title → intro → play ⇄ dialog → ending

const ROSI = { name: "Rosi", emoji: "🦄" };
const ALMA = { name: "Prinzessin Alma", emoji: "👸" };

// ---------------- Figuren ----------------
const alma = makePrincess();
const trolls = [makeTroll(0xb59ce8, 0xff5fa2), makeTroll(0x8fd9b6, 0x4dd0ff), makeTroll(0xffb38a, 0xc77dff)];
const trollo = makeTroll(0x9ec5ff, 0xffd23f, 1.35, true);

// ---------------- Die Helfer-Kreaturen (eine pro Gebiet) ----------------
function npc(def) {
  const [x, z] = at(def.dx, def.z);
  def.pos = new THREE.Vector3(x, 0, z);
  def.char.root.position.copy(def.pos);
  scene.add(def.char.root);
  def.char.root.traverse(o => { if (o.isMesh) o.castShadow = true; });
  def.state = "new"; def.armed = true;
  def.bubbleTalk = emojiSprite("💬", 0.7); def.bubbleNew = emojiSprite("❗", 0.8);
  [def.bubbleTalk, def.bubbleNew].forEach(b => { b.position.set(x, def.char.h + 0.6, z); scene.add(b); });
  return def;
}

const NPCS = [
  npc({ name: "Hoppel", emoji: "🐰", zone: 0, dx: 2, z: -50, char: makeBunny(),
    intro: ["Hallo Rosi! Ich bin Hoppel, der Hase.",
            "Ich habe die Trolle gesehen! Sie hatten Prinzessin Alma dabei.",
            "Bring mir 5 Sterne von der Blumenwiese. Dann stelle ich dir ein Rätsel – und wenn du es löst, verrate ich dir, wohin sie gelaufen sind!"],
    riddle: { q: "Ich bin gelb und rund und strahle am Himmel. Wenn ich scheine, wird es schön warm. Wer bin ich?",
              options: ["🌙 Der Mond", "☀️ Die Sonne", "☁️ Eine Wolke"], answer: 1 },
    solved: ["Richtig! Die Sonne! Super gemacht!",
             "Die Trolle sind hoch in die Regenbogen-Berge gelaufen.",
             "Ich öffne dir das Regenbogentor. Hoppel-hoppel – viel Glück, Rosi!"],
    done: ["Die Trolle sind in die Regenbogen-Berge gelaufen! Lauf durch das Regenbogentor!"],
    onSolved: () => openGate(gateWiese) }),

  npc({ name: "Funki", emoji: "🐲", zone: 1, dx: -3, z: -110, char: makeDragon(),
    intro: ["Hui! Hallo! Ich bin Funki, der kleine Drache.",
            "Ich fliege hier oben herum und sehe alles!",
            "Sammle 5 Sterne in den Bergen. Dann bekommst du mein Rätsel!"],
    riddle: { q: "Ich habe vier Beine, eine bunte Mähne und ein Horn auf der Stirn. Wer bin ich?",
              options: ["🐴 Ein Pferd", "🦄 Ein Einhorn", "🐲 Ein Drache"], answer: 1 },
    solved: ["Jaaa! Ein Einhorn – das bist ja du!",
             "Von hier oben habe ich die Trolle gesehen: Sie sind zum Süßigkeiten-Fluss hinuntergelaufen.",
             "Das Regenbogentor ist offen. Flieg… äh, lauf los!"],
    done: ["Die Trolle sind zum Süßigkeiten-Fluss gelaufen! Durch das Regenbogentor!"],
    onSolved: () => openGate(gateBerge) }),

  npc({ name: "Quaki", emoji: "🐸", zone: 2, dx: 5, z: -143, char: makeFrog(),
    intro: ["Quak! Hallo! Ich bin Quaki, der Froschkönig vom Erdbeermilch-Fluss.",
            "Die Trolle sind über den Fluss gehüpft – auf meinen Zauber-Macarons!",
            "Bring mir 5 Sterne und löse mein Rätsel. Dann lasse ich die Macarons auftauchen."],
    riddle: { q: "Welche Farbe bekommst du, wenn du Rot und Weiß mischst?",
              options: ["💚 Grün", "🩷 Rosa", "💙 Blau"], answer: 1 },
    solved: ["Quak-tastisch! Rosa – so wie du!",
             "Schau, die Macarons tauchen auf! Hüpf von einem zum nächsten über den Fluss.",
             "Dahinter liegt das Schlösser-Tal. Dorthin sind die Trolle gelaufen!"],
    done: ["Hüpf über die Macarons! Dahinter liegt das Schlösser-Tal."],
    onSolved: () => raiseMacarons() }),

  npc({ name: "Fee Funkel", emoji: "🧚", zone: 3, dx: -3, z: -228, char: makeFairy(),
    intro: ["Hallo, liebes Einhorn! Ich bin Fee Funkel.",
            "Die Trolle sind durch das große Schlosstor geschlichen. Aber jetzt ist es verschlossen.",
            "Bring mir 5 Sterne. Wenn du dann mein Rätsel löst, zaubere ich das Tor auf!"],
    riddle: { q: "Ein Schloss hat 2 Türme. Ein anderes Schloss hat 3 Türme. Wie viele Türme sind das zusammen?",
              options: ["4", "5", "6"], answer: 1 },
    solved: ["Wunderbar! Fünf Türme!",
             "Simsalabim – das Schlosstor ist offen!",
             "Dahinter liegt das Trolldorf. Dort ist Prinzessin Alma!"],
    done: ["Das Tor ist offen! Im Trolldorf wartet Prinzessin Alma auf dich."],
    onSolved: () => openGate(portcullis) }),

  npc({ name: "Trollo", emoji: "😜", zone: 4, dx: 0, z: -275, char: trollo,
    intro: ["Hohoho! Ich bin Trollo, der Chef der Trolle!",
            "Du willst Prinzessin Alma zurückhaben? Hihihi!",
            "Wir Trolle LIEBEN Sterne! Bring uns 5 Sterne und löse unser Rätsel!"],
    riddle: { q: "Wer ist die allerbeste Freundin von Prinzessin Alma?",
              options: ["😜 Trollo", "🦄 Rosi, das Einhorn", "🎃 Ein Kürbis"], answer: 1 },
    solved: ["Oh… ja. Das stimmt. Du bist ihre allerbeste Freundin.",
             "Weißt du… wir haben Alma nur mitgenommen, weil wir so allein waren.",
             "Wir wollten auch einmal mit jemandem spielen…",
             { ...ALMA, text: "Aber Trollo! Ihr müsst doch niemanden mitnehmen." },
             { ...ALMA, text: "Fragt einfach – dann spielen wir ALLE zusammen!" },
             { ...ROSI, text: "Genau! Je mehr Freunde, desto lustiger! 💖" },
             "Wirklich?! Juhuuu! Wir sind jetzt alle Freunde!"],
    done: ["Juhuu! Wir sind Freunde!"],
    onSolved: () => startEnding() }),
];
trollo.root.rotation.y = 0;

// ---------------- Anzeige: Ort & Aufgabe ----------------
const locEl = document.getElementById("loc"), questEl = document.getElementById("quest");
function updateQuestHud() {
  const z = zoneAt(uni.pos.z), n = NPCS[z];
  locEl.textContent = ZONES[z].name;
  let task = "";
  if (game.state === "ending") task = "🎉 Alma ist gerettet!";
  else if (n.state === "new") task = `Finde ${n.name} ${n.emoji}`;
  else if (n.state === "collect")
    task = zoneStars[z] >= STARS_NEEDED ? `Genug Sterne! Zurück zu ${n.name} ${n.emoji}` : `Sammle Sterne für ${n.name}: ${zoneStars[z]} / ${STARS_NEEDED} ⭐`;
  else task = z === 2 ? "Hüpf über die Macarons ➜" : "Weiter zum nächsten Gebiet ➜";
  questEl.textContent = task;
}

// ---------------- Dialoge ----------------
const dlgEl = document.getElementById("dialog"), dlgAvatar = document.getElementById("dAvatar"),
      dlgName = document.getElementById("dName"), dlgText = document.getElementById("dText"),
      dlgOpts = document.getElementById("dOpts"), dlgNext = document.getElementById("dNext");
const dlg = { queue: [], current: null };

function say(steps) {
  dlg.queue = steps.slice();
  game.state = "dialog";
  dlgEl.style.display = "flex";
  showNext();
}
function showNext() {
  const step = dlg.queue.shift();
  if (!step) return closeDialog();
  if (step.fn) { step.fn(); return showNext(); }
  dlg.current = step;
  dlgAvatar.textContent = step.emoji; dlgName.textContent = step.name;
  dlgOpts.innerHTML = "";
  if (step.riddle) {
    dlgText.innerHTML = "🧩 <b>Rätsel:</b> " + step.riddle.q;
    step.riddle.options.forEach((o, i) => {
      const b = document.createElement("button");
      b.textContent = o;
      b.onclick = () => answer(i);
      dlgOpts.appendChild(b);
    });
    dlgNext.style.display = "none";
  } else {
    dlgText.textContent = step.text;
    dlgNext.style.display = "inline-block";
  }
  Sound.talk();
  if (step.riddle) Voice.speak(`${step.name} fragt: ${step.riddle.q} ` + step.riddle.options.map((o, i) => `${i + 1}: ${o}.`).join(" "));
  else Voice.speak(step.text);
}
function answer(i) {
  const step = dlg.current;
  if (!step || !step.riddle) return;
  if (i === step.riddle.answer) {
    Sound.correct();
    for (let k = 0; k < 30; k++) spawnParticle(uni.pos.clone().setY(2), RAINBOW[k % 7],
      new THREE.Vector3(rand(-3, 3), rand(2, 5), rand(-3, 3)), 1.2, 1.6, 6);
    dlg.queue = step.onCorrect.concat(dlg.queue);
    showNext();
  } else {
    Sound.wrong();
    dlgText.innerHTML = "🤔 Hmm, das stimmt nicht ganz. Versuch es nochmal!<br><br>🧩 " + step.riddle.q;
    Voice.speak("Hmm, das stimmt nicht ganz. Versuch es nochmal!");
    [...dlgOpts.children][i].disabled = true;
  }
}
function closeDialog() {
  if (game.state !== "ending") Voice.stop();
  dlgEl.style.display = "none";
  dlg.current = null;
  if (game.state === "dialog") game.state = "play";
  uni.jumpHeld = true;            // Leertaste vom Weiterklicken soll keinen Sprung auslösen
  updateQuestHud();
}
dlgNext.addEventListener("click", () => showNext());
window.addEventListener("keydown", e => {
  if (game.state !== "dialog" || !dlg.current) return;
  if (dlg.current.riddle) { const n = parseInt(e.key); if (n >= 1 && n <= 3) answer(n - 1); }
  else if (["Enter", "Space", "KeyE"].includes(e.code) && !e.repeat) showNext();
});

function talkTo(n) {
  const sp = { name: n.name, emoji: n.emoji };
  const lines = arr => arr.map(t => typeof t === "string" ? { ...sp, text: t } : t);
  if (n.state === "done") return say(lines(n.done));
  const steps = [];
  const wasNew = n.state === "new";
  if (wasNew) { steps.push(...lines(n.intro)); n.state = "collect"; }
  const have = zoneStars[n.zone];
  if (have >= STARS_NEEDED) {
    steps.push({ ...sp, text: `Oh, du hast ${have} Sterne! Dann hör gut zu – hier ist mein Rätsel:` });
    steps.push({ ...sp, riddle: n.riddle, onCorrect: [...lines(n.solved), { fn: () => { n.state = "done"; n.onSolved(); } }] });
  } else if (!wasNew) {
    steps.push({ ...sp, text: `Du hast schon ${have} von ${STARS_NEEDED} Sternen. Such weiter – manche liegen ganz hoch, da musst du springen!` });
  }
  say(steps);
}

// ---------------- Herzen & Feuerwerk ----------------
const floaters = [];
function floatEmoji(emoji, pos, size = 0.6, life = 2) {
  const s = emojiSprite(emoji, size); s.position.copy(pos); scene.add(s);
  floaters.push({ s, life, max: life });
}
function updateFloaters(dt) {
  for (let i = floaters.length - 1; i >= 0; i--) {
    const f = floaters[i];
    f.life -= dt; f.s.position.y += dt * 0.8;
    f.s.material.opacity = Math.min(1, f.life / f.max * 2);
    if (f.life <= 0) { scene.remove(f.s); floaters.splice(i, 1); }
  }
}
function firework(center) {
  const p = center.clone().add(new THREE.Vector3(rand(-8, 8), rand(6, 10), rand(-6, 4)));
  const col = RAINBOW[Math.floor(Math.random() * 7)];
  for (let i = 0; i < 40; i++) {
    const v = new THREE.Vector3(rand(-1, 1), rand(-1, 1), rand(-1, 1)).normalize().multiplyScalar(rand(3, 5));
    spawnParticle(p, i % 4 ? col : 0xffffff, v, 1.4, 1.8, 2);
  }
  Sound.star(false);
}

// =====================================================================
//  Intro-Film (ca. 30 Sekunden)
// =====================================================================
const captionEl = document.getElementById("caption");
const CAPTIONS = [
  [0,  "Es war einmal ein kleines Einhorn namens Rosi …"],
  [3,  "… und seine allerbeste Freundin: Prinzessin Alma! 👑"],
  [7.5, "Jeden Tag spielten die beiden zusammen Fangen auf der Blumenwiese."],
  [12, "Sie hüpften, lachten und hatten sich ganz doll lieb. 💖"],
  [17, "Doch plötzlich kamen drei freche Trolle aus dem Wald gehüpft! Hihihi!"],
  [21, "Sie schnappten sich Alma und liefen mit ihr davon! „Rosi, hilf mir!“"],
  [26, "Rosi muss Alma retten! Sammle Sterne, löse Rätsel und folge den Troll-Spuren! 🌈"],
];
const INTRO_END = 31;
const smooth = x => x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x);
const headingTo = (from, to) => Math.atan2(to.x - from.x, to.z - from.z);
const circlePos = (a, r = 3) => new THREE.Vector3(Math.cos(a) * r, 0, Math.sin(a) * r);
const camGoal = { pos: new THREE.Vector3(0, 6, 14), look: new THREE.Vector3(0, 1, 0) };
const camLook = new THREE.Vector3(0, 1, 0);
let voiceWait = 0, lastCaption = -1, giggled = 0, introHearts = 0;

function placeIntroStart() {
  camera.position.set(0, 6, 14); camLook.set(0, 1, 0); camera.lookAt(camLook);
  introUpdate(0);
}
function setUni(pos, heading, speed, y = 0) {
  uni.pos.set(pos.x, y, pos.z);
  uni.heading = heading;
  uni.vel.set(Math.sin(heading) * speed, 0, Math.cos(heading) * speed);
  uni.onGround = y <= 0.001;
}
function jumpArc(T, t0, dur, h) { const f = (T - t0) / dur; return f > 0 && f < 1 ? 4 * h * f * (1 - f) : 0; }

function introUpdate(dt) {
  // Damit die Stimme jeden Satz zu Ende lesen kann, wartet der Film kurz am Übergang
  const nextCap = CAPTIONS[lastCaption + 1];
  if (nextCap && game.t >= nextCap[0] && Voice.speaking && voiceWait < 4) { game.t = nextCap[0] - 0.001; voiceWait += dt; }
  else if (!Voice.speaking) voiceWait = 0;
  const T = game.t;
  // Untertitel
  let ci = 0; CAPTIONS.forEach((c, i) => { if (T >= c[0]) ci = i; });
  if (ci !== lastCaption) { lastCaption = ci; captionEl.textContent = CAPTIONS[ci][1]; Voice.speak(CAPTIONS[ci][1]); captionEl.classList.remove("show"); void captionEl.offsetWidth; captionEl.classList.add("show"); }

  const B_END = 12, SPEED = 0.85, aEnd = (B_END - 3) * SPEED;
  const pStand = new THREE.Vector3(1.2, 0, 0.5), uStand = new THREE.Vector3(-1.2, 0, 0.5);
  alma.wave = 0; alma.cheer = 0; alma.walk = 0;
  trolls.forEach(tr => { tr.walk = 0; tr.carry = 0; tr.dance = 0; });

  if (T < 3) {                                   // A: Begrüßung
    const p = circlePos(0), u = circlePos(-0.75);
    alma.root.position.copy(p); alma.root.rotation.y = headingTo(p, u); alma.wave = 1;
    setUni(u, headingTo(u, p), 0);
    camGoal.pos.set(0, 4, 11); camGoal.look.set(0, 1, 0);
  } else if (T < B_END) {                        // B: Fangen spielen im Kreis
    const a = (T - 3) * SPEED, p = circlePos(a), u = circlePos(a - 0.75);
    alma.root.position.copy(p); alma.root.rotation.y = Math.atan2(-Math.sin(a), Math.cos(a)); alma.walk = 1;
    setUni(u, Math.atan2(-Math.sin(a - 0.75), Math.cos(a - 0.75)), 3 * SPEED);
    const ca = (T - 3) * 0.15;
    camGoal.pos.set(Math.sin(ca) * 9, 3.2, Math.cos(ca) * 9); camGoal.look.set(0, 1, 0);
  } else if (T < 17) {                           // C: zusammen hüpfen
    const f = smooth((T - B_END) / 1.2);
    const p = circlePos(aEnd).lerp(pStand, f), u = circlePos(aEnd - 0.75).lerp(uStand, f);
    alma.walk = f < 1 ? 1 : 0;
    let jy = 0, py = 0;
    [13.3, 14.5, 15.7].forEach(t0 => { jy += jumpArc(T, t0, 0.7, 0.9); py += jumpArc(T, t0, 0.7, 0.45); });
    alma.root.position.set(p.x, py, p.z);
    alma.root.rotation.y = f < 1 ? headingTo(circlePos(aEnd), pStand) : headingTo(pStand, uStand);
    alma.cheer = py > 0 ? 1 : 0;
    setUni(u, f < 1 ? headingTo(circlePos(aEnd - 0.75), uStand) : headingTo(uStand, pStand), f < 1 ? 2 : 0, jy);
    if (T > 15.8 && introHearts < 6 && Math.random() < 0.15) { introHearts++; floatEmoji("💖", new THREE.Vector3(rand(-1, 1), 2.6, 0.5), 0.5); }
    camGoal.pos.set(0, 2.3, 6.5); camGoal.look.set(0, 1.1, 0.5);
  }
  if (T < 17) trolls.forEach(tr => tr.root.visible = false);
  if (T < 17) {} else if (T < 21) {                           // D: Trolle kommen
    alma.root.position.copy(pStand); alma.root.rotation.y = headingTo(pStand, new THREE.Vector3(-6, 0, -4)); alma.cheer = 0.45;
    setUni(uStand, headingTo(uStand, new THREE.Vector3(-6, 0, -4)), 0);
    trolls.forEach((tr, i) => {
      const from = new THREE.Vector3(-16 + i * 1.2, 0, -8 - i * 1.5);
      const to = pStand.clone().add(new THREE.Vector3(i - 1, 0, i === 1 ? -1.3 : -0.9));
      const f = smooth((T - 17) / 3.4);
      tr.root.visible = true;
      tr.root.position.copy(from.lerp(to, f));
      tr.root.rotation.y = headingTo(tr.root.position, pStand);
      tr.walk = f < 1 ? 1 : 0; tr.dance = f >= 1 ? 1 : 0;
    });
    if (giggled === 0 && T > 17.4) { giggled = 1; Sound.giggle(); }
    if (giggled === 1 && T > 20) { giggled = 2; Sound.giggle(); }
    camGoal.pos.set(5, 3.5, 8); camGoal.look.set(-3, 1, -2);
  } else {                                       // E + F: Entführung, Rosi schaut hinterher
    const target = new THREE.Vector3(pathX(-45), 0, -45);
    const dir = target.clone().sub(pStand).normalize();
    const run = Math.max(0, T - 21.6) * 5.5;
    const base = pStand.clone().addScaledVector(dir, run);
    const lift = Math.min(1.15, (T - 21) * 2.5);
    alma.root.position.set(base.x, lift, base.z - 0.3);
    alma.root.rotation.y = headingTo(target, pStand); alma.cheer = 1;
    trolls.forEach((tr, i) => {
      tr.root.position.copy(base).add(new THREE.Vector3((i - 1) * 0.55, 0, i === 1 ? -0.6 : 0));
      tr.root.rotation.y = headingTo(pStand, target);
      tr.walk = run > 0 ? 1 : 0; tr.carry = 1;
      tr.root.visible = run < 32;
    });
    alma.root.visible = run < 32;
    if (giggled === 2 && T > 22) { giggled = 3; Sound.giggle(); }
    // Rosi läuft ein Stück hinterher und bleibt dann entschlossen stehen
    const uf = smooth((T - 22) / 3);
    const u = uStand.clone().addScaledVector(dir, uf * 4);
    setUni(u, headingTo(uStand, target), uf > 0 && uf < 1 ? 3 : 0);
    if (T < 26) { camGoal.pos.set(u.x + 3, 3.5, u.z + 7); camGoal.look.copy(base).setY(1.2); }
    else { camGoal.pos.set(u.x - dir.x * 6.5, 3, u.z - dir.z * 6.5); camGoal.look.set(u.x + dir.x * 10, 1.2, u.z + dir.z * 10); }
  }
  if (dt > 0) {
    camera.position.lerp(camGoal.pos, Math.min(1, dt * 2));
    camLook.lerp(camGoal.look, Math.min(1, dt * 2.5));
  }
  camera.lookAt(camLook);
  if (T >= INTRO_END) startPlay();
}

// ---------------- Spielstart nach dem Intro ----------------
function startPlay() {
  game.state = "play";
  Voice.stop();
  captionEl.classList.remove("show"); captionEl.style.display = "none";
  document.getElementById("skip").style.display = "none";
  document.getElementById("hudWrap").style.display = "block";
  // Alma sitzt jetzt im Bonbon-Käfig im Trolldorf, die Trolle tanzen drumherum
  alma.root.visible = true; alma.root.position.copy(CAGE_POS).setY(0.4); alma.root.rotation.y = 0;
  trolls.forEach((tr, i) => { tr.root.visible = true; });
  // Rosi startet auf dem Weg, Blick in Richtung Abenteuer
  if (uni.pos.z < -2) uni.pos.set(pathX(-2), 0, -2);
  uni.pos.y = 0; uni.vel.set(0, 0, 0); uni.heading = Math.PI; uni.onGround = true;
  cam.yaw = 0; cam.pitch = 0.3; cam.target.set(uni.pos.x, 1.5, uni.pos.z);
  Sound.play(ZONES[0].song);
  updateQuestHud();
}

// ---------------- Ende: alle feiern ----------------
let endT = 0;
function startEnding() {
  game.state = "ending"; endT = 0;
  openCage();
  Sound.play("fest");
  updateQuestHud();
}
function endingUpdate(dt, t) {
  endT += dt;
  const meet = uni.pos.clone().add(new THREE.Vector3(1.6, 0, -0.6));
  // Alma läuft aus dem Käfig zu Rosi
  const f = smooth((endT - 1.5) / 3);
  const from = CAGE_POS.clone().setY(0.4);
  alma.root.position.lerpVectors(from, meet, f);
  if (f >= 1) alma.root.position.y = Math.abs(Math.sin(endT * 4)) * 0.3;
  alma.walk = f > 0 && f < 1 ? 1 : 0;
  alma.cheer = f >= 1 ? 1 : 0.5;
  alma.root.rotation.y = f < 1 ? headingTo(from, meet) : headingTo(meet, uni.pos);
  if (f >= 1 && Math.random() < 0.04) floatEmoji("💖", alma.root.position.clone().setY(2.2), 0.5);
  // Rosi hüpft vor Freude
  if (endT > 4.5 && uni.onGround && Math.random() < 0.03) uni.vel.y = 7;
  // Trolle tanzen
  [...trolls, trollo].forEach(tr => tr.dance = 1);
  // Feuerwerk
  if (endT > 1 && Math.random() < dt * 2.5) firework(uni.pos);
  if (endT > 7 && document.getElementById("end").style.display !== "flex") {
    document.getElementById("endStars").textContent = `Du hast ${starCount} von ${stars.length} Sternen gesammelt! ⭐`;
    document.getElementById("end").style.display = "flex";
  }
}
document.getElementById("again").addEventListener("click", () => location.reload());

// ---------------- jedes Bild: Geschichte aktualisieren ----------------
let hudTimer = 0;
function updateStory(dt, t) {
  alma.update(dt, t);
  trolls.forEach(tr => tr.update(dt, t));
  NPCS.forEach(n => {
    if (n.char !== trollo) n.char.update(dt, t);
    else trollo.update(dt, t);
    // Kreatur schaut zu Rosi, wenn sie in der Nähe ist
    const d = Math.hypot(uni.pos.x - n.pos.x, uni.pos.z - n.pos.z);
    if (d < 12) n.char.root.rotation.y += angleDiff(n.char.root.rotation.y, headingTo(n.pos, uni.pos)) * Math.min(1, dt * 3);
    const ready = n.state === "new" || (n.state === "collect" && zoneStars[n.zone] >= STARS_NEEDED);
    n.bubbleNew.visible = ready && game.state !== "ending";
    n.bubbleTalk.visible = !ready && n.state === "collect";
    n.bubbleNew.position.y = n.bubbleTalk.position.y = n.char.h + 0.6 + Math.sin(t * 3) * 0.1;
    // Ansprechen, wenn Rosi nah genug ist
    if (game.state === "play") {
      if (d < 3.2 && n.armed) { n.armed = false; talkTo(n); }
      else if (d > 5) n.armed = true;
    }
  });
  // Im Trolldorf tanzen die Trolle um Almas Käfig
  if (game.state !== "intro" && game.state !== "title") {
    trolls.forEach((tr, i) => {
      const a = t * 0.6 + i * Math.PI * 2 / 3;
      tr.root.position.set(CAGE_POS.x + Math.cos(a) * 3.3, 0, CAGE_POS.z + Math.sin(a) * 3.3);
      tr.root.rotation.y = a + Math.PI;          // laufen im Kreis
      tr.walk = 1; tr.dance = game.state === "ending" ? 1 : 0.3; tr.carry = 0;
    });
    if (game.state !== "ending") { alma.wave = Math.sin(t * 0.7) > 0.3 ? 1 : 0; }
  }
  updateFloaters(dt);
  hudTimer -= dt;
  if (hudTimer < 0 && game.state !== "title" && game.state !== "intro") { hudTimer = 0.3; updateQuestHud(); }
}

// ---------------- Titelbild ----------------
document.getElementById("start").addEventListener("click", () => {
  Sound.init();
  Sound.play("wiese");
  document.getElementById("title").style.display = "none";
  document.getElementById("skip").style.display = "block";
  captionEl.style.display = "block";
  game.state = "intro"; game.t = 0;
});
document.getElementById("skip").addEventListener("click", () => { if (game.state === "intro") startPlay(); });
placeIntroStart();
