// =====================================================================
//  chapter2.js – Kapitel 2: Mit dem Rentier Sven zum Nordpol
//  Alma reitet auf Sven durch den Abendhimmel, sammelt die Buchstaben
//  des Weihnachtsgrußes, hilft am Nordpol den Elfen und bekommt ein Malbuch.
// =====================================================================

const SENTENCE = "WIR WÜNSCHEN FROHE WEIHNACHTEN";
const CH2_X = 4000;                       // die Winterwelt liegt weit weg von Kapitel 1
const SVEN = { name: "Sven", emoji: "🦌" };
const SANTA = { name: "Weihnachtsmann", emoji: "🎅" };
const ELVES = [{ name: "Elfe Flocke", emoji: "🧝‍♀️" }, { name: "Elf Zimtstern", emoji: "🧝" }, { name: "Elfe Glöckchen", emoji: "🧝‍♀️" }];
Object.assign(NARRATOR, {
  "Sven": "Da sprach das Rentier Sven:",
  "Weihnachtsmann": "Da sprach der Weihnachtsmann:",
  "Elfe Flocke": "Da sprach die Elfe Flocke:",
  "Elf Zimtstern": "Da sprach der Elf Zimtstern:",
  "Elfe Glöckchen": "Da sprach die Elfe Glöckchen:",
});

const ch2 = {
  state: null, t: 0,
  pos: new THREE.Vector3(), vel: new THREE.Vector3(),
  slots: [], found: 0, letters: [], nextIndex: 0, spawnZ: 0,
  village: null, presents: [], wrapped: 0,
};

// =====================================================================
//  Figuren
// =====================================================================
function makeReindeer() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body);
  const coat = mat(0xa0683c, { roughness: 0.6 }), light = mat(0xf1dcc0, { roughness: 0.6 });
  const dark = mat(0x3d2b22, { roughness: 0.4 }), antlerM = mat(0xe0caa0, { roughness: 0.5 });
  const part = (geo, m, x, y, z, parent = body) => { const o = mesh(geo, m); o.position.set(x, y, z); parent.add(o); return o; };

  const torso = part(new THREE.CapsuleGeometry(0.33, 0.75, 8, 16), coat, 0, 1.05, 0); torso.rotation.x = Math.PI / 2;
  part(new THREE.SphereGeometry(0.36, 16, 12), coat, 0, 1.08, 0.45).scale.set(0.85, 1, 0.9);
  part(new THREE.SphereGeometry(0.37, 16, 12), coat, 0, 1.1, -0.45);
  part(new THREE.SphereGeometry(0.3, 14, 10), light, 0, 0.88, 0.05).scale.set(0.85, 0.55, 1.5);
  part(new THREE.SphereGeometry(0.12, 10, 8), light, 0, 1.25, -0.82);                 // Puschelschwanz

  // Beine
  const legs = [[-0.17, -0.48, false], [0.17, -0.48, false], [-0.17, 0.48, true], [0.17, 0.48, true]].map(([x, z, front]) => {
    const hip = new THREE.Group(); hip.position.set(x, 0.9, z); body.add(hip);
    part(new THREE.CylinderGeometry(front ? 0.1 : 0.13, 0.06, 0.48, 10), coat, 0, -0.22, 0, hip);
    const knee = new THREE.Group(); knee.position.y = -0.44; hip.add(knee);
    part(new THREE.CylinderGeometry(0.05, 0.055, 0.38, 8), coat, 0, -0.19, 0, knee);
    part(new THREE.CylinderGeometry(0.065, 0.08, 0.09, 10), dark, 0, -0.42, 0.02, knee);
    return { hip, knee, front };
  });

  // Hals & Kopf
  const neck = part(new THREE.CylinderGeometry(0.15, 0.24, 0.7, 14), coat, 0, 1.38, 0.62); neck.rotation.x = 0.55;
  const head = new THREE.Group(); head.position.set(0, 1.72, 0.85); body.add(head);
  part(new THREE.SphereGeometry(0.19, 16, 12), coat, 0, 0, 0, head).scale.set(0.9, 0.95, 1.05);
  const snout = part(new THREE.CylinderGeometry(0.1, 0.15, 0.3, 14), light, 0, -0.06, 0.22, head); snout.rotation.x = Math.PI / 2 + 0.25;
  part(new THREE.SphereGeometry(0.075, 10, 8), dark, 0, -0.1, 0.38, head);            // Nase
  addEyes(head, 0.11, 0.05, 0.13, 0.035);
  addCheeks(head, 0.13, -0.05, 0.12, 0.035);
  [-1, 1].forEach(s => {
    const ear = part(new THREE.ConeGeometry(0.05, 0.18, 8), coat, s * 0.17, 0.08, -0.05, head); ear.rotation.z = -s * 1.2; ear.scale.z = 0.6;
    // Geweih: Hauptstange mit drei Sprossen
    const antler = new THREE.Group(); antler.position.set(s * 0.09, 0.15, -0.04); antler.rotation.z = -s * 0.45; head.add(antler);
    part(new THREE.CylinderGeometry(0.025, 0.035, 0.45, 6), antlerM, 0, 0.22, 0, antler);
    [[0.12, 0.6, 0.14], [0.26, -0.5, 0.12], [0.38, 0.5, 0.1]].forEach(([y, rz, l]) => {
      const tine = part(new THREE.CylinderGeometry(0.018, 0.025, l, 6), antlerM, 0, y + l / 2 * Math.cos(rz), 0, antler);
      tine.position.x = s * Math.sin(Math.abs(rz)) * l / 2 * (rz > 0 ? 1 : -1); tine.rotation.z = -s * rz;
    });
  });
  // rosa Schal und Sattel für Alma
  const scarf = part(new THREE.TorusGeometry(0.21, 0.06, 8, 18), mat(0xff5fa2), 0, 1.5, 0.72); scarf.rotation.x = Math.PI / 2 - 0.55;
  const tail = part(new THREE.BoxGeometry(0.1, 0.32, 0.04), mat(0xff5fa2), 0.12, 1.32, 0.86); tail.rotation.z = 0.3;
  const blanket = part(new THREE.BoxGeometry(0.62, 0.05, 0.6), mat(0xb388ff), 0, 1.38, -0.05);
  part(new THREE.BoxGeometry(0.64, 0.02, 0.62), mat(0xffd34d, { metalness: 0.5 }), 0, 1.36, -0.05);
  const saddle = new THREE.Group(); saddle.position.set(0, 1.12, -0.02); body.add(saddle);

  const r = { root, body, head, legs, saddle, phase: 0, mode: "stand" };
  r.update = (dt, t) => {
    const fly = r.mode === "fly", walk = r.mode === "walk";
    r.phase += dt * (fly ? 7 : walk ? 8 : 0);
    legs.forEach((l, i) => {
      const p = r.phase + (l.front ? 0 : Math.PI) + (i % 2) * 0.4;
      if (fly) {                                   // fliegender Galopp: Beine strecken & anziehen
        l.hip.rotation.x = (l.front ? -0.6 : 0.6) + Math.sin(p) * 0.6;
        l.knee.rotation.x = (l.front ? 1 : -1) * (0.5 + Math.max(0, Math.cos(p)) * 0.8);
      } else if (walk) {
        l.hip.rotation.x = -Math.sin(p) * 0.5; l.knee.rotation.x = Math.max(0, Math.cos(p)) * 0.7 * (l.front ? 1 : -1);
      } else { l.hip.rotation.x *= 0.9; l.knee.rotation.x *= 0.9; }
    });
    body.position.y = fly ? Math.sin(r.phase) * 0.08 : walk ? Math.abs(Math.sin(r.phase)) * 0.04 : Math.sin(t * 2) * 0.01;
    head.rotation.x = fly ? -0.15 + Math.sin(r.phase) * 0.05 : Math.sin(t * 0.8) * 0.05;
    tail.rotation.x = Math.sin(t * 6) * 0.3;
  };
  scene.add(root);
  return r;
}

function makeSanta() {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body); root.scale.setScalar(1.15);
  const red = mat(0xd62839, { roughness: 0.6 }), white = mat(0xffffff, { roughness: 0.9 }), skin = mat(0xffd2b8);
  const black = mat(0x222222, { roughness: 0.4 }), gold = mat(0xffd34d, { metalness: 0.6, roughness: 0.3 });
  const p = (geo, m, x, y, z, parent = body) => { const o = mesh(geo, m); o.position.set(x, y, z); parent.add(o); return o; };
  p(new THREE.CylinderGeometry(0.5, 0.6, 1.0, 20), red, 0, 0.75, 0);
  p(new THREE.SphereGeometry(0.55, 20, 14), red, 0, 1.15, 0.02).scale.set(1, 0.9, 0.95);
  p(new THREE.TorusGeometry(0.58, 0.08, 8, 24), white, 0, 0.27, 0).rotation.x = Math.PI / 2;
  p(new THREE.TorusGeometry(0.57, 0.06, 8, 24), black, 0, 1.0, 0).rotation.x = Math.PI / 2;
  p(new THREE.BoxGeometry(0.2, 0.16, 0.06), gold, 0, 1.0, 0.58);
  p(new THREE.BoxGeometry(0.1, 0.8, 0.04), white, 0, 0.7, 0.55);
  [-1, 1].forEach(s => p(new THREE.CapsuleGeometry(0.1, 0.12, 4, 8), black, s * 0.2, 0.15, 0.1).rotation.x = Math.PI / 2);
  const head = new THREE.Group(); head.position.y = 1.85; body.add(head);
  p(new THREE.SphereGeometry(0.3, 18, 14), skin, 0, 0, 0, head);
  p(new THREE.SphereGeometry(0.3, 16, 12), white, 0, -0.2, 0.1, head).scale.set(1, 1.1, 0.7);      // Bart
  [-1, 1].forEach(s => { const m = p(new THREE.SphereGeometry(0.1, 10, 8), white, s * 0.09, -0.05, 0.27, head); m.scale.set(1.3, 0.6, 0.7); });
  p(new THREE.SphereGeometry(0.06, 10, 8), mat(0xff9e9e), 0, 0.02, 0.3, head);
  addEyes(head, 0.1, 0.1, 0.25, 0.03);
  addCheeks(head, 0.18, 0.02, 0.22, 0.05);
  const hat = new THREE.Group(); hat.position.y = 0.22; hat.rotation.z = 0.25; head.add(hat);
  p(new THREE.ConeGeometry(0.28, 0.55, 16), red, 0, 0.27, 0, hat);
  p(new THREE.TorusGeometry(0.28, 0.07, 8, 20), white, 0, 0, 0, hat).rotation.x = Math.PI / 2;
  p(new THREE.SphereGeometry(0.09, 10, 8), white, 0, 0.56, 0, hat);
  const arms = [-1, 1].map(s => {
    const sh = new THREE.Group(); sh.position.set(s * 0.55, 1.35, 0); body.add(sh);
    p(new THREE.CapsuleGeometry(0.11, 0.4, 4, 8), red, 0, -0.28, 0, sh);
    p(new THREE.TorusGeometry(0.11, 0.04, 6, 12), white, 0, -0.5, 0, sh).rotation.x = Math.PI / 2;
    p(new THREE.SphereGeometry(0.1, 10, 8), black, 0, -0.6, 0, sh);
    sh.rotation.z = s * 0.35; return sh;
  });
  const sa = { root, update: (dt, t) => {
    arms[1].rotation.z = 0.35 + (sa.wave ? 2.2 + Math.sin(t * 8) * 0.3 : 0);
    body.rotation.z = Math.sin(t * 1.5) * 0.03;
    head.rotation.y = Math.sin(t * 0.7) * 0.2;
  }, wave: true };
  scene.add(root);
  return sa;
}

function makeElf(tunic, hatColor) {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body);
  const skin = mat(0xffdcc6), green = mat(tunic), hatM = mat(hatColor);
  const p = (geo, m, x, y, z, parent = body) => { const o = mesh(geo, m); o.position.set(x, y, z); parent.add(o); return o; };
  p(new THREE.ConeGeometry(0.28, 0.55, 14), green, 0, 0.42, 0);
  p(new THREE.TorusGeometry(0.17, 0.035, 6, 14), mat(0x5a3a22), 0, 0.45, 0).rotation.x = Math.PI / 2;
  [-1, 1].forEach(s => { const f = p(new THREE.SphereGeometry(0.07, 8, 6), mat(0x8b5a2b), s * 0.1, 0.06, 0.06); f.scale.set(0.8, 0.6, 1.6); });
  const head = new THREE.Group(); head.position.y = 0.85; body.add(head);
  p(new THREE.SphereGeometry(0.17, 16, 12), skin, 0, 0, 0, head);
  addEyes(head, 0.06, 0.02, 0.15, 0.025);
  addSmile(head, -0.05, 0.15, 0.04);
  addCheeks(head, 0.1, -0.03, 0.13, 0.03);
  [-1, 1].forEach(s => { const e = p(new THREE.ConeGeometry(0.04, 0.16, 6), skin, s * 0.18, 0.03, 0, head); e.rotation.z = -s * 1.3; });
  const hat = new THREE.Group(); hat.position.y = 0.1; head.add(hat);
  p(new THREE.ConeGeometry(0.17, 0.42, 12), hatM, 0, 0.2, -0.04, hat).rotation.x = -0.4;
  p(new THREE.SphereGeometry(0.05, 8, 6), mat(0xffd34d, { metalness: 0.6 }), 0, 0.36, -0.22, hat);
  const arms = [-1, 1].map(s => { const sh = new THREE.Group(); sh.position.set(s * 0.14, 0.62, 0); body.add(sh);
    p(new THREE.CapsuleGeometry(0.04, 0.2, 4, 6), green, 0, -0.12, 0, sh); p(new THREE.SphereGeometry(0.045, 8, 6), skin, 0, -0.26, 0, sh); return sh; });
  const e = { root, cheer: 0, seed: Math.random() * 6 };
  e.update = (dt, t) => {
    const k = t * 6 + e.seed;
    arms.forEach((a, i) => { a.rotation.x = -1.0 + Math.sin(k + i * Math.PI) * 0.4 * (1 - e.cheer); a.rotation.z = (i ? 1 : -1) * (0.2 + e.cheer * 2.4); });
    body.position.y = e.cheer ? Math.abs(Math.sin(t * 7 + e.seed)) * 0.25 : Math.abs(Math.sin(k)) * 0.02;
  };
  root.scale.setScalar(1.2);
  scene.add(root);
  return e;
}

function makePresent(color, ribbon, size) {
  const g = new THREE.Group();
  const box = mesh(new THREE.BoxGeometry(size, size * 0.8, size), mat(0xc9a36b, { roughness: 0.9 }));
  box.position.y = size * 0.4; g.add(box);
  const deco = new THREE.Group(); deco.visible = false; g.add(deco);
  const rm = mat(ribbon, { roughness: 0.4 });
  [[size * 1.02, size * 0.82, size * 0.18], [size * 0.18, size * 0.82, size * 1.02]].forEach(([w, h, d]) => {
    const r = mesh(new THREE.BoxGeometry(w, h, d), rm, false); r.position.y = size * 0.4; deco.add(r);
  });
  [-1, 1].forEach(s => { const b = mesh(new THREE.TorusGeometry(size * 0.15, size * 0.05, 6, 12), rm, false); b.position.set(s * size * 0.12, size * 0.85, 0); b.rotation.y = Math.PI / 2; b.rotation.x = s * 0.5; deco.add(b); });
  return { g, box, deco, color, pop: 0 };
}

// =====================================================================
//  Winterwelt für den Flug (drei Abschnitte, die immer wieder nach vorne wandern)
// =====================================================================
const winter = new THREE.Group(); winter.visible = false; scene.add(winter);
const TILE = 300;
const tiles = [];
const snowTex = canvasTexture(256, (g, S) => {
  g.fillStyle = "#e9eef8"; g.fillRect(0, 0, S, S);
  for (let i = 0; i < 900; i++) { g.fillStyle = `rgba(${rand(190, 230)}, ${rand(205, 235)}, 255, 0.35)`; g.beginPath(); g.arc(Math.random() * S, Math.random() * S, rand(2, 10), 0, Math.PI * 2); g.fill(); }
}, 30, 30);
// Hügel – in der Mitte (wo man fliegt und landet) bleibt ein flaches Schneetal
const hillH = (x, z) => Math.max(0, (noise3(x * 0.02, 0, z * 0.02) * 4 + noise3(x * 0.06, 1, z * 0.05) * 1.2 + Math.abs(x) * 0.04) * Math.min(1, Math.max(0, (Math.abs(x) - 8) / 20)));
function makeTile(k) {
  const g = new THREE.Group();
  const geo = new THREE.PlaneGeometry(260, TILE, 52, 60); geo.rotateX(-Math.PI / 2);
  const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) p.setY(i, hillH(p.getX(i), p.getZ(i)));
  geo.computeVertexNormals();
  const ground = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ map: snowTex, roughness: 0.9 }));
  ground.receiveShadow = true; g.add(ground);
  // verschneite Tannen (Instancing → wenig Rechenarbeit)
  const N = 260;
  const parts = [[new THREE.CylinderGeometry(0.15, 0.22, 1, 6).translate(0, 0.5, 0), 0x6b4a32],
                 [new THREE.ConeGeometry(1.5, 2.2, 8).translate(0, 2.0, 0), 0x2f7a55],
                 [new THREE.ConeGeometry(1.1, 1.8, 8).translate(0, 3.0, 0), 0x358a60],
                 [new THREE.ConeGeometry(0.45, 0.8, 8).translate(0, 3.85, 0), 0xffffff]];
  const insts = parts.map(([geo2, c]) => { const m = new THREE.InstancedMesh(geo2, mat(c, { flatShading: true }), N); m.castShadow = true; g.add(m); return m; });
  const d = new THREE.Object3D();
  for (let i = 0; i < N; i++) {
    let x = rand(-120, 120); if (Math.abs(x) < 6) x += 12 * Math.sign(x || 1);
    const z = rand(-TILE / 2, TILE / 2);
    d.position.set(x, hillH(x, z) - 0.2, z); d.scale.setScalar(rand(0.8, 2)); d.rotation.y = Math.random() * 6; d.updateMatrix();
    insts.forEach(m => m.setMatrixAt(i, d.matrix));
  }
  // zugefrorene Seen und kleine Häuschen mit warmem Licht
  for (let i = 0; i < 3; i++) {
    const lake = new THREE.Mesh(new THREE.CircleGeometry(rand(6, 12), 24), mat(0xbfe3ff, { roughness: 0.15, metalness: 0.2 }));
    lake.rotation.x = -Math.PI / 2; const lx = rand(-50, 50), lz = rand(-TILE / 2, TILE / 2);
    lake.position.set(lx, hillH(lx, lz) + 0.05, lz); g.add(lake);
  }
  for (let i = 0; i < 4; i++) {
    const h = new THREE.Group(), hx = rand(-70, 70) || 20, hz = rand(-TILE / 2, TILE / 2);
    const wall = mesh(new THREE.BoxGeometry(3, 2.2, 3), mat(0xa86b45)); wall.position.y = 1.1; h.add(wall);
    const roof = mesh(new THREE.ConeGeometry(2.6, 1.6, 4), mat(0xffffff)); roof.position.y = 3; roof.rotation.y = Math.PI / 4; h.add(roof);
    const win = new THREE.Mesh(new THREE.PlaneGeometry(0.8, 0.8), new THREE.MeshBasicMaterial({ color: 0xffd27a })); win.position.set(0, 1.2, 1.51); h.add(win);
    h.position.set(hx, hillH(hx, hz), hz); h.rotation.y = Math.random() * 6; g.add(h);
  }
  g.position.set(CH2_X, 0, -k * TILE + TILE / 2 - 20);
  winter.add(g);
  return g;
}
for (let k = 0; k < 3; k++) tiles.push(makeTile(k));

// Sterne am Himmel (bewegen sich mit dem Himmel mit)
const nightStars = (function () {
  const N = 700, pos = [];
  for (let i = 0; i < N; i++) {
    const a = Math.random() * Math.PI * 2, e = rand(0.15, 1.3);
    pos.push(Math.cos(a) * Math.cos(e) * 150, Math.sin(e) * 150, Math.sin(a) * Math.cos(e) * 150);
  }
  const g = new THREE.BufferGeometry(); g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  const pts = new THREE.Points(g, new THREE.PointsMaterial({ color: 0xffffff, size: 1.6, sizeAttenuation: false, fog: false, transparent: true, opacity: 0.85 }));
  pts.visible = false; sky.add(pts);
  return pts;
})();
// Schneeflocken rund um die Kamera
const snowfall = (function () {
  const N = 1500, pos = new Float32Array(N * 3);
  for (let i = 0; i < N; i++) { pos[i * 3] = rand(-30, 30); pos[i * 3 + 1] = rand(-15, 20); pos[i * 3 + 2] = rand(-40, 20); }
  const g = new THREE.BufferGeometry(); g.setAttribute("position", new THREE.BufferAttribute(pos, 3));
  const pts = new THREE.Points(g, new THREE.PointsMaterial({ color: 0xffffff, size: 0.12, transparent: true, opacity: 0.9 }));
  pts.visible = false; scene.add(pts);
  return pts;
})();

// Abenddämmerung mit Sonnenuntergang einschalten
function setDusk() {
  sky.material.uniforms.top.value.set(0x2a2466);
  HORIZON.set(0xff9a76);                      // gleicher Farbwert für Himmel-Horizont und Nebel
  sky.material.uniforms.sunDir.value.set(0.15, 0.05, -1).normalize();
  scene.fog.near = 50; scene.fog.far = 170;
  sun.color.set(0xffa66b); sun.intensity = 1.6;
  scene.children.forEach(o => { if (o.isHemisphereLight) { o.color.set(0x9c8cff); o.groundColor.set(0x6a4a7a); o.intensity = 0.8; } });
  renderer.toneMappingExposure = 1.1;
  nightStars.visible = true; snowfall.visible = true; winter.visible = true;
}

// =====================================================================
//  Figuren für Kapitel 2 & das Nordpol-Dorf
// =====================================================================
const sven = makeReindeer();
sven.root.visible = false;
let santa = null, elves = [];

function buildVillage(cx, cz) {
  const v = new THREE.Group(); v.position.set(cx, 0, cz); scene.add(v);
  const ground = mesh(new THREE.CircleGeometry(40, 40), new THREE.MeshStandardMaterial({ map: snowTex, roughness: 0.9 }), false);
  ground.rotation.x = -Math.PI / 2; ground.position.y = 0.05; v.add(ground);
  // Nordpol-Stange mit Streifen
  const pole = mesh(new THREE.CylinderGeometry(0.25, 0.25, 6, 16), mat(0xffffff, { map: stripeTex })); pole.position.set(-6, 3, -4); v.add(pole);
  const ball = mesh(new THREE.SphereGeometry(0.5, 16, 12), mat(0xffd34d, { metalness: 0.6, roughness: 0.25 })); ball.position.set(-6, 6.3, -4); v.add(ball);
  // Haus des Weihnachtsmanns
  const house = new THREE.Group(); house.position.set(4, 0, -9); v.add(house);
  const wallM = mat(0xffffff, { map: barkTex.clone(), roughness: 1 }); wallM.map.needsUpdate = true; wallM.map.repeat.set(4, 2);
  const walls = mesh(new THREE.BoxGeometry(8, 4, 6), wallM); walls.position.y = 2; house.add(walls);
  const roofGeo = new THREE.CylinderGeometry(0.01, 5.2, 3, 4, 1); roofGeo.rotateY(Math.PI / 4); roofGeo.scale(1.2, 1, 0.9);
  const roof = mesh(roofGeo, mat(0xd62839)); roof.position.y = 5.5; house.add(roof);
  const snowRoof = mesh(roofGeo.clone(), mat(0xffffff)); snowRoof.position.y = 5.75; snowRoof.scale.set(0.75, 0.6, 0.75); house.add(snowRoof);
  const door = mesh(new THREE.PlaneGeometry(1.4, 2.4), mat(0x2e7d32), false); door.position.set(0, 1.2, 3.01); house.add(door);
  [-2.6, 2.6].forEach(x => { const w = new THREE.Mesh(new THREE.PlaneGeometry(1.3, 1.2), new THREE.MeshBasicMaterial({ color: 0xffd27a })); w.position.set(x, 2.3, 3.01); house.add(w); });
  const chimney = mesh(new THREE.BoxGeometry(0.9, 2, 0.9), mat(0xb5462e)); chimney.position.set(2, 6.2, 0); house.add(chimney);
  ch2.chimney = new THREE.Vector3(cx + 6, 7.5, cz - 9);
  // großer Weihnachtsbaum mit Lichterkette und Stern
  const tree = new THREE.Group(); tree.position.set(-4, 0, 3); v.add(tree);
  const tTrunk = mesh(new THREE.CylinderGeometry(0.3, 0.4, 1.2, 8), mat(0x6b4a32)); tTrunk.position.y = 0.6; tree.add(tTrunk);
  [[2.4, 2.6, 2.2], [1.9, 2.2, 3.6], [1.3, 1.8, 4.8]].forEach(([r, h, y]) => { const c = mesh(new THREE.ConeGeometry(r, h, 12), mat(0x2f7a55)); c.position.y = y; tree.add(c); });
  ch2.lights = [];
  for (let i = 0; i < 40; i++) {
    const y = 1.4 + i * 0.11, r = (1 - (y - 1.2) / 5) * 2.2, a = i * 1.1;
    const l = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6), new THREE.MeshBasicMaterial({ color: RAINBOW[i % 7] }));
    l.position.set(Math.cos(a) * r, y, Math.sin(a) * r); tree.add(l); ch2.lights.push(l);
  }
  const top = emojiSprite("⭐", 1.2); top.position.y = 6.2; tree.add(top);
  // Werkbank mit Geschenken
  const table = mesh(new THREE.BoxGeometry(5, 0.2, 1.8), mat(0x8b5a2b)); table.position.set(3, 1, 1); v.add(table);
  [[-2.2, -0.7], [2.2, -0.7], [-2.2, 0.7], [2.2, 0.7]].forEach(([x, z]) => { const l = mesh(new THREE.BoxGeometry(0.15, 1, 0.15), mat(0x6b4a32)); l.position.set(3 + x, 0.5, 1 + z); v.add(l); });
  const colors = [[0xff5fa2, 0xffd34d], [0x4dd0ff, 0xffffff], [0x7ed957, 0xff3b3b], [0xb388ff, 0xffd34d], [0xff9f1c, 0x2b5cd6]];
  ch2.presents = colors.map(([c, r], i) => {
    const pr = makePresent(c, r, 0.55 + (i % 2) * 0.15);
    pr.g.position.set(1.2 + i * 0.9, 1.1, 1); v.add(pr.g); return pr;
  });
  // Weihnachtsmann & Elfen
  santa = makeSanta(); santa.root.position.set(cx - 1, 0, cz - 2.5); santa.root.rotation.y = 0.2;
  elves = [[0x2e9d4f, 0xd62839], [0xd62839, 0x2e9d4f], [0x2e9d4f, 0xffffff]].map(([t, h], i) => {
    const e = makeElf(t, h); e.root.position.set(cx + 1.6 + i * 1.5, 0, cz - 0.4); e.root.rotation.y = 0; return e;
  });
  // ein Schneemann und Zuckerstangen
  const sm = new THREE.Group(); sm.position.set(9, 0, 4); v.add(sm);
  [[0.9, 0.9], [0.65, 2.2], [0.45, 3.15]].forEach(([r, y]) => { const b = mesh(new THREE.SphereGeometry(r, 16, 12), mat(0xffffff)); b.position.y = y; sm.add(b); });
  const carrot = mesh(new THREE.ConeGeometry(0.08, 0.4, 8), mat(0xff8c1a)); carrot.position.set(0, 3.15, 0.55); carrot.rotation.x = Math.PI / 2; sm.add(carrot);
  addEyes(sm, 0.15, 3.3, 0.4, 0.05);
  const hatS = mesh(new THREE.CylinderGeometry(0.3, 0.3, 0.5, 14), mat(0x222222)); hatS.position.y = 3.75; sm.add(hatS);
  candyCane(cx - 8, cz + 6, 1); candyCane(cx + 8, cz - 2, 1.1); candyCane(cx - 10, cz - 6, 0.9);
  return v;
}

// =====================================================================
//  Anzeige: der Lösungssatz
// =====================================================================
const sentenceEl = document.getElementById("sentence");
function buildSentenceHud() {
  sentenceEl.innerHTML = "";
  ch2.slots = [];
  SENTENCE.split(" ").forEach(word => {
    const w = document.createElement("div"); w.className = "word";
    [...word].forEach(ch => {
      const s = document.createElement("span"); s.textContent = ""; w.appendChild(s);
      ch2.slots.push({ ch, el: s, found: false });
    });
    sentenceEl.appendChild(w);
  });
}

// Buchstaben als leuchtende, goldene Bilder
const letterTexCache = {};
function letterSprite(ch) {
  if (!letterTexCache[ch]) letterTexCache[ch] = canvasTexture(128, (g, S) => {
    g.font = `bold ${S * 0.78}px "Comic Sans MS","Chalkboard SE",sans-serif`; g.textAlign = "center"; g.textBaseline = "middle";
    const grd = g.createLinearGradient(0, S * 0.15, 0, S * 0.85); grd.addColorStop(0, "#fff6b0"); grd.addColorStop(1, "#ffb300");
    g.lineWidth = 10; g.strokeStyle = "#ff5fa2"; g.strokeText(ch, S / 2, S / 2 + 6);
    g.fillStyle = grd; g.fillText(ch, S / 2, S / 2 + 6);
  });
  const grp = new THREE.Group();
  const glow = new THREE.Sprite(glowMat); glow.scale.setScalar(4); grp.add(glow);
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: letterTexCache[ch], transparent: true, depthWrite: false })); s.scale.setScalar(2.6); grp.add(s);
  scene.add(grp);
  return grp;
}
function spawnLetter(slotIndex, z) {
  const grp = letterSprite(ch2.slots[slotIndex].ch);
  grp.position.set(CH2_X + rand(-9, 9), rand(6, 14), z);
  ch2.letters.push({ grp, slot: slotIndex, baseY: grp.position.y, seed: Math.random() * 6 });
}
function nextMissingSlot(after) {
  for (let i = after; i < ch2.slots.length; i++) if (!ch2.slots[i].found && !ch2.letters.some(l => l.slot === i)) return i;
  return -1;
}

// =====================================================================
//  Ablauf von Kapitel 2
// =====================================================================
const fadeEl = document.getElementById("fade");
function fade(on) { fadeEl.style.opacity = on ? 1 : 0; }
function showCaption(text, speak = true) {
  captionEl.style.display = "block"; captionEl.textContent = text;
  captionEl.classList.remove("show"); void captionEl.offsetWidth; captionEl.classList.add("show");
  if (speak) Voice.speak(text);
}
function hideCaption() { captionEl.classList.remove("show"); captionEl.style.display = "none"; }

// Start aus dem Trolldorf (nach der Rettung)
function startChapter2() {
  document.getElementById("end").style.display = "none";
  game.chapter = 2; game.state = "chapter2"; ch2.state = "arrive"; ch2.t = 0;
  document.getElementById("hudWrap").style.display = "none";
  sven.root.visible = true; sven.mode = "fly";
  ch2.from = new THREE.Vector3(uni.pos.x - 14, 14, uni.pos.z - 12);
  ch2.land = alma.root.position.clone().add(new THREE.Vector3(2.2, 0, 0)).setY(0);
  sven.root.position.copy(ch2.from);
  Sound.play("schlitten");
}

// Direkt-Start vom Titelbild
function startChapter2Direct() {
  game.chapter = 2; game.state = "chapter2";
  document.getElementById("title").style.display = "none";
  mountAlma();
  beginFlight();
}

function mountAlma() {
  sven.saddle.add(alma.root);
  alma.root.position.set(0, 0, 0); alma.root.rotation.set(0, 0, 0); alma.root.scale.setScalar(0.85);
  alma.root.visible = true; alma.walk = 0; alma.cheer = 0;
}

function beginFlight() {
  setDusk();
  ch2.state = "flight"; ch2.t = 0;
  ch2.pos.set(CH2_X, 9, 0); ch2.vel.set(0, 0, 0);
  sven.root.visible = true; sven.mode = "fly";
  buildSentenceHud();
  document.getElementById("hudWrap").style.display = "none";
  document.getElementById("ch2Hud").style.display = "block";
  ch2.spawnZ = -30;
  for (let i = 0; i < 4; i++) { spawnLetter(i, ch2.spawnZ); ch2.spawnZ -= 32; }
  ch2.nextIndex = 4;
  camera.position.set(CH2_X, 13, 12);
  Sound.play("schlitten");
  fade(false);
  showCaption("Und so flogen Alma und Sven hinauf in den Abendhimmel. Sammle alle goldenen Buchstaben ein!");
  setTimeout(() => { if (ch2.state === "flight") hideCaption(); }, 9000);
}

function landAtPole() {
  ch2.state = "landing"; ch2.t = 0;
  const vz = ch2.pos.z - 70;
  ch2.village = buildVillage(CH2_X, vz);
  ch2.villageZ = vz;
  ch2.from = ch2.pos.clone();
  ch2.land = new THREE.Vector3(CH2_X - 3, 0, vz + 5);
  showCaption("Ihr habt alle Buchstaben gefunden! Seht ihr dort unten die Lichter? Das ist der Nordpol.");
  Sound.play("fest");
}

function poleDialog() {
  ch2.state = "pole";
  hideCaption();
  const S = t => ({ ...SANTA, text: t });
  const steps = [
    S("Ho, ho, ho! Willkommen am Nordpol, Prinzessin Alma. Und hallo, Sven!"),
    S("Ihr habt alle Buchstaben von meinem Weihnachtsgruß gefunden. Lies doch einmal vor, was dort steht."),
    { ...ALMA, text: "Wir wünschen frohe Weihnachten!" },
    S("Wunderbar! Jetzt kann ich meine Weihnachtskarten an alle Kinder schicken."),
    S("Meine Elfen haben aber noch viel zu tun. Hilfst du ihnen beim Einpacken der Geschenke? Zu jedem Geschenk gibt es ein kleines Rätsel."),
    { ...ALMA, text: "Oh ja, sehr gern!" },
  ];
  ELF_RIDDLES.forEach((r, i) => {
    const elf = ELVES[i % 3];
    steps.push({ ...elf, riddle: r, onCorrect: [{ ...elf, text: r.thanks }, { fn: () => wrapPresent(i) }] });
  });
  steps.push(S("Ho, ho, ho! Alle Geschenke sind eingepackt. Ihr seid die allerbesten Helfer!"));
  steps.push(S("Als Dankeschön schenke ich dir ein Malbuch. Viel Freude beim Ausmalen!"));
  say(steps, () => openPaint(true));
}

const ELF_RIDDLES = [
  { q: "Ich bin grün, stehe im Wohnzimmer und trage Kugeln und Lichter. Wer bin ich?",
    options: ["🌲 Der Weihnachtsbaum", "🌵 Ein Kaktus", "🥦 Ein Brokkoli"], answer: 0,
    thanks: "Richtig, der Weihnachtsbaum! Schau, das erste Geschenk ist eingepackt." },
  { q: "Ich bin weiß und kalt und falle im Winter leise vom Himmel. Was bin ich?",
    options: ["🌧️ Der Regen", "🏖️ Der Sand", "❄️ Der Schnee"], answer: 2,
    thanks: "Ganz genau, der Schnee! Schon ist das nächste Geschenk fertig." },
  { q: "Ein Elf hat zwei Geschenke. Ein anderer Elf hat drei Geschenke. Wie viele Geschenke sind das zusammen?",
    options: ["fünf Geschenke", "vier Geschenke", "sechs Geschenke"], answer: 0, ask: "Sind es fünf, vier oder sechs Geschenke?",
    thanks: "Fünf Geschenke, das stimmt! Du kannst aber gut rechnen." },
  { q: "Welche Farbe hat der Mantel vom Weihnachtsmann?",
    options: ["💙 Blau", "❤️ Rot", "💚 Grün"], answer: 1,
    thanks: "Richtig, rot! So rot wie die Bänder an diesem Geschenk." },
  { q: "Wer zieht den Schlitten vom Weihnachtsmann durch die Luft?",
    options: ["🐄 Die Kühe", "🦌 Die Rentiere", "🐟 Die Fische"], answer: 1,
    thanks: "Die Rentiere, so wie unser Sven! Das letzte Geschenk ist eingepackt." },
];

function wrapPresent(i) {
  const pr = ch2.presents[i];
  if (!pr) return;
  pr.box.material = mat(pr.color, { roughness: 0.5 });
  pr.deco.visible = true; pr.pop = 1;
  const wp = new THREE.Vector3(); pr.g.getWorldPosition(wp);
  for (let k = 0; k < 25; k++) spawnParticle(wp.clone().setY(wp.y + 0.6), RAINBOW[k % 7], new THREE.Vector3(rand(-2, 2), rand(2, 4), rand(-2, 2)), 1, 1.3, 6);
  elves.forEach(e => e.cheer = 1); setTimeout(() => elves.forEach(e => e.cheer = 0), 1500);
  Sound.magic();
}

// =====================================================================
//  jedes Bild
// =====================================================================
const camGoal2 = { pos: new THREE.Vector3(), look: new THREE.Vector3() };
function updateChapter2(dt, t) {
  ch2.t += dt;
  sven.update(dt, t);
  if (santa) santa.update(dt, t);
  elves.forEach(e => e.update(dt, t));

  if (ch2.state === "arrive") {                     // Sven landet im Trolldorf
    animateUnicorn(dt, t);
    const f = smooth(ch2.t / 4);
    sven.root.position.lerpVectors(ch2.from, ch2.land, f);
    sven.root.position.y = (1 - f) * ch2.from.y + Math.sin(f * Math.PI) * 2;
    sven.root.rotation.y = headingTo(ch2.from, ch2.land);
    sven.mode = f < 1 ? "fly" : "stand";
    if (f >= 1) sven.root.rotation.y += angleDiff(sven.root.rotation.y, headingTo(ch2.land, alma.root.position)) * dt * 3;
    camGoal2.pos.copy(alma.root.position).add(new THREE.Vector3(1, 3.5, 9)); camGoal2.look.copy(alma.root.position).setY(1.5).add(new THREE.Vector3(1, 0, 0));
    camera.position.lerp(camGoal2.pos, dt * 2); camera.lookAt(camGoal2.look);
    if (ch2.t > 4.3 && !ch2.talked) {
      ch2.talked = true;
      say([
        { ...SVEN, text: "Hallo, Prinzessin Alma! Hallo, Rosi! Ich bin Sven, das Rentier vom Weihnachtsmann." },
        { ...SVEN, text: "Ein Wintersturm hat die Buchstaben vom großen Weihnachtsgruß davongeweht. Ohne sie kann der Weihnachtsmann seine Karten nicht schreiben." },
        { ...SVEN, text: "Prinzessin Alma, hilfst du mir, sie zu suchen? Steig auf meinen Rücken, dann fliegen wir zum Nordpol!" },
        { ...ALMA, text: "Oh ja, sehr gern!" },
        { ...ROSI, text: "Flieg nur, liebe Alma. Ich warte hier mit den Trollen auf dich." },
      ], () => { mountAlma(); ch2.state = "takeoff"; ch2.t = 0; ch2.from = sven.root.position.clone(); });
    }
  } else if (ch2.state === "takeoff") {             // Abflug in den Himmel
    animateUnicorn(dt, t);
    sven.mode = "fly";
    const dir = new THREE.Vector3(Math.sin(sven.root.rotation.y), 0, Math.cos(sven.root.rotation.y));
    sven.root.position.copy(ch2.from).addScaledVector(dir, ch2.t * ch2.t * 3).setY(ch2.t * ch2.t * 1.6);
    camera.lookAt(sven.root.position);
    if (ch2.t > 2.6) fade(true);
    if (ch2.t > 3.6) beginFlight();
  } else if (ch2.state === "flight") {
    flightUpdate(dt, t);
  } else if (ch2.state === "landing") {
    const f = smooth(ch2.t / 6);
    sven.root.position.lerpVectors(ch2.from, ch2.land, f);
    sven.root.position.y = ch2.from.y * (1 - f);
    sven.root.rotation.set(0, Math.PI, 0);
    sven.mode = f < 1 ? "fly" : "stand";
    camGoal2.pos.set(CH2_X + 6, 6 + (1 - f) * 8, ch2.villageZ + 16); camGoal2.look.set(CH2_X, 2, ch2.villageZ);
    camera.position.lerp(camGoal2.pos, dt * 1.5); camera.lookAt(camGoal2.look);
    if (f >= 1 && ch2.t > 6.5) {
      // Alma steigt ab und stellt sich neben Sven
      scene.add(alma.root); alma.root.scale.setScalar(1);
      alma.root.position.set(CH2_X - 1.2, 0, ch2.villageZ + 4.5); alma.root.rotation.y = Math.PI;
      sven.root.rotation.y = Math.PI - 0.4;
      poleDialog();
    }
  } else if (ch2.state === "pole" || ch2.state === "paint" || ch2.state === "done") {
    const a = Math.sin(t * 0.15) * 0.25;
    camGoal2.pos.set(CH2_X + Math.sin(a) * 13, 5.5, ch2.villageZ + 12 + Math.cos(a) * 2);
    camGoal2.look.set(CH2_X + 1, 1.6, ch2.villageZ);
    camera.position.lerp(camGoal2.pos, dt * 1.5); camera.lookAt(camGoal2.look);
    alma.root.rotation.y = headingTo(alma.root.position, santa.root.position);
    alma.cheer = game.state === "dialog" ? 0 : 0.5;
  }

  // Geschenke hüpfen beim Einpacken, Lichter blinken, Kamin raucht
  ch2.presents.forEach(p => { if (p.pop > 0) { p.pop = Math.max(0, p.pop - dt * 1.5); p.g.scale.setScalar(1 + Math.sin(p.pop * Math.PI) * 0.35); } });
  if (ch2.lights) ch2.lights.forEach((l, i) => l.visible = Math.sin(t * 3 + i) > -0.3);
  if (ch2.chimney && Math.random() < 0.3) spawnParticle(ch2.chimney, 0xdddddd, new THREE.Vector3(rand(-0.3, 0.3), 1.2, rand(-0.3, 0.3)), 2, 2.5, -0.2);

  // Himmel, Sonne und Schnee folgen der Kamera
  sky.position.copy(camera.position);
  sun.position.copy(sven.root.position).addScaledVector(new THREE.Vector3(0.3, 0.5, -1).normalize(), 40);
  sun.target.position.copy(sven.root.position);
  if (snowfall.visible) {
    snowfall.position.copy(camera.position);
    const p = snowfall.geometry.attributes.position;
    for (let i = 0; i < p.count; i++) {
      let y = p.getY(i) - dt * 2.2, x = p.getX(i) + Math.sin(t + i) * dt * 0.5;
      if (y < -15) y = 20;
      p.setXY(i, x, y);
    }
    p.needsUpdate = true;
  }
  // Winter-Abschnitte nach vorne versetzen
  tiles.forEach(tl => { if (tl.position.z - TILE / 2 > ch2.pos.z + 60) tl.position.z -= TILE * tiles.length; });
}

function flightUpdate(dt, t) {
  // Steuerung: links/rechts, hoch/runter, Shift = schneller
  const side = (keys.KeyD || keys.ArrowRight ? 1 : 0) - (keys.KeyA || keys.ArrowLeft ? 1 : 0) + touch.x;
  const up = (keys.KeyW || keys.ArrowUp || keys.Space ? 1 : 0) - (keys.KeyS || keys.ArrowDown ? 1 : 0) - touch.y;
  const speed = keys.ShiftLeft || keys.ShiftRight || touch.run ? 24 : 15;
  ch2.vel.x += (side * 11 - ch2.vel.x) * Math.min(1, dt * 3);
  ch2.vel.y += (up * 7 - ch2.vel.y) * Math.min(1, dt * 3);
  ch2.vel.z += (-speed - ch2.vel.z) * Math.min(1, dt * 2);
  ch2.pos.addScaledVector(ch2.vel, dt);
  ch2.pos.x = Math.max(CH2_X - 22, Math.min(CH2_X + 22, ch2.pos.x));
  ch2.pos.y = Math.max(4 + hillH(ch2.pos.x - CH2_X, 0), Math.min(24, ch2.pos.y));
  sven.root.position.copy(ch2.pos);
  sven.root.rotation.set(-ch2.vel.y * 0.03, Math.PI, -ch2.vel.x * 0.035);

  // Regenbogen-Glitzer hinter Sven
  if (Math.random() < 0.7) spawnParticle(ch2.pos.clone().add(new THREE.Vector3(rand(-0.4, 0.4), 1 + rand(0, 0.6), 1.2)),
    RAINBOW[Math.floor(Math.random() * 7)], new THREE.Vector3(0, rand(-0.3, 0.3), 2), 1, 1.2, 0);

  // Buchstaben: schweben, einsammeln, verpasste kommen weiter vorne wieder
  for (let i = ch2.letters.length - 1; i >= 0; i--) {
    const L = ch2.letters[i];
    L.grp.position.y = L.baseY + Math.sin(t * 2 + L.seed) * 0.4;
    const d = L.grp.position.distanceTo(sven.root.position.clone().setY(sven.root.position.y + 1.2));
    if (d < 2.6) {
      const slot = ch2.slots[L.slot];
      slot.found = true; slot.el.textContent = slot.ch; slot.el.classList.add("got");
      ch2.found++;
      for (let k = 0; k < 20; k++) spawnParticle(L.grp.position, k % 2 ? 0xffd34d : RAINBOW[k % 7], new THREE.Vector3(rand(-3, 3), rand(-1, 3), rand(-3, 3)), 0.8, 1.5, 2);
      Sound.star(ch2.found === ch2.slots.length);
      scene.remove(L.grp); ch2.letters.splice(i, 1);
      const n = nextMissingSlot(0);
      if (n >= 0) { ch2.spawnZ = Math.min(ch2.spawnZ, ch2.pos.z - 90); spawnLetter(n, ch2.spawnZ); ch2.spawnZ -= 32; }
    } else if (L.grp.position.z > ch2.pos.z + 8) {          // verpasst → weiter vorne neu
      ch2.spawnZ = Math.min(ch2.spawnZ, ch2.pos.z - 90);
      L.grp.position.set(CH2_X + rand(-9, 9), rand(6, 14), ch2.spawnZ); L.baseY = L.grp.position.y; ch2.spawnZ -= 32;
    }
  }
  if (ch2.found === ch2.slots.length && ch2.letters.length === 0) landAtPole();

  // Kamera hinter Sven
  camGoal2.pos.copy(ch2.pos).add(new THREE.Vector3(0, 3.2, 9));
  camera.position.lerp(camGoal2.pos, Math.min(1, dt * 4));
  camGoal2.look.copy(ch2.pos).add(new THREE.Vector3(0, 1.2, -8));
  camera.lookAt(camGoal2.look);
}

document.getElementById("ch2Btn").addEventListener("click", startChapter2);
document.getElementById("ch2Direct").addEventListener("click", () => { Sound.init(); startChapter2Direct(); });

// Sätze für die Märchenstimme
const CH2_VOICE_LINES = [
  "Und so flogen Alma und Sven hinauf in den Abendhimmel. Sammle alle goldenen Buchstaben ein!",
  "Ihr habt alle Buchstaben gefunden! Seht ihr dort unten die Lichter? Das ist der Nordpol.",
  "Hallo, Prinzessin Alma! Hallo, Rosi! Ich bin Sven, das Rentier vom Weihnachtsmann.",
  "Ein Wintersturm hat die Buchstaben vom großen Weihnachtsgruß davongeweht. Ohne sie kann der Weihnachtsmann seine Karten nicht schreiben.",
  "Prinzessin Alma, hilfst du mir, sie zu suchen? Steig auf meinen Rücken, dann fliegen wir zum Nordpol!",
  "Oh ja, sehr gern!",
  "Flieg nur, liebe Alma. Ich warte hier mit den Trollen auf dich.",
  "Ho, ho, ho! Willkommen am Nordpol, Prinzessin Alma. Und hallo, Sven!",
  "Ihr habt alle Buchstaben von meinem Weihnachtsgruß gefunden. Lies doch einmal vor, was dort steht.",
  "Wir wünschen frohe Weihnachten!",
  "Wunderbar! Jetzt kann ich meine Weihnachtskarten an alle Kinder schicken.",
  "Meine Elfen haben aber noch viel zu tun. Hilfst du ihnen beim Einpacken der Geschenke? Zu jedem Geschenk gibt es ein kleines Rätsel.",
  "Ho, ho, ho! Alle Geschenke sind eingepackt. Ihr seid die allerbesten Helfer!",
  "Als Dankeschön schenke ich dir ein Malbuch. Viel Freude beim Ausmalen!",
  ...ELF_RIDDLES.flatMap(r => [r.q, riddleAsk(r), r.thanks]),
  ...Object.values(NARRATOR),
];
