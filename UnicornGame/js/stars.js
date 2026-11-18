// =====================================================================
//  stars.js – Sterne zum Einsammeln
// =====================================================================

const starShape = new THREE.Shape();
for (let i = 0; i < 10; i++) {
  const r = i % 2 ? 0.17 : 0.4, a = Math.PI / 2 + i * Math.PI / 5;
  i ? starShape.lineTo(Math.cos(a) * r, Math.sin(a) * r) : starShape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
}
const starGeo = new THREE.ExtrudeGeometry(starShape, { depth: 0.07, bevelEnabled: true, bevelThickness: 0.05, bevelSize: 0.035, bevelSegments: 3 });
starGeo.center();
const starMat = new THREE.MeshStandardMaterial({ color: 0xffcf40, metalness: 0.7, roughness: 0.22, emissive: 0xff9a00, emissiveIntensity: 0.35 });
const glowTex = canvasTexture(64, (g, S) => {
  const grd = g.createRadialGradient(S / 2, S / 2, 0, S / 2, S / 2, S / 2);
  grd.addColorStop(0, "rgba(255,240,180,0.9)"); grd.addColorStop(0.4, "rgba(255,220,120,0.35)"); grd.addColorStop(1, "rgba(255,220,120,0)");
  g.fillStyle = grd; g.fillRect(0, 0, S, S);
});
const glowMat = new THREE.SpriteMaterial({ map: glowTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending });

// [x, z, Höhe] – niedrige Sterne liegen frei, hohe muss man erspringen
const STAR_SPOTS = [
  [2, -3, 1.0], [-3, 0, 1.0], [7, 1, 1.0], [-8, -1, 1.0], [3, 12, 1.0],
  [-5, 10, 1.0], [12, -2, 1.0], [-12, 6, 1.0], [15, 6, 1.0], [-16, -6, 1.0],
  [-6, -4, 2.75],  // auf dem Heuballen
  [10, -8, 2.75],  // auf dem zweiten Heuballen
  [-9, 3, 2.2],    // auf dem großen Stein
  [1, 8, 1.95],    // auf dem Baumstumpf
  [0, -7, 2.5],    // über dem Zaun
  [0, 4, 2.8],     // hoch in der Luft
];
const stars = STAR_SPOTS.map(([x, z, y], i) => {
  const g = new THREE.Group();
  const s = mesh(starGeo, starMat); g.add(s);
  const glow = new THREE.Sprite(glowMat); glow.scale.setScalar(1.6); g.add(glow);
  g.position.set(x, y, z);
  scene.add(g);
  return { g, s, baseY: y, seed: i, collected: false };
});

let starCount = 0;
const scoreEl = document.getElementById("score"), winEl = document.getElementById("win");
function updateScore() { scoreEl.textContent = `⭐ ${starCount} / ${stars.length}`; }
function resetStars() {
  stars.forEach(st => { st.collected = false; st.g.visible = true; st.g.scale.setScalar(1); });
  starCount = 0; updateScore(); winEl.style.display = "none";
}
updateScore();
winEl.addEventListener("pointerdown", resetStars);
window.addEventListener("keydown", e => { if (e.code === "KeyR") resetStars(); });

// kleiner "Pling"-Ton (Browser erlauben Ton erst nach der ersten Taste)
let audio = null;
function chime(final) {
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const notes = final ? [784, 988, 1175, 1568, 1976] : [1047, 1568];
    notes.forEach((f, i) => {
      const o = audio.createOscillator(), gn = audio.createGain();
      const t0 = audio.currentTime + i * 0.08;
      o.type = "sine"; o.frequency.value = f;
      gn.gain.setValueAtTime(0.0001, t0);
      gn.gain.exponentialRampToValueAtTime(0.18, t0 + 0.01);
      gn.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.35);
      o.connect(gn).connect(audio.destination);
      o.start(t0); o.stop(t0 + 0.4);
    });
  } catch (e) { /* kein Ton möglich – egal */ }
}

function updateStars(dt, t) {
  const center = new THREE.Vector3(uni.pos.x, uni.pos.y + 1.4, uni.pos.z);
  for (const st of stars) {
    if (!st.g.visible) continue;
    if (st.collected) {             // Einsammel-Animation: hochfliegen & schrumpfen
      st.g.position.y += dt * 4;
      st.g.scale.multiplyScalar(1 - dt * 5);
      st.s.rotation.y += dt * 20;
      if (st.g.scale.x < 0.05) st.g.visible = false;
      continue;
    }
    st.s.rotation.y = t * 2 + st.seed;
    st.g.position.y = st.baseY + Math.sin(t * 2 + st.seed) * 0.12;
    if (st.g.position.distanceTo(center) < 1.3) {
      st.collected = true;
      starCount++; updateScore();
      scoreEl.classList.add("pop"); setTimeout(() => scoreEl.classList.remove("pop"), 150);
      for (let i = 0; i < 18; i++) {
        const a = Math.random() * Math.PI * 2, e = Math.random() * 2 - 0.5;
        spawnParticle(st.g.position, i % 3 ? 0xffd700 : RAINBOW[i % 7],
          new THREE.Vector3(Math.cos(a) * 3, 2 + e * 2, Math.sin(a) * 3), 0.8, 1.3, 6);
      }
      const done = starCount === stars.length;
      chime(done);
      if (done) setTimeout(() => winEl.style.display = "block", 500);
    }
  }
}
