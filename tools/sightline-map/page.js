const D = window.__DATA__;
const SP = D.spacing, COR = D.corridor;
const legs = D.legs, byCell = new Map(legs.map(l => [l.c[0]+","+l.c[1], l]));
const beams = D.stations || [];
const pins = [];
for (const [id, spots] of Object.entries(D.structures))
  for (const [x, z] of spots) pins.push({id, x, z});

document.getElementById("facts").innerHTML = [
  ["save", D.name], ["seed", D.seed], ["terrain", D.generator.replace("minecraft:","")],
  ["explored", D.chunks + " chunks"], ["starts", pins.length], ["cells drawn", legs.length],
  ["stations", beams.length]
].map(([k,v]) => `${k} <b>${v}</b>`).join("");

const ex = D.extent.map(v => v*16);
const layers = {legs:1, corridor:1, beamlines:1, nodes:1, pins:1, grid:1, extent:1};
const chips = document.getElementById("chips");
for (const k of Object.keys(layers)) {
  const b = document.createElement("button");
  b.className = "chip"; b.type = "button"; b.textContent = k;
  b.setAttribute("aria-pressed", "true");
  b.onclick = () => { layers[k] = !layers[k]; b.setAttribute("aria-pressed", String(!!layers[k])); draw(); };
  chips.appendChild(b);
}
const reset = document.createElement("button");
reset.className = "chip"; reset.type = "button"; reset.textContent = "recentre";
reset.onclick = () => { view.s = fit(); view.x = 0; view.z = 0; draw(); };
chips.appendChild(reset);

const cv = document.getElementById("map"), ctx = cv.getContext("2d");
let W = 0, H = 0, DPR = 1;
const view = {x:0, z:0, s:0.09};
function fit(){ return Math.min(W, H) / (SP * (2*D.range + 1)) * 0.92; }
function toS(x, z){ return [W/2 + (x - view.x)*view.s, H/2 + (z - view.z)*view.s]; }
function toW(px, pz){ return [(px - W/2)/view.s + view.x, (pz - H/2)/view.s + view.z]; }

function size(){
  DPR = Math.min(devicePixelRatio || 1, 2);
  const r = cv.getBoundingClientRect();
  W = r.width; H = r.height;
  cv.width = W*DPR; cv.height = H*DPR;
  ctx.setTransform(DPR,0,0,DPR,0,0);
}
function css(n){ return getComputedStyle(document.documentElement).getPropertyValue(n).trim(); }

function draw(){
  const C = {panel:css("--panel"), edge:css("--edge"), edge2:css("--edge2"), teal:css("--teal"),
             far:css("--far"), gold:css("--gold"), rust:css("--rust"), faint:css("--faint"), dim:css("--dim")};
  ctx.fillStyle = C.panel; ctx.fillRect(0,0,W,H);

  if (layers.grid){
    ctx.strokeStyle = C.edge; ctx.lineWidth = 1;
    for (let c = -D.range; c <= D.range+1; c++){
      const [gx] = toS(c*SP, 0), [,gz] = toS(0, c*SP);
      ctx.beginPath(); ctx.moveTo(gx,0); ctx.lineTo(gx,H); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0,gz); ctx.lineTo(W,gz); ctx.stroke();
    }
  }
  if (layers.extent){
    const [x0,z0] = toS(ex[0], ex[2]), [x1,z1] = toS(ex[1], ex[3]);
    ctx.strokeStyle = C.edge2; ctx.setLineDash([4,4]); ctx.lineWidth = 1;
    ctx.strokeRect(x0, z0, x1-x0, z1-z0); ctx.setLineDash([]);
  }
  if (layers.corridor){
    ctx.strokeStyle = C.far; ctx.globalAlpha = .35;
    ctx.lineWidth = Math.max(1, COR*2*view.s); ctx.lineCap = "butt";
    for (const l of legs){ const a = toS(l.a[0], l.a[1]), b = toS(l.b[0], l.b[1]);
      ctx.beginPath(); ctx.moveTo(a[0],a[1]); ctx.lineTo(b[0],b[1]); ctx.stroke(); }
    ctx.globalAlpha = 1;
  }
  if (layers.legs){
    ctx.strokeStyle = C.teal; ctx.lineWidth = 1.25;
    for (const l of legs){ const a = toS(l.a[0], l.a[1]), b = toS(l.b[0], l.b[1]);
      ctx.beginPath(); ctx.moveTo(a[0],a[1]); ctx.lineTo(b[0],b[1]); ctx.stroke();
      const ang = Math.atan2(b[1]-a[1], b[0]-a[0]), h = 5;
      ctx.beginPath();
      ctx.moveTo(b[0], b[1]);
      ctx.lineTo(b[0]-h*Math.cos(ang-0.5), b[1]-h*Math.sin(ang-0.5));
      ctx.moveTo(b[0], b[1]);
      ctx.lineTo(b[0]-h*Math.cos(ang+0.5), b[1]-h*Math.sin(ang+0.5));
      ctx.stroke();
    }
  }
  if (layers.beamlines){
    // The tangent: the leg's own direction carried on past the node, and an
    // open ring where it lands. Dashed, because nothing is built along it -
    // the line is a sightline, not a road.
    ctx.strokeStyle = C.gold; ctx.globalAlpha = .55; ctx.lineWidth = 1;
    ctx.setLineDash([3,3]);
    for (const b of beams){
      const n = toS(b.n[0], b.n[1]), s = toS(b.s[0], b.s[1]);
      ctx.beginPath(); ctx.moveTo(n[0],n[1]); ctx.lineTo(s[0],s[1]); ctx.stroke();
    }
    ctx.setLineDash([]); ctx.globalAlpha = 1;
    ctx.strokeStyle = C.gold; ctx.lineWidth = 1.2;
    for (const b of beams){
      const s = toS(b.s[0], b.s[1]);
      ctx.beginPath(); ctx.arc(s[0], s[1], 3, 0, 6.2832); ctx.stroke();
    }
  }
  if (layers.nodes){
    ctx.fillStyle = C.gold;
    for (const l of legs){ const a = toS(l.a[0], l.a[1]);
      ctx.fillRect(a[0]-2, a[1]-2, 4, 4); }
  }
  if (layers.pins){
    ctx.strokeStyle = C.rust; ctx.lineWidth = 1.4;
    for (const p of pins){ const [x,y] = toS(p.x, p.z);
      ctx.beginPath(); ctx.moveTo(x-3.5,y); ctx.lineTo(x+3.5,y);
      ctx.moveTo(x,y-3.5); ctx.lineTo(x,y+3.5); ctx.stroke(); }
  }
  const [sx,sy] = toS(D.spawn[0], D.spawn[2]);
  ctx.strokeStyle = css("--ink"); ctx.lineWidth = 1.25;
  ctx.beginPath(); ctx.arc(sx, sy, 5, 0, 6.2832); ctx.stroke();
  ctx.font = "10px " + css("--mono"); ctx.fillStyle = C.dim;
  ctx.fillText("spawn " + D.spawn[0] + " " + D.spawn[2], sx + 9, sy + 3);
  ctx.fillStyle = C.faint;
  ctx.fillText("N ↑   1 cell = " + SP + " blocks", 10, 16);
}

const el = id => document.getElementById(id);
function report(wx, wz){
  const cx = Math.floor(wx/SP), cz = Math.floor(wz/SP);
  const l = byCell.get(cx+","+cz);
  el("rXZ").textContent = Math.round(wx) + " " + Math.round(wz);
  el("rCell").textContent = cx + " " + cz;
  const v = el("rVerdict");
  if (!l){ el("rLeg").textContent = "—"; el("rDist").textContent = "—";
    v.textContent = "—"; v.className = ""; return; }
  el("rLeg").textContent = l.h.toLowerCase();
  const lx = l.b[0]-l.a[0], lz = l.b[1]-l.a[1];
  const d = Math.abs(lz*(wx-l.a[0]) - lx*(wz-l.a[1])) / Math.hypot(lx,lz);
  el("rDist").textContent = Math.round(d) + " b";
  const near = d <= COR;
  v.textContent = near ? "1 in 8 broken" : "4 in 8 broken";
  v.className = "verdict " + (near ? "stand" : "break");
  // The nearest station, so the cursor can answer "is there a beamline end
  // near here" the same way a feature would ask Beamline.nearest.
  let best = null, bestD = Infinity;
  for (const b of beams){
    const dd = Math.hypot(b.s[0]-wx, b.s[1]-wz);
    if (dd < bestD){ bestD = dd; best = b; }
  }
  const st = el("rStation");
  if (st) st.textContent = best ? Math.round(bestD) + " b " + best.h.toLowerCase() : "—";
}

let drag = null;
cv.addEventListener("pointerdown", e => { drag = {x:e.clientX, z:e.clientY}; cv.setPointerCapture(e.pointerId); });
cv.addEventListener("pointerup", () => { drag = null; });
cv.addEventListener("pointercancel", () => { drag = null; });
cv.addEventListener("pointermove", e => {
  const r = cv.getBoundingClientRect();
  if (drag){ view.x -= (e.clientX-drag.x)/view.s; view.z -= (e.clientY-drag.z)/view.s;
    drag = {x:e.clientX, z:e.clientY}; draw(); }
  const [wx, wz] = toW(e.clientX - r.left, e.clientY - r.top);
  report(wx, wz);
});
cv.addEventListener("wheel", e => {
  e.preventDefault();
  const r = cv.getBoundingClientRect();
  const [ax, az] = toW(e.clientX - r.left, e.clientY - r.top);
  view.s = Math.min(2, Math.max(0.012, view.s * (e.deltaY < 0 ? 1.12 : 1/1.12)));
  const [bx, bz] = toW(e.clientX - r.left, e.clientY - r.top);
  view.x += ax - bx; view.z += az - bz; draw();
}, {passive:false});

const q = (s) => document.querySelector(s);
const proof = q("#proof tbody");
for (const t of D.proof){
  for (let i = 0; i < t.rows.length; i++){
    const r = t.rows[i], tr = document.createElement("tr");
    tr.innerHTML =
      `<td>${i ? "" : t.id}</td>` +
      `<td>${t.spacing ? t.spacing + " ch" : '<span class="tag">no grid recoverable</span>'}</td>` +
      `<td>${r.chunk[0]} ${r.chunk[1]}</td>` +
      `<td>${r.cell ? r.cell[0] + " " + r.cell[1] : "—"}</td>` +
      `<td>${r.off ? r.off[0] + " " + r.off[1] : "—"}</td>` +
      `<td>${t.window != null ? "0–" + t.window : "—"}</td>` +
      `<td class="${r.ok ? "ok" : r.ok === false ? "no" : ""}">${r.ok ? "fits" : r.ok === false ? "outside" : ""}</td>`;
    proof.appendChild(tr);
  }
}
const mir = q("#mirror tbody");
for (const [a, b] of D.mirror.pairs){
  const tr = document.createElement("tr");
  tr.innerHTML = `<td>${a[0]} ${a[1]}</td><td>${b[0]} ${b[1]}</td>`;
  mir.appendChild(tr);
}
const lt = q("#legs tbody");
for (const l of legs.slice(0, 24)){
  const lx = l.b[0]-l.a[0], lz = l.b[1]-l.a[1];
  const along = (l.h === "EAST" || l.h === "WEST") ? Math.abs(lx) : Math.abs(lz);
  const across = (l.h === "EAST" || l.h === "WEST") ? Math.abs(lz) : Math.abs(lx);
  const tr = document.createElement("tr");
  tr.innerHTML = `<td>${l.c[0]} ${l.c[1]}</td><td>${l.a[0]} ${l.a[1]}</td>` +
    `<td>${l.h.toLowerCase()}</td><td>${l.b[0]} ${l.b[1]}</td>` +
    `<td>${Math.round(Math.hypot(lx,lz))} b</td>` +
    `<td>${(Math.atan2(across, along)*180/Math.PI).toFixed(1)}°</td>`;
  lt.appendChild(tr);
}

addEventListener("resize", () => { size(); draw(); });
size(); view.s = fit(); draw();
