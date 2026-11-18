// =====================================================================
//  game.js – Eingabe, Physik, Kamera, Partikel und die Spielschleife
// =====================================================================

const WALK = 3.5, RUN = 9, GRAVITY = 25, JUMP = 9.2, RADIUS = 0.6;

// ---------------- Eingabe: Tastatur & Maus ----------------
const keys = {};
window.addEventListener("keydown", e => {
  keys[e.code] = true;
  if (e.code.startsWith("Arrow") || e.code === "Space") e.preventDefault();
});
window.addEventListener("keyup", e => { keys[e.code] = false; });

const cam = { yaw: Math.PI, pitch: 0.3, dist: 6.5, target: new THREE.Vector3(0, 1.5, 0) };

let dragging = false, lastX = 0, lastY = 0;
renderer.domElement.addEventListener("mousedown", e => { dragging = true; lastX = e.clientX; lastY = e.clientY; });
window.addEventListener("mouseup", () => { dragging = false; });
window.addEventListener("mousemove", e => {
  if (!dragging) return;
  cam.yaw -= (e.clientX - lastX) * 0.006;
  cam.pitch = Math.max(-0.05, Math.min(1.25, cam.pitch + (e.clientY - lastY) * 0.005));
  lastX = e.clientX; lastY = e.clientY;
});
renderer.domElement.addEventListener("wheel", e => {
  cam.dist = Math.max(3.5, Math.min(14, cam.dist + e.deltaY * 0.01));
  e.preventDefault();
}, { passive: false });

// ---------------- Eingabe: Touch (links Joystick, rechts Kamera) ----------------
const touch = { x: 0, y: 0, jump: false, run: false, look: false };
if ("ontouchstart" in window || navigator.maxTouchPoints > 0) {
  document.getElementById("touch").style.display = "flex";
  const stick = document.getElementById("stick"), knob = stick.firstElementChild;
  let stickId = null, camId = null, sx = 0, sy = 0;
  renderer.domElement.addEventListener("touchstart", e => {
    for (const t of e.changedTouches) {
      if (t.clientX < window.innerWidth / 2 && stickId === null) {
        stickId = t.identifier; sx = t.clientX; sy = t.clientY;
        stick.style.left = sx + "px"; stick.style.top = sy + "px"; stick.style.display = "block";
      } else if (camId === null) { camId = t.identifier; lastX = t.clientX; lastY = t.clientY; }
    }
    e.preventDefault();
  }, { passive: false });
  renderer.domElement.addEventListener("touchmove", e => {
    for (const t of e.changedTouches) {
      if (t.identifier === stickId) {
        let dx = t.clientX - sx, dy = t.clientY - sy;
        const len = Math.hypot(dx, dy), max = 45;
        if (len > max) { dx *= max / len; dy *= max / len; }
        touch.x = dx / max; touch.y = dy / max;
        knob.style.transform = `translate(${dx}px, ${dy}px)`;
      } else if (t.identifier === camId) {
        cam.yaw -= (t.clientX - lastX) * 0.008;
        cam.pitch = Math.max(-0.05, Math.min(1.25, cam.pitch + (t.clientY - lastY) * 0.006));
        lastX = t.clientX; lastY = t.clientY;
      }
    }
    e.preventDefault();
  }, { passive: false });
  renderer.domElement.addEventListener("touchend", e => {
    for (const t of e.changedTouches) {
      if (t.identifier === stickId) { stickId = null; touch.x = touch.y = 0; stick.style.display = "none"; knob.style.transform = ""; }
      if (t.identifier === camId) camId = null;
    }
  });
  const hold = (id, prop) => {
    const b = document.getElementById(id);
    b.addEventListener("pointerdown", e => { touch[prop] = true; e.preventDefault(); });
    ["pointerup", "pointerleave", "pointercancel"].forEach(ev => b.addEventListener(ev, () => touch[prop] = false));
  };
  hold("bJump", "jump"); hold("bLook", "look");
  document.getElementById("bRun").addEventListener("pointerdown", e => {
    touch.run = !touch.run; e.currentTarget.classList.toggle("on", touch.run);
  });
}

const input = {
  get fwd()  { return (keys.KeyW || keys.ArrowUp ? 1 : 0) - (keys.KeyS || keys.ArrowDown ? 1 : 0) - touch.y; },
  get side() { return (keys.KeyD || keys.ArrowRight ? 1 : 0) - (keys.KeyA || keys.ArrowLeft ? 1 : 0) + touch.x; },
  get jump() { return keys.Space || touch.jump; },
  get run()  { return keys.ShiftLeft || keys.ShiftRight || touch.run; },
  get look() { return keys.KeyE || touch.look; },
};

// ---------------- Partikel (Glitzer & Staub) ----------------
const particles = [];
const sparkGeo = new THREE.OctahedronGeometry(0.06, 0);
function spawnParticle(pos, color, vel, life, size, gravity) {
  const m = new THREE.Mesh(sparkGeo, new THREE.MeshBasicMaterial({ color, transparent: true }));
  m.position.copy(pos); m.scale.setScalar(size);
  scene.add(m);
  particles.push({ m, vel, life, max: life, gravity });
}
function dust() {
  const a = Math.random() * Math.PI * 2;
  spawnParticle(uni.pos.clone().add(new THREE.Vector3(Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5)), 0xb8a888,
    new THREE.Vector3(Math.cos(a) * 1.5, 0.8 + Math.random(), Math.sin(a) * 1.5), 0.5, 1.6, 4);
}

// ---------------- Kollision ----------------
// Wie weit steckt das Einhorn (Kreis) im Hindernis? → Schiebevektor oder null
function penetration(o, x, z) {
  let cx, cz;
  if (o.shape === "circle") { cx = o.x; cz = o.z; }
  else { cx = Math.max(o.x - o.hw, Math.min(o.x + o.hw, x)); cz = Math.max(o.z - o.hd, Math.min(o.z + o.hd, z)); }
  const dx = x - cx, dz = z - cz, d = Math.hypot(dx, dz);
  const need = RADIUS + (o.shape === "circle" ? o.r : 0);
  if (d >= need) return null;
  if (d < 1e-4) return { x: need, z: 0 };
  return { x: dx / d * (need - d), z: dz / d * (need - d) };
}
// höchste Oberfläche unter den Hufen
function supportHeight(p) {
  let s = 0;
  for (const o of obstacles) if (o.h <= p.y + 0.05 && penetration(o, p.x, p.z)) s = Math.max(s, o.h);
  return s;
}
const angleDiff = (a, b) => Math.atan2(Math.sin(b - a), Math.cos(b - a));

// ---------------- Update ----------------
function update(dt, t) {
  const u = uni;

  // Bewegung relativ zur Kamera
  const f = input.fwd, s = input.side;
  const fx = -Math.sin(cam.yaw), fz = -Math.cos(cam.yaw);   // vorwärts (weg von der Kamera)
  const rx = Math.cos(cam.yaw), rz = -Math.sin(cam.yaw);    // rechts
  const mx = fx * f + rx * s, mz = fz * f + rz * s;
  const mlen = Math.min(1, Math.hypot(mx, mz));
  const moving = mlen > 0.1;
  const speed = input.run ? RUN : WALK;
  if (moving) u.heading += angleDiff(u.heading, Math.atan2(mx, mz)) * Math.min(1, dt * (u.onGround ? 8 : 3));
  // läuft immer in Blickrichtung → dreht sich in Kurven natürlich
  const tvx = moving ? Math.sin(u.heading) * speed * mlen : 0;
  const tvz = moving ? Math.cos(u.heading) * speed * mlen : 0;
  const acc = u.onGround ? 6 : 2.5;
  u.vel.x += (tvx - u.vel.x) * Math.min(1, dt * acc);
  u.vel.z += (tvz - u.vel.z) * Math.min(1, dt * acc);

  // Springen (mit Anlauf höher)
  const hs = Math.hypot(u.vel.x, u.vel.z);
  if (input.jump && u.onGround && !u.jumpHeld) {
    u.vel.y = JUMP + hs * 0.18;
    u.onGround = false;
    for (let i = 0; i < 10; i++) dust();
  }
  u.jumpHeld = input.jump;
  if (!input.jump && u.vel.y > 3) u.vel.y -= GRAVITY * 1.2 * dt;  // kurz drücken = kleiner Sprung

  // horizontal bewegen + aus Hindernissen herausschieben
  u.pos.x += u.vel.x * dt; u.pos.z += u.vel.z * dt;
  for (const o of obstacles) {
    if (u.pos.y >= o.h - 0.05) continue;
    const p = penetration(o, u.pos.x, u.pos.z);
    if (p) { u.pos.x += p.x; u.pos.z += p.z; }
  }
  const r = Math.hypot(u.pos.x, u.pos.z);
  if (r > WORLD_R) { u.pos.x *= WORLD_R / r; u.pos.z *= WORLD_R / r; }

  // vertikal + landen
  const ground = supportHeight(u.pos);
  u.vel.y -= GRAVITY * dt;
  u.pos.y += u.vel.y * dt;
  if (u.pos.y <= ground) {
    if (!u.onGround && u.vel.y < -4) {
      u.squash = Math.min(1, -u.vel.y / 14);
      for (let i = 0; i < 12; i++) dust();
    }
    u.pos.y = ground; u.vel.y = 0; u.onGround = true;
  } else u.onGround = false;

  animateUnicorn(dt, t);
  updateStars(dt, t);

  // Regenbogen-Glitzer beim Rennen
  const sp = Math.hypot(u.vel.x, u.vel.z);
  if (sp > WALK * 1.6) {
    const back = new THREE.Vector3(Math.sin(u.heading), 0, Math.cos(u.heading)).multiplyScalar(-1.3);
    spawnParticle(u.pos.clone().add(back).add(new THREE.Vector3((Math.random() - .5) * .6, 1.0 + Math.random() * .8, (Math.random() - .5) * .6)),
      RAINBOW[Math.floor(Math.random() * 7)], new THREE.Vector3(0, 0.5 + Math.random(), 0), 0.9, 1 + Math.random(), -1);
  }
  // Horn funkelt ab und zu
  if (Math.random() < 0.04) {
    const hp = new THREE.Vector3(0, 0.3, 0); uni.horn.localToWorld(hp);
    spawnParticle(hp, 0xffffff, new THREE.Vector3(0, 0.5, 0), 0.6, 0.8, 0);
  }

  // Partikel bewegen
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life -= dt;
    p.vel.y -= p.gravity * dt;
    p.m.position.addScaledVector(p.vel, dt);
    p.m.rotation.y += dt * 4;
    p.m.material.opacity = Math.max(0, p.life / p.max);
    if (p.life <= 0) { scene.remove(p.m); p.m.material.dispose(); particles.splice(i, 1); }
  }

  // Wolken ziehen langsam
  clouds.forEach(c => { c.position.x += dt * 0.8; if (c.position.x > 230) c.position.x = -230; });

  // --- Third-Person-Kamera ---
  cam.target.lerp(new THREE.Vector3(u.pos.x, u.pos.y + 1.5, u.pos.z), Math.min(1, dt * 8));
  if (moving && !dragging && f > 0.1) {      // dreht sich beim Laufen langsam hinter das Einhorn
    cam.yaw += angleDiff(cam.yaw, u.heading + Math.PI) * Math.min(1, dt * 1.2);
  }
  const cp = Math.cos(cam.pitch);
  camera.position.set(
    cam.target.x + Math.sin(cam.yaw) * cp * cam.dist,
    cam.target.y + Math.sin(cam.pitch) * cam.dist,
    cam.target.z + Math.cos(cam.yaw) * cp * cam.dist);
  if (camera.position.y < 0.4) camera.position.y = 0.4;
  camera.lookAt(cam.target);
  sky.position.copy(camera.position);

  // Sonne & Schatten folgen dem Einhorn
  sun.position.copy(u.pos).addScaledVector(SUN_DIR, 40);
  sun.target.position.copy(u.pos);
  windTime.value = t;
}

// ---------------- Spielschleife ----------------
const clock = new THREE.Clock();
function loop() {
  const dt = Math.min(0.033, clock.getDelta());
  update(dt, clock.elapsedTime);
  renderer.render(scene, camera);
  requestAnimationFrame(loop);
}
loop();
