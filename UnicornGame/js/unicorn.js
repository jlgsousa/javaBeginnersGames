// =====================================================================
//  unicorn.js – das Einhorn: Modell (aus Grundformen) und Animation
//  Das Modell schaut in Richtung +Z, die Hufe stehen bei y = 0.
// =====================================================================

const HAIR_COLORS = [0xff7aa2, 0xffa07a, 0xffd97a, 0x9fe3a8, 0x8cc8ff, 0xb49cff, 0xf59ce6];
const RAINBOW = [0xff3b3b, 0xff9a1f, 0xffe11f, 0x3ddc5a, 0x2fa8ff, 0x8a4dff, 0xff4fd8];

const coat = new THREE.MeshStandardMaterial({ color: 0xf08ac0, roughness: 0.5 });
const coatLight = new THREE.MeshStandardMaterial({ color: 0xf7b5d6, roughness: 0.55 });
const hoofMat = new THREE.MeshStandardMaterial({ color: 0x5e4a58, roughness: 0.35 });
const hairMats = HAIR_COLORS.map(c => new THREE.MeshStandardMaterial({ color: c, roughness: 0.4, metalness: 0.05 }));
const hornMat = new THREE.MeshStandardMaterial({ color: 0xf0d48a, metalness: 0.55, roughness: 0.25, emissive: 0x3a2a00 });
const eyeMat = new THREE.MeshStandardMaterial({ color: 0x1e120c, roughness: 0.05, metalness: 0.1 });

function hairColor(f) {      // f = 0..1 → weicher Regenbogenverlauf
  const i = Math.min(HAIR_COLORS.length - 1, Math.floor(f * HAIR_COLORS.length));
  return hairMats[i];
}

const uni = {
  root: new THREE.Group(),   // Position & Drehung in der Welt
  body: new THREE.Group(),   // für Wippen / Neigen / Stauchen
  pos: new THREE.Vector3(0, 0, 0),
  vel: new THREE.Vector3(),
  heading: 0, onGround: true, jumpHeld: false,
  phase: 0, runBlend: 0, airBlend: 0, squash: 0,
  legs: [], mane: [], tail: [], forelock: [],
  headPivot: null, eyes: [], horn: null,
  look: { yaw: 0, pitch: 0, ty: 0, tp: 0, timer: 2, idle: 0, held: false },
  blink: 2,
};
uni.root.add(uni.body);
scene.add(uni.root);

function part(geo, material, x, y, z, parent = uni.body) {
  const m = mesh(geo, material);
  m.position.set(x, y, z);
  parent.add(m);
  return m;
}

// ---------------- Rumpf: Brust, Bauch, Hinterteil ----------------
const barrel = part(new THREE.CapsuleGeometry(0.4, 0.85, 8, 20), coat, 0, 1.24, 0);
barrel.rotation.x = Math.PI / 2; barrel.scale.set(0.92, 1, 1.0);
const chest = part(new THREE.SphereGeometry(0.44, 24, 16), coat, 0, 1.27, 0.55);
chest.scale.set(0.82, 1.0, 0.9);
const rump = part(new THREE.SphereGeometry(0.45, 24, 16), coat, 0, 1.33, -0.55);
rump.scale.set(0.95, 0.98, 1.0);
const withers = part(new THREE.SphereGeometry(0.3, 16, 12), coat, 0, 1.48, 0.42);
withers.scale.set(0.8, 0.7, 1.2);
const belly = part(new THREE.SphereGeometry(0.38, 20, 12), coatLight, 0, 1.03, 0.0);
belly.scale.set(0.85, 0.55, 1.5);

// ---------------- Beine ----------------
// Hüfte → Oberschenkel → Knie/Sprunggelenk → Röhrbein → Fesselgelenk → Huf
function makeLeg(x, z, front) {
  const hipY = front ? 1.05 : 1.12;
  const upperLen = front ? 0.48 : 0.5;
  const lowerLen = hipY - upperLen;
  const hip = new THREE.Group(); hip.position.set(x, hipY, z);
  uni.body.add(hip);
  // Oberschenkel (hinten kräftiger)
  const upper = part(new THREE.CylinderGeometry(front ? 0.13 : 0.16, front ? 0.068 : 0.075, upperLen + 0.08, 14),
    coat, 0, -upperLen / 2 + 0.02, 0, hip);
  if (!front) upper.rotation.x = 0.08;
  const knee = new THREE.Group(); knee.position.y = -upperLen; hip.add(knee);
  part(new THREE.SphereGeometry(front ? 0.08 : 0.085, 12, 8), coat, 0, 0, 0, knee);
  // Röhrbein (schlank)
  part(new THREE.CylinderGeometry(0.052, 0.06, lowerLen - 0.17, 10), coat, 0, -(lowerLen - 0.17) / 2, 0, knee);
  // Fesselgelenk mit etwas Behang (helles Fell)
  part(new THREE.SphereGeometry(0.07, 12, 8), coatLight, 0, -(lowerLen - 0.15), 0.01, knee);
  const pastern = part(new THREE.CylinderGeometry(0.055, 0.065, 0.09, 10), coatLight, 0, -(lowerLen - 0.085), 0.025, knee);
  pastern.rotation.x = 0.25;
  // Huf
  const hoof = part(new THREE.CylinderGeometry(0.068, 0.088, 0.1, 14), hoofMat, 0, -(lowerLen - 0.05), 0.035, knee);
  hoof.scale.z = 1.1;
  return { hip, knee, front };
}
uni.legs = [
  makeLeg(-0.21, -0.6, false), makeLeg(0.21, -0.6, false),   // hinten links / rechts
  makeLeg(-0.2, 0.58, true), makeLeg(0.2, 0.58, true),       // vorne links / rechts
];

// ---------------- Hals & Kopf (drehbar zum Umschauen) ----------------
const headPivot = new THREE.Group(); headPivot.position.set(0, 1.42, 0.68);
uni.body.add(headPivot); uni.headPivot = headPivot;
const NECK_TILT = 0.5;
const neck = part(new THREE.CylinderGeometry(0.17, 0.3, 0.95, 20), coat, 0, 0.38, 0.2, headPivot);
neck.rotation.x = NECK_TILT;
// Mähnenkamm (leicht gewölbter Hals)
const crest = part(new THREE.SphereGeometry(0.2, 16, 12), coat, 0, 0.45, 0.1, headPivot);
crest.scale.set(0.75, 2.4, 0.8); crest.rotation.x = NECK_TILT;

// Kopf: zeigt mit der Nase nach vorne-unten
const head = new THREE.Group(); head.position.set(0, 0.86, 0.47); head.rotation.x = 0.75;
headPivot.add(head);
const cranium = part(new THREE.SphereGeometry(0.2, 20, 16), coat, 0, 0, 0, head);
cranium.scale.set(0.85, 0.95, 1.1);
const jaw = part(new THREE.SphereGeometry(0.17, 18, 12), coat, 0, -0.08, -0.02, head);
jaw.scale.set(0.82, 0.75, 1.1);
// Nasenrücken (verjüngt sich nach vorne)
const muzzleBridge = part(new THREE.CylinderGeometry(0.105, 0.165, 0.42, 20), coat, 0, -0.01, 0.28, head);
muzzleBridge.rotation.x = Math.PI / 2;
const muzzle = part(new THREE.SphereGeometry(0.125, 18, 12), coatLight, 0, -0.02, 0.5, head);
muzzle.scale.set(1, 0.9, 1.05);
[-1, 1].forEach(s => {
  // Nüstern
  const nostril = part(new THREE.SphereGeometry(0.028, 8, 6), new THREE.MeshStandardMaterial({ color: 0x8a4a66, roughness: 0.6 }),
    s * 0.055, 0.03, 0.6, head);
  nostril.scale.set(0.8, 1.4, 0.6);
  // Augen: dunkel, glänzend, mandelförmig, seitlich am Kopf
  const eye = new THREE.Group(); eye.position.set(s * 0.155, 0.055, 0.06); eye.rotation.y = s * 0.35; head.add(eye);
  const ball = part(new THREE.SphereGeometry(0.045, 14, 10), eyeMat, 0, 0, 0, eye);
  ball.scale.set(0.7, 0.85, 1.2);
  part(new THREE.SphereGeometry(0.011, 6, 4), new THREE.MeshBasicMaterial({ color: 0xffffff }), s * 0.02, 0.018, 0.035, eye);
  // Augenlid (Fell-Wulst darüber)
  const lid = part(new THREE.TorusGeometry(0.045, 0.012, 6, 14, Math.PI), coat, 0, 0.004, 0, eye);
  lid.rotation.y = Math.PI / 2 * s; lid.scale.set(1, 0.9, 1.2);
  uni.eyes.push(eye);
  // Ohren: spitz, leicht nach vorne gedreht
  const ear = new THREE.Group(); ear.position.set(s * 0.1, 0.19, -0.1); ear.rotation.set(-0.5, 0, -s * 0.25); head.add(ear);
  const outer = part(new THREE.ConeGeometry(0.055, 0.2, 10), coat, 0, 0.1, 0, ear); outer.scale.z = 0.6;
  const inner = part(new THREE.ConeGeometry(0.035, 0.15, 8), coatLight, 0, 0.09, 0.015, ear); inner.scale.z = 0.4;
  // Wange
  const cheek = part(new THREE.SphereGeometry(0.09, 12, 8), coat, s * 0.1, -0.1, 0.02, head);
  cheek.scale.set(0.5, 0.8, 1);
});

// Horn: Kegel mit spiralförmigen Rillen
(function makeHorn() {
  const geo = new THREE.CylinderGeometry(0.004, 0.06, 0.62, 28, 30);
  const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const a = Math.atan2(z, x);
    const f = 1 + 0.16 * Math.sin(a * 2 - y * 38);
    p.setX(i, x * f); p.setZ(i, z * f);
  }
  geo.computeVertexNormals();
  uni.horn = part(geo, hornMat, 0, 0.24 + 0.31, 0.07, head);
  uni.horn.rotation.x = -0.15;
})();

// ---------------- Haare ----------------
// Eine Strähne = Kette aus Gliedern, damit sie sich wellenförmig biegen kann
function strand(parent, x, y, z, segments, segLen, thick, material) {
  const chain = [];
  let p = parent;
  for (let k = 0; k < segments; k++) {
    const seg = new THREE.Group();
    seg.position.set(k ? 0 : x, k ? -segLen : y, k ? 0 : z);
    const r0 = thick * (1 - k / segments * 0.6), r1 = thick * (1 - (k + 1) / segments * 0.6);
    const m = mesh(new THREE.CylinderGeometry(r0, r1, segLen * 1.08, 6), material);
    m.position.y = -segLen / 2;
    m.scale.x = 1.25;                      // etwas flach wie eine Haarsträhne
    seg.add(m);
    p.add(seg); chain.push(seg); p = seg;
  }
  return chain;
}

// Mähne entlang des Halskamms (vom Genick bis zum Widerrist)
const crestTop = new THREE.Vector3(0, 0.86, 0.25), crestBottom = new THREE.Vector3(0, 0.12, -0.26);
const MANE_N = 30;
for (let i = 0; i < MANE_N; i++) {
  const f = i / (MANE_N - 1);
  const pt = crestTop.clone().lerp(crestBottom, f);
  const side = i % 4 === 3 ? -1 : 1;           // fällt meist auf eine Seite, wie bei echten Pferden
  const segs = 5, len = 0.12 + Math.sin(f * Math.PI) * 0.05;
  const chain = strand(headPivot, side * 0.04, pt.y, pt.z, segs, len, 0.04, hairColor(f));
  chain.side = side; chain.f = f;
  uni.mane.push(chain);
}
// Stirnschopf
for (let i = 0; i < 5; i++) {
  const chain = strand(head, (i - 2) * 0.03, 0.2, -0.04, 3, 0.07, 0.022, hairMats[(i * 2) % 7]);
  chain.i = i;
  uni.forelock.push(chain);
}
// Schweif: 16 lange Strähnen
const tailRoot = new THREE.Group(); tailRoot.position.set(0, 1.5, -0.98); uni.body.add(tailRoot);
part(new THREE.SphereGeometry(0.09, 10, 8), coat, 0, -0.03, 0.02, tailRoot);
for (let i = 0; i < 16; i++) {
  const a = i / 16 * Math.PI * 2;
  const chain = strand(tailRoot, Math.cos(a) * 0.05, Math.sin(a) * 0.04, 0, 5, 0.2, 0.038, hairMats[i % 7]);
  chain.i = i; chain.spread = Math.cos(a) * 0.18;
  uni.tail.push(chain);
}

// ---------------- Animation ----------------
function animateUnicorn(dt, t) {
  const u = uni;
  const sp = Math.hypot(u.vel.x, u.vel.z);
  const mov = Math.min(1, sp / 0.6);

  if (u.onGround) u.phase += dt * (sp * 2.4 + (sp > 0.2 ? 2 : 0));
  u.runBlend += ((sp > (WALK + RUN) / 2 ? 1 : 0) - u.runBlend) * Math.min(1, dt * 6);
  u.airBlend += ((u.onGround ? 0 : 1) - u.airBlend) * Math.min(1, dt * 12);
  u.squash = Math.max(0, u.squash - dt * 4);

  u.root.position.copy(u.pos);
  u.root.rotation.y = u.heading;

  // Körper wippt, atmet, staucht beim Landen
  const bob = u.onGround ? Math.abs(Math.sin(u.phase)) * (0.03 + u.runBlend * 0.08) * mov : 0;
  const breathe = (1 - mov) * Math.sin(t * 2.2) * 0.012;
  u.body.position.y = bob - u.squash * 0.08;
  u.body.scale.set(1 + u.squash * 0.05 + breathe, 1 - u.squash * 0.06, 1);
  const gallopRock = Math.sin(u.phase) * 0.06 * u.runBlend * mov;
  u.body.rotation.x = u.onGround ? gallopRock : Math.max(-0.3, Math.min(0.3, -u.vel.y * 0.03));

  // Beine: Schritt/Trab beim Gehen, Galopp beim Rennen
  const rb = u.runBlend;
  const offs = [0, Math.PI * (1 - rb) + 0.6 * rb, Math.PI * (1 - rb) + (Math.PI + 0.6) * rb, Math.PI];
  u.legs.forEach((leg, i) => {
    const p = u.phase + offs[i];
    const amp = 0.42 + rb * 0.5;
    let swing = -Math.sin(p) * amp * mov;
    let bend = Math.max(0, Math.cos(p)) * (0.9 + rb * 0.6) * mov * (leg.front ? 1 : -1);
    const tuckSwing = leg.front ? -0.8 : 0.7, tuckBend = leg.front ? 1.9 : -1.3;
    swing = swing * (1 - u.airBlend) + tuckSwing * u.airBlend;
    bend = bend * (1 - u.airBlend) + tuckBend * u.airBlend;
    leg.hip.rotation.x = swing;
    leg.knee.rotation.x = bend;
  });

  // Haare wehen – stärker beim Rennen
  const wind = Math.min(1, sp / RUN);
  const air = u.onGround ? 0 : 1;
  u.mane.forEach((chain, i) => {
    chain[0].rotation.x = 0.35 + wind * 1.0 + air * 0.3 + Math.sin(t * 5 + i * 0.5) * (0.05 + wind * 0.12);
    chain[0].rotation.z = chain.side * (0.6 - wind * 0.3);
    for (let k = 1; k < chain.length; k++) {
      chain[k].rotation.x = Math.sin(t * 6 - k * 0.8 + i * 0.4) * (0.08 + wind * 0.25);
      chain[k].rotation.z = -chain.side * 0.12;   // fällt am Hals entlang nach unten
    }
  });
  u.forelock.forEach((chain, i) => {
    chain[0].rotation.x = -1.3 + Math.sin(t * 3 + i) * 0.08 - wind * 0.4;   // fällt über die Stirn
    chain[0].rotation.z = (i - 2) * 0.15;
    chain[1].rotation.x = 0.3; chain[2].rotation.x = 0.3;
  });
  u.tail.forEach((chain, i) => {
    // Schweif hebt sich am Ansatz und fällt dann nach unten
    chain[0].rotation.x = 0.85 + wind * 0.75 + air * 0.4 + Math.sin(t * 2 + i) * 0.03;
    chain[0].rotation.z = chain.spread * (1 + wind);
    for (let k = 1; k < chain.length; k++) {
      chain[k].rotation.x = -0.28 * (1 - wind * 0.85) + Math.sin(t * 5 - k * 0.9 + i * 0.3) * (0.05 + wind * 0.16);
      chain[k].rotation.z = Math.sin(t * 2.5 + i + k) * 0.06;
    }
  });

  // Umschauen
  const L = u.look;
  const idle = sp < 0.2 && u.onGround;
  L.idle = idle ? L.idle + dt : 0;
  const pickLook = () => {
    const choices = [[-0.9, 0], [0.9, 0], [0, -0.4], [0, 0.55], [-0.6, 0.35], [0.6, -0.25], [0, 0]];
    [L.ty, L.tp] = choices[Math.floor(Math.random() * choices.length)];
    L.timer = 1.2 + Math.random() * 1.8;
  };
  if (input.look && !L.held) pickLook();
  else if (L.idle > 1.5) { L.timer -= dt; if (L.timer <= 0) pickLook(); }
  else if (!idle) { L.ty = 0; L.tp = u.onGround ? 0.05 * rb : -0.2; L.timer = 0.4; }
  L.held = input.look;
  L.yaw += (L.ty - L.yaw) * Math.min(1, dt * 5);
  L.pitch += (L.tp - L.pitch) * Math.min(1, dt * 5);
  u.headPivot.rotation.order = "YXZ";
  u.headPivot.rotation.set(L.pitch, L.yaw, 0);

  // Blinzeln
  u.blink -= dt;
  if (u.blink < -0.12) u.blink = 2 + Math.random() * 3;
  u.eyes.forEach(e => e.scale.y = u.blink < 0 ? 0.15 : 1);
}
