// =====================================================================
//  stars.js – Sterne zum Einsammeln (Positionen kommen aus level.js)
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

const stars = STAR_SPOTS.map(([x, z, y], i) => {
  const g = new THREE.Group();
  const s = mesh(starGeo, starMat); g.add(s);
  const glow = new THREE.Sprite(glowMat); glow.scale.setScalar(1.6); g.add(glow);
  g.position.set(x, y, z);
  scene.add(g);
  return { g, s, baseY: y, seed: i, zone: zoneAt(z), collected: false };
});

let starCount = 0;
const zoneStars = ZONES.map(() => 0);     // gesammelte Sterne je Gebiet
const scoreEl = document.getElementById("score");
function updateScore() { scoreEl.textContent = `⭐ ${starCount}`; }
updateScore();

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
    // weit entfernte Sterne nicht animieren (spart Rechenzeit)
    if (Math.abs(st.g.position.z - uni.pos.z) > 60) continue;
    st.s.rotation.y = t * 2 + st.seed;
    st.g.position.y = st.baseY + Math.sin(t * 2 + st.seed) * 0.12;
    if (game.state === "play" && st.g.position.distanceTo(center) < 1.3) {
      st.collected = true;
      starCount++; zoneStars[st.zone]++;
      updateScore(); updateQuestHud();
      scoreEl.classList.add("pop"); setTimeout(() => scoreEl.classList.remove("pop"), 150);
      for (let i = 0; i < 18; i++) {
        const a = Math.random() * Math.PI * 2, e = Math.random() * 2 - 0.5;
        spawnParticle(st.g.position, i % 3 ? 0xffd700 : RAINBOW[i % 7],
          new THREE.Vector3(Math.cos(a) * 3, 2 + e * 2, Math.sin(a) * 3), 0.8, 1.3, 6);
      }
      Sound.star(zoneStars[st.zone] === STARS_NEEDED);
    }
  }
}
