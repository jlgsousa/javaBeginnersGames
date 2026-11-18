// =====================================================================
//  characters.js – Prinzessin Lilli, die Trolle und die Helfer-Kreaturen
//  Jede Figur ist eine Gruppe aus einfachen Formen und schaut nach +Z.
// =====================================================================

// Emoji als schwebendes Bild (für Sprechblasen, Herzen …)
const emojiTexCache = {};
function emojiSprite(emoji, size = 0.6) {
  if (!emojiTexCache[emoji]) {
    emojiTexCache[emoji] = canvasTexture(128, (g, S) => {
      g.font = `${S * 0.8}px "Apple Color Emoji","Segoe UI Emoji","Noto Color Emoji",sans-serif`;
      g.textAlign = "center"; g.textBaseline = "middle";
      g.fillText(emoji, S / 2, S / 2 + S * 0.05);
    });
  }
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: emojiTexCache[emoji], transparent: true, depthWrite: false }));
  s.scale.setScalar(size);
  return s;
}

// kleine Helfer für Gesichter
function addEyes(parent, x, y, z, r = 0.035, spread = 1) {
  const black = mat(0x1d1220, { roughness: 0.1 }), white = new THREE.MeshBasicMaterial({ color: 0xffffff });
  [-1, 1].forEach(s => {
    const e = mesh(new THREE.SphereGeometry(r, 12, 8), black, false);
    e.position.set(s * x * spread, y, z); e.scale.set(0.85, 1.1, 0.7); parent.add(e);
    const h = new THREE.Mesh(new THREE.SphereGeometry(r * 0.35, 6, 4), white);
    h.position.set(s * x * spread + r * 0.3, y + r * 0.35, z + r * 0.55); parent.add(h);
  });
}
function addSmile(parent, y, z, w = 0.045, color = 0xc2185b) {
  const m = mesh(new THREE.TorusGeometry(w, w * 0.2, 6, 14, Math.PI), mat(color), false);
  m.position.set(0, y, z); m.rotation.z = Math.PI; parent.add(m);
  return m;
}
function addCheeks(parent, x, y, z, r = 0.035) {
  const pinkM = mat(0xff8fb8, { transparent: true, opacity: 0.7 });
  [-1, 1].forEach(s => { const c = mesh(new THREE.SphereGeometry(r, 8, 6), pinkM, false); c.position.set(s * x, y, z); c.scale.z = 0.4; parent.add(c); });
}

// =====================================================================
//  Prinzessin Lilli
// =====================================================================
function makePrincess() {
  const root = new THREE.Group(), body = new THREE.Group();
  root.add(body);
  const skin = mat(0xffd9c4, { roughness: 0.6 }), dress = mat(0xff6fae, { roughness: 0.45 });
  const dress2 = mat(0xffc0dd, { roughness: 0.5 }), hair = mat(0xffcc55, { roughness: 0.4 });
  const gold = mat(0xffd34d, { metalness: 0.6, roughness: 0.25 });

  // Kleid als Glocke (Drehkörper)
  const pts = [[0.46, 0.04], [0.47, 0.1], [0.4, 0.3], [0.3, 0.5], [0.2, 0.68], [0.14, 0.8]].map(([r, y]) => new THREE.Vector2(r, y));
  body.add(mesh(new THREE.LatheGeometry(pts, 28), dress));
  const ruffle = mesh(new THREE.TorusGeometry(0.46, 0.045, 8, 28), dress2); ruffle.rotation.x = Math.PI / 2; ruffle.position.y = 0.07; body.add(ruffle);
  for (let i = 0; i < 8; i++) {   // kleine Herzen/Punkte auf dem Kleid
    const d = mesh(new THREE.SphereGeometry(0.03, 6, 4), dress2, false);
    const a = i / 8 * Math.PI * 2; d.position.set(Math.cos(a) * 0.36, 0.3, Math.sin(a) * 0.36); body.add(d);
  }
  const bodice = mesh(new THREE.CylinderGeometry(0.12, 0.15, 0.3, 16), dress); bodice.position.y = 0.92; body.add(bodice);
  const sash = mesh(new THREE.TorusGeometry(0.15, 0.03, 6, 20), dress2); sash.rotation.x = Math.PI / 2; sash.position.y = 0.8; body.add(sash);
  const neckM = mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.1, 10), skin); neckM.position.y = 1.1; body.add(neckM);

  // Kopf
  const head = new THREE.Group(); head.position.y = 1.27; body.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.17, 20, 16), skin));
  addEyes(head, 0.06, 0.02, 0.15, 0.025);
  const smile = addSmile(head, -0.05, 0.155, 0.04);
  addCheeks(head, 0.1, -0.03, 0.13, 0.03);
  const nose = mesh(new THREE.SphereGeometry(0.018, 8, 6), skin, false); nose.position.set(0, -0.01, 0.17); head.add(nose);
  // Haare: Kappe + Pony + zwei Zöpfe mit Schleifen
  const cap = mesh(new THREE.SphereGeometry(0.185, 20, 14, 0, Math.PI * 2, 0, Math.PI * 0.55), hair);
  cap.rotation.x = -0.35; cap.position.set(0, 0.015, -0.02); head.add(cap);
  const back = mesh(new THREE.SphereGeometry(0.17, 16, 12), hair); back.position.set(0, -0.04, -0.06); back.scale.set(1.05, 1.05, 0.9); head.add(back);
  const braids = [];
  [-1, 1].forEach(s => {
    const pivot = new THREE.Group(); pivot.position.set(s * 0.16, 0.0, -0.04); head.add(pivot);
    const b = mesh(new THREE.CapsuleGeometry(0.055, 0.28, 4, 10), hair); b.position.y = -0.2; pivot.add(b);
    const bow = mesh(new THREE.SphereGeometry(0.045, 8, 6), mat(0xff3d8b)); bow.position.y = -0.03; bow.scale.set(1.6, 0.8, 0.8); pivot.add(bow);
    braids.push(pivot);
  });
  // Krone
  const crown = new THREE.Group(); crown.position.set(0, 0.16, -0.01); crown.rotation.x = -0.15; head.add(crown);
  crown.add(mesh(new THREE.CylinderGeometry(0.085, 0.09, 0.05, 12, 1, true), gold));
  for (let i = 0; i < 5; i++) {
    const a = i / 5 * Math.PI * 2, p = mesh(new THREE.ConeGeometry(0.022, 0.07, 6), gold);
    p.position.set(Math.sin(a) * 0.085, 0.055, Math.cos(a) * 0.085); crown.add(p);
  }
  const gem = mesh(new THREE.SphereGeometry(0.02, 8, 6), mat(0xff2f92, { roughness: 0.1, metalness: 0.3 })); gem.position.set(0, 0.01, 0.09); crown.add(gem);

  // Arme mit Puffärmeln
  const arms = [];
  [-1, 1].forEach(s => {
    const sh = new THREE.Group(); sh.position.set(s * 0.15, 1.02, 0); body.add(sh);
    const puff = mesh(new THREE.SphereGeometry(0.075, 12, 8), dress2); sh.add(puff);
    const arm = mesh(new THREE.CapsuleGeometry(0.035, 0.26, 4, 8), skin); arm.position.y = -0.18; sh.add(arm);
    const hand = mesh(new THREE.SphereGeometry(0.042, 8, 6), skin); hand.position.y = -0.34; sh.add(hand);
    sh.rotation.z = s * 0.25;
    arms.push(sh);
  });
  // Schuhe
  const feet = [-1, 1].map(s => { const f = mesh(new THREE.SphereGeometry(0.06, 10, 6), mat(0xff3d8b)); f.scale.set(0.8, 0.6, 1.3); f.position.set(s * 0.09, 0.03, 0.06); root.add(f); return f; });

  const p = { root, body, head, arms, braids, feet, walk: 0, phase: 0, wave: 0, cheer: 0,
    mats: { dress, dress2, hair }, crown, smile, braidsOut: false };   // für Verkleiden & Schminken (Kapitel 3)
  p.update = (dt, t) => {
    p.phase += dt * 9 * p.walk;
    const step = Math.sin(p.phase) * p.walk;
    body.position.y = Math.abs(Math.sin(p.phase)) * 0.05 * p.walk;
    body.rotation.z = step * 0.04;
    feet[0].position.z = 0.06 + step * 0.12; feet[1].position.z = 0.06 - step * 0.12;
    arms.forEach((a, i) => {
      const s = i ? 1 : -1;
      const waveArm = (i === 1 ? p.wave : 0) + p.cheer;
      a.rotation.x = step * 0.6 * (i ? -1 : 1) * (1 - waveArm);
      a.rotation.z = s * (0.25 + waveArm * (2.4 + Math.sin(t * 12 + i) * 0.35));
    });
    braids.forEach((b, i) => {
      if (p.braidsOut) { b.rotation.x = 0; b.rotation.z = (i ? -1 : 1) * (1.45 + Math.sin(t * 4 + i) * 0.05); return; }   // abstehende Zöpfe
      b.rotation.x = 0.2 + Math.sin(t * 3 + i) * 0.08 + p.walk * 0.25; b.rotation.z = (i ? -1 : 1) * (0.15 + Math.sin(p.phase) * 0.1 * p.walk);
    });
    head.rotation.z = Math.sin(t * 1.5) * 0.05;
  };
  scene.add(root);
  root.traverse(o => { if (o.isMesh) o.castShadow = true; });
  return p;
}

// =====================================================================
//  Trolle – rund, bunt, mit wilden Haaren (frech, aber lieb!)
// =====================================================================
function makeTroll(skinColor, hairColor, size = 1, chief = false) {
  const root = new THREE.Group(), body = new THREE.Group();
  root.add(body); root.scale.setScalar(size);
  const skin = mat(skinColor, { roughness: 0.6 });
  const light = mat(new THREE.Color(skinColor).lerp(new THREE.Color(0xffffff), 0.4), { roughness: 0.6 });
  const tummy = mesh(new THREE.SphereGeometry(0.38, 20, 14), skin); tummy.position.y = 0.55; tummy.scale.set(1, 0.95, 0.9); body.add(tummy);
  const belly = mesh(new THREE.SphereGeometry(0.26, 16, 10), light); belly.position.set(0, 0.5, 0.17); belly.scale.z = 0.6; body.add(belly);
  // Blätterröckchen
  const skirt = mesh(new THREE.CylinderGeometry(0.36, 0.42, 0.18, 12), mat(0x6fcf6f, { flatShading: true })); skirt.position.y = 0.32; body.add(skirt);

  const head = new THREE.Group(); head.position.y = 1.0; body.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.3, 20, 16), skin));
  const nose = mesh(new THREE.SphereGeometry(0.1, 12, 10), mat(new THREE.Color(skinColor).lerp(new THREE.Color(0xff7f7f), 0.5)));
  nose.position.set(0, -0.02, 0.3); nose.scale.set(1, 0.9, 1.1); head.add(nose);
  [-1, 1].forEach(s => {
    const w = mesh(new THREE.SphereGeometry(0.075, 12, 10), new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.3 }), false);
    w.position.set(s * 0.11, 0.09, 0.24); head.add(w);
    const pu = mesh(new THREE.SphereGeometry(0.04, 10, 8), mat(0x1d1220, { roughness: 0.1 }), false);
    pu.position.set(s * 0.11, 0.09, 0.31); head.add(pu);
    const ear = mesh(new THREE.ConeGeometry(0.08, 0.22, 8), skin); ear.position.set(s * 0.3, 0.05, 0); ear.rotation.z = -s * 1.3; head.add(ear);
  });
  const grin = addSmile(head, -0.12, 0.25, 0.1, 0x7a2346);
  [-1, 1].forEach(s => { const tooth = mesh(new THREE.BoxGeometry(0.035, 0.04, 0.02), mat(0xffffff), false); tooth.position.set(s * 0.03, -0.135, 0.29); head.add(tooth); });
  addCheeks(head, 0.2, -0.04, 0.22, 0.045);
  // Wilde Troll-Haare: viele bunte Spitzen nach oben
  const hairM = mat(hairColor, { roughness: 0.5 }), tufts = [];
  for (let i = 0; i < 7; i++) {
    const a = (i - 3) * 0.2, t = mesh(new THREE.ConeGeometry(0.09, 0.55, 8), hairM);
    t.position.set(Math.sin(a) * 0.15, 0.48, Math.cos(i) * 0.05); t.rotation.z = -a * 0.9; head.add(t); tufts.push(t);
  }
  if (chief) {        // Troll-Chef trägt eine schiefe Krone
    const crown = mesh(new THREE.CylinderGeometry(0.16, 0.18, 0.12, 8, 1, true), mat(0xffd34d, { metalness: 0.6, roughness: 0.3 }));
    crown.position.set(0.05, 0.33, 0); crown.rotation.z = 0.25; head.add(crown);
  }
  // Arme & Beine
  const arms = [-1, 1].map(s => {
    const sh = new THREE.Group(); sh.position.set(s * 0.36, 0.7, 0); body.add(sh);
    const a = mesh(new THREE.CapsuleGeometry(0.07, 0.3, 4, 8), skin); a.position.y = -0.2; sh.add(a);
    const h = mesh(new THREE.SphereGeometry(0.09, 10, 8), skin); h.position.y = -0.4; sh.add(h);
    sh.rotation.z = s * 0.5; return sh;
  });
  const legs = [-1, 1].map(s => {
    const hip = new THREE.Group(); hip.position.set(s * 0.15, 0.3, 0); root.add(hip);
    const l = mesh(new THREE.CapsuleGeometry(0.08, 0.14, 4, 8), skin); l.position.y = -0.12; hip.add(l);
    const f = mesh(new THREE.SphereGeometry(0.12, 12, 8), skin); f.position.set(0, -0.26, 0.06); f.scale.set(1, 0.55, 1.4); hip.add(f);
    return hip;
  });

  const tr = { root, body, head, arms, legs, walk: 0, phase: Math.random() * 6, dance: 0, carry: 0 };
  tr.update = (dt, t) => {
    tr.phase += dt * (10 * tr.walk + 6 * tr.dance);
    const s = Math.sin(tr.phase);
    body.rotation.z = s * (0.12 * tr.walk + 0.2 * tr.dance);
    body.position.y = Math.abs(s) * (0.08 * tr.walk + 0.15 * tr.dance);
    legs[0].rotation.x = s * 0.7 * tr.walk; legs[1].rotation.x = -s * 0.7 * tr.walk;
    arms.forEach((a, i) => {
      const side = i ? 1 : -1;
      a.rotation.x = tr.carry ? -2.8 : s * 0.8 * tr.walk * side;
      a.rotation.z = side * (tr.carry ? 0.2 : 0.5 + tr.dance * (1.6 + Math.sin(t * 10 + i * 2) * 0.4));
    });
    head.rotation.y = Math.sin(t * 2 + tr.phase * 0.1) * 0.15;
  };
  scene.add(root);
  return tr;
}

// =====================================================================
//  Helfer-Kreaturen
// =====================================================================
function makeBunny() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body); root.scale.setScalar(1.4);
  const fur = mat(0xffffff, { roughness: 0.7 }), pink = mat(0xffa6c9);
  const b = mesh(new THREE.SphereGeometry(0.26, 18, 14), fur); b.position.y = 0.3; b.scale.set(1, 0.95, 1.15); body.add(b);
  const head = new THREE.Group(); head.position.set(0, 0.6, 0.16); body.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.19, 18, 14), fur));
  addEyes(head, 0.08, 0.04, 0.15, 0.03);
  addCheeks(head, 0.11, -0.04, 0.13, 0.035);
  const nose = mesh(new THREE.SphereGeometry(0.025, 8, 6), pink); nose.position.set(0, -0.02, 0.19); head.add(nose);
  addSmile(head, -0.06, 0.175, 0.025, 0xd14b80);
  const ears = [-1, 1].map(s => {
    const e = new THREE.Group(); e.position.set(s * 0.08, 0.14, -0.02); e.rotation.z = -s * 0.15; head.add(e);
    const o = mesh(new THREE.CapsuleGeometry(0.055, 0.32, 4, 10), fur); o.position.y = 0.2; o.scale.z = 0.6; e.add(o);
    const i = mesh(new THREE.CapsuleGeometry(0.03, 0.26, 4, 8), pink); i.position.set(0, 0.2, 0.03); i.scale.z = 0.4; e.add(i);
    return e;
  });
  const bow = mesh(new THREE.SphereGeometry(0.05, 8, 6), mat(0xff3d8b)); bow.position.set(0.09, 0.2, 0.02); bow.scale.set(1.6, 0.8, 0.8); head.add(bow);
  const tail = mesh(new THREE.SphereGeometry(0.08, 10, 8), fur); tail.position.set(0, 0.28, -0.3); body.add(tail);
  [-1, 1].forEach(s => { const f = mesh(new THREE.SphereGeometry(0.08, 10, 8), fur); f.position.set(s * 0.12, 0.06, 0.12); f.scale.set(0.8, 0.6, 1.4); body.add(f); });
  const c = { root, h: 1.5 };
  c.update = (dt, t) => {
    const hop = Math.max(0, Math.sin(t * 4));
    body.position.y = (Math.floor(t / 1.57) % 3 === 0) ? hop * 0.25 : 0;
    ears.forEach((e, i) => e.rotation.x = Math.sin(t * 3 + i) * 0.15);
    head.rotation.z = Math.sin(t * 1.3) * 0.12;
  };
  return c;
}

function makeDragon() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body); root.scale.setScalar(1.1);
  const green = mat(0x8fe3c0, { roughness: 0.5 }), belly = mat(0xfff1a8), lilac = mat(0xc9a6ff, { side: THREE.DoubleSide, roughness: 0.5 });
  const b = mesh(new THREE.SphereGeometry(0.4, 18, 14), green); b.scale.set(1, 1.05, 1); body.add(b);
  const bl = mesh(new THREE.SphereGeometry(0.3, 14, 10), belly); bl.position.set(0, -0.05, 0.18); bl.scale.z = 0.6; body.add(bl);
  const head = new THREE.Group(); head.position.set(0, 0.55, 0.12); body.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.3, 18, 14), green));
  const snout = mesh(new THREE.SphereGeometry(0.17, 14, 10), green); snout.position.set(0, -0.07, 0.24); snout.scale.set(1.1, 0.8, 1); head.add(snout);
  addEyes(head, 0.13, 0.08, 0.24, 0.045);
  addSmile(head, -0.12, 0.38, 0.05, 0x3f8f6f);
  addCheeks(head, 0.18, -0.03, 0.22);
  [-1, 1].forEach(s => { const h = mesh(new THREE.ConeGeometry(0.05, 0.18, 8), belly); h.position.set(s * 0.12, 0.3, -0.05); h.rotation.z = -s * 0.3; head.add(h); });
  for (let i = 0; i < 4; i++) { const sp = mesh(new THREE.ConeGeometry(0.05, 0.12, 6), lilac); sp.position.set(0, 0.35 - i * 0.2, -0.33 - (i > 0 ? 0.05 : 0)); sp.rotation.x = -0.6; body.add(sp); }
  // Flügel
  const wingShape = new THREE.Shape(); wingShape.moveTo(0, 0); wingShape.quadraticCurveTo(0.5, 0.5, 0.8, 0.2); wingShape.quadraticCurveTo(0.55, 0.05, 0.6, -0.15); wingShape.quadraticCurveTo(0.3, -0.05, 0, 0);
  const wings = [-1, 1].map(s => {
    const w = new THREE.Group(); w.position.set(s * 0.2, 0.2, -0.2); body.add(w);
    const m = mesh(new THREE.ShapeGeometry(wingShape), lilac); m.scale.x = s; w.add(m);
    return w;
  });
  const tail = mesh(new THREE.ConeGeometry(0.12, 0.6, 10), green); tail.position.set(0, -0.25, -0.45); tail.rotation.x = -2; body.add(tail);
  const c = { root, h: 2.3 };
  c.update = (dt, t) => {
    body.position.y = 1.0 + Math.sin(t * 2.5) * 0.15;
    wings.forEach((w, i) => w.rotation.y = (i ? -1 : 1) * (0.3 + Math.sin(t * 10) * 0.5));
    head.rotation.z = Math.sin(t * 1.7) * 0.1;
  };
  return c;
}

function makeFrog() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body); root.scale.setScalar(1.3);
  const green = mat(0x7fd36b, { roughness: 0.4 });
  const pad = mesh(new THREE.CylinderGeometry(0.6, 0.6, 0.04, 20, 1, false, 0.3, Math.PI * 2 - 0.3), mat(0x4caf50)); pad.position.y = 0.02; root.add(pad);
  const b = mesh(new THREE.SphereGeometry(0.38, 18, 14), green); b.position.y = 0.32; b.scale.set(1.1, 0.8, 1); body.add(b);
  const bl = mesh(new THREE.SphereGeometry(0.28, 14, 10), mat(0xeaf7b0)); bl.position.set(0, 0.27, 0.16); bl.scale.set(1, 0.7, 0.7); body.add(bl);
  [-1, 1].forEach(s => {
    const e = mesh(new THREE.SphereGeometry(0.12, 14, 10), green); e.position.set(s * 0.18, 0.58, 0.08); body.add(e);
  });
  addEyes(body, 0.18, 0.6, 0.18, 0.05);
  const mouth = addSmile(body, 0.36, 0.33, 0.17, 0x2e7d32);
  addCheeks(body, 0.27, 0.4, 0.26, 0.05);
  const crown = mesh(new THREE.CylinderGeometry(0.1, 0.12, 0.1, 8, 1, true), mat(0xffd34d, { metalness: 0.6, roughness: 0.3 }));
  crown.position.set(0, 0.62, 0); body.add(crown);
  [-1, 1].forEach(s => { const f = mesh(new THREE.SphereGeometry(0.12, 10, 8), green); f.position.set(s * 0.32, 0.08, 0.15); f.scale.set(1, 0.4, 1.4); body.add(f); });
  const c = { root, h: 1.4 };
  c.update = (dt, t) => {
    body.scale.set(1 + Math.sin(t * 3) * 0.03, 1 - Math.sin(t * 3) * 0.03, 1);
    body.position.y = Math.max(0, Math.sin(t * 2.2)) > 0.97 ? 0.12 : body.position.y * 0.9;
    mouth.scale.x = 1 + Math.sin(t * 5) * 0.05;
  };
  return c;
}

function makeFairy() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body);
  const skin = mat(0xffe0cc), dress = mat(0xc77dff, { roughness: 0.4 }), hair = mat(0xff8fc8);
  const d = mesh(new THREE.ConeGeometry(0.22, 0.5, 16), dress); d.position.y = 0.25; body.add(d);
  const head = new THREE.Group(); head.position.y = 0.62; body.add(head);
  head.add(mesh(new THREE.SphereGeometry(0.13, 16, 12), skin));
  addEyes(head, 0.045, 0.01, 0.115, 0.02);
  addSmile(head, -0.04, 0.12, 0.03);
  addCheeks(head, 0.075, -0.02, 0.1, 0.022);
  const h = mesh(new THREE.SphereGeometry(0.14, 16, 12, 0, Math.PI * 2, 0, Math.PI * 0.6), hair); h.position.y = 0.02; h.rotation.x = -0.3; head.add(h);
  const bun = mesh(new THREE.SphereGeometry(0.07, 10, 8), hair); bun.position.set(0, 0.13, -0.06); head.add(bun);
  // Flügel: durchscheinend & schimmernd
  const wingM = new THREE.MeshBasicMaterial({ color: 0xbfe9ff, transparent: true, opacity: 0.55, side: THREE.DoubleSide, depthWrite: false });
  const wings = [];
  [-1, 1].forEach(s => [0, 1].forEach(k => {
    const w = new THREE.Group(); w.position.set(s * 0.05, 0.45 - k * 0.12, -0.08); body.add(w);
    const m = new THREE.Mesh(new THREE.CircleGeometry(k ? 0.14 : 0.2, 16), wingM);
    m.position.set(s * (k ? 0.12 : 0.18), k ? -0.05 : 0.08, 0); m.scale.y = 0.6; m.rotation.z = s * (k ? -0.6 : 0.5);
    w.add(m); w.userData.s = s; wings.push(w);
  }));
  // Zauberstab mit Stern
  const wand = mesh(new THREE.CylinderGeometry(0.01, 0.01, 0.3, 6), mat(0xffffff)); wand.position.set(0.2, 0.42, 0.1); wand.rotation.z = -0.5; body.add(wand);
  const star = emojiSprite("⭐", 0.18); star.position.set(0.28, 0.56, 0.1); body.add(star);
  const c = { root, h: 2.2 };
  c.update = (dt, t) => {
    body.position.y = 1.2 + Math.sin(t * 2) * 0.12;
    wings.forEach(w => w.rotation.y = w.userData.s * Math.sin(t * 18) * 0.5);
    if (Math.random() < 0.08) {
      const p = new THREE.Vector3(); star.getWorldPosition(p);
      spawnParticle(p, [0xffffff, 0xffe066, 0xff9ad5][Math.floor(Math.random() * 3)], new THREE.Vector3((Math.random() - .5), -0.3, (Math.random() - .5)), 1, 0.7, 0);
    }
  };
  return c;
}
