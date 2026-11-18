// =====================================================================
//  level.js – die Reise: 5 Gebiete hintereinander in Richtung -Z
//  🌸 Blumenwiese → ⛰️ Regenbogen-Berge → 🍭 Süßigkeiten-Fluss → 🏰 Schlösser-Tal → 🍄 Trolldorf
// =====================================================================

const ZONES = [
  { name: "🌸 Blumenwiese",        start: 14,   end: -60,  color: 0x86d665, song: "wiese" },
  { name: "⛰️ Regenbogen-Berge",   start: -60,  end: -120, color: 0xb4c9a6, song: "abenteuer" },
  { name: "🍭 Süßigkeiten-Fluss",  start: -120, end: -180, color: 0xffc4e1, song: "zauber" },
  { name: "🏰 Schlösser-Tal",      start: -180, end: -240, color: 0x9be37f, song: "zauber" },
  { name: "🍄 Trolldorf",          start: -240, end: -295, color: 0x9fdcc4, song: "abenteuer" },
];
const WORLD_END = -292;
function zoneAt(z) { for (let i = 0; i < ZONES.length; i++) if (z > ZONES[i].end) return i; return ZONES.length - 1; }
const at = (dx, z) => [pathX(z) + dx, z];      // Position relativ zur Wegmitte

// ---------------- Boden: ein langer Streifen, Farbe wechselt je Gebiet ----------------
(function buildGround() {
  const tex = canvasTexture(512, (g, S) => {
    g.fillStyle = "#d8d8d8"; g.fillRect(0, 0, S, S);
    for (let i = 0; i < 2500; i++) {
      const l = 70 + Math.random() * 30;
      g.fillStyle = `hsla(0, 0%, ${l}%, 0.35)`;
      g.beginPath(); g.arc(Math.random() * S, Math.random() * S, rand(2, 14), 0, Math.PI * 2); g.fill();
    }
  }, 50, 80);
  const geo = new THREE.PlaneGeometry(220, 340, 1, 170);
  geo.rotateX(-Math.PI / 2);
  geo.translate(0, 0, -140);
  const p = geo.attributes.position, colors = [], c = new THREE.Color(), c2 = new THREE.Color();
  for (let i = 0; i < p.count; i++) {
    const z = p.getZ(i), zi = zoneAt(z);
    c.set(ZONES[zi].color);
    // weicher Übergang an den Gebietsgrenzen
    const next = ZONES[zi + 1];
    if (next && z - ZONES[zi].end < 6) c.lerp(c2.set(next.color), 0.5 - (z - ZONES[zi].end) / 12);
    colors.push(c.r, c.g, c.b);
  }
  geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
  const ground = mesh(geo, new THREE.MeshStandardMaterial({ map: tex, vertexColors: true, roughness: 1 }), false);
  scene.add(ground);
  // Trampelpfad mit Hufspuren der Trolle (helle Erde)
  const pathMat = new THREE.MeshStandardMaterial({ color: 0xf3dcc0, roughness: 1, transparent: true, opacity: 0.55 });
  const pos = [], idx = [];
  let n = 0;
  for (let z = 14; z >= WORLD_END; z -= 1, n++) {
    const w = 1.3 + Math.sin(z * 0.3) * 0.15;
    pos.push(pathX(z) - w, 0.025, z, pathX(z) + w, 0.025, z);
    if (n) idx.push(2 * n - 2, 2 * n - 1, 2 * n, 2 * n - 1, 2 * n + 1, 2 * n);
  }
  const tg = new THREE.BufferGeometry();
  tg.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  tg.setIndex(idx); tg.computeVertexNormals();
  const trail = new THREE.Mesh(tg, pathMat);
  trail.receiveShadow = true;
  scene.add(trail);
  // Troll-Fußspuren auf dem Pfad
  const printGeo = new THREE.CircleGeometry(0.13, 10); printGeo.rotateX(-Math.PI / 2); printGeo.scale(1, 1, 1.5);
  const prints = new THREE.InstancedMesh(printGeo, new THREE.MeshBasicMaterial({ color: 0xa07ec8, transparent: true, opacity: 0.45 }), 260);
  const d = new THREE.Object3D();
  for (let i = 0; i < 260; i++) {
    const z = 0 - i * 1.1, side = i % 2 ? 0.25 : -0.25;
    d.position.set(pathX(z) + side + Math.sin(i) * 0.3, 0.035, z); d.updateMatrix();
    prints.setMatrixAt(i, d.matrix);
  }
  scene.add(prints);
})();

// ---------------- Gemeinsame Deko-Bausteine ----------------
function bigHill(x, z, sx, sy, sz, color) {
  const h = mesh(new THREE.SphereGeometry(1, 18, 12), mat(color), false);
  h.scale.set(sx, sy, sz); h.position.set(x, -1, z); scene.add(h);
}
// Reihe von Deko links und rechts des Weges
function edgeRow(z0, z1, step, minOff, maxOff, make) {
  for (let z = z0; z > z1; z -= step) {
    [-1, 1].forEach(s => {
      const zz = z + rand(-step * 0.3, step * 0.3);
      make(pathX(zz) + s * rand(minOff, maxOff), zz, s);
    });
  }
}

// =====================================================================
//  Gebiet 1: Blumenwiese
// =====================================================================
scatterGrass(20000, 14, -62, 17, 0.25, 0.55);
scatterFlowers(380, 14, -62, 15, [0xffffff, 0xfff3a0, 0xffd23f, 0xf7a1c4, 0xc9a7ff, 0xff7eb9]);
edgeRow(10, -58, 5, PATH_HALF + 1.5, PATH_HALF + 4, (x, z) => tree(x, z, rand(0.9, 1.3)));
edgeRow(8, -58, 8, PATH_HALF + 7, PATH_HALF + 12, (x, z) => tree(x, z, rand(1.1, 1.5)));
for (let z = 0; z > -60; z -= 15) [-1, 1].forEach(s => bigHill(pathX(z) + s * rand(32, 42), z, 16, 7, 12, 0x77b85a));
rock(...at(-5, -10), 0.7, 0.5);
log(...at(4, -16), 3, true);
hay(...at(-6, -26));
fence(...at(5, -34), 4, true);
bush(...at(5, -41));
stump(...at(-3, -46));
rock(...at(-7, -52), 0.9, 0.8);

// =====================================================================
//  Gebiet 2: Regenbogen-Berge
// =====================================================================
const MOUNTAIN_COLORS = [0xc7b3ff, 0xffb8d9, 0xa8d8ff, 0xb8f0d0];
function mountain(x, z, r, h, color) {
  const m = mesh(new THREE.ConeGeometry(r, h, 9), mat(color, { flatShading: true }), false);
  m.position.set(x, h / 2 - 0.5, z); m.rotation.y = Math.random(); scene.add(m);
  const snow = mesh(new THREE.ConeGeometry(r * 0.36, h * 0.36, 9), mat(0xffffff, { flatShading: true }), false);
  snow.position.set(x, h - 0.5 - h * 0.18 + 0.05, z); snow.rotation.y = m.rotation.y; scene.add(snow);
}
function pine(x, z, s = 1) {
  const g = new THREE.Group();
  const trunk = mesh(new THREE.CylinderGeometry(0.12 * s, 0.18 * s, 1 * s, 8), mat(0x7a5236)); trunk.position.y = 0.5 * s; g.add(trunk);
  const green = mat(0x3f9d6a, { flatShading: true });
  [[1.6, 1.3, 1.2], [1.25, 1.1, 2.0], [0.85, 0.9, 2.7]].forEach(([r, h, y]) => {
    const c = mesh(new THREE.ConeGeometry(r * s, h * s * 1.3, 9), green); c.position.y = y * s; g.add(c);
  });
  g.position.set(x, 0, z); scene.add(g);
}
scatterGrass(6000, -58, -122, 15, 0.2, 0.4, -0.03);
scatterFlowers(150, -58, -122, 14, [0xffffff, 0xc9a7ff, 0xa8d8ff]);
edgeRow(-56, -122, 4, PATH_HALF + 1, PATH_HALF + 5, (x, z) => Math.random() < 0.5 ? pine(x, z, rand(0.8, 1.2)) : rock(x, z, rand(0.8, 1.6), rand(0.8, 1.8)));
for (let z = -55; z > -128; z -= 9) [-1, 1].forEach(s =>
  mountain(pathX(z) + s * rand(20, 30), z + rand(-3, 3), rand(9, 14), rand(16, 28), MOUNTAIN_COLORS[Math.floor(Math.random() * 4)]));
// Regenbogen-Bogen über dem Pass
RAINBOW.slice(0, 6).forEach((c, i) => {
  const arc = mesh(new THREE.TorusGeometry(13 - i * 0.55, 0.28, 8, 40, Math.PI), mat(c, { roughness: 0.4, emissive: c, emissiveIntensity: 0.25 }), false);
  arc.position.set(pathX(-92), 0, -92); scene.add(arc);
});
// Felsstufen zum Draufspringen
rock(...at(-4, -70), 1.2, 0.7);
rock(...at(-6.5, -74), 1.2, 1.3);
rock(...at(5, -82), 0.8, 0.5);
rock(...at(3, -90), 1.5, 1.1);
rock(...at(-5, -96), 1.0, 0.8);
rock(...at(6, -104), 1.3, 1.4);

// =====================================================================
//  Gebiet 3: Süßigkeiten-Fluss
// =====================================================================
const swirlTex = canvasTexture(256, (g, S) => {
  const cols = ["#ff5fa2", "#ffffff", "#ffd23f", "#7fd3ff", "#b98bff"];
  for (let r = S / 2; r > 0; r -= 6) {
    g.fillStyle = cols[Math.floor(r / 6) % cols.length];
    g.beginPath(); g.arc(S / 2 + Math.sin(r * 0.2) * 6, S / 2 + Math.cos(r * 0.2) * 6, r, 0, Math.PI * 2); g.fill();
  }
});
const stripeTex = canvasTexture(64, (g, S) => {
  g.fillStyle = "#ffffff"; g.fillRect(0, 0, S, S);
  g.fillStyle = "#ff3d6e"; for (let i = -1; i < 3; i++) { g.beginPath(); g.moveTo(0, i * 32); g.lineTo(S, i * 32 + 32); g.lineTo(S, i * 32 + 48); g.lineTo(0, i * 32 + 16); g.fill(); }
}, 1, 6);
function lollipop(x, z, s = 1) {
  const g = new THREE.Group();
  const stick = mesh(new THREE.CylinderGeometry(0.07 * s, 0.07 * s, 3 * s, 8), mat(0xffffff)); stick.position.y = 1.5 * s; g.add(stick);
  const candy = mesh(new THREE.CylinderGeometry(1 * s, 1 * s, 0.25 * s, 32), [mat(0xff5fa2, { roughness: 0.25 }), mat(0xffffff, { map: swirlTex, roughness: 0.25 }), mat(0xffffff, { map: swirlTex, roughness: 0.25 })]);
  candy.rotation.x = Math.PI / 2; candy.position.y = 3.6 * s; g.add(candy);
  g.rotation.y = rand(-0.6, 0.6);
  g.position.set(x, 0, z); scene.add(g);
}
function candyCane(x, z, s = 1) {
  const pts = [];
  for (let i = 0; i <= 10; i++) pts.push(new THREE.Vector3(0, i * 0.3 * s, 0));
  for (let i = 1; i <= 8; i++) { const a = i / 8 * Math.PI; pts.push(new THREE.Vector3((1 - Math.cos(a)) * 0.45 * s, 3 * s + Math.sin(a) * 0.45 * s, 0)); }
  const m = mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 40, 0.13 * s, 10), mat(0xffffff, { map: stripeTex, roughness: 0.3 }));
  m.position.set(x, 0, z); m.rotation.y = Math.random() * Math.PI * 2; scene.add(m);
}
const cottonMat = mat(0xffb3dc, { roughness: 0.9 });
function cottonTree(x, z, s = 1) {
  const g = new THREE.Group();
  const stick = mesh(new THREE.CylinderGeometry(0.08 * s, 0.1 * s, 2.2 * s, 8), mat(0xfff3e0)); stick.position.y = 1.1 * s; g.add(stick);
  const col = [0xffb3dc, 0xc9b3ff, 0xb3e5ff][Math.floor(Math.random() * 3)];
  const m = mat(col, { roughness: 0.95 });
  [[0, 2.8, 0, 1.1], [0.6, 2.5, 0.2, 0.75], [-0.6, 2.6, -0.1, 0.8], [0, 3.4, 0.1, 0.7]].forEach(([a, b, c, r]) => {
    const f = mesh(foliageGeometry(1, 1), m); f.scale.setScalar(r * s); f.position.set(a * s, b * s, c * s); g.add(f);
  });
  g.position.set(x, 0, z); scene.add(g);
}
function cupcake(x, z) {
  const g = new THREE.Group();
  const cup = mesh(new THREE.CylinderGeometry(0.75, 0.55, 0.6, 16), mat(0x8ad8ff, { flatShading: true })); cup.position.y = 0.3; g.add(cup);
  const icing = mesh(new THREE.SphereGeometry(0.8, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xfff0f7, { roughness: 0.4 })); icing.position.y = 0.6; icing.scale.y = 0.6; g.add(icing);
  const cherry = mesh(new THREE.SphereGeometry(0.14, 10, 8), mat(0xff2244, { roughness: 0.2 })); cherry.position.y = 1.15; g.add(cherry);
  for (let i = 0; i < 12; i++) {
    const sp = mesh(new THREE.CapsuleGeometry(0.02, 0.06, 2, 4), mat(RAINBOW[i % 7]), false);
    const a = Math.random() * Math.PI * 2, r = rand(0.1, 0.6);
    sp.position.set(Math.cos(a) * r, 0.6 + Math.sqrt(1 - (r / 0.8) ** 2) * 0.48, Math.sin(a) * r); sp.rotation.set(Math.random() * 3, 0, Math.random() * 3); g.add(sp);
  }
  addObstacle({ shape: "circle", x, z, r: 0.75, h: 0.9 }, g);
}
function gummyBear(x, z, color) {
  const g = new THREE.Group();
  const m = new THREE.MeshPhysicalMaterial({ color, roughness: 0.15, clearcoat: 1, transparent: true, opacity: 0.88 });
  const b = mesh(new THREE.SphereGeometry(0.5, 16, 12), m); b.position.y = 0.55; b.scale.set(1, 1.15, 0.8); g.add(b);
  const h = mesh(new THREE.SphereGeometry(0.36, 16, 12), m); h.position.y = 1.25; g.add(h);
  [-1, 1].forEach(s => {
    const e = mesh(new THREE.SphereGeometry(0.12, 8, 6), m); e.position.set(s * 0.25, 1.52, 0); g.add(e);
    const a = mesh(new THREE.SphereGeometry(0.17, 8, 6), m); a.position.set(s * 0.45, 0.75, 0.1); g.add(a);
    const l = mesh(new THREE.SphereGeometry(0.2, 8, 6), m); l.position.set(s * 0.25, 0.15, 0.15); g.add(l);
  });
  addEyes(h, 0.12, 0.05, 0.32, 0.03);
  g.rotation.y = rand(-0.5, 0.5);
  addObstacle({ shape: "circle", x, z, r: 0.6, h: 1.6 }, g);
}
function chocolate(x, z) {
  const g = new THREE.Group(), choc = mat(0x6b3a22, { roughness: 0.35 });
  const base = mesh(new THREE.BoxGeometry(2, 0.6, 1.2), choc); base.position.y = 0.3; g.add(base);
  for (let i = 0; i < 3; i++) for (let k = 0; k < 2; k++) {
    const c = mesh(new THREE.BoxGeometry(0.58, 0.18, 0.5), mat(0x7d4529, { roughness: 0.3 })); c.position.set(-0.66 + i * 0.66, 0.68, -0.28 + k * 0.56); g.add(c);
  }
  addObstacle({ shape: "box", x, z, hw: 1, hd: 0.6, h: 0.8 }, g);
}
function donut(x, z) {
  const g = new THREE.Group();
  const d = mesh(new THREE.TorusGeometry(0.6, 0.3, 14, 24), mat(0xe0a060)); d.rotation.x = Math.PI / 2; d.position.y = 0.3; g.add(d);
  const icing = mesh(new THREE.TorusGeometry(0.6, 0.25, 10, 24, Math.PI * 2), mat(0xff7eb9, { roughness: 0.3 })); icing.rotation.x = Math.PI / 2; icing.position.y = 0.42; icing.scale.z = 0.6; g.add(icing);
  addObstacle({ shape: "circle", x, z, r: 0.9, h: 0.6 }, g);
}
// Streusel auf dem Boden
(function sprinkles() {
  const N = Math.round(2500 * Math.max(0.5, DETAIL)), inst = new THREE.InstancedMesh(new THREE.CapsuleGeometry(0.03, 0.1, 2, 4), new THREE.MeshLambertMaterial({ color: 0xffffff }), N);
  const d = new THREE.Object3D(), c = new THREE.Color();
  for (let i = 0; i < N; i++) {
    const z = rand(-118, -182);
    d.position.set(pathX(z) + rand(-16, 16), 0.03, z); d.rotation.set(Math.PI / 2, 0, Math.random() * 6); d.updateMatrix();
    inst.setMatrixAt(i, d.matrix); inst.setColorAt(i, c.set(RAINBOW[i % 7]));
  }
  scene.add(inst);
})();
edgeRow(-118, -182, 4, PATH_HALF + 1.5, PATH_HALF + 5, (x, z) => {
  const r = Math.random();
  r < 0.4 ? lollipop(x, z, rand(0.7, 1.1)) : r < 0.7 ? cottonTree(x, z, rand(0.9, 1.3)) : candyCane(x, z, rand(0.8, 1.2));
});
for (let z = -120; z > -180; z -= 14) [-1, 1].forEach(s => bigHill(pathX(z) + s * rand(28, 38), z, 14, 8, 12, [0xffd1ea, 0xe5d4ff, 0xd4f1ff][Math.floor(Math.random() * 3)]));
cupcake(...at(-5, -126));
gummyBear(...at(5, -131), 0xff4d6d);
chocolate(...at(0, -136));
donut(...at(-5, -141));
cupcake(...at(4, -166));
gummyBear(...at(-4, -172), 0x4dd0ff);

// Der Fluss aus Erdbeermilch (quer über den Weg)
const RIVER = { z0: -155, z1: -145 };
const riverMat = new THREE.ShaderMaterial({
  uniforms: { uTime: windTime },
  vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: `
    uniform float uTime; varying vec2 vUv;
    void main() {
      float w = sin(vUv.x * 60.0 + uTime * 2.0 + sin(vUv.y * 12.0 + uTime) * 2.0) * 0.5 + 0.5;
      float sparkle = step(0.985, fract(sin(dot(floor(vUv * vec2(400.0, 40.0)) + floor(uTime * 3.0), vec2(12.9898, 78.233))) * 43758.5453));
      vec3 col = mix(vec3(1.0, 0.55, 0.75), vec3(1.0, 0.75, 0.88), w) + sparkle * 0.5;
      gl_FragColor = vec4(col, 1.0);
      #include <tonemapping_fragment>
      #include <encodings_fragment>
    }`,
});
const river = new THREE.Mesh(new THREE.PlaneGeometry(120, RIVER.z1 - RIVER.z0), riverMat);
river.rotation.x = -Math.PI / 2; river.position.set(pathX(-150), 0.06, (RIVER.z0 + RIVER.z1) / 2);
scene.add(river);
[RIVER.z0 - 0.3, RIVER.z1 + 0.3].forEach(z => {   // Schokoladen-Ufer
  const bank = mesh(new THREE.BoxGeometry(120, 0.12, 0.7), mat(0x6b3a22, { roughness: 0.4 }), false);
  bank.position.set(pathX(-150), 0.05, z); scene.add(bank);
});
// Macaron-Steine: tauchen erst auf, wenn Quakis Rätsel gelöst ist
const macarons = [];
[-146.6, -148.5, -150.4, -152.3, -154.2].forEach((z, i) => {
  const g = new THREE.Group(), col = [0xff8fc8, 0xb9a3ff, 0x9fe3c0, 0xffe08a, 0x8fd0ff][i];
  const shellM = mat(col, { roughness: 0.6 });
  [0.08, 0.3].forEach(y => { const sh = mesh(new THREE.CylinderGeometry(0.6, 0.6, 0.16, 20), shellM); sh.position.y = y; g.add(sh); });
  const cream = mesh(new THREE.CylinderGeometry(0.55, 0.55, 0.08, 20), mat(0xfffaf0)); cream.position.y = 0.19; g.add(cream);
  const [x] = at(i % 2 ? 0.8 : -0.8, z);
  const o = addObstacle({ shape: "circle", x, z, r: 0.6, h: 0.38, off: true }, g);
  g.position.y = -0.6;
  macarons.push(o);
});

// =====================================================================
//  Gebiet 4: Schlösser-Tal
// =====================================================================
const CASTLE_COLORS = [0xffd6ec, 0xe8dcff, 0xfff1c9];
const ROOF_COLORS = [0xff6fae, 0xa77bff, 0x6fc3ff];
function tower(g, x, z, r, h, wallM, roofM, flag = true) {
  const t = mesh(new THREE.CylinderGeometry(r, r * 1.05, h, 16), wallM); t.position.set(x, h / 2, z); g.add(t);
  const roof = mesh(new THREE.ConeGeometry(r * 1.3, r * 2.2, 16), roofM); roof.position.set(x, h + r * 1.1, z); g.add(roof);
  for (let i = 0; i < 3; i++) {   // Fenster
    const w = mesh(new THREE.CircleGeometry(r * 0.22, 12), mat(0x5a8fd6, { emissive: 0x223355 }), false);
    const a = i * 2.1; w.position.set(x + Math.sin(a) * r * 1.01, h * 0.65, z + Math.cos(a) * r * 1.01); w.rotation.y = a; g.add(w);
  }
  if (flag) {
    const pole = mesh(new THREE.CylinderGeometry(0.03, 0.03, 1.2, 6), mat(0xffffff)); pole.position.set(x, h + r * 2.2 + 0.5, z); g.add(pole);
    const f = mesh(new THREE.PlaneGeometry(0.7, 0.4), mat(RAINBOW[Math.floor(Math.random() * 7)], { side: THREE.DoubleSide })); f.position.set(x + 0.36, h + r * 2.2 + 0.9, z); g.add(f);
    flags.push(f);
  }
}
const flags = [];
function castle(x, z, s = 1, facing = 0) {
  const g = new THREE.Group(), k = Math.floor(Math.random() * 3);
  const wallM = mat(CASTLE_COLORS[k], { roughness: 0.7 }), roofM = mat(ROOF_COLORS[k], { roughness: 0.5 });
  const keep = mesh(new THREE.BoxGeometry(4, 5, 4), wallM); keep.position.y = 2.5; g.add(keep);
  for (let i = 0; i < 4; i++) {   // Zinnen
    for (let j = 0; j < 4; j++) { const b = mesh(new THREE.BoxGeometry(0.5, 0.5, 0.5), wallM); const e = -1.75 + j * 1.17; b.position.set(i < 2 ? e : (i === 2 ? -1.75 : 1.75), 5.25, i < 2 ? (i ? 1.75 : -1.75) : e); g.add(b); }
  }
  const door = mesh(new THREE.PlaneGeometry(1.2, 1.8), mat(0x9b5de5), false); door.position.set(0, 0.9, 2.01); g.add(door);
  [[-2, -2], [2, -2], [-2, 2], [2, 2]].forEach(([a, b]) => tower(g, a, b, 0.8, 6.5, wallM, roofM));
  tower(g, 0, 0, 1.1, 8, wallM, roofM);
  g.scale.setScalar(s); g.position.set(x, 0, z); g.rotation.y = facing;
  scene.add(g);
}
function hedge(x, z, w, d, h = 1.0, solid = true) {
  const g = new THREE.Group();
  const m = mesh(new THREE.BoxGeometry(w, h, d), mat(0x3f9d4a, { roughness: 0.95 })); m.position.y = h / 2; g.add(m);
  for (let i = 0; i < Math.max(2, w * 1.5); i++) {   // Röschen
    const r = mesh(new THREE.SphereGeometry(0.07, 8, 6), mat([0xff4d8d, 0xffffff, 0xffb3d1][i % 3]), false);
    r.position.set(rand(-w / 2, w / 2), rand(0.3, h), d / 2 + 0.02); g.add(r);
  }
  if (solid) addObstacle({ shape: "box", x, z, hw: w / 2, hd: d / 2, h }, g);
  else { g.position.set(x, 0, z); scene.add(g); }
}
function fountain(x, z) {
  const g = new THREE.Group(), stone = mat(0xf2e6ff, { roughness: 0.6 });
  const basin = mesh(new THREE.CylinderGeometry(1.3, 1.4, 0.7, 24), stone); basin.position.y = 0.35; g.add(basin);
  const water = mesh(new THREE.CircleGeometry(1.15, 24), mat(0x9fdcff, { roughness: 0.1, metalness: 0.2 }), false);
  water.rotation.x = -Math.PI / 2; water.position.y = 0.66; g.add(water);
  const col = mesh(new THREE.CylinderGeometry(0.15, 0.2, 1.4, 12), stone); col.position.y = 1.1; g.add(col);
  const bowl = mesh(new THREE.SphereGeometry(0.45, 16, 8, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), stone); bowl.position.y = 1.8; g.add(bowl);
  addObstacle({ shape: "circle", x, z, r: 1.35, h: 0.7 }, g);
  fountains.push(new THREE.Vector3(x, 2.0, z));
}
const fountains = [];
scatterGrass(15000, -178, -242, 16, 0.2, 0.4, 0.02);
scatterFlowers(260, -178, -242, 15, [0xff4d8d, 0xffffff, 0xffb3d1, 0xffe066]);
for (let z = -182; z > -238; z -= 8) [-1, 1].forEach(s => hedge(pathX(z) + s * (PATH_HALF + 1), z, 0.9, 7.6, 1.3, false));
[[-1, -190], [1, -200], [-1, -214], [1, -228]].forEach(([s, z]) => castle(pathX(z) + s * rand(20, 26), z, rand(1.1, 1.5), s > 0 ? -Math.PI / 2 : Math.PI / 2));
for (let z = -182; z > -240; z -= 12) [-1, 1].forEach(s => bigHill(pathX(z) + s * rand(34, 44), z, 16, 9, 12, 0x8fd16f));
hedge(...at(-5, -190), 2.4, 0.9);
fountain(...at(0, -200));
hedge(...at(5, -208), 2.4, 0.9);
// Topiary-Kugeln
[[-6, -218], [6, -196], [6, -222]].forEach(([dx, z]) => {
  const g = new THREE.Group();
  const pot = mesh(new THREE.CylinderGeometry(0.35, 0.28, 0.5, 12), mat(0xffb3d1)); pot.position.y = 0.25; g.add(pot);
  const ball = mesh(foliageGeometry(1, 2), foliageMat); ball.scale.setScalar(0.7); ball.position.y = 1.1; g.add(ball);
  const [x] = at(dx, z); addObstacle({ shape: "circle", x, z, r: 0.5, h: 1.8 }, g);
});

// Die große Schlossmauer mit Fallgitter (Tor ins Trolldorf)
const CASTLE_GATE_Z = -240;
(function castleWall() {
  const cx = pathX(CASTLE_GATE_Z), wallM = mat(0xffd6ec, { roughness: 0.7 }), roofM = mat(0xff6fae);
  const g = new THREE.Group();
  [-1, 1].forEach(s => {
    const w = mesh(new THREE.BoxGeometry(30, 5, 1.6), wallM); w.position.set(s * (3.2 + 15), 2.5, 0); g.add(w);
    for (let i = 0; i < 24; i++) { const b = mesh(new THREE.BoxGeometry(0.6, 0.6, 1.6), wallM); b.position.set(s * (3.5 + i * 1.2), 5.3, 0); g.add(b); }
    tower(g, s * 3.6, 0, 1.2, 7.5, wallM, roofM);
    obstacles.push({ shape: "box", x: cx + s * (3.2 + 15), z: CASTLE_GATE_Z, hw: 15, hd: 0.8, h: 99 });
  });
  const arch = mesh(new THREE.BoxGeometry(6.4, 1.2, 1.6), wallM); arch.position.y = 4.8; g.add(arch);
  g.position.set(cx, 0, CASTLE_GATE_Z); scene.add(g);
})();

// =====================================================================
//  Gebiet 5: Trolldorf
// =====================================================================
function mushroom(x, z, s = 1, color = 0xff4d6d, solid = false) {
  const g = new THREE.Group();
  const stem = mesh(new THREE.CylinderGeometry(0.35 * s, 0.45 * s, 1.2 * s, 14), mat(0xfff6e6)); stem.position.y = 0.6 * s; g.add(stem);
  const cap = mesh(new THREE.SphereGeometry(1 * s, 20, 12, 0, Math.PI * 2, 0, Math.PI / 2), mat(color, { roughness: 0.4 })); cap.position.y = 1.1 * s; cap.scale.y = 0.6; g.add(cap);
  for (let i = 0; i < 7; i++) {
    const d = mesh(new THREE.SphereGeometry(0.13 * s, 8, 6), mat(0xffffff), false);
    const a = i / 7 * Math.PI * 2, el = i === 0 ? 0 : 0.7;
    d.position.set(Math.cos(a) * el * 0.75 * s, 1.1 * s + Math.cos(el) * 0.58 * s, Math.sin(a) * el * 0.75 * s); d.scale.y = 0.4; g.add(d);
  }
  if (solid) addObstacle({ shape: "circle", x, z, r: 0.95 * s, h: 1.3 * s }, g);
  else { g.position.set(x, 0, z); scene.add(g); }
}
function mushroomHouse(x, z, s, color) {
  const g = new THREE.Group();
  const stem = mesh(new THREE.CylinderGeometry(1.4, 1.6, 2.6, 18), mat(0xfff1dc)); stem.position.y = 1.3; g.add(stem);
  const cap = mesh(new THREE.SphereGeometry(2.6, 22, 12, 0, Math.PI * 2, 0, Math.PI / 2), mat(color, { roughness: 0.45 })); cap.position.y = 2.4; cap.scale.y = 0.75; g.add(cap);
  const door = mesh(new THREE.CircleGeometry(0.5, 16, 0, Math.PI), mat(0x9b5de5), false); door.position.set(0, 0.01, 1.61); door.scale.y = 2; g.add(door);
  const win = mesh(new THREE.CircleGeometry(0.3, 12), mat(0xffe066, { emissive: 0xffa000, emissiveIntensity: 0.4 }), false); win.position.set(0.8, 1.8, 1.42); win.rotation.y = 0.5; g.add(win);
  for (let i = 0; i < 8; i++) {
    const d = mesh(new THREE.SphereGeometry(0.3, 8, 6), mat(0xffffff), false);
    const a = i / 8 * Math.PI * 2; d.position.set(Math.cos(a) * 1.7, 3.6, Math.sin(a) * 1.7); d.scale.y = 0.4; g.add(d);
  }
  g.scale.setScalar(s); g.position.set(x, 0, z); g.rotation.y = x > pathX(z) ? -Math.PI / 2.5 : Math.PI / 2.5;
  scene.add(g);
}
scatterGrass(12000, -238, -296, 16, 0.25, 0.5, 0.12);
scatterFlowers(200, -238, -296, 15, [0xffe066, 0xff7eb9, 0x9fe3ff]);
edgeRow(-244, -294, 4, PATH_HALF + 1, PATH_HALF + 4, (x, z) => mushroom(x, z, rand(1, 1.8), [0xff4d6d, 0xc77dff, 0xff9f1c, 0x4dd0ff][Math.floor(Math.random() * 4)]));
[[-1, -250], [1, -258], [-1, -270], [1, -280]].forEach(([s, z], i) => mushroomHouse(pathX(z) + s * rand(15, 19), z, 1.1, [0xff4d6d, 0xc77dff, 0xff9f1c, 0x4dd0ff][i]));
for (let z = -246; z > -300; z -= 12) [-1, 1].forEach(s => bigHill(pathX(z) + s * rand(30, 40), z, 14, 9, 12, 0x7ccfb0));
// Rückwand des Dorfes
for (let x = -16; x <= 16; x += 2.2) mushroom(pathX(-296) + x, -296 + rand(-1, 1), rand(1.4, 2.2), [0xff4d6d, 0xc77dff, 0xff9f1c][Math.floor(Math.random() * 3)]);
mushroom(...at(-6, -262), 1.0, 0xc77dff, true);
mushroom(...at(6, -256), 0.8, 0xff9f1c, true);
// Wimpelketten
(function bunting() {
  for (let k = 0; k < 3; k++) {
    const z = -262 - k * 6, x0 = pathX(z) - 8, x1 = pathX(z) + 8;
    for (let i = 0; i <= 16; i++) {
      const f = i / 16, x = x0 + (x1 - x0) * f, y = 4.2 - Math.sin(f * Math.PI) * 1.2;
      const tri = mesh(new THREE.ConeGeometry(0.25, 0.5, 3), mat(RAINBOW[i % 7], { side: THREE.DoubleSide }), false);
      tri.position.set(x, y - 0.25, z); tri.rotation.z = Math.PI; tri.scale.z = 0.1; scene.add(tri);
    }
  }
})();

// Almas Bonbon-Käfig auf einer kleinen Bühne
const CAGE_POS = new THREE.Vector3(...[pathX(-283), 0, -283]);
const cage = { bars: new THREE.Group(), open: 0 };
(function buildCage() {
  const stage = mesh(new THREE.CylinderGeometry(2.4, 2.6, 0.4, 28), mat(0xc77dff)); stage.position.copy(CAGE_POS).setY(0.2); scene.add(stage);
  obstacles.push({ shape: "circle", x: CAGE_POS.x, z: CAGE_POS.z, r: 2.4, h: 0.4 });
  for (let i = 0; i < 14; i++) {
    const a = i / 14 * Math.PI * 2;
    const bar = mesh(new THREE.CylinderGeometry(0.06, 0.06, 2.6, 8), mat(0xffffff, { map: stripeTex, roughness: 0.3 }));
    bar.position.set(Math.cos(a) * 1.15, 1.3, Math.sin(a) * 1.15); cage.bars.add(bar);
  }
  const top = mesh(new THREE.SphereGeometry(1.25, 20, 10, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xff5fa2, { roughness: 0.3 })); top.position.y = 2.55; top.scale.y = 0.5; cage.bars.add(top);
  const bow = emojiSprite("🎀", 0.9); bow.position.y = 3.4; cage.bars.add(bow);
  cage.bars.position.copy(CAGE_POS).setY(0.4); scene.add(cage.bars);
  cage.obstacle = { shape: "circle", x: CAGE_POS.x, z: CAGE_POS.z, r: 1.3, h: 99 };
  obstacles.push(cage.obstacle);
})();

// ---------------- Sterne: [dx, z, Höhe] je Gebiet ----------------
const STAR_SPOTS = [
  // Blumenwiese
  [3, -8, 1], [-4, -13, 1], [-6, -26, 2.75], [0, -21, 2.8], [6, -30, 1], [-2, -38, 1], [5, -34, 2.5], [-7, -52, 2.2],
  // Berge
  [0, -66, 1], [-6.5, -74, 2.6], [5, -82, 1.8], [3, -90, 2.45], [-2, -86, 1], [6, -104, 2.75], [0, -98, 2.8], [-5, -115, 1],
  // Süßigkeiten (die letzten drei liegen hinter dem Fluss)
  [5, -122, 1], [-5, -126, 2.25], [-2, -129, 2.8], [-6, -134, 1], [0, -136, 2.15], [6, -139, 1], [-2, -143, 1],
  [-0.8, -150.4, 1.7], [4, -162, 1], [-4, -172, 3.0],
  // Schlösser
  [4, -186, 1], [-5, -190, 2.4], [-1, -195, 1], [0, -200, 2.2], [-6, -206, 1], [5, -208, 2.4], [-5, -214, 1], [2, -220, 2.8],
  // Trolldorf
  [4, -246, 1], [-4, -250, 1], [0, -254, 2.8], [6, -256, 2.2], [-6, -262, 2.65], [3, -266, 1], [-3, -270, 1],
].map(([dx, z, y]) => [pathX(z) + dx, z, y]);

// ---------------- Regenbogentore zwischen den Gebieten ----------------
const gateMat = new THREE.ShaderMaterial({
  transparent: true, depthWrite: false, side: THREE.DoubleSide,
  uniforms: { uTime: windTime, uFade: { value: 1 } },
  vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
  fragmentShader: `
    uniform float uTime; uniform float uFade; varying vec2 vUv;
    vec3 hsv(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }
    void main() {
      float shimmer = sin(vUv.y * 30.0 - uTime * 3.0 + vUv.x * 10.0) * 0.5 + 0.5;
      vec3 col = mix(hsv(fract(vUv.x * 1.5 + uTime * 0.1)), vec3(1.0), 0.45);
      gl_FragColor = vec4(col, (0.25 + shimmer * 0.25) * uFade * smoothstep(1.0, 0.7, vUv.y));
    }`,
});
const gates = [];
function rainbowGate(z) {
  const g = new THREE.Group(), x = pathX(z);
  const curtain = new THREE.Mesh(new THREE.PlaneGeometry(PATH_HALF * 2 + 2, 6), gateMat.clone());
  curtain.material.uniforms.uTime = windTime;
  curtain.position.y = 3; g.add(curtain);
  [-1, 1].forEach(s => {
    const pillar = mesh(new THREE.CylinderGeometry(0.35, 0.4, 6, 12), mat(0xffffff, { map: stripeTex }));
    pillar.position.set(s * (PATH_HALF + 1), 3, 0); g.add(pillar);
    const ball = mesh(new THREE.SphereGeometry(0.5, 14, 10), mat(0xffd34d, { metalness: 0.5, roughness: 0.3 }));
    ball.position.set(s * (PATH_HALF + 1), 6.3, 0); g.add(ball);
  });
  g.position.set(x, 0, z); scene.add(g);
  const gate = { z, open: false, fade: 1, group: g, curtain };
  gates.push(gate);
  return gate;
}
const gateWiese = rainbowGate(-60);
const gateBerge = rainbowGate(-120);
// Fallgitter im Schlosstor
const portcullis = (function () {
  const g = new THREE.Group(), iron = mat(0x9b7bd0, { metalness: 0.4, roughness: 0.4 });
  for (let i = 0; i < 9; i++) { const b = mesh(new THREE.BoxGeometry(0.12, 4.3, 0.12), iron); b.position.set(-2.9 + i * 0.725, 2.15, 0); g.add(b); }
  for (let i = 0; i < 5; i++) { const b = mesh(new THREE.BoxGeometry(6.4, 0.1, 0.1), iron); b.position.set(0, 0.5 + i * 0.9, 0); g.add(b); }
  g.position.set(pathX(CASTLE_GATE_Z), 0, CASTLE_GATE_Z); scene.add(g);
  const gate = { z: CASTLE_GATE_Z, open: false, fade: 1, group: g, portcullis: true };
  gates.push(gate);
  return gate;
})();

function openGate(gate) {
  gate.open = true;
  Sound.magic();
}
function updateLevel(dt, t) {
  gates.forEach(g => {
    if (!g.open || g.fade <= 0) return;
    g.fade = Math.max(0, g.fade - dt * 0.6);
    if (g.portcullis) g.group.position.y = (1 - g.fade) * 4.3;
    else {
      g.curtain.material.uniforms.uFade.value = g.fade;
      if (Math.random() < 0.6) spawnParticle(new THREE.Vector3(g.group.position.x + rand(-PATH_HALF, PATH_HALF), rand(0.5, 5), g.z),
        RAINBOW[Math.floor(Math.random() * 7)], new THREE.Vector3(0, rand(0.5, 2), 0), 1.2, 1.5, 0);
      if (g.fade === 0) g.curtain.visible = false;
    }
  });
  macarons.forEach((m, i) => {
    if (m.off) return;
    m.object.position.y = Math.min(0, m.object.position.y + dt * 1.2);
  });
  flags.forEach((f, i) => f.rotation.y = Math.sin(t * 3 + i) * 0.3);
  fountains.forEach(p => {
    if (Math.random() < 0.5) spawnParticle(p, 0xbfe9ff, new THREE.Vector3(rand(-1, 1), rand(2, 3), rand(-1, 1)), 0.7, 1.2, 8);
  });
  if (cage.open > 0 && cage.open < 1) {
    cage.open = Math.min(1, cage.open + dt * 0.5);
    cage.bars.position.y = 0.4 - cage.open * 3;
  }
}
function raiseMacarons() { macarons.forEach(m => m.off = false); Sound.magic(); }
function openCage() { cage.open = 0.001; cage.obstacle.off = true; Sound.magic(); }
