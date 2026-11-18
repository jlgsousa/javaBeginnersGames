// =====================================================================
//  chapter3.js – Capítulo 3: Alma voa de vassoura até Portugal,
//  à casa enorme da Avó e do Avô. Tudo sobre sentimentos e estar juntos.
//  (Kapitel 3 – komplett auf Portugiesisch aus Portugal)
// =====================================================================

const CH3_X = 8000;                               // Portugal liegt weit weg von den anderen Kapiteln
const HOUSE = new THREE.Vector3(CH3_X, 0, -2000); // das Innere des großen Hauses
const AVO_F = { name: "Avó", emoji: "👵", voice: "avoF", lang: "pt" };
const AVO_M = { name: "Avô", emoji: "👴", voice: "avoM", lang: "pt" };
const ALMA_PT = { name: "Alma", emoji: "👸", voice: "alma", lang: "pt" };
const NARR_PT = { name: "", emoji: "📖", voice: "pt", lang: "pt" };
const ch3 = { state: null, t: 0, pos: new THREE.Vector3(), vel: new THREE.Vector3(), hearts: [], heartCount: 0, spawnZ: 0,
  walls: [], frames: [], found: 0, allowTouch: false };
const HEARTS_NEEDED = 8;
const H = (dx, dz) => new THREE.Vector3(HOUSE.x + dx, 0, HOUSE.z + dz);   // Position im Haus

// =====================================================================
//  Die Avó und der Avô
// =====================================================================
function makeGrandparent(female) {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body);
  const skin = mat(0xf5cfb8, { roughness: 0.6 });
  const p = (geo, m, x, y, z, parent = body) => { const o = mesh(geo, m); o.position.set(x, y, z); parent.add(o); return o; };
  let headY;
  if (female) {
    const dress = mat(0xc9a0dc, { roughness: 0.6 });
    const pts = [[0.42, 0.04], [0.42, 0.2], [0.36, 0.6], [0.3, 0.95], [0.26, 1.2]].map(([r, y]) => new THREE.Vector2(r, y));
    body.add(mesh(new THREE.LatheGeometry(pts, 24), dress));
    const apron = p(new THREE.CylinderGeometry(0.3, 0.38, 0.75, 16, 1, true, -0.9, 1.8), mat(0xffffff, { side: THREE.DoubleSide }), 0, 0.55, 0.03);
    for (let i = 0; i < 10; i++) {    // Blümchen auf dem Kleid
      const f = mesh(new THREE.SphereGeometry(0.035, 6, 4), mat([0xff7eb9, 0xffe066, 0xffffff][i % 3]), false);
      const a = i / 10 * Math.PI * 2 + 0.3; f.position.set(Math.cos(a) * 0.4, 0.25 + (i % 3) * 0.1, Math.sin(a) * 0.4); body.add(f);
    }
    p(new THREE.TorusGeometry(0.15, 0.025, 6, 20), mat(0xffffff, { roughness: 0.2 }), 0, 1.25, 0.02).rotation.x = Math.PI / 2 - 0.2;   // Perlenkette
    headY = 1.47;
  } else {
    const trousers = mat(0x4a5568), shirt = mat(0x9fd3ff), vest = mat(0x8b5a2b, { roughness: 0.7 });
    [-1, 1].forEach(s => { p(new THREE.CylinderGeometry(0.11, 0.12, 0.8, 10), trousers, s * 0.14, 0.42, 0); p(new THREE.SphereGeometry(0.11, 10, 6), mat(0x3d2b22), s * 0.14, 0.05, 0.05).scale.set(1, 0.6, 1.5); });
    p(new THREE.CylinderGeometry(0.3, 0.28, 0.75, 16), shirt, 0, 1.18, 0);
    p(new THREE.SphereGeometry(0.32, 16, 12), vest, 0, 1.05, 0.02).scale.set(1, 1.15, 0.95);           // Bäuchlein mit Weste
    [0.95, 1.1, 1.25].forEach(y => p(new THREE.SphereGeometry(0.025, 6, 4), mat(0xffd34d), 0, y, 0.33));
    headY = 1.78;
  }
  const head = new THREE.Group(); head.position.y = headY; body.add(head);
  const R = female ? 0.26 : 0.27;
  p(new THREE.SphereGeometry(R, 20, 16), skin, 0, 0, 0, head);
  p(new THREE.SphereGeometry(R * 0.18, 8, 6), skin, 0, -R * 0.05, R * 0.98, head);                     // Nase
  const rig = makeFaceRig(head, R, { browColor: female ? 0x9a9aa6 : 0xdedede });
  // Brille
  const glassM = mat(female ? 0xb03060 : 0x333333, { metalness: 0.4, roughness: 0.3 });
  [-1, 1].forEach(s => { const g = p(new THREE.TorusGeometry(R * 0.2, R * 0.025, 6, 16), glassM, s * R * 0.36, R * 0.12, R * 0.97, head); });
  p(new THREE.BoxGeometry(R * 0.22, R * 0.03, R * 0.03), glassM, 0, R * 0.14, R * 1.0, head);
  if (female) {
    const hair = mat(0xdcdce6, { roughness: 0.7 });
    const cap = p(new THREE.SphereGeometry(R * 1.06, 18, 12, 0, Math.PI * 2, 0, Math.PI * 0.55), hair, 0, R * 0.04, -R * 0.06, head); cap.rotation.x = -0.3;
    p(new THREE.SphereGeometry(R * 0.42, 12, 10), hair, 0, R * 0.55, -R * 0.75, head);                // Dutt
    [-1, 1].forEach(s => p(new THREE.SphereGeometry(R * 0.07, 8, 6), mat(0xffd34d, { metalness: 0.6 }), s * R * 0.98, -R * 0.25, 0, head));   // Ohrringe
  } else {
    const hair = mat(0xf2f2f2, { roughness: 0.8 });
    [-1, 1].forEach(s => p(new THREE.SphereGeometry(R * 0.4, 10, 8), hair, s * R * 0.82, R * 0.05, -R * 0.25, head).scale.set(0.6, 0.8, 1.2));
    [-1, 1].forEach(s => { const m = p(new THREE.SphereGeometry(R * 0.2, 10, 8), hair, s * R * 0.17, -R * 0.2, R * 0.92, head); m.scale.set(1.3, 0.55, 0.7); });   // Schnurrbart
    const capM = mat(0x2f3b6b, { roughness: 0.8 });
    p(new THREE.CylinderGeometry(R * 1.05, R * 1.08, R * 0.3, 18), capM, 0, R * 0.62, -R * 0.05, head);  // Schiebermütze
    p(new THREE.BoxGeometry(R * 1.2, R * 0.06, R * 0.6), capM, 0, R * 0.5, R * 0.75, head);
  }
  const sleeve = female ? mat(0xc9a0dc) : mat(0x9fd3ff);
  const shoulderY = female ? 1.18 : 1.45, shoulderX = female ? 0.28 : 0.34;
  const arms = [-1, 1].map(s => {
    const sh = new THREE.Group(); sh.position.set(s * shoulderX, shoulderY, 0); body.add(sh);
    p(new THREE.CapsuleGeometry(0.07, 0.42, 4, 8), sleeve, 0, -0.28, 0, sh);
    p(new THREE.SphereGeometry(0.075, 8, 6), skin, 0, -0.55, 0, sh);
    sh.userData.s = s; return sh;
  });
  const c = { root, body, head, arms, rig, female, walk: 0, phase: 0, wave: 0, pose: null, baseRotY: 0 };
  c.update = (dt, t) => {
    c.phase += dt * 8 * c.walk;
    body.position.y = Math.abs(Math.sin(c.phase)) * 0.04 * c.walk;
    body.rotation.z = Math.sin(t * 1.3 + (female ? 0 : 1)) * 0.02;
    arms.forEach((a, i) => {
      const s = a.userData.s;
      a.rotation.x = Math.sin(c.phase) * 0.5 * c.walk * s;
      a.rotation.z = s * (0.2 + (i === 1 ? c.wave * (2.4 + Math.sin(t * 8) * 0.3) : 0));
    });
    head.rotation.y = Math.sin(t * 0.6 + (female ? 0 : 2)) * 0.12;
  };
  root.traverse(o => { if (o.isMesh) o.castShadow = true; });
  scene.add(root);
  return c;
}

// =====================================================================
//  Hexenhut, Umhang & Besen für Alma
// =====================================================================
const witchHat = (function () {
  const g = new THREE.Group(), purple = mat(0x6a3fb5, { roughness: 0.6 });
  const cone = mesh(new THREE.ConeGeometry(0.17, 0.42, 16), purple); cone.position.y = 0.24; cone.rotation.z = 0.15; g.add(cone);
  const brim = mesh(new THREE.CylinderGeometry(0.3, 0.3, 0.02, 24), purple); brim.position.y = 0.04; g.add(brim);
  const band = mesh(new THREE.TorusGeometry(0.165, 0.025, 6, 18), mat(0xffd34d, { metalness: 0.5 })); band.rotation.x = Math.PI / 2; band.position.y = 0.08; g.add(band);
  const st = emojiSprite("⭐", 0.12); st.position.set(0, 0.2, 0.15); g.add(st);
  g.position.set(0, 0.16, -0.01); g.visible = false;
  alma.head.add(g);
  return g;
})();
const capeMats = { bruxa: new THREE.MeshStandardMaterial({ color: 0x4b2a8a, side: THREE.DoubleSide, roughness: 0.6 }),
                   gelo: new THREE.MeshStandardMaterial({ color: 0xbfeaff, side: THREE.DoubleSide, transparent: true, opacity: 0.6, roughness: 0.2 }) };
const cape = (function () {
  // weich fallender Umhang: halber, nach unten weiter werdender Zylinder auf dem Rücken
  const geo = new THREE.CylinderGeometry(0.17, 0.4, 0.85, 16, 1, true, Math.PI / 2 + 0.25, Math.PI - 0.5); geo.translate(0, -0.42, 0);
  const m = new THREE.Mesh(geo, capeMats.bruxa); m.position.set(0, 1.05, -0.02); m.rotation.x = 0.08; m.visible = false;
  alma.body.add(m); return m;
})();
const broom = (function () {
  const g = new THREE.Group();
  const stick = mesh(new THREE.CylinderGeometry(0.035, 0.04, 2.0, 8), mat(0x8b5a2b)); stick.rotation.x = Math.PI / 2; g.add(stick);
  const bristles = mesh(new THREE.ConeGeometry(0.22, 0.6, 12), mat(0xe8c35a, { roughness: 1 })); bristles.rotation.x = -Math.PI / 2; bristles.position.z = -1.15; g.add(bristles);
  const tie = mesh(new THREE.TorusGeometry(0.07, 0.025, 6, 12), mat(0xff5fa2)); tie.position.z = -0.85; g.add(tie);
  g.visible = false; scene.add(g);
  return g;
})();
// Schminke: Wangenrot, Glitzer, Stern & Herz auf der Wange
const makeup = (function () {
  const g = new THREE.Group(); alma.head.add(g);
  const blushM = new THREE.MeshBasicMaterial({ color: 0xff4f8b, transparent: true, opacity: 0.55 });
  const blush = [-1, 1].map(s => { const b = new THREE.Mesh(new THREE.SphereGeometry(0.045, 10, 8), blushM); b.position.set(s * 0.1, -0.035, 0.135); b.scale.z = 0.35; g.add(b); return b; });
  const glitter = new THREE.Group(); g.add(glitter);
  for (let i = 0; i < 6; i++) { const s = emojiSprite("✨", 0.05); s.position.set((i % 2 ? 1 : -1) * (0.08 + Math.random() * 0.06), -0.02 + Math.random() * 0.06, 0.15); glitter.add(s); }
  const star = emojiSprite("⭐", 0.07); star.position.set(-0.11, 0.02, 0.15); g.add(star);
  const heart = emojiSprite("❤️", 0.065); heart.position.set(0.11, 0.02, 0.15); g.add(heart);
  const freckles = new THREE.Group(); g.add(freckles);
  for (let i = 0; i < 10; i++) { const f = new THREE.Mesh(new THREE.SphereGeometry(0.008, 4, 3), new THREE.MeshBasicMaterial({ color: 0xa0522d })); f.position.set((i % 2 ? 1 : -1) * (0.05 + (i % 5) * 0.015), 0.0 + (i % 3) * 0.012, 0.16); freckles.add(f); }
  const items = { blush: blush, glitter: [glitter], estrela: [star], coracao: [heart], freckles: [freckles] };
  Object.values(items).flat().forEach(o => o.visible = false);
  return { set(name, on) { items[name].forEach(o => o.visible = on); }, items };
})();

// Kostüme
const COSTUMES = {
  princesa: { dress: 0xff6fae, dress2: 0xffc0dd, hair: 0xffcc55, crown: true },
  bruxa:    { dress: 0x7b4fd6, dress2: 0xb388ff, hair: 0xffcc55, hat: true, cape: "bruxa" },
  gelo:     { dress: 0x8fd8ff, dress2: 0xe6f8ff, hair: 0xf6f1e4, crown: true, cape: "gelo", snow: true },
  trancas:  { dress: 0x5cb85c, dress2: 0xffd23f, hair: 0xff6a1a, braids: true, freckles: true },
};
let snowSparkle = false;
function wearCostume(id) {
  const c = COSTUMES[id];
  alma.mats.dress.color.set(c.dress); alma.mats.dress2.color.set(c.dress2); alma.mats.hair.color.set(c.hair);
  alma.crown.visible = !!c.crown; witchHat.visible = !!c.hat;
  cape.visible = !!c.cape; if (c.cape) cape.material = capeMats[c.cape];
  alma.braidsOut = !!c.braids; makeup.set("freckles", !!c.freckles);
  snowSparkle = !!c.snow;
  ch3.costume = id;
}

// =====================================================================
//  Umgebung: sonniger Tag in Portugal
// =====================================================================
function setDay() {
  sky.material.uniforms.top.value.set(0x3d9bff);
  HORIZON.set(0xd6f0ff);
  sky.material.uniforms.sunDir.value.set(0.4, 0.75, -0.4).normalize();
  scene.fog.near = 70; scene.fog.far = 175;
  sun.color.set(0xfff2dc); sun.intensity = 2.3;
  scene.children.forEach(o => { if (o.isHemisphereLight) { o.color.set(0xd6ecff); o.groundColor.set(0x8a9a6a); o.intensity = 0.85; } });
  renderer.toneMappingExposure = 1.05;
  nightStars.visible = false; snowfall.visible = false; winter.visible = false;
}

// Das Meer, die Küste und kleine Häuser mit orangen Dächern
const ptWorld = new THREE.Group(); ptWorld.visible = false; scene.add(ptWorld);
const seaMat = new THREE.ShaderMaterial({
  uniforms: { uTime: windTime },
  vertexShader: `varying vec3 vW; void main() { vec4 w = modelMatrix * vec4(position, 1.0); vW = w.xyz; gl_Position = projectionMatrix * viewMatrix * w; }`,
  fragmentShader: `
    uniform float uTime; varying vec3 vW;
    void main() {
      float w = sin(vW.x * 0.35 + uTime * 1.5) * sin(vW.z * 0.3 - uTime * 1.2) * 0.5 + 0.5;
      float spark = step(0.992, fract(sin(dot(floor(vW.xz * 2.0) + floor(uTime * 2.0), vec2(12.9898, 78.233))) * 43758.5453));
      vec3 col = mix(vec3(0.05, 0.42, 0.78), vec3(0.18, 0.62, 0.9), w) + spark * 0.6;
      gl_FragColor = vec4(col, 1.0);
      #include <tonemapping_fragment>
      #include <encodings_fragment>
    }`,
});
const sea = new THREE.Mesh(new THREE.PlaneGeometry(700, 700), seaMat);
sea.rotation.x = -Math.PI / 2; ptWorld.add(sea);
const coast = new THREE.Group(); ptWorld.add(coast);
(function buildCoast() {
  // Strand und grüne Hügel links, dazu weiße Häuser mit orangen Dächern
  const sand = mesh(new THREE.BoxGeometry(14, 0.6, 700), mat(0xf3d9a4, { roughness: 1 }), false); sand.position.set(-34, 0.1, 0); coast.add(sand);
  const land = mesh(new THREE.BoxGeometry(120, 3, 700), mat(0x8cc56a, { roughness: 1 }), false); land.position.set(-101, 1.2, 0); coast.add(land);
  const wallC = [0xffffff, 0xfff3c4, 0xcfe8ff, 0xffd6e8];
  for (let i = 0; i < 60; i++) {
    const h = new THREE.Group(), w = rand(2.5, 4.5), d = rand(2.5, 4), hh = rand(2.2, 4);
    const b = mesh(new THREE.BoxGeometry(w, hh, d), mat(wallC[i % 4])); b.position.y = hh / 2; h.add(b);
    const roof = mesh(new THREE.ConeGeometry(Math.max(w, d) * 0.75, 1.4, 4), mat(0xe2643a, { roughness: 0.8 })); roof.position.y = hh + 0.7; roof.rotation.y = Math.PI / 4; h.add(roof);
    const win = new THREE.Mesh(new THREE.PlaneGeometry(0.6, 0.7), new THREE.MeshBasicMaterial({ color: 0x3d7bd6 })); win.position.set(0, hh * 0.55, d / 2 + 0.01); h.add(win);
    h.position.set(rand(-75, -45), 2.7, rand(-330, 330)); h.rotation.y = Math.PI / 2 + rand(-0.3, 0.3); coast.add(h);
  }
  // Leuchtturm mit roten Streifen
  const lt = new THREE.Group();
  for (let i = 0; i < 6; i++) { const s = mesh(new THREE.CylinderGeometry(1.4 - i * 0.12, 1.5 - i * 0.12, 2, 16), mat(i % 2 ? 0xffffff : 0xe23b3b)); s.position.y = 1 + i * 2; lt.add(s); }
  const lamp = mesh(new THREE.CylinderGeometry(0.8, 0.8, 1.2, 12), mat(0xfff3a0, { emissive: 0xffd34d, emissiveIntensity: 0.6 })); lamp.position.y = 12.6; lt.add(lamp);
  const top = mesh(new THREE.ConeGeometry(1.1, 1.2, 12), mat(0xe23b3b)); top.position.y = 13.8; lt.add(top);
  lt.position.set(-30, 0.4, -120); coast.add(lt);
  // Segelboote
  for (let i = 0; i < 10; i++) {
    const b = new THREE.Group();
    const hull = mesh(new THREE.BoxGeometry(1.2, 0.5, 3), mat([0x2b5cd6, 0xffd23f, 0xe23b3b, 0x3ddc84][i % 4])); hull.position.y = 0.25; b.add(hull);
    const mast = mesh(new THREE.CylinderGeometry(0.05, 0.05, 3, 6), mat(0x8b5a2b)); mast.position.y = 1.8; b.add(mast);
    const sail = mesh(new THREE.ConeGeometry(1, 2.4, 3), mat(0xffffff, { side: THREE.DoubleSide })); sail.position.set(0, 1.9, -0.3); sail.scale.z = 0.05; b.add(sail);
    b.position.set(rand(-20, 40), 0, rand(-300, 300)); b.rotation.y = Math.random() * 6; b.userData.seed = i; coast.add(b);
  }
})();
ptWorld.position.set(CH3_X, 0, -200);

// Das riesige Haus von außen (auf einer Klippe an der Küste)
const bigHouse = (function () {
  const g = new THREE.Group();
  const azulejo = canvasTexture(128, (c, S) => { for (let y = 0; y < 2; y++) for (let x = 0; x < 2; x++) azulejoTile(c, x * S / 2, y * S / 2, S / 2); }, 8, 4);
  const cliff = mesh(new THREE.CylinderGeometry(28, 32, 6, 32), mat(0x9bc96f, { roughness: 1 }), false); cliff.position.y = -1; g.add(cliff);
  const body = mesh(new THREE.BoxGeometry(24, 14, 16), mat(0xffffff, { map: azulejo })); body.position.y = 9; g.add(body);
  const roof = mesh(new THREE.ConeGeometry(18, 7, 4), mat(0xe2643a)); roof.position.y = 19.5; roof.rotation.y = Math.PI / 4; roof.scale.z = 0.7; g.add(roof);
  const door = mesh(new THREE.PlaneGeometry(4, 6), mat(0x2e7d32), false); door.position.set(0, 5, 8.02); g.add(door);
  for (let i = 0; i < 4; i++) { const w = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 2.4), new THREE.MeshBasicMaterial({ color: 0xfff3a0 })); w.position.set(-8 + (i % 2) * 16, i < 2 ? 6 : 12, 8.02); g.add(w); }
  [-6, 6].forEach(x => { const w = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 2.4), new THREE.MeshBasicMaterial({ color: 0xfff3a0 })); w.position.set(x, 12, 8.02); g.add(w); });
  const heart = emojiSprite("💖", 4); heart.position.y = 25; g.add(heart);
  g.visible = false; scene.add(g);
  return g;
})();

// =====================================================================
//  Das Innere des Hauses: Sala, Cozinha, Quarto e Sala de brincar
// =====================================================================
const houseGroup = new THREE.Group(); houseGroup.position.copy(HOUSE); houseGroup.visible = false; scene.add(houseGroup);
// Azulejo: weiße Fliese mit blauem Blumenmuster
function azulejoTile(c, px, py, ts) {
  c.fillStyle = "#ffffff"; c.fillRect(px, py, ts, ts);
  c.fillStyle = "#2b5cd6"; c.strokeStyle = "#2b5cd6"; c.lineWidth = Math.max(1, ts * 0.04);
  const cx = px + ts / 2, cy = py + ts / 2;
  for (let k = 0; k < 4; k++) {                       // vier Blütenblätter
    const a = k * Math.PI / 2;
    c.beginPath(); c.ellipse(cx + Math.cos(a) * ts * 0.2, cy + Math.sin(a) * ts * 0.2, ts * 0.17, ts * 0.08, a, 0, Math.PI * 2); c.fill();
  }
  [[px, py], [px + ts, py], [px, py + ts], [px + ts, py + ts]].forEach(([x, y]) => { c.beginPath(); c.arc(x, y, ts * 0.18, 0, Math.PI * 2); c.fill(); });   // Ecken
  c.fillStyle = "#ffd23f"; c.beginPath(); c.arc(cx, cy, ts * 0.07, 0, Math.PI * 2); c.fill();
  c.strokeStyle = "rgba(43,92,214,.35)"; c.strokeRect(px + 0.5, py + 0.5, ts - 1, ts - 1);
}
const wallTex = canvasTexture(256, (c, S) => {
  c.fillStyle = "#fff4dc"; c.fillRect(0, 0, S, S);
  const n = 6, ts = S / n, rows = 2;
  for (let x = 0; x < n; x++) for (let y = 0; y < rows; y++) azulejoTile(c, x * ts, S - (y + 1) * ts, ts);
  c.fillStyle = "#2b5cd6"; c.fillRect(0, S - rows * ts - 6, S, 6);
});
const floorTex = canvasTexture(256, (c, S) => {
  for (let i = 0; i < 8; i++) { c.fillStyle = ["#c98b5e", "#bf8155", "#d29567"][i % 3]; c.fillRect(0, i * S / 8, S, S / 8); c.fillStyle = "rgba(90,50,20,.35)"; c.fillRect(0, i * S / 8, S, 2); c.fillRect((i * 97) % S, i * S / 8, 2, S / 8); }
}, 6, 5);
const floor = mesh(new THREE.PlaneGeometry(32, 24), new THREE.MeshStandardMaterial({ map: floorTex, roughness: 0.7 }), false);
floor.rotation.x = -Math.PI / 2; floor.position.y = 0.01; houseGroup.add(floor);
function hwall(x1, z1, x2, z2) {         // Wandstück (achsparallel) mit Azulejos unten
  const len = Math.hypot(x2 - x1, z2 - z1), alongX = Math.abs(x2 - x1) > Math.abs(z2 - z1);
  const t = wallTex.clone(); t.needsUpdate = true; t.repeat.set(len / 3, 1);
  const m = mesh(new THREE.BoxGeometry(alongX ? len : 0.3, 4.2, alongX ? 0.3 : len), new THREE.MeshStandardMaterial({ map: t, roughness: 0.8 }));
  m.position.set((x1 + x2) / 2, 2.1, (z1 + z2) / 2); houseGroup.add(m);
  ch3.walls.push({ shape: "box", x: HOUSE.x + (x1 + x2) / 2, z: HOUSE.z + (z1 + z2) / 2, hw: alongX ? len / 2 : 0.15, hd: alongX ? 0.15 : len / 2, h: 99 });
}
hwall(-16, -12, 16, -12); hwall(-16, 12, -10, 12); hwall(-6, 12, 16, 12); hwall(-16, -12, -16, 12); hwall(16, -12, 16, 12);
hwall(0, -12, 0, -1.6); hwall(0, 1.6, 0, 12);
hwall(-16, 0, -9.5, 0); hwall(-5.5, 0, 5.5, 0); hwall(9.5, 0, 16, 0);
function furn(x, z, w, h, d, color, solid = true, y = 0) {   // einfacher Möbelklotz
  const m = mesh(new THREE.BoxGeometry(w, h, d), mat(color, { roughness: 0.7 })); m.position.set(x, y + h / 2, z); houseGroup.add(m);
  if (solid) ch3.walls.push({ shape: "box", x: HOUSE.x + x, z: HOUSE.z + z, hw: w / 2, hd: d / 2, h: 99 });
  return m;
}
const roomLabel = (txt, x, z) => { const s = emojiSprite(txt, 1.6); s.position.set(x, 3.8, z); houseGroup.add(s); };
// Sala (Wohnzimmer)
const rug = mesh(new THREE.CircleGeometry(3, 32), mat(0xff9fc8, { roughness: 1 }), false); rug.rotation.x = -Math.PI / 2; rug.position.set(-8, 0.02, 6); houseGroup.add(rug);
furn(-8, 10.8, 5, 0.8, 1.4, 0xb388ff); furn(-8, 11.4, 5, 1.6, 0.4, 0xb388ff);                  // Sofa
furn(-3, 9, 1.4, 0.8, 1.4, 0xe2643a); furn(-3, 9.6, 1.4, 1.6, 0.3, 0xe2643a);                  // Sessel vom Avô
furn(-8, 7.6, 2, 0.5, 1, 0x8b5a2b);                                                            // Couchtisch
furn(-15.4, 5, 0.8, 3, 3, 0x8b5a2b);                                                           // Bücherregal
for (let i = 0; i < 12; i++) furn(-15, 3.7 + (i % 6) * 0.45, 0.3, 0.5, 0.35, RAINBOW[i % 7], false, 0.4 + Math.floor(i / 6) * 1.1);
roomLabel("🛋️", -8, 6);
// Cozinha (Küche)
furn(-8, -11.3, 10, 1, 1.2, 0xffffff); furn(-5, -11.3, 1.5, 0.05, 1, 0x333333, false, 1);    // Arbeitsplatte & Herd
furn(-14.8, -11, 1.6, 2.4, 1.4, 0xe6f2ff);                                                     // Kühlschrank
furn(-8, -5, 3, 0.9, 2, 0xd29567);                                                             // Tisch
for (let i = 0; i < 6; i++) { const pastel = mesh(new THREE.CylinderGeometry(0.16, 0.12, 0.12, 12), mat(0xf0b84a)); pastel.position.set(-8.8 + (i % 3) * 0.4, 0.98, -5.2 + Math.floor(i / 3) * 0.4); houseGroup.add(pastel); }
roomLabel("🍳", -8, -6);
// Quarto (Schlafzimmer)
furn(10, -9.5, 4, 0.7, 5, 0xffffff); furn(10, -9.5, 4.1, 0.25, 3.5, 0xff9fc8, false, 0.7); furn(10, -11.6, 4, 1.6, 0.3, 0x8b5a2b);
furn(15.2, -4, 1.4, 3.4, 3, 0xa0683c);                                                         // Kleiderschrank
roomLabel("🛏️", 8, -5);
// Sala de brincar (Spielzimmer) mit dem Baú (Truhe) und einem Spiegel
const trunk = furn(13.5, 10.6, 2.4, 1.2, 1.4, 0x8b5a2b);
furn(13.5, 10.6, 2.5, 0.2, 1.5, 0xffd34d, false, 1.2);
const mirror = mesh(new THREE.PlaneGeometry(1.6, 2.6), new THREE.MeshStandardMaterial({ color: 0xdff4ff, metalness: 0.9, roughness: 0.05 }), false);
mirror.position.set(15.8, 1.8, 6); mirror.rotation.y = -Math.PI / 2; houseGroup.add(mirror);
furn(15.85, 6, 0.1, 3, 1.9, 0xffd34d, false);
[0xff5fa2, 0x4dd0ff, 0xffd23f].forEach((c, i) => { const b = mesh(new THREE.SphereGeometry(0.45, 14, 10), mat(c, { roughness: 0.3 }), false); b.position.set(4 + i * 1.2, 3 + (i % 2) * 0.4, 10.5); houseGroup.add(b); });
roomLabel("🎭", 8, 6);
// Lampen & warmes Licht
const houseLight = new THREE.PointLight(0xffe2b0, 0.6, 40); houseLight.position.set(0, 6, 0); houseGroup.add(houseLight);

// Bilder mit Gesichtern für das Spiel vom Avô
// look: Aussehen der Person, girl: Mädchen (ela) oder Junge (ele), opts: Antworten (die richtige ist dabei)
const FRAMES = [
  { emo: "triste",       girl: true,  look: { hair: "#5a3a22", style: "curly", bg: "#e6f4ff" }, x: -15.8, z: 8,    face: "+x", opts: ["feliz", "triste", "zangado"] },
  { emo: "surpreendido", girl: false, look: { hair: "#8b5a2b", style: "short", bg: "#fff6d6" }, x: -3,    z: 11.8, face: "-z", opts: ["surpreendido", "cansado", "triste"] },
  { emo: "zangado",      girl: false, look: { hair: "#2b2b2b", style: "short", skin: "#e8b48f", bg: "#ffe6e6" }, x: -15.8, z: -6, face: "+x", opts: ["calmo", "feliz", "zangado"] },
  { emo: "feliz",        girl: true,  look: { hair: "#ffcc55", style: "pigtails", bg: "#fff0f7" }, x: -12, z: -0.2, face: "-z", opts: ["feliz", "medo", "triste"] },
  { emo: "cansado",      girl: true,  look: { hair: "#8b5a2b", style: "bun", skin: "#f1c7a5", bg: "#ece6ff" }, x: 5, z: -11.8, face: "+z", opts: ["zangado", "cansado", "surpreendido"] },
  { emo: "medo",         girl: false, look: { hair: "#e2643a", style: "short", bg: "#e8fff0" }, x: 15.8, z: 2, face: "-x", opts: ["feliz", "calmo", "medo"] },
];
FRAMES.forEach(f => {
  const g = new THREE.Group();
  const frame = mesh(new THREE.BoxGeometry(1.5, 1.5, 0.08), mat(0xffd34d, { metalness: 0.4, roughness: 0.4 }), false); g.add(frame);
  const tex = new THREE.CanvasTexture(faceCanvas(f.emo, f.look, 256)); tex.encoding = THREE.sRGBEncoding;
  const pic = new THREE.Mesh(new THREE.PlaneGeometry(1.25, 1.25), new THREE.MeshBasicMaterial({ map: tex })); pic.position.z = 0.05; g.add(pic);
  const glow = new THREE.Sprite(glowMat.clone()); glow.scale.setScalar(3); glow.position.z = -0.1; g.add(glow);
  const done = emojiSprite("⭐", 0.6); done.position.set(0.7, 0.7, 0.15); done.visible = false; g.add(done);
  g.position.set(f.x, 2.3, f.z);
  g.rotation.y = { "+z": 0, "-z": Math.PI, "+x": Math.PI / 2, "-x": -Math.PI / 2 }[f.face];
  houseGroup.add(g);
  Object.assign(f, { group: g, glow, done, solved: false, armed: true, pos: H(f.x, f.z) });
});

// Figuren
const avoF = makeGrandparent(true), avoM = makeGrandparent(false);
avoF.root.visible = avoM.root.visible = false;

// =====================================================================
//  Texte (alles auf Portugiesisch aus Portugal)
// =====================================================================
const st = (who, text) => ({ ...who, text });
const PT = {
  intro: [
    "O Pai Natal deu à Alma uma vassoura mágica e um chapéu de bruxinha.",
    "Agora a Alma vai voar até Portugal, para visitar a Avó e o Avô. Apanha os corações pelo caminho!",
  ],
  houseSeen: "Olha! Ali está a casa enorme da Avó e do Avô!",
  greet: [
    st(AVO_F, "Olá, minha querida Alma! Que saudades! Anda cá dar um abraço à avó."),
    st(AVO_M, "Olá, princesa! Chegaste de vassoura? Que bruxinha tão corajosa!"),
    st(ALMA_PT, "Olá, Avó! Olá, Avô! Eu também tinha muitas saudades vossas!"),
    st(AVO_F, "Hoje vamos aprender uma coisa muito importante: os sentimentos."),
    st(AVO_M, "Sim! Às vezes estamos felizes, às vezes tristes. Todos os sentimentos são importantes."),
    st(AVO_F, "Olha bem para as nossas caras. Nós vamos mostrar-te os sentimentos, um de cada vez."),
  ],
  lessonEnd: [
    st(AVO_M, "Muito bem! Agora já conheces oito sentimentos."),
    st(AVO_M, "E agora vamos jogar! Pela casa há seis fotografias com caras."),
    st(AVO_M, "Encontra as fotografias e diz-me como se sente cada pessoa. Vamos lá, detetive!"),
  ],
  opaDone: [
    st(AVO_M, "Encontraste todas as fotografias! És uma detetive de sentimentos!"),
    st(AVO_M, "Sabes uma piada? Porque é que o livro de matemática estava triste? Porque tinha muitos problemas! Ha, ha, ha!"),
    st(ALMA_PT, "Ha, ha! Ó Avô, tu és tão brincalhão!"),
    st(AVO_F, "Agora é a minha vez! Eu adoro palavras cruzadas. Vamos escrever palavras de sentimentos, letra a letra."),
  ],
  crossDone: [
    st(AVO_F, "Fantástico! Escreveste todas as palavras sozinha. Estou tão orgulhosa de ti!"),
    st(ALMA_PT, "Avó, sabes do que eu gosto muito? De me mascarar e de pintar a cara!"),
    st(AVO_F, "Eu sei! Por isso, o meu baú está cheio de roupas e maquilhagem. Anda, vamos experimentar!"),
  ],
  dressDone: [
    st(AVO_M, "Ena! Que linda que estás! Pareces uma estrela!"),
    st(ALMA_PT, "Obrigada, Avô! Agora apetece-me dançar!"),
    st(AVO_M, "Então vamos fazer uma festa! Eu digo os passos, e dançamos os três juntos!"),
  ],
  danceDone: "E agora... um grande abraço de família!",
  end: "A Avó é carinhosa e adora palavras. O Avô é brincalhão e adora rir. A Alma adora mascarar-se, pintar a cara e dançar. Cada um é diferente, e juntos são uma família muito feliz!",
  frameQ: f => `Como se sente ${f.girl ? "esta menina" : "este menino"}?`,
  frameOk: f => `Boa! ${f.girl ? "Ela está" : "Ele está"} ${EMOTIONS[f.emo][f.girl ? "f" : "m"]}.`,
  frameAsk: f => { const o = f.opts.map(id => EMOTIONS[id][f.girl ? "f" : "m"]); return `${f.girl ? "Ela está" : "Ele está"} ${o[0]}, ${o[1]} ou ${o[2]}?`; },
  inGerman: "Em alemão diz-se:",
};
// Gefühle zeigen: abwechselnd Avó und Avô
const LESSON = [
  { who: AVO_F, emo: "feliz",        text: "Olha para a minha cara. Eu estou feliz! Quando estou feliz, eu sorrio e apetece-me cantar." },
  { who: AVO_M, emo: "zangado",      text: "Agora eu. Grrr! Eu estou zangado! Franzo a testa. Mas depois respiro fundo, e passa." },
  { who: AVO_F, emo: "triste",       text: "Agora estou triste. Quando estou triste, às vezes choro. Um abraço ajuda muito." },
  { who: AVO_M, emo: "medo",         text: "Ai, ai! Eu estou com medo! Quando tenho medo, gosto de dar a mão a alguém." },
  { who: AVO_F, emo: "envergonhado", text: "Eu estou envergonhada. Olha, as minhas bochechas ficam vermelhas!" },
  { who: AVO_M, emo: "surpreendido", text: "Oh! Eu estou surpreendido! Abro muito os olhos e a boca." },
  { who: AVO_F, emo: "calmo",        text: "Agora estou calma. Respiro devagar, como as ondas do mar." },
  { who: AVO_M, emo: "cansado",      text: "Ahh... eu estou cansado. Preciso de uma sesta!" },
];

// =====================================================================
//  Ablauf
// =====================================================================
const ch3Hud = document.getElementById("ch3Hud"), ch3Count = document.getElementById("ch3Count"), ch3Hint = document.getElementById("ch3Hint");
function hud3(count, hint) { ch3Hud.style.display = "block"; ch3Count.textContent = count; ch3Hint.textContent = hint || ""; }

function startChapter3() {
  Sound.init();
  ["title", "final", "paint", "ch2Hud", "hudWrap", "end"].forEach(id => document.getElementById(id).style.display = "none");
  game.chapter = 3; game.state = "chapter3";
  setDay();
  sven.root.visible = false; ptWorld.visible = true;
  // Alma als kleine Hexe auf dem Besen
  scene.add(alma.root); alma.root.scale.setScalar(0.85); alma.root.position.set(0, 0, 0); alma.root.rotation.set(0, 0, 0); alma.root.visible = true;
  wearCostume("bruxa");
  broom.visible = true;
  ch3.state = "flight"; ch3.t = 0;
  ch3.pos.set(CH3_X, 9, 0); ch3.vel.set(0, 0, 0);
  ch3.hearts.forEach(h => scene.remove(h.s)); ch3.hearts = []; ch3.heartCount = 0;
  ch3.spawnZ = -35;
  for (let i = 0; i < 4; i++) spawnHeart();
  camera.position.set(CH3_X, 13, 12);
  hud3(`❤️ 0 / ${HEARTS_NEEDED}`, "← → / A D: virar · ↑ ↓ / W S: subir e descer · Shift: mais depressa");
  Sound.play("portugal");
  fade(false);
  captionEl.style.display = "block"; captionEl.textContent = PT.intro[0] + " " + PT.intro[1];
  captionEl.classList.remove("show"); void captionEl.offsetWidth; captionEl.classList.add("show");
  Voice.speak(PT.intro.map(t => ({ text: t, voice: "pt" })));
  setTimeout(() => { if (ch3.state === "flight") hideCaption(); }, 11000);
  ch3.allowTouch = true;
}
function spawnHeart() {
  const s = emojiSprite("💖", 2.2);
  s.position.set(CH3_X + rand(-9, 9), rand(6, 13), ch3.spawnZ);
  scene.add(s);
  ch3.hearts.push({ s, baseY: s.position.y, seed: Math.random() * 6 });
  ch3.spawnZ -= 38;
}

function flight3(dt, t) {
  const side = (keys.KeyD || keys.ArrowRight ? 1 : 0) - (keys.KeyA || keys.ArrowLeft ? 1 : 0) + touch.x;
  const up = (keys.KeyW || keys.ArrowUp || keys.Space ? 1 : 0) - (keys.KeyS || keys.ArrowDown ? 1 : 0) - touch.y;
  const speed = keys.ShiftLeft || keys.ShiftRight || touch.run ? 22 : 14;
  ch3.vel.x += (side * 11 - ch3.vel.x) * Math.min(1, dt * 3);
  ch3.vel.y += (up * 7 - ch3.vel.y) * Math.min(1, dt * 3);
  ch3.vel.z += (-speed - ch3.vel.z) * Math.min(1, dt * 2);
  ch3.pos.addScaledVector(ch3.vel, dt);
  ch3.pos.x = Math.max(CH3_X - 22, Math.min(CH3_X + 22, ch3.pos.x));
  ch3.pos.y = Math.max(3.5, Math.min(22, ch3.pos.y));
  placeRider(ch3.pos, -ch3.vel.x * 0.04, -ch3.vel.y * 0.03, t);
  if (Math.random() < 0.6) spawnParticle(ch3.pos.clone().add(new THREE.Vector3(rand(-0.3, 0.3), 0.4, 1.4)), [0xffd34d, 0xffffff, 0xff9ad5][Math.floor(Math.random() * 3)], new THREE.Vector3(0, rand(-0.3, 0.3), 2), 1, 1, 0);
  // das Meer & die Küste wandern mit
  if (ptWorld.position.z - ch3.pos.z > 150) ptWorld.position.z -= 300;
  ch3.hearts.forEach(h => { h.s.position.y = h.baseY + Math.sin(t * 2 + h.seed) * 0.4; });
  for (let i = ch3.hearts.length - 1; i >= 0; i--) {
    const h = ch3.hearts[i];
    if (h.s.position.distanceTo(ch3.pos.clone().setY(ch3.pos.y + 1)) < 2.6) {
      ch3.heartCount++; Sound.star(ch3.heartCount === HEARTS_NEEDED);
      for (let k = 0; k < 16; k++) spawnParticle(h.s.position, [0xff5fa2, 0xff9ad5, 0xffffff][k % 3], new THREE.Vector3(rand(-3, 3), rand(-1, 3), rand(-3, 3)), 0.8, 1.4, 2);
      scene.remove(h.s); ch3.hearts.splice(i, 1);
      hud3(`❤️ ${ch3.heartCount} / ${HEARTS_NEEDED}`, ch3Hint.textContent);
      if (ch3.heartCount + ch3.hearts.length < HEARTS_NEEDED) { ch3.spawnZ = Math.min(ch3.spawnZ, ch3.pos.z - 110); spawnHeart(); }
    } else if (h.s.position.z > ch3.pos.z + 8) {
      ch3.spawnZ = Math.min(ch3.spawnZ, ch3.pos.z - 110);
      h.s.position.set(CH3_X + rand(-9, 9), rand(6, 13), ch3.spawnZ); h.baseY = h.s.position.y; ch3.spawnZ -= 38;
    }
  }
  if (ch3.heartCount >= HEARTS_NEEDED) {
    ch3.state = "approach"; ch3.t = 0;
    bigHouse.position.set(CH3_X - 6, 0, ch3.pos.z - 95); bigHouse.visible = true;
    ch3.from = ch3.pos.clone();
    showCaption(PT.houseSeen, false); Voice.speak({ text: PT.houseSeen, voice: "pt" });
  }
  camera.position.lerp(ch3.pos.clone().add(new THREE.Vector3(0, 3, 8)), Math.min(1, dt * 4));
  camera.lookAt(ch3.pos.clone().add(new THREE.Vector3(0, 1, -8)));
}
// Alma auf dem Besen an eine Position setzen
function placeRider(pos, roll, pitch, t) {
  broom.position.copy(pos).add(new THREE.Vector3(0, 0.38 + Math.sin(t * 3) * 0.08, 0));
  broom.rotation.set(pitch, 0, roll);
  alma.root.position.copy(broom.position).add(new THREE.Vector3(0, -0.36, 0));
  alma.root.rotation.set(pitch, Math.PI, roll);
  alma.walk = 0; alma.cheer = 0.3;
  cape.rotation.x = 0.35 + Math.sin(t * 9) * 0.06;
}

function enterHouse() {
  ch3.state = "greet"; ch3.t = 0;
  ptWorld.visible = false; bigHouse.visible = false; broom.visible = false; houseGroup.visible = true;
  ch3.hearts.forEach(h => scene.remove(h.s)); ch3.hearts = [];
  alma.root.scale.setScalar(1); alma.root.rotation.set(0, Math.PI, 0); alma.root.position.copy(H(-8, 10.5 - 1));
  alma.root.position.copy(H(-8, 8.5));
  cape.rotation.x = 0.08;
  avoF.root.visible = avoM.root.visible = true;
  avoF.root.position.copy(H(-9.5, 5.5)); avoM.root.position.copy(H(-6.3, 5.5));
  cam.yaw = 0; camera.position.copy(H(-8, 15).setY(4.5)); camera.lookAt(H(-8, 7).setY(1.2));
  scene.fog.near = 200; scene.fog.far = 400;
  hideCaption(); ch3Hud.style.display = "none";
  Sound.play("zauber");
  fade(false);
  setTimeout(() => say(PT.greet, startLesson), 1200);
}

// ---------------- Gefühle zeigen (Lektion) ----------------
const feelEl = document.getElementById("feel");
function startLesson() { ch3.state = "lesson"; ch3.lesson = 0; showLesson(); }
function showLesson() {
  const L = LESSON[ch3.lesson], e = EMOTIONS[L.emo], who = L.who === AVO_F ? avoF : avoM;
  avoF.rig.set("feliz"); avoM.rig.set("feliz");
  who.rig.set(L.emo);
  ch3.speaker = who;
  feelEl.style.display = "flex";
  const fc = document.getElementById("feelFace"); fc.innerHTML = ""; fc.appendChild(faceCanvas(L.emo, L.who === AVO_F ? { hair: "#dcdce6", style: "bun", bg: "#fff5fb" } : { hair: "#f2f2f2", style: "short", bg: "#eef6ff" }, 200));
  document.getElementById("feelWho").textContent = `${L.who.emoji} ${L.who.name}`;
  document.getElementById("feelPt").textContent = emoLabel(L.emo);
  document.getElementById("feelDe").textContent = "🇩🇪 " + e.de;
  document.getElementById("feelText").textContent = L.text;
  document.getElementById("feelDots").textContent = LESSON.map((_, i) => i <= ch3.lesson ? "●" : "○").join(" ");
  Voice.speak([{ text: L.text, voice: L.who.voice }, { text: PT.inGerman, voice: "pt" }, { text: e.de, voice: "de" }]);
}
document.getElementById("feelNext").addEventListener("click", () => {
  if (ch3.state !== "lesson") return;
  ch3.lesson++;
  if (ch3.lesson < LESSON.length) return showLesson();
  feelEl.style.display = "none";
  avoF.rig.set("feliz"); avoM.rig.set("feliz");
  say(PT.lessonEnd, startOpaGame);
});

// ---------------- Spiel vom Avô: Fotografias finden ----------------
function startOpaGame() {
  ch3.state = "explore"; ch3.found = 0;
  avoM.root.position.copy(H(-3, 7.6)); avoF.root.position.copy(H(-8, -2.8));
  hud3(`🖼️ 0 / ${FRAMES.length}`, "Procura as fotografias pela casa! · W A S D / setas: andar · rato: rodar a câmara");
}
function frameRiddle(f) {
  const g = f.girl ? "f" : "m";
  const step = { ...AVO_M,
    riddle: { q: PT.frameQ(f), ask: PT.frameAsk(f), options: f.opts.map(id => `${EMOTIONS[id].emoji} ${EMOTIONS[id][g]}  (${EMOTIONS[id].de})`), answer: f.opts.indexOf(f.emo) },
    onCorrect: [st(AVO_M, PT.frameOk(f)), { fn: () => solveFrame(f) }] };
  say([step]);
}
function solveFrame(f) {
  f.solved = true; f.done.visible = true; f.glow.visible = false;
  ch3.found++;
  hud3(`🖼️ ${ch3.found} / ${FRAMES.length}`, ch3Hint.textContent);
  Sound.magic();
  if (ch3.found === FRAMES.length) {
    ch3.state = "talk";
    ch3Hud.style.display = "none";
    setTimeout(() => {
      avoM.root.position.copy(alma.root.position).add(new THREE.Vector3(1.4, 0, 1.4));
      avoF.root.position.copy(alma.root.position).add(new THREE.Vector3(-1.4, 0, 1.4));
      say(PT.opaDone, () => openCrossword());
    }, 600);
  }
}

// Alma läuft durchs Haus
function explore3(dt, t) {
  const f = (keys.KeyW || keys.ArrowUp ? 1 : 0) - (keys.KeyS || keys.ArrowDown ? 1 : 0) - touch.y;
  const s = (keys.KeyD || keys.ArrowRight ? 1 : 0) - (keys.KeyA || keys.ArrowLeft ? 1 : 0) + touch.x;
  const yaw = cam.yaw;
  let mx = -Math.sin(yaw) * f + Math.cos(yaw) * s, mz = -Math.cos(yaw) * f - Math.sin(yaw) * s;
  const len = Math.hypot(mx, mz);
  const p = alma.root.position;
  if (len > 0.1) {
    mx /= len; mz /= len;
    const sp = (keys.ShiftLeft || keys.ShiftRight || touch.run ? 5.5 : 3.4) * Math.min(1, len);
    p.x += mx * sp * dt; p.z += mz * sp * dt;
    alma.root.rotation.y += angleDiff(alma.root.rotation.y, Math.atan2(mx, mz)) * Math.min(1, dt * 10);
    alma.walk = 1;
  } else alma.walk = 0;
  for (const w of ch3.walls) { const pen = penetration(w, p.x, p.z); if (pen) { p.x += pen.x * 0.9; p.z += pen.z * 0.9; } }
  // Bilder in der Nähe?
  FRAMES.forEach(fr => {
    if (fr.solved) return;
    fr.glow.material.opacity = 0.6 + Math.sin(t * 4) * 0.3;
    const d = Math.hypot(p.x - fr.pos.x, p.z - fr.pos.z);
    if (d < 2.6 && fr.armed && game.state !== "dialog") { fr.armed = false; frameRiddle(fr); }
    else if (d > 3.5) fr.armed = true;
  });
}

// Kamera hinter Alma (von schräg oben, damit man über die Wände schaut)
function houseCam(dt, target, dist = 7.5, pitch = 0.75) {
  const tg = target.clone().setY(target.y + 1.2);
  const cp = Math.cos(pitch);
  const goal = new THREE.Vector3(tg.x + Math.sin(cam.yaw) * cp * dist, tg.y + Math.sin(pitch) * dist, tg.z + Math.cos(cam.yaw) * cp * dist);
  camera.position.lerp(goal, Math.min(1, dt * 4));
  camera.lookAt(tg);
}
function closeUp(dt, who, dist = 2.6) {
  const head = new THREE.Vector3(); who.head.getWorldPosition(head);
  const dir = new THREE.Vector3(Math.sin(who.root.rotation.y), 0, Math.cos(who.root.rotation.y));
  camera.position.lerp(head.clone().addScaledVector(dir, dist).add(new THREE.Vector3(0.4, 0.1, 0)), Math.min(1, dt * 3));
  camera.lookAt(head.clone().add(new THREE.Vector3(0, -0.1, 0)));
}

// =====================================================================
//  jedes Bild
// =====================================================================
function updateChapter3(dt, t) {
  ch3.t += dt;
  [avoF, avoM].forEach(c => c.root.visible && c.update(dt, t));
  ch3.allowTouch = ch3.state === "flight" || ch3.state === "explore";
  if (ch3.state === "flight") flight3(dt, t);
  else if (ch3.state === "approach") {
    const goal = bigHouse.position.clone().add(new THREE.Vector3(0, 5, 10));
    const f = smooth(ch3.t / 4.5);
    ch3.pos.lerpVectors(ch3.from, goal, f);
    placeRider(ch3.pos, 0, 0, t);
    camera.position.lerp(ch3.pos.clone().add(new THREE.Vector3(3, 3, 9)), Math.min(1, dt * 2));
    camera.lookAt(bigHouse.position.clone().setY(8));
    if (ch3.t > 3.6) fade(true);
    if (ch3.t > 4.8) enterHouse();
  } else if (ch3.houseVisible !== false && houseGroup.visible) {
    // im Haus: Großeltern schauen zu Alma, Kamera je nach Situation
    [avoF, avoM].forEach(c => { if (!c.pose) c.root.rotation.y += angleDiff(c.root.rotation.y, headingTo(c.root.position, alma.root.position)) * Math.min(1, dt * 3); });
    if (ch3.state === "explore") { if (game.state !== "dialog") explore3(dt, t); else alma.walk = 0; houseCam(dt, alma.root.position); }
    else if (ch3.state === "lesson" && ch3.speaker) { alma.walk = 0; closeUp(dt, ch3.speaker); }
    else if (ch3.state === "dress") { alma.walk = 0; closeUp(dt, alma, 2.0); }
    else if (ch3.state === "dance" || ch3.state === "end") danceUpdate(dt, t);
    else { alma.walk = 0; houseCam(dt, alma.root.position, 6.5, 0.45); }
  }
  if (snowSparkle && Math.random() < 0.3) spawnParticle(alma.root.position.clone().add(new THREE.Vector3(rand(-0.6, 0.6), rand(0.5, 1.8), rand(-0.6, 0.6))), 0xdff6ff, new THREE.Vector3(0, -0.3, 0), 1.2, 0.8, 0);
  sky.position.copy(camera.position);
  sun.position.copy(alma.root.position).addScaledVector(SUN_DIR, 40);
  sun.target.position.copy(alma.root.position);
}

// alle gesprochenen Sätze des Kapitels (für die Aufnahmen)
function CH3_VOICE_LINES() {
  const L = [];
  const addSteps = steps => steps.forEach(s => {
    if (s.text) L.push({ text: s.text, voice: s.voice });
    if (s.riddle) { L.push({ text: "Ouve com atenção:", voice: s.voice }, { text: s.riddle.q, voice: s.voice }, { text: s.riddle.ask, voice: s.voice }, { text: WRONG_TEXT_PT, voice: s.voice }); addSteps(s.onCorrect || []); }
  });
  PT.intro.forEach(t => L.push({ text: t, voice: "pt" }));
  L.push({ text: PT.houseSeen, voice: "pt" }, { text: PT.inGerman, voice: "pt" }, { text: PT.end, voice: "pt" });
  [PT.greet, PT.lessonEnd, PT.opaDone, PT.crossDone, PT.dressDone].forEach(addSteps);
  L.push({ text: PT.danceDone, voice: "avoM" });
  LESSON.forEach(l => { L.push({ text: l.text, voice: l.who.voice }, { text: EMOTIONS[l.emo].de, voice: "de" }); });
  FRAMES.forEach(f => addSteps([{ ...AVO_M, riddle: { q: PT.frameQ(f), ask: PT.frameAsk(f) }, onCorrect: [st(AVO_M, PT.frameOk(f))] }]));
  if (typeof CH3UI_VOICE_LINES !== "undefined") L.push(...CH3UI_VOICE_LINES());
  return L;
}
document.getElementById("ch3Btn").addEventListener("click", startChapter3);
document.getElementById("ch3Direct").addEventListener("click", startChapter3);
