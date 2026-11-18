// =====================================================================
//  paint.js – das Malbuch: Ausmalbilder mit Farbeimer und Pinsel
//  Klick mit dem Farbeimer füllt einen ganzen Abschnitt (zwischen den Linien).
// =====================================================================

const PAINT_COLORS = [
  ["#ff3b3b", "Rot"], ["#ff9a1f", "Orange"], ["#ffe11f", "Gelb"], ["#7ed957", "Hellgrün"], ["#1f8a3a", "Dunkelgrün"],
  ["#5ec8ff", "Hellblau"], ["#2b5cd6", "Dunkelblau"], ["#9b5de5", "Lila"], ["#ff7eb9", "Rosa"], ["#8b5a2b", "Braun"],
];
const PW = 900, PH = 650;                                   // Zeichenfläche

const paint = {
  color: PAINT_COLORS[8][0], tool: "fill", size: 14, picture: 0, undo: [],
  colorCv: document.createElement("canvas"), lineCv: document.createElement("canvas"),
};

// ---------------- Zeichen-Helfer für die Linienbilder ----------------
function lineCtx() {
  const g = paint.lineCv.getContext("2d");
  g.clearRect(0, 0, PW, PH);
  g.lineWidth = 6; g.lineJoin = "round"; g.lineCap = "round"; g.strokeStyle = "#222";
  return g;
}
const P = {
  circle(g, x, y, r) { g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.stroke(); },
  ellipse(g, x, y, rx, ry, rot = 0) { g.beginPath(); g.ellipse(x, y, rx, ry, rot, 0, Math.PI * 2); g.stroke(); },
  rect(g, x, y, w, h, r = 0) { g.beginPath(); g.roundRect ? g.roundRect(x, y, w, h, r) : g.rect(x, y, w, h); g.stroke(); },
  poly(g, pts, close = true) { g.beginPath(); pts.forEach(([x, y], i) => i ? g.lineTo(x, y) : g.moveTo(x, y)); if (close) g.closePath(); g.stroke(); },
  line(g, x1, y1, x2, y2) { g.beginPath(); g.moveTo(x1, y1); g.lineTo(x2, y2); g.stroke(); },
  star(g, x, y, r1, r2, n = 5) { const pts = []; for (let i = 0; i < n * 2; i++) { const r = i % 2 ? r2 : r1, a = -Math.PI / 2 + i * Math.PI / n; pts.push([x + Math.cos(a) * r, y + Math.sin(a) * r]); } P.poly(g, pts); },
  heart(g, x, y, s) { g.beginPath(); g.moveTo(x, y + s); g.bezierCurveTo(x - s * 2, y - s * 0.5, x - s * 0.6, y - s * 1.8, x, y - s * 0.5); g.bezierCurveTo(x + s * 0.6, y - s * 1.8, x + s * 2, y - s * 0.5, x, y + s); g.stroke(); },
  ground(g, y) { g.beginPath(); g.moveTo(0, y); g.bezierCurveTo(250, y - 30, 600, y + 30, PW, y - 10); g.stroke(); },
  eye(g, x, y) { g.fillStyle = "#222"; g.beginPath(); g.arc(x, y, 7, 0, Math.PI * 2); g.fill(); },
  smile(g, x, y, w) { g.beginPath(); g.arc(x, y - w * 0.6, w, 0.25 * Math.PI, 0.75 * Math.PI); g.stroke(); },
};

const PICTURES = [
  { name: "Weihnachtsbaum", icon: "🎄", draw(g) {
    P.ground(g, 560);
    P.poly(g, [[450, 108], [340, 240], [560, 240]]);
    P.poly(g, [[450, 180], [290, 360], [610, 360]]);
    P.poly(g, [[450, 270], [250, 490], [650, 490]]);
    P.rect(g, 415, 490, 70, 60);
    P.star(g, 450, 60, 45, 20);
    [[440, 215, 14], [480, 310, 18], [380, 330, 16], [520, 430, 20], [360, 450, 18], [450, 400, 16]].forEach(([x, y, r]) => P.circle(g, x, y, r));
    // Geschenke
    P.rect(g, 140, 470, 120, 90); P.line(g, 200, 470, 200, 560); P.line(g, 140, 510, 260, 510); P.ellipse(g, 180, 455, 22, 14); P.ellipse(g, 220, 455, 22, 14);
    P.rect(g, 650, 450, 100, 110); P.line(g, 700, 450, 700, 560); P.ellipse(g, 682, 437, 18, 12); P.ellipse(g, 718, 437, 18, 12);
    P.star(g, 100, 100, 25, 10); P.star(g, 800, 140, 30, 12); P.circle(g, 760, 70, 40);
  } },
  { name: "Schneemann", icon: "⛄", draw(g) {
    P.ground(g, 560);
    P.circle(g, 450, 450, 120); P.circle(g, 450, 280, 85); P.circle(g, 450, 150, 60);
    P.rect(g, 400, 12, 100, 68); P.rect(g, 370, 78, 160, 18);
    P.poly(g, [[450, 150], [520, 165], [450, 170]]);
    P.eye(g, 430, 135); P.eye(g, 470, 135); P.smile(g, 450, 190, 22);
    P.poly(g, [[385, 205], [515, 205], [520, 235], [380, 235]]); P.poly(g, [[480, 230], [520, 230], [530, 320], [490, 320]]);
    [260, 300, 340].forEach(y => P.circle(g, 450, y, 10));
    P.line(g, 370, 280, 250, 210); P.line(g, 290, 235, 265, 190); P.line(g, 530, 280, 650, 210); P.line(g, 610, 235, 640, 195);
    [[120, 120], [760, 90], [700, 320], [180, 340], [820, 450]].forEach(([x, y]) => P.star(g, x, y, 22, 9, 6));
  } },
  { name: "Rentier Sven", icon: "🦌", draw(g) {
    P.ground(g, 590);
    P.ellipse(g, 420, 380, 190, 100);
    [[290, 440], [360, 450], [480, 450], [550, 440]].forEach(([x, y]) => { P.rect(g, x - 18, y, 36, 130, 10); P.rect(g, x - 22, y + 115, 44, 22, 6); });
    P.poly(g, [[540, 330], [595, 200], [660, 215], [590, 350]]);         // Hals
    P.circle(g, 640, 175, 60);                                               // Kopf
    P.ellipse(g, 705, 205, 42, 30); P.circle(g, 742, 200, 13);              // Schnauze & Nase
    P.eye(g, 640, 165); P.smile(g, 700, 228, 14);
    P.ellipse(g, 582, 140, 28, 13, -0.5); P.ellipse(g, 692, 128, 28, 13, 0.6);   // Ohren
    P.poly(g, [[615, 118], [590, 50], [560, 25]], false); P.line(g, 600, 78, 565, 68);
    P.poly(g, [[662, 116], [690, 48], [725, 22]], false); P.line(g, 678, 78, 715, 70);
    P.poly(g, [[560, 290], [650, 300], [642, 328], [552, 316]]);                  // Schal
    P.rect(g, 340, 268, 150, 40, 10);                                       // Sattel
    P.ellipse(g, 235, 360, 30, 22);                                         // Schwanz
    [[150, 120], [820, 90], [250, 60]].forEach(([x, y]) => P.star(g, x, y, 24, 10));
  } },
  { name: "Einhorn Rosi", icon: "🦄", draw(g) {
    P.ground(g, 590);
    P.ellipse(g, 420, 390, 180, 95);
    [[290, 450], [360, 460], [480, 460], [550, 450]].forEach(([x, y]) => { P.rect(g, x - 18, y, 36, 120, 10); P.rect(g, x - 22, y + 108, 44, 22, 6); });
    P.poly(g, [[540, 335], [600, 195], [668, 225], [585, 360]]);         // Hals
    P.ellipse(g, 645, 185, 72, 50, 0.3);                                    // Kopf
    P.ellipse(g, 708, 222, 34, 27, 0.3);                                    // Schnauze
    P.eye(g, 645, 172); P.smile(g, 705, 245, 13);
    P.poly(g, [[600, 140], [598, 100], [624, 136]]);                         // Ohr
    P.poly(g, [[622, 138], [652, 132], [660, 42]]); P.line(g, 630, 115, 652, 108); P.line(g, 640, 88, 656, 82);   // Horn
    [[585, 195, 26], [568, 245, 26], [552, 295, 26]].forEach(([x, y, r]) => P.circle(g, x, y, r));   // Mähne
    [[240, 355, 32], [214, 405, 30], [212, 458, 28]].forEach(([x, y, r]) => P.circle(g, x, y, r));   // Schweif
    P.heart(g, 140, 140, 30); P.heart(g, 800, 120, 26); P.heart(g, 300, 120, 20);
  } },
  { name: "Prinzessin Alma", icon: "👸", draw(g) {
    P.ground(g, 600);
    P.poly(g, [[450, 280], [330, 570], [570, 570]]);
    P.rect(g, 410, 230, 80, 70, 14);
    P.circle(g, 450, 165, 62);
    P.eye(g, 428, 160); P.eye(g, 472, 160); P.smile(g, 450, 205, 20);
    P.poly(g, [[400, 115], [410, 60], [430, 95], [450, 50], [470, 95], [490, 60], [500, 115]]);
    P.ellipse(g, 370, 210, 26, 70); P.ellipse(g, 530, 210, 26, 70);
    P.poly(g, [[410, 240], [350, 330], [370, 345], [420, 270]]); P.poly(g, [[490, 240], [550, 330], [530, 345], [480, 270]]);
    P.circle(g, 360, 345, 16); P.circle(g, 540, 345, 16);
    P.line(g, 360, 470, 540, 470); P.heart(g, 450, 400, 22);
    P.star(g, 150, 150, 30, 12); P.star(g, 760, 140, 30, 12); P.heart(g, 180, 420, 24); P.heart(g, 730, 420, 24);
  } },
  { name: "Haus vom Weihnachtsmann", icon: "🏠", draw(g) {
    P.ground(g, 580);
    P.rect(g, 250, 300, 400, 260);
    P.poly(g, [[220, 310], [450, 130], [680, 310]]);
    P.rect(g, 540, 150, 55, 110);
    P.rect(g, 410, 420, 80, 140, 8);
    P.rect(g, 290, 360, 80, 70); P.line(g, 330, 360, 330, 430); P.line(g, 290, 395, 370, 395);
    P.rect(g, 530, 360, 80, 70); P.line(g, 570, 360, 570, 430); P.line(g, 530, 395, 610, 395);
    P.circle(g, 470, 490, 7);
    P.circle(g, 750, 90, 45); P.ellipse(g, 770, 80, 35, 40);
    [[120, 100], [250, 70], [640, 60], [830, 200], [100, 250]].forEach(([x, y]) => P.star(g, x, y, 20, 8));
    P.poly(g, [[130, 560], [100, 480], [160, 480]]); P.poly(g, [[130, 500], [95, 420], [165, 420]]); P.poly(g, [[130, 440], [105, 380], [155, 380]]);
    P.poly(g, [[790, 560], [760, 480], [820, 480]]); P.poly(g, [[790, 500], [755, 420], [825, 420]]);
  } },
];

// ---------------- Malbuch öffnen & Bild wechseln ----------------
const paintEl = document.getElementById("paint");
const pc = document.getElementById("paintCanvas"), pctx = pc.getContext("2d");
pc.width = PW; pc.height = PH;
paint.colorCv.width = paint.lineCv.width = PW; paint.colorCv.height = paint.lineCv.height = PH;

function render() {
  pctx.fillStyle = "#fff"; pctx.fillRect(0, 0, PW, PH);
  pctx.drawImage(paint.colorCv, 0, 0);
  pctx.drawImage(paint.lineCv, 0, 0);
}
function loadPicture(i) {
  paint.picture = i;
  const c = paint.colorCv.getContext("2d");
  c.fillStyle = "#fff"; c.fillRect(0, 0, PW, PH);
  PICTURES[i].draw(lineCtx());
  paint.mask = paint.lineCv.getContext("2d").getImageData(0, 0, PW, PH).data;
  paint.undo = [];
  document.querySelectorAll("#pictures button").forEach((b, k) => b.classList.toggle("on", k === i));
  render();
}
function openPaint(fromStory) {
  ch2.state = "paint";
  game.state = "paint";
  paint.fromStory = fromStory;
  paintEl.style.display = "flex";
  document.getElementById("ch2Hud").style.display = "none";
  document.getElementById("title").style.display = "none";
  Sound.init(); Sound.play("zauber");
  if (!paint.started) { paint.started = true; loadPicture(0); }
  Voice.speak("Such dir eine Farbe aus und tippe auf eine Fläche, dann wird sie bunt.");
}

// Werkzeugleiste aufbauen
(function buildToolbar() {
  const pics = document.getElementById("pictures");
  PICTURES.forEach((p, i) => { const b = document.createElement("button"); b.textContent = p.icon; b.title = p.name; b.onclick = () => loadPicture(i); pics.appendChild(b); });
  const cols = document.getElementById("colors");
  PAINT_COLORS.forEach(([c, name], i) => {
    const b = document.createElement("button"); b.style.background = c; b.title = name;
    b.onclick = () => { paint.color = c; if (paint.tool === "erase") setTool("fill"); document.querySelectorAll("#colors button").forEach(x => x.classList.remove("on")); b.classList.add("on"); };
    if (i === 8) b.classList.add("on");
    cols.appendChild(b);
  });
})();
function setTool(t, size) {
  paint.tool = t; if (size) paint.size = size;
  document.querySelectorAll("#tools [data-tool]").forEach(b => b.classList.toggle("on", b.dataset.tool === t && (!b.dataset.size || +b.dataset.size === paint.size)));
}
document.querySelectorAll("#tools [data-tool]").forEach(b => b.onclick = () => setTool(b.dataset.tool, +b.dataset.size || 0));
setTool("fill");

// ---------------- Malen ----------------
function pushUndo() {
  paint.undo.push(paint.colorCv.getContext("2d").getImageData(0, 0, PW, PH));
  if (paint.undo.length > 20) paint.undo.shift();
}
function canvasPos(e) {
  const r = pc.getBoundingClientRect();
  return [Math.round((e.clientX - r.left) / r.width * PW), Math.round((e.clientY - r.top) / r.height * PH)];
}
// Farbeimer: füllt alles bis zu den schwarzen Linien
function floodFill(x, y, hex) {
  const mask = paint.mask, isLine = i => mask[i * 4 + 3] > 90;
  if (x < 0 || y < 0 || x >= PW || y >= PH || isLine(y * PW + x)) return;
  const c = paint.colorCv.getContext("2d"), img = c.getImageData(0, 0, PW, PH), d = img.data;
  const r = parseInt(hex.slice(1, 3), 16), gr = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
  const seen = new Uint8Array(PW * PH), stack = [y * PW + x];
  const set = i => { d[i * 4] = r; d[i * 4 + 1] = gr; d[i * 4 + 2] = b; d[i * 4 + 3] = 255; };
  while (stack.length) {
    const i = stack.pop();
    if (seen[i]) continue;
    seen[i] = 1;
    set(i);
    if (isLine(i)) continue;                       // Linienrand mitfärben, aber nicht darüber hinaus
    const px = i % PW;
    if (px > 0) stack.push(i - 1);
    if (px < PW - 1) stack.push(i + 1);
    if (i >= PW) stack.push(i - PW);
    if (i < PW * (PH - 1)) stack.push(i + PW);
  }
  c.putImageData(img, 0, 0);
}
let drawing = false, last = null;
function brushTo(x, y) {
  const c = paint.colorCv.getContext("2d");
  c.strokeStyle = paint.tool === "erase" ? "#ffffff" : paint.color;
  c.lineWidth = paint.size; c.lineCap = "round"; c.lineJoin = "round";
  c.beginPath(); c.moveTo(last[0], last[1]); c.lineTo(x, y); c.stroke();
  last = [x, y];
}
pc.addEventListener("pointerdown", e => {
  e.preventDefault();
  const [x, y] = canvasPos(e);
  pushUndo();
  if (paint.tool === "fill") { floodFill(x, y, paint.color); Sound.talk(); render(); return; }
  drawing = true; last = [x, y]; brushTo(x + 0.1, y); render();
  pc.setPointerCapture(e.pointerId);
});
pc.addEventListener("pointermove", e => { if (!drawing) return; const [x, y] = canvasPos(e); brushTo(x, y); render(); });
["pointerup", "pointercancel"].forEach(ev => pc.addEventListener(ev, () => drawing = false));

document.getElementById("pUndo").onclick = () => { const u = paint.undo.pop(); if (u) { paint.colorCv.getContext("2d").putImageData(u, 0, 0); render(); } };
document.getElementById("pClear").onclick = () => { pushUndo(); const c = paint.colorCv.getContext("2d"); c.fillStyle = "#fff"; c.fillRect(0, 0, PW, PH); render(); };
document.getElementById("pSave").onclick = () => {
  render();
  const a = document.createElement("a");
  a.download = `Malbild-${PICTURES[paint.picture].name.replace(/\s+/g, "-")}.png`;
  a.href = pc.toDataURL("image/png");
  document.body.appendChild(a); a.click(); a.remove();
};
document.getElementById("pDone").onclick = () => {
  paintEl.style.display = "none";
  ch2.state = "done"; game.state = "done";
  document.getElementById("final").style.display = "flex";
  Sound.play("fest");
  Voice.speak(FINAL_TEXT);
};
const FINAL_TEXT = "Und so war Weihnachten gerettet. Alma, Sven, Rosi und alle ihre Freunde wünschen dir frohe Weihnachten!";
document.getElementById("paintMore").onclick = () => { document.getElementById("final").style.display = "none"; openPaint(false); };
document.getElementById("paintFromTitle").addEventListener("click", () => openPaint(false));
const PAINT_VOICE_LINES = ["Such dir eine Farbe aus und tippe auf eine Fläche, dann wird sie bunt.", FINAL_TEXT];
