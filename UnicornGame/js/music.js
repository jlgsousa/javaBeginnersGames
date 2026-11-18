// =====================================================================
//  music.js – fröhliche Musik & Soundeffekte, komplett im Browser erzeugt
//  (Web Audio API, keine Musikdateien nötig)
// =====================================================================

const Sound = (() => {
  let ac = null, master, musicBus, sfxBus, echo;
  let muted = false;
  let song = null, songName = null, beat = 0, nextTime = 0, events = [], evIndex = 0, loopBeats = 0;

  // Notenname → Frequenz, z. B. "A4" → 440, "F#5", "Bb3"
  const SEMI = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
  function freq(n) {
    const m = n.match(/^([A-G])([#b]?)(\d)$/);
    let s = SEMI[m[1]] + (m[2] === "#" ? 1 : m[2] === "b" ? -1 : 0);
    const midi = (parseInt(m[3]) + 1) * 12 + s;
    return 440 * Math.pow(2, (midi - 69) / 12);
  }

  function init() {
    if (ac) { if (ac.state === "suspended") ac.resume(); return; }
    ac = new (window.AudioContext || window.webkitAudioContext)();
    master = ac.createGain(); master.gain.value = 0.9; master.connect(ac.destination);
    musicBus = ac.createGain(); musicBus.gain.value = 0.32; musicBus.connect(master);
    sfxBus = ac.createGain(); sfxBus.gain.value = 0.6; sfxBus.connect(master);
    // kleines Echo → klingt verträumter
    echo = ac.createDelay(); echo.delayTime.value = 0.27;
    const fb = ac.createGain(); fb.gain.value = 0.28;
    const wet = ac.createGain(); wet.gain.value = 0.22;
    echo.connect(fb).connect(echo); echo.connect(wet).connect(master);
    setInterval(scheduler, 25);
  }

  // ---------------- Instrumente ----------------
  function env(g, t, a, peak, dec) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + dec);
  }
  function osc(type, f, t, dur, vol, bus, toEcho) {
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = type; o.frequency.value = f;
    env(g, t, 0.008, vol, dur);
    o.connect(g); g.connect(bus); if (toEcho) g.connect(echo);
    o.start(t); o.stop(t + dur + 0.05);
    return o;
  }
  // Glockenspiel / Spieluhr: Grundton + heller Oberton
  function bell(f, t, dur, vol = 0.22, bus = musicBus) {
    osc("sine", f, t, dur * 1.6 + 0.3, vol, bus, true);
    osc("sine", f * 2, t, dur * 0.6 + 0.1, vol * 0.35, bus, true);
    osc("triangle", f * 3, t, 0.12, vol * 0.08, bus, false);
  }
  function pluck(f, t, vol = 0.09) { osc("triangle", f, t, 0.35, vol, musicBus, false); }
  function bass(f, t, dur) { osc("triangle", f, t, dur, 0.26, musicBus, false); osc("sine", f, t, dur, 0.18, musicBus, false); }
  function kick(t) {
    const o = ac.createOscillator(), g = ac.createGain();
    o.frequency.setValueAtTime(140, t); o.frequency.exponentialRampToValueAtTime(45, t + 0.12);
    env(g, t, 0.003, 0.35, 0.16);
    o.connect(g).connect(musicBus); o.start(t); o.stop(t + 0.2);
  }
  let noiseBuf = null;
  function noise(t, dur, vol, hp, bus = musicBus) {
    if (!noiseBuf) {
      noiseBuf = ac.createBuffer(1, ac.sampleRate * 0.5, ac.sampleRate);
      const d = noiseBuf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    const s = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain();
    s.buffer = noiseBuf; f.type = "highpass"; f.frequency.value = hp;
    env(g, t, 0.002, vol, dur);
    s.connect(f).connect(g).connect(bus); s.start(t); s.stop(t + dur + 0.05);
  }

  // ---------------- Lieder (eigene Kompositionen) ----------------
  // melody: [Note, Schläge]  (null = Pause), chords: ein Akkord pro Takt
  const CH = {
    C: ["C", "E", "G"], F: ["F", "A", "C"], G: ["G", "B", "D"], Am: ["A", "C", "E"], Dm: ["D", "F", "A"],
    Em: ["E", "G", "B"], D: ["D", "F#", "A"], A: ["A", "C#", "E"], Bb: ["Bb", "D", "F"], Gm: ["G", "Bb", "D"],
  };
  const SONGS = {
    // "Blumenwiese" – leicht und verspielt (C-Dur)
    wiese: { bpm: 112, bar: 4, chords: ["C", "C", "F", "G", "C", "F", "G", "C", "F", "C", "Dm", "G", "Am", "F", "G", "C"],
      melody: [["E4",1],["G4",1],["C5",1],["G4",1], ["A4",1],["G4",1],["E4",2], ["F4",1],["A4",1],["D5",1],["C5",1], ["B4",1],["G4",3],
               ["E4",1],["G4",1],["C5",1],["E5",1], ["D5",1],["C5",1],["A4",1],["F4",1], ["G4",1],["E4",1],["D4",1],["G4",1], ["C5",3],[null,1],
               ["A4",1],["C5",1],["A4",1],["F4",1], ["G4",1],["E4",1],["C4",2], ["D4",1],["F4",1],["A4",1],["F4",1], ["G4",1],["A4",.5],["B4",.5],["G4",2],
               ["E5",1],["D5",1],["C5",1],["A4",1], ["F4",1],["A4",1],["C5",2], ["D5",1],["C5",.5],["B4",.5],["D5",1],["B4",1], ["C5",3],[null,1]] },
    // "Abenteuer" – hüpfend und mutig (G-Dur)
    abenteuer: { bpm: 126, bar: 4, chords: ["G", "D", "Em", "C", "G", "C", "D", "G"],
      melody: [["G4",.5],["B4",.5],["D5",1],["B4",.5],["D5",.5],["G5",1], ["F#5",1],["E5",.5],["D5",.5],["A4",2],
               ["E5",.5],["D5",.5],["B4",1],["G4",.5],["A4",.5],["B4",1], ["C5",1],["E5",1],["D5",2],
               ["G4",.5],["B4",.5],["D5",1],["G5",1],["D5",1], ["E5",1],["C5",.5],["E5",.5],["G5",2],
               ["F#5",1],["E5",1],["D5",.5],["C5",.5],["A4",1], ["G4",3],[null,1]] },
    // "Zauberwalzer" – für Süßigkeiten & Schlösser (D-Dur, 3/4)
    zauber: { bpm: 138, bar: 3, chords: ["D", "D", "G", "A", "G", "D", "A", "D"],
      melody: [["F#4",1],["A4",1],["D5",1], ["F#5",2],["E5",1], ["D5",1],["B4",1],["G4",1], ["A4",3],
               ["B4",1],["D5",1],["G5",1], ["F#5",2],["D5",1], ["E5",1],["C#5",1],["A4",1], ["D5",3]] },
    // "Schlittenflug" – Flug durch den Winterabend, mit Schlittenglöckchen (C-Dur)
    schlitten: { bpm: 120, bar: 4, jingle: true, chords: ["C", "Am", "F", "G", "C", "Am", "Dm", "G"],
      melody: [["G4",.5],["C5",.5],["E5",1],["D5",.5],["C5",.5],["G4",1], ["A4",.5],["C5",.5],["E5",1],["A5",2],
               ["F5",1],["E5",.5],["D5",.5],["C5",1],["A4",1], ["B4",1],["D5",1],["G5",2],
               ["E5",.5],["G5",.5],["E5",1],["C5",1],["E5",1], ["A5",1],["G5",.5],["E5",.5],["C5",2],
               ["D5",.5],["F5",.5],["A5",1],["G5",.5],["F5",.5],["E5",1], ["D5",1],["B4",1],["C5",2]] },
    // "Portugal" – fröhlicher Volkstanz im Dreiertakt (G-Dur), eigene Melodie
    portugal: { bpm: 168, bar: 3, chords: ["G", "D", "D", "G", "C", "G", "D", "G"],
      melody: [["B4",.5],["D5",.5],["B4",.5],["D5",.5],["G5",1], ["F#5",1],["E5",.5],["D5",.5],["C5",.5],["A4",.5],
               ["A4",.5],["C5",.5],["A4",.5],["C5",.5],["F#5",1], ["G5",1],["D5",1],["B4",1],
               ["C5",.5],["E5",.5],["C5",.5],["E5",.5],["G5",1], ["B4",.5],["D5",.5],["G5",1],["D5",1],
               ["A4",.5],["B4",.5],["C5",.5],["D5",.5],["F#4",1], ["G4",2],[null,1]] },
    // "Fest" – Feier am Ende (F-Dur)
    fest: { bpm: 132, bar: 4, chords: ["F", "Bb", "C", "F", "F", "Bb", "C", "F"],
      melody: [["F4",.5],["F4",.5],["A4",1],["C5",1],["A4",1], ["Bb4",1],["A4",.5],["G4",.5],["F4",2],
               ["G4",.5],["G4",.5],["Bb4",1],["C5",1],["Bb4",1], ["A4",1],["G4",.5],["F4",.5],["G4",2],
               ["F4",.5],["A4",.5],["C5",1],["F5",1],["C5",1], ["D5",1],["C5",.5],["Bb4",.5],["A4",2],
               ["G4",.5],["A4",.5],["Bb4",1],["E4",1],["G4",1], ["F4",3],[null,1]] },
  };

  // Lied in eine Liste von Ereignissen (Zeit in Schlägen) umwandeln
  function compile(s) {
    const ev = [];
    let b = 0;
    s.melody.forEach(([n, d]) => { if (n) ev.push({ b, type: "mel", f: freq(n), d }); b += d; });
    const total = s.chords.length * s.bar;
    s.chords.forEach((name, i) => {
      const start = i * s.bar, notes = CH[name];
      const root = freq(notes[0] + "3") / 2;   // tiefer Basston
      if (s.bar === 3) {            // Walzer: Bass auf 1, Akkord auf 2 und 3
        ev.push({ b: start, type: "bass", f: root, d: 1 });
        [1, 2].forEach(k => notes.forEach(n => ev.push({ b: start + k, type: "pluck", f: freq(n + "4") * 0.999 })));
        ev.push({ b: start, type: "kick" });
      } else {                       // 4/4: Bass + Arpeggio + leichter Beat
        ev.push({ b: start, type: "bass", f: root, d: 1.5 });
        ev.push({ b: start + 2, type: "bass", f: root * 1.5, d: 1.5 });
        const arp = [0, 1, 2, 1, 0, 1, 2, 1];
        arp.forEach((k, j) => ev.push({ b: start + j * 0.5, type: "pluck", f: freq(notes[k] + "4") }));
        ev.push({ b: start, type: "kick" }, { b: start + 2, type: "kick" });
        for (let j = 0; j < 8; j++) ev.push({ b: start + j * 0.5, type: "hat", acc: j % 2 });
        ev.push({ b: start + 1, type: "clap" }, { b: start + 3, type: "clap" });
        if (s.jingle) for (let j = 0; j < 8; j++) ev.push({ b: start + j * 0.5, type: "jingle", acc: j % 2 });
      }
    });
    ev.sort((a, c) => a.b - c.b);
    return { ev, total };
  }
  const compiled = {};
  Object.keys(SONGS).forEach(k => compiled[k] = compile(SONGS[k]));

  function play(name) {
    if (!ac || name === songName) return;
    songName = name; song = SONGS[name];
    events = compiled[name].ev; loopBeats = compiled[name].total;
    evIndex = 0; beat = 0;
    // sanft überblenden
    musicBus.gain.cancelScheduledValues(ac.currentTime);
    musicBus.gain.setValueAtTime(0.0001, ac.currentTime);
    musicBus.gain.exponentialRampToValueAtTime(muted ? 0.0001 : 0.32, ac.currentTime + 0.8);
    nextTime = ac.currentTime + 0.1;
  }

  function scheduler() {
    if (!song) return;
    const spb = 60 / song.bpm;
    // war der Tab im Hintergrund? → nicht alles nachholen, sondern neu ansetzen
    if (nextTime + loopBeats * spb < ac.currentTime) { nextTime = ac.currentTime + 0.1; evIndex = 0; }
    while (true) {
      const e = events[evIndex];
      const t = nextTime + (e.b - beat) * spb;
      if (t > ac.currentTime + 0.2) break;
      if (t > ac.currentTime - 0.05) {
        switch (e.type) {
          case "mel": bell(e.f, t, e.d * spb); break;
          case "pluck": pluck(e.f, t); break;
          case "bass": bass(e.f, t, e.d * spb); break;
          case "kick": kick(t); break;
          case "hat": noise(t, 0.04, e.acc ? 0.03 : 0.05, 7000); break;
          case "clap": noise(t, 0.09, 0.06, 1500); break;
          case "jingle":   // Schlittenglöckchen: helles Klingeln
            noise(t, 0.07, e.acc ? 0.025 : 0.04, 9000);
            osc("sine", 2637 * (1 + Math.random() * 0.02), t, 0.09, 0.012, musicBus, false);
            osc("sine", 3520 * (1 + Math.random() * 0.02), t + 0.01, 0.07, 0.008, musicBus, false);
            break;
        }
      }
      evIndex++;
      if (evIndex >= events.length) {   // Lied von vorne
        evIndex = 0;
        nextTime += (loopBeats - beat) * spb; beat = 0;
      }
    }
  }

  // ---------------- Soundeffekte ----------------
  function star(final) {
    if (!ac) return;
    const t = ac.currentTime;
    (final ? ["C6", "E6", "G6", "C7"] : ["E6", "B6"]).forEach((n, i) => bell(freq(n), t + i * 0.07, 0.2, 0.18, sfxBus));
  }
  function jump() {
    if (!ac) return;
    const t = ac.currentTime, o = ac.createOscillator(), g = ac.createGain();
    o.type = "sine"; o.frequency.setValueAtTime(320, t); o.frequency.exponentialRampToValueAtTime(720, t + 0.15);
    env(g, t, 0.005, 0.12, 0.16); o.connect(g).connect(sfxBus); o.start(t); o.stop(t + 0.25);
  }
  function correct() {
    if (!ac) return;
    const t = ac.currentTime;
    ["C5", "E5", "G5", "C6", "E6", "G6"].forEach((n, i) => bell(freq(n), t + i * 0.08, 0.3, 0.2, sfxBus));
  }
  function wrong() {   // freundliches "Hmm, nochmal!"
    if (!ac) return;
    const t = ac.currentTime;
    osc("triangle", freq("E5"), t, 0.18, 0.15, sfxBus, false);
    osc("triangle", freq("C5"), t + 0.16, 0.25, 0.15, sfxBus, false);
  }
  function giggle() {  // Troll-Kichern "hihihi"
    if (!ac) return;
    const t = ac.currentTime;
    for (let i = 0; i < 5; i++) {
      const o = ac.createOscillator(), g = ac.createGain(), s = t + i * 0.11;
      o.type = "square"; o.frequency.setValueAtTime(700 + i * 40, s); o.frequency.exponentialRampToValueAtTime(950 + i * 40, s + 0.06);
      env(g, s, 0.005, 0.05, 0.08); o.connect(g).connect(sfxBus); o.start(s); o.stop(s + 0.12);
    }
  }
  function magic() {   // Tor öffnet sich: Glitzer-Glissando
    if (!ac) return;
    const t = ac.currentTime;
    for (let i = 0; i < 14; i++) bell(freq(["C5", "D5", "E5", "G5", "A5"][i % 5]) * (1 + Math.floor(i / 5)), t + i * 0.05, 0.15, 0.1, sfxBus);
  }
  function splash() {
    if (!ac) return;
    noise(ac.currentTime, 0.35, 0.2, 600, sfxBus);
  }
  function talk() {
    if (!ac) return;
    bell(freq("G5"), ac.currentTime, 0.08, 0.08, sfxBus);
  }

  // Musik leiser stellen, während die Märchenstimme erzählt
  let ducked = false;
  function duck(on) {
    if (!ac || on === ducked) return;
    ducked = on;
    musicBus.gain.setTargetAtTime(on ? 0.1 : 0.32, ac.currentTime, 0.25);
  }

  function toggleMute() {
    muted = !muted;
    if (ac) master.gain.setTargetAtTime(muted ? 0 : 0.9, ac.currentTime, 0.05);
    return muted;
  }

  return { init, play, duck, star, jump, correct, wrong, giggle, magic, splash, talk, toggleMute, get muted() { return muted; } };
})();
