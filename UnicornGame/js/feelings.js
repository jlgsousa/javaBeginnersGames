// =====================================================================
//  feelings.js – Gefühle (Sentimentos): Wörter auf Portugiesisch & Deutsch,
//  gezeichnete Gesichter (2D) und bewegliche Gesichter für 3D-Figuren
// =====================================================================

const EMOTIONS = {
  feliz:        { m: "feliz",        f: "feliz",        de: "glücklich",  emoji: "😊" },
  triste:       { m: "triste",       f: "triste",       de: "traurig",    emoji: "😢" },
  zangado:      { m: "zangado",      f: "zangada",      de: "wütend",     emoji: "😠" },
  medo:         { m: "com medo",     f: "com medo",     de: "ängstlich",  emoji: "😨" },
  surpreendido: { m: "surpreendido", f: "surpreendida", de: "überrascht", emoji: "😮" },
  envergonhado: { m: "envergonhado", f: "envergonhada", de: "verlegen",   emoji: "😳" },
  cansado:      { m: "cansado",      f: "cansada",      de: "müde",       emoji: "😴" },
  calmo:        { m: "calmo",        f: "calma",        de: "ruhig",      emoji: "😌" },
};
const emoLabel = id => { const e = EMOTIONS[id]; return e.m === e.f ? e.m : `${e.m} / ${e.f}`; };

// ---------------- 2D-Gesicht zeichnen (für Karten, Bilderrahmen, Rätsel) ----------------
// look: { skin, hair, style: "pigtails" | "short" | "bun" | "curly" }
function drawFace(g, cx, cy, r, id, look = {}) {
  const skin = look.skin || "#ffd9c4", hair = look.hair || "#ffcc55", style = look.style || "pigtails";
  g.save();
  g.lineCap = "round"; g.lineJoin = "round";
  // Haare hinten
  g.fillStyle = hair;
  if (style === "pigtails") { [-1, 1].forEach(s => { g.beginPath(); g.ellipse(cx + s * r * 1.05, cy + r * 0.1, r * 0.28, r * 0.55, s * 0.3, 0, Math.PI * 2); g.fill(); }); }
  if (style === "curly") for (let i = 0; i < 12; i++) { const a = i / 12 * Math.PI * 2; g.beginPath(); g.arc(cx + Math.cos(a) * r * 0.95, cy + Math.sin(a) * r * 0.9 - r * 0.1, r * 0.3, 0, Math.PI * 2); g.fill(); }
  if (style === "bun") { g.beginPath(); g.arc(cx, cy - r * 1.05, r * 0.35, 0, Math.PI * 2); g.fill(); }
  // Gesicht
  g.fillStyle = id === "zangado" ? "#ffb3a3" : skin;
  g.strokeStyle = "#5a2a50"; g.lineWidth = r * 0.05;
  g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.fill(); g.stroke();
  // Pony / Haare oben
  g.fillStyle = hair;
  g.beginPath(); g.arc(cx, cy, r * 1.0, Math.PI * 1.08, Math.PI * 1.92); g.quadraticCurveTo(cx, cy - r * 0.45, cx - r * 0.93, cy - r * 0.32); g.fill();
  if (style === "short") { g.beginPath(); g.arc(cx, cy - r * 0.15, r * 1.02, Math.PI * 1.05, Math.PI * 1.95); g.fill(); }

  const ex = r * 0.36, ey = cy - r * 0.05, ink = "#3a1f3d";
  g.strokeStyle = ink; g.fillStyle = ink; g.lineWidth = r * 0.07;
  const brow = (s, inner, outer, y = ey - r * 0.32) => { g.beginPath(); g.moveTo(cx + s * ex * 0.45, y + inner); g.lineTo(cx + s * ex * 1.35, y + outer); g.stroke(); };
  const dotEyes = (size = 0.11, dx = 0) => [-1, 1].forEach(s => { g.beginPath(); g.arc(cx + s * ex + dx, ey, r * size, 0, Math.PI * 2); g.fill(); g.fillStyle = "#fff"; g.beginPath(); g.arc(cx + s * ex + dx + r * 0.04, ey - r * 0.04, r * size * 0.35, 0, Math.PI * 2); g.fill(); g.fillStyle = ink; });
  const bigEyes = () => [-1, 1].forEach(s => { g.fillStyle = "#fff"; g.beginPath(); g.arc(cx + s * ex, ey, r * 0.17, 0, Math.PI * 2); g.fill(); g.stroke(); g.fillStyle = ink; g.beginPath(); g.arc(cx + s * ex, ey, r * 0.07, 0, Math.PI * 2); g.fill(); });
  const closedEyes = up => [-1, 1].forEach(s => { g.beginPath(); g.arc(cx + s * ex, ey + (up ? r * 0.05 : -r * 0.05), r * 0.12, up ? Math.PI * 1.1 : Math.PI * 0.1, up ? Math.PI * 1.9 : Math.PI * 0.9); g.stroke(); });
  const blush = (a = 0.35, s = 1) => { g.fillStyle = `rgba(255,90,140,${a})`; [-1, 1].forEach(k => { g.beginPath(); g.ellipse(cx + k * r * 0.58, cy + r * 0.22, r * 0.18 * s, r * 0.11 * s, 0, 0, Math.PI * 2); g.fill(); }); g.fillStyle = ink; };
  const mouthArc = (w, y, smile) => { g.beginPath(); if (smile) g.arc(cx, y - w * 0.6, w, Math.PI * 0.2, Math.PI * 0.8); else g.arc(cx, y + w * 0.9, w, Math.PI * 1.2, Math.PI * 1.8); g.stroke(); };

  switch (id) {
    case "feliz":
      brow(-1, -2, 2); brow(1, -2, 2); dotEyes(); blush(0.3);
      g.fillStyle = "#c2185b"; g.beginPath(); g.arc(cx, cy + r * 0.25, r * 0.35, 0, Math.PI); g.closePath(); g.fill(); g.stroke(); break;
    case "triste":
      brow(-1, -r * 0.12, r * 0.05); brow(1, -r * 0.12, r * 0.05); dotEyes(); mouthArc(r * 0.28, cy + r * 0.5, false);
      g.fillStyle = "#5ec8ff"; g.beginPath(); g.moveTo(cx + ex, ey + r * 0.15); g.quadraticCurveTo(cx + ex + r * 0.12, ey + r * 0.4, cx + ex, ey + r * 0.45); g.quadraticCurveTo(cx + ex - r * 0.12, ey + r * 0.4, cx + ex, ey + r * 0.15); g.fill(); break;
    case "zangado":
      brow(-1, r * 0.12, -r * 0.06); brow(1, r * 0.12, -r * 0.06); dotEyes(0.09); mouthArc(r * 0.25, cy + r * 0.5, false);
      g.beginPath(); g.moveTo(cx - r * 0.2, cy + r * 0.45); g.lineTo(cx + r * 0.2, cy + r * 0.45); g.stroke(); break;
    case "medo":
      brow(-1, -r * 0.14, 0); brow(1, -r * 0.14, 0); bigEyes();
      g.beginPath(); g.moveTo(cx - r * 0.25, cy + r * 0.45); for (let i = 1; i <= 4; i++) g.lineTo(cx - r * 0.25 + i * r * 0.125, cy + r * 0.45 + (i % 2 ? -r * 0.06 : r * 0.06)); g.stroke();
      g.fillStyle = "#9fdcff"; g.beginPath(); g.ellipse(cx + r * 0.8, cy - r * 0.45, r * 0.07, r * 0.12, 0, 0, Math.PI * 2); g.fill(); break;
    case "surpreendido":
      brow(-1, -r * 0.1, -r * 0.1, ey - r * 0.42); brow(1, -r * 0.1, -r * 0.1, ey - r * 0.42); bigEyes();
      g.fillStyle = "#c2185b"; g.beginPath(); g.ellipse(cx, cy + r * 0.45, r * 0.13, r * 0.18, 0, 0, Math.PI * 2); g.fill(); g.stroke(); break;
    case "envergonhado":
      brow(-1, -r * 0.06, 0); brow(1, -r * 0.06, 0); dotEyes(0.1, r * 0.08); blush(0.65, 1.4);
      g.beginPath(); g.moveTo(cx - r * 0.18, cy + r * 0.42); g.quadraticCurveTo(cx - r * 0.06, cy + r * 0.5, cx, cy + r * 0.42); g.quadraticCurveTo(cx + r * 0.08, cy + r * 0.36, cx + r * 0.18, cy + r * 0.44); g.stroke(); break;
    case "cansado":
      brow(-1, 0, r * 0.04); brow(1, 0, r * 0.04); closedEyes(false);
      g.beginPath(); g.ellipse(cx, cy + r * 0.45, r * 0.1, r * 0.13, 0, 0, Math.PI * 2); g.stroke();
      g.font = `bold ${r * 0.35}px sans-serif`; g.fillText("Z", cx + r * 0.75, cy - r * 0.7); g.font = `bold ${r * 0.25}px sans-serif`; g.fillText("z", cx + r * 1.0, cy - r * 0.95); break;
    case "calmo":
      brow(-1, -1, 1); brow(1, -1, 1); closedEyes(true); blush(0.25); mouthArc(r * 0.2, cy + r * 0.38, true); break;
  }
  g.restore();
}
// Gesicht als Bild (Canvas) – z. B. für Karten im HTML
function faceCanvas(id, look, size = 220) {
  const c = document.createElement("canvas"); c.width = c.height = size;
  const g = c.getContext("2d");
  g.fillStyle = look && look.bg || "#fff5fb"; g.fillRect(0, 0, size, size);
  drawFace(g, size / 2, size / 2 + size * 0.04, size * 0.33, id, look);
  return c;
}

// ---------------- 3D-Gesicht mit Gefühlen (für Avó, Avô) ----------------
// Baut Augen, Augenbrauen, verschiedene Münder und Extras auf den Kopf (Gesicht zeigt nach +Z).
function makeFaceRig(head, R, opts = {}) {
  const ink = mat(0x2a1530, { roughness: 0.3 }), white = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.3 });
  const z = R * 0.92, rig = { eyes: [], brows: [], mouths: {}, emotion: "feliz" };
  [-1, 1].forEach(s => {
    const eye = new THREE.Group(); eye.position.set(s * R * 0.36, R * 0.12, z - R * 0.05); head.add(eye);
    const w = new THREE.Mesh(new THREE.SphereGeometry(R * 0.17, 12, 10), white); w.scale.z = 0.5; eye.add(w);
    const p = new THREE.Mesh(new THREE.SphereGeometry(R * 0.09, 10, 8), ink); p.position.z = R * 0.06; eye.add(p);
    eye.userData.pupil = p;
    rig.eyes.push(eye);
    const brow = new THREE.Mesh(new THREE.BoxGeometry(R * 0.32, R * 0.06, R * 0.06), mat(opts.browColor || 0x8a8a8a));
    brow.position.set(s * R * 0.36, R * 0.38, z); brow.userData.s = s; head.add(brow); rig.brows.push(brow);
  });
  const mouthM = mat(0xb03060, { roughness: 0.5 });
  const mk = (geo, rz = 0) => { const m = new THREE.Mesh(geo, mouthM); m.position.set(0, -R * 0.35, z); m.rotation.z = rz; m.visible = false; head.add(m); return m; };
  rig.mouths.smile = mk(new THREE.TorusGeometry(R * 0.22, R * 0.045, 6, 16, Math.PI), Math.PI);
  rig.mouths.frown = mk(new THREE.TorusGeometry(R * 0.2, R * 0.045, 6, 16, Math.PI));
  rig.mouths.o = mk(new THREE.TorusGeometry(R * 0.1, R * 0.045, 6, 14));
  rig.mouths.flat = mk(new THREE.BoxGeometry(R * 0.3, R * 0.06, R * 0.05));
  rig.mouths.small = mk(new THREE.TorusGeometry(R * 0.12, R * 0.04, 6, 12, Math.PI), Math.PI);
  rig.mouths.frown.position.y = -R * 0.45;
  const blushM = new THREE.MeshBasicMaterial({ color: 0xff6f9e, transparent: true, opacity: 0.35 });
  rig.blush = [-1, 1].map(s => { const b = new THREE.Mesh(new THREE.SphereGeometry(R * 0.13, 10, 8), blushM); b.position.set(s * R * 0.55, -R * 0.12, z - R * 0.12); b.scale.z = 0.3; head.add(b); return b; });
  rig.tear = emojiSprite("💧", R * 0.5); rig.tear.position.set(R * 0.42, -R * 0.15, z + R * 0.05); head.add(rig.tear);
  rig.zzz = emojiSprite("💤", R * 0.9); rig.zzz.position.set(R * 0.9, R * 0.9, 0); head.add(rig.zzz);
  rig.anger = emojiSprite("💢", R * 0.7); rig.anger.position.set(-R * 0.85, R * 0.85, 0); head.add(rig.anger);
  rig.sweat = emojiSprite("💦", R * 0.6); rig.sweat.position.set(R * 0.9, R * 0.5, 0); head.add(rig.sweat);

  rig.set = id => {
    rig.emotion = id;
    Object.values(rig.mouths).forEach(m => m.visible = false);
    [rig.tear, rig.zzz, rig.anger, rig.sweat].forEach(s => s.visible = false);
    let browAng = 0, browY = R * 0.38, eyeS = 1, eyeY = 1, mouth = "smile", blush = 0.35, blushS = 1, look = 0;
    switch (id) {
      case "feliz": mouth = "smile"; browY = R * 0.42; break;
      case "triste": mouth = "frown"; browAng = -0.35; rig.tear.visible = true; blush = 0.15; break;
      case "zangado": mouth = "flat"; browAng = 0.45; browY = R * 0.32; eyeY = 0.7; rig.anger.visible = true; blush = 0.55; break;
      case "medo": mouth = "o"; browAng = -0.3; browY = R * 0.46; eyeS = 1.3; rig.sweat.visible = true; blush = 0.1; break;
      case "surpreendido": mouth = "o"; browY = R * 0.52; eyeS = 1.35; break;
      case "envergonhado": mouth = "small"; browAng = -0.15; blush = 0.85; blushS = 1.5; look = 1; break;
      case "cansado": mouth = "o"; eyeY = 0.15; browY = R * 0.34; rig.zzz.visible = true; break;
      case "calmo": mouth = "small"; eyeY = 0.15; browY = R * 0.4; break;
    }
    rig.mouths[mouth].visible = true;
    rig.mouths.o.scale.setScalar(id === "cansado" ? 0.8 : 1);
    rig.brows.forEach(b => { b.rotation.z = b.userData.s * browAng; b.position.y = browY; });
    rig.eyes.forEach(e => { e.scale.set(eyeS, eyeS * eyeY, eyeS); e.userData.pupil.position.x = look * R * 0.05; });
    rig.blush.forEach(b => { b.material.opacity = blush; b.scale.set(blushS, blushS, 0.3); });
  };
  rig.set("feliz");
  return rig;
}
