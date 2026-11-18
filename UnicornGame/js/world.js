// =====================================================================
//  world.js – Renderer, Licht, Himmel und die Wiese mit Hindernissen
// =====================================================================

// Farben werden als sRGB angegeben und korrekt umgerechnet → natürlichere Farben
THREE.ColorManagement.legacyMode = false;

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputEncoding = THREE.sRGBEncoding;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const HORIZON = new THREE.Color(0xd4e6f2);
scene.fog = new THREE.Fog(HORIZON, 35, 140);

const camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 500);
window.addEventListener("resize", () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

// Gemeinsame Zeit für Wind-Animationen in Shadern
const windTime = { value: 0 };

// ---------------- Hilfsfunktionen ----------------
const mat = (color, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness: 0.8, ...extra });
function mesh(geo, material, shadow = true) {
  const m = new THREE.Mesh(geo, material);
  m.castShadow = shadow; m.receiveShadow = true;
  return m;
}
// einfache, wiederholbare "Rausch"-Funktion (gleiche Eingabe → gleiches Ergebnis)
function noise3(x, y, z) {
  return Math.sin(x * 1.7 + Math.sin(z * 2.3)) * Math.cos(y * 2.1 + Math.sin(x * 1.3)) * 0.5
       + Math.sin(z * 3.1 + y * 1.9) * 0.3 + Math.sin(x * 4.7 + z * 5.3 + y * 3.9) * 0.2;
}
// Textur aus einem gemalten Canvas (keine Bilddateien nötig)
function canvasTexture(size, draw, repeatX = 1, repeatY = 1) {
  const c = document.createElement("canvas");
  c.width = c.height = size;
  draw(c.getContext("2d"), size);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(repeatX, repeatY);
  t.encoding = THREE.sRGBEncoding;
  t.anisotropy = renderer.capabilities.getMaxAnisotropy();
  return t;
}
const rand = (a, b) => a + Math.random() * (b - a);

// ---------------- Licht ----------------
const SUN_DIR = new THREE.Vector3(0.55, 0.62, 0.35).normalize();
scene.add(new THREE.HemisphereLight(0xcfe6ff, 0x5d7a3a, 0.75));
const sun = new THREE.DirectionalLight(0xfff0d8, 2.4);
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.bias = -0.0004;
sun.shadow.normalBias = 0.03;
Object.assign(sun.shadow.camera, { left: -16, right: 16, top: 16, bottom: -16, near: 1, far: 80 });
scene.add(sun, sun.target);

// ---------------- Himmel (Farbverlauf + Sonne) ----------------
const sky = new THREE.Mesh(
  new THREE.SphereGeometry(400, 32, 16),
  new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: {
      top: { value: new THREE.Color(0x3d86d6) },
      horizon: { value: HORIZON },
      sunDir: { value: SUN_DIR },
    },
    vertexShader: `
      varying vec3 vDir;
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        vDir = wp.xyz - cameraPosition;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }`,
    fragmentShader: `
      uniform vec3 top; uniform vec3 horizon; uniform vec3 sunDir;
      varying vec3 vDir;
      void main() {
        vec3 d = normalize(vDir);
        float h = max(d.y, 0.0);
        vec3 col = mix(horizon, top, pow(h, 0.55));
        float s = max(dot(d, sunDir), 0.0);
        col += vec3(1.0, 0.92, 0.75) * (pow(s, 900.0) * 6.0 + pow(s, 12.0) * 0.25);
        gl_FragColor = vec4(col, 1.0);
        #include <tonemapping_fragment>
        #include <encodings_fragment>
      }`,
  }));
sky.renderOrder = -1;
scene.add(sky);

// Wolken: Haufen aus weichen Kugeln
const cloudMat = new THREE.MeshLambertMaterial({ color: 0xffffff, fog: false });
const clouds = [];
for (let i = 0; i < 12; i++) {
  const c = new THREE.Group();
  const n = 6 + Math.floor(Math.random() * 5);
  for (let k = 0; k < n; k++) {
    const r = rand(2.2, 4.5);
    const s = new THREE.Mesh(new THREE.SphereGeometry(r, 12, 8), cloudMat);
    s.position.set(k * 2.6 - n * 1.3 + rand(-1, 1), rand(0, 1.5) + (r - 2.2) * 0.5, rand(-1.5, 1.5));
    s.scale.y = 0.7;
    c.add(s);
  }
  const a = Math.random() * Math.PI * 2, r = rand(90, 220);
  c.position.set(Math.cos(a) * r, rand(45, 75), Math.sin(a) * r);
  c.rotation.y = Math.random() * Math.PI;
  scene.add(c); clouds.push(c);
}

// ---------------- Boden ----------------
const WORLD_R = 30;   // Radius der begehbaren Wiese

const groundTex = canvasTexture(512, (g, S) => {
  g.fillStyle = "#5b8f37"; g.fillRect(0, 0, S, S);
  for (let i = 0; i < 2500; i++) {
    const l = 30 + Math.random() * 25, hue = 85 + Math.random() * 25;
    g.fillStyle = `hsla(${hue}, 45%, ${l}%, 0.35)`;
    g.beginPath(); g.arc(Math.random() * S, Math.random() * S, rand(2, 14), 0, Math.PI * 2); g.fill();
  }
  for (let i = 0; i < 300; i++) {   // Erdflecken
    g.fillStyle = `rgba(110, 85, 50, ${rand(0.15, 0.35)})`;
    g.beginPath(); g.arc(Math.random() * S, Math.random() * S, rand(1, 4), 0, Math.PI * 2); g.fill();
  }
}, 40, 40);
const ground = mesh(new THREE.CircleGeometry(160, 64), new THREE.MeshStandardMaterial({ map: groundTex, roughness: 1 }), false);
ground.rotation.x = -Math.PI / 2;
scene.add(ground);

// Ferne Hügel & Berge (werden vom Dunst bläulich gefärbt)
const hillMat = mat(0x5f8f45, { flatShading: false });
for (let i = 0; i < 26; i++) {
  const a = i / 26 * Math.PI * 2 + rand(-0.1, 0.1), r = rand(55, 80);
  const h = mesh(new THREE.SphereGeometry(1, 16, 10), hillMat, false);
  h.scale.set(rand(12, 22), rand(4, 9), rand(10, 16));
  h.position.set(Math.cos(a) * r, -1, Math.sin(a) * r);
  scene.add(h);
}
const mountainMat = mat(0x7d8fa8, { flatShading: true });
for (let i = 0; i < 14; i++) {
  const a = i / 14 * Math.PI * 2 + rand(-0.15, 0.15), r = rand(115, 135);
  const m = new THREE.Mesh(new THREE.ConeGeometry(rand(18, 30), rand(25, 45), 7), mountainMat);
  m.position.set(Math.cos(a) * r, 8, Math.sin(a) * r);
  scene.add(m);
}

// ---------------- Gras: tausende Halme, die sich im Wind wiegen ----------------
function bladeGeometry() {
  const w = 0.032, pts = [[-w, 0], [w, 0], [-w * 0.75, 0.35], [w * 0.75, 0.35], [-w * 0.4, 0.7], [w * 0.4, 0.7], [0, 1]];
  const pos = [], col = [], nor = [];
  const dark = new THREE.Color(0x335a22), light = new THREE.Color(0xa6cf62);
  pts.forEach(([x, y]) => {
    pos.push(x, y, y * y * 0.12);
    const c = dark.clone().lerp(light, y);
    col.push(c.r, c.g, c.b);
    nor.push(0, 1, 0);           // Normalen nach oben → Gras wird wie der Boden beleuchtet
  });
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute("color", new THREE.Float32BufferAttribute(col, 3));
  g.setAttribute("normal", new THREE.Float32BufferAttribute(nor, 3));
  g.setIndex([0, 1, 2, 2, 1, 3, 2, 3, 4, 4, 3, 5, 4, 5, 6]);
  return g;
}
// Material mit Wind: verbiegt die Halmspitzen im Vertex-Shader
function windy(material, strength) {
  material.onBeforeCompile = shader => {
    shader.uniforms.uTime = windTime;
    shader.vertexShader = "uniform float uTime;\n" + shader.vertexShader.replace("#include <begin_vertex>", `
      #include <begin_vertex>
      vec4 wp = instanceMatrix * vec4(0.0, 0.0, 0.0, 1.0);
      float sway = sin(uTime * 1.6 + wp.x * 0.35 + wp.z * 0.25) + 0.4 * sin(uTime * 3.7 + wp.x * 1.3);
      transformed.x += sway * position.y * position.y * ${strength.toFixed(2)};
      transformed.z += cos(uTime * 1.2 + wp.z * 0.4) * position.y * position.y * ${(strength * 0.5).toFixed(2)};
    `);
  };
  return material;
}
const dummy = new THREE.Object3D();
function scatterGrass(count, minR, maxR, minH, maxH) {
  const inst = new THREE.InstancedMesh(bladeGeometry(),
    windy(new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide }), 0.18), count);
  const c = new THREE.Color();
  for (let i = 0; i < count; i++) {
    const a = Math.random() * Math.PI * 2, r = minR + Math.sqrt(Math.random()) * (maxR - minR);
    dummy.position.set(Math.cos(a) * r, 0, Math.sin(a) * r);
    dummy.rotation.set(rand(-0.15, 0.15), Math.random() * Math.PI * 2, rand(-0.15, 0.15));
    const h = rand(minH, maxH);
    dummy.scale.set(rand(0.8, 1.3), h, 1);
    dummy.updateMatrix();
    inst.setMatrixAt(i, dummy.matrix);
    c.setHSL(rand(0.22, 0.3), rand(0.35, 0.6), rand(0.4, 0.6));
    inst.setColorAt(i, c.multiplyScalar(1.6));
  }
  inst.receiveShadow = true;
  scene.add(inst);
}
scatterGrass(26000, 0, WORLD_R + 3, 0.25, 0.55);
scatterGrass(9000, WORLD_R + 3, 50, 0.3, 0.6);

// Wildblumen
(function flowers() {
  const N = 450;
  const stem = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.008, 0.012, 1, 4).translate(0, 0.5, 0),
    windy(new THREE.MeshLambertMaterial({ color: 0x3f6b25 }), 0.1), N);
  const blossomGeo = new THREE.SphereGeometry(0.055, 8, 4); blossomGeo.scale(1, 0.45, 1);
  const blossom = new THREE.InstancedMesh(blossomGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), N);
  const palette = [0xffffff, 0xfff3a0, 0xffd23f, 0xf7a1c4, 0xc9a7ff, 0xffffff];
  const c = new THREE.Color();
  for (let i = 0; i < N; i++) {
    const a = Math.random() * Math.PI * 2, r = Math.sqrt(Math.random()) * (WORLD_R + 4);
    const h = rand(0.25, 0.5), x = Math.cos(a) * r, z = Math.sin(a) * r;
    dummy.position.set(x, 0, z); dummy.rotation.set(0, 0, 0); dummy.scale.set(1, h, 1); dummy.updateMatrix();
    stem.setMatrixAt(i, dummy.matrix);
    dummy.position.set(x, h, z); dummy.scale.setScalar(rand(0.8, 1.4)); dummy.updateMatrix();
    blossom.setMatrixAt(i, dummy.matrix);
    blossom.setColorAt(i, c.set(palette[i % palette.length]));
  }
  scene.add(stem, blossom);
})();

// ---------------- Texturen für Holz & Heu ----------------
const barkTex = canvasTexture(256, (g, S) => {
  g.fillStyle = "#6b4a2e"; g.fillRect(0, 0, S, S);
  for (let i = 0; i < 160; i++) {
    g.strokeStyle = `rgba(${rand(30, 60)}, ${rand(20, 40)}, 15, ${rand(0.3, 0.7)})`;
    g.lineWidth = rand(1, 4);
    const x = Math.random() * S;
    g.beginPath(); g.moveTo(x, 0);
    for (let y = 0; y <= S; y += 32) g.lineTo(x + rand(-4, 4), y);
    g.stroke();
  }
}, 2, 1);
const ringTex = canvasTexture(256, (g, S) => {
  g.fillStyle = "#d8b07a"; g.fillRect(0, 0, S, S);
  for (let r = 8; r < S / 2; r += rand(5, 10)) {
    g.strokeStyle = `rgba(140, 95, 50, ${rand(0.3, 0.6)})`; g.lineWidth = rand(1, 2.5);
    g.beginPath(); g.arc(S / 2 + rand(-2, 2), S / 2 + rand(-2, 2), r, 0, Math.PI * 2); g.stroke();
  }
  g.strokeStyle = "#5a3a20"; g.lineWidth = 10; g.beginPath(); g.arc(S / 2, S / 2, S / 2 - 5, 0, Math.PI * 2); g.stroke();
});
const hayTex = canvasTexture(256, (g, S) => {
  g.fillStyle = "#c9a447"; g.fillRect(0, 0, S, S);
  for (let i = 0; i < 1400; i++) {
    g.strokeStyle = `hsla(${rand(38, 50)}, ${rand(50, 75)}%, ${rand(40, 75)}%, 0.8)`;
    g.lineWidth = rand(0.8, 2);
    const x = Math.random() * S, y = Math.random() * S, a = rand(-0.4, 0.4), l = rand(8, 30);
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.cos(a) * l, y + Math.sin(a) * l); g.stroke();
  }
}, 3, 1);
const woodTex = canvasTexture(128, (g, S) => {
  g.fillStyle = "#a08868"; g.fillRect(0, 0, S, S);
  for (let i = 0; i < 40; i++) {
    g.strokeStyle = `rgba(80, 60, 40, ${rand(0.15, 0.4)})`; g.lineWidth = rand(0.5, 2);
    const x = Math.random() * S; g.beginPath(); g.moveTo(x, 0); g.lineTo(x + rand(-3, 3), S); g.stroke();
  }
});

// ---------------- Hindernisse ----------------
// shape "circle" = rund (r), "box" = eckig (hw/hd = halbe Breite/Tiefe). h = Höhe zum Draufstehen.
const obstacles = [];
function addObstacle(o, object) {
  object.position.x = o.x; object.position.z = o.z;
  scene.add(object);
  obstacles.push(o);
}

function rock(x, z, r, h) {
  const geo = new THREE.IcosahedronGeometry(1, 3);
  const p = geo.attributes.position, colors = [];
  const c = new THREE.Color(), seed = x * 3.1 + z * 1.7;
  for (let i = 0; i < p.count; i++) {
    let vx = p.getX(i), vy = p.getY(i), vz = p.getZ(i);
    const n = 1 + noise3(vx * 2 + seed, vy * 2, vz * 2) * 0.18 + noise3(vx * 6, vy * 6 + seed, vz * 6) * 0.05;
    vx *= n; vy *= n; vz *= n;
    if (vy < -0.2) vy = -0.2;                    // unten flach (liegt auf dem Boden)
    p.setXYZ(i, vx, vy, vz);
    const g = 0.42 + noise3(vx * 8, vy * 8, vz * 8 + seed) * 0.08;
    c.setRGB(g, g * 0.98, g * 0.95);
    if (vy > 0.55 + noise3(vx * 5, 0, vz * 5) * 0.2) c.setRGB(0.3, 0.42, 0.18);   // Moos oben
    colors.push(c.r, c.g, c.b);
  }
  geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
  geo.computeVertexNormals();
  const m = mesh(geo, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.95, flatShading: true }));
  m.scale.set(r, h / 1.1, r * 0.9);
  m.position.y = 0.2 * h / 1.1;
  const g = new THREE.Group(); g.add(m);
  addObstacle({ shape: "circle", x, z, r: r * 0.95, h }, g);
}

function log(x, z, len, alongX) {
  const g = new THREE.Group();
  const bark = barkTex.clone(); bark.needsUpdate = true; bark.repeat.set(3, len / 1.5);
  const m = mesh(new THREE.CylinderGeometry(0.36, 0.4, len, 20, 1, true), mat(0xffffff, { map: bark, roughness: 1 }));
  m.rotation.z = Math.PI / 2; m.position.y = 0.38; g.add(m);
  [-1, 1].forEach(s => {
    const c = mesh(new THREE.CircleGeometry(s > 0 ? 0.36 : 0.4, 20), mat(0xffffff, { map: ringTex }));
    c.rotation.y = s * Math.PI / 2; c.position.set(s * len / 2, 0.38, 0); g.add(c);
  });
  if (!alongX) g.rotation.y = Math.PI / 2;
  addObstacle({ shape: "box", x, z, hw: alongX ? len / 2 : 0.38, hd: alongX ? 0.38 : len / 2, h: 0.76 }, g);
}

function bush(x, z) {
  const g = new THREE.Group();
  for (let i = 0; i < 6; i++) {
    const r = rand(0.35, 0.55);
    const b = foliage(r, i);
    const a = i / 6 * Math.PI * 2;
    b.position.set(Math.cos(a) * 0.35, r * 0.9 + (i % 2) * 0.15, Math.sin(a) * 0.35); g.add(b);
  }
  const berry = mat(0xc4203d, { roughness: 0.3 });
  for (let i = 0; i < 14; i++) {
    const s = mesh(new THREE.SphereGeometry(0.04, 6, 4), berry, false);
    const a = Math.random() * Math.PI * 2; s.position.set(Math.cos(a) * 0.78, rand(0.3, 0.95), Math.sin(a) * 0.78); g.add(s);
  }
  addObstacle({ shape: "circle", x, z, r: 0.85, h: 1.0 }, g);
}

function hay(x, z) {
  const g = new THREE.Group();
  const side = hayTex.clone(); side.needsUpdate = true;
  const m = mesh(new THREE.CylinderGeometry(0.78, 0.78, 1.3, 28), mat(0xffffff, { map: side, roughness: 1 }));
  m.rotation.z = Math.PI / 2; m.rotation.y = Math.random() * Math.PI; m.position.y = 0.78; g.add(m);
  addObstacle({ shape: "circle", x, z, r: 0.8, h: 1.5 }, g);
  // wird liegend gezeigt → Höhe 1.56, darauf stehen bei 1.5
}

function stump(x, z) {
  const g = new THREE.Group();
  const bark = barkTex.clone(); bark.needsUpdate = true; bark.repeat.set(2, 0.4);
  const m = mesh(new THREE.CylinderGeometry(0.45, 0.6, 0.6, 16), mat(0xffffff, { map: bark, roughness: 1 })); m.position.y = 0.3; g.add(m);
  const top = mesh(new THREE.CircleGeometry(0.45, 16), mat(0xffffff, { map: ringTex }), false);
  top.rotation.x = -Math.PI / 2; top.position.y = 0.605; g.add(top);
  // Pilze
  [[0.75, 0.2, 0.17], [0.6, 0.5, 0.11]].forEach(([px, pz, s]) => {
    const stalk = mesh(new THREE.CylinderGeometry(s * 0.3, s * 0.35, s * 1.2, 8), mat(0xf2ead8)); stalk.position.set(px, s * 0.6, pz); g.add(stalk);
    const cap = mesh(new THREE.SphereGeometry(s, 12, 6, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xb5301f, { roughness: 0.5 }));
    cap.position.set(px, s * 1.1, pz); cap.scale.y = 0.7; g.add(cap);
  });
  addObstacle({ shape: "circle", x, z, r: 0.6, h: 0.6 }, g);
}

function fence(x, z, len, alongX) {
  const g = new THREE.Group(), wood = mat(0xffffff, { map: woodTex, roughness: 1 });
  const n = Math.round(len / 1.2);
  for (let i = 0; i <= n; i++) {
    const p = mesh(new THREE.BoxGeometry(0.13, 1.0, 0.13), wood);
    p.position.set(-len / 2 + i * len / n, 0.5, 0); p.rotation.y = rand(-0.1, 0.1); p.rotation.z = rand(-0.03, 0.03); g.add(p);
  }
  [0.4, 0.8].forEach(y => { const r = mesh(new THREE.BoxGeometry(len + 0.2, 0.1, 0.05), wood); r.position.set(0, y, 0.08); g.add(r); });
  if (!alongX) g.rotation.y = Math.PI / 2;
  addObstacle({ shape: "box", x, z, hw: alongX ? len / 2 : 0.12, hd: alongX ? 0.12 : len / 2, h: 1.0 }, g);
}

// ---------------- Bäume ----------------
// Blätterkugel: verbeulte Kugel mit weichen Normalen und Farbverlauf (unten dunkler)
const foliageMat = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.9 });
const foliageCache = {};
function foliageGeometry(r, variant) {
  const key = variant % 4;
  if (!foliageCache[key]) {
    const geo = new THREE.IcosahedronGeometry(1, 2);
    const p = geo.attributes.position, nor = geo.attributes.normal, colors = [];
    const c = new THREE.Color();
    for (let i = 0; i < p.count; i++) {
      const vx = p.getX(i), vy = p.getY(i), vz = p.getZ(i);
      const n = 1 + noise3(vx * 3 + key * 7, vy * 3, vz * 3) * 0.16 + noise3(vx * 9, vy * 9 + key, vz * 9) * 0.06;
      p.setXYZ(i, vx * n, vy * n, vz * n);
      nor.setXYZ(i, vx, vy, vz);             // weiche Kugel-Normalen
      const shade = 0.55 + vy * 0.25 + noise3(vx * 12, vy * 12, vz * 12 + key) * 0.12;
      c.setHSL(0.27 + key * 0.012, 0.5, 0.13 + shade * 0.17);
      colors.push(c.r, c.g, c.b);
    }
    geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    foliageCache[key] = geo;
  }
  return foliageCache[key];
}
// Blätterkugel als Mesh (Geometrie wird geteilt → spart Speicher)
function foliage(r, variant) {
  const m = mesh(foliageGeometry(r, variant), foliageMat);
  m.scale.setScalar(r);
  return m;
}
function tree(x, z, s = 1) {
  const g = new THREE.Group();
  const bark = barkTex.clone(); bark.needsUpdate = true; bark.repeat.set(2, 2);
  const trunk = mesh(new THREE.CylinderGeometry(0.2 * s, 0.38 * s, 3.2 * s, 12), mat(0xffffff, { map: bark, roughness: 1 }));
  trunk.position.y = 1.6 * s; g.add(trunk);
  // zwei Äste
  [[1, 0.6], [-1, -0.5]].forEach(([d, rot]) => {
    const br = mesh(new THREE.CylinderGeometry(0.06 * s, 0.12 * s, 1.4 * s, 8), mat(0xffffff, { map: bark }));
    br.position.set(d * 0.45 * s, 2.7 * s, 0); br.rotation.z = -d * 0.7; br.rotation.y = rot; g.add(br);
  });
  const blobs = [[0, 4.2, 0, 1.6], [1.1, 3.7, 0.3, 1.2], [-1.1, 3.8, -0.2, 1.25], [0.3, 3.5, 1.0, 1.1],
                 [-0.3, 3.6, -1.0, 1.1], [0.5, 4.8, -0.3, 1.0], [-0.6, 4.6, 0.5, 0.95]];
  blobs.forEach(([a, b, c, r], i) => {
    const l = foliage(r * s, i + Math.floor(Math.abs(x)));
    l.position.set(a * s, b * s, c * s); g.add(l);
  });
  g.rotation.y = Math.random() * Math.PI * 2;
  addObstacle({ shape: "circle", x, z, r: 0.45 * s, h: 99 }, g);   // zu hoch zum Drüberspringen
}

// ---------------- Testparcours ----------------
rock(4, 3, 0.65, 0.5);
log(-4, 4, 3, true);
bush(6, -3);
hay(-6, -4);
stump(1, 8);
fence(0, -7, 5, true);
rock(-9, 3, 0.95, 0.85);
log(9, 5, 2.5, false);
stump(-2, -11);
hay(10, -8);
rock(-13, -12, 1.4, 1.1);
rock(16, -12, 0.5, 0.35);

// Bäume am Rand (Wiesengrenze) und ein paar innen
for (let i = 0; i < 24; i++) {
  const a = i / 24 * Math.PI * 2 + rand(-0.05, 0.05);
  const r = WORLD_R + rand(1.5, 4);
  tree(Math.cos(a) * r, Math.sin(a) * r, rand(0.9, 1.4));
}
for (let i = 0; i < 30; i++) {                 // Wald dahinter (nur Deko)
  const a = Math.random() * Math.PI * 2, r = rand(38, 48);
  tree(Math.cos(a) * r, Math.sin(a) * r, rand(1, 1.6));
}
tree(13, 12, 1.1); tree(-14, -9, 1.0); tree(-12, 14, 1.2);
