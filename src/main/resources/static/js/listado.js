// Panel de pedidos: resumen por estado (calculado aqui), filtros y tabla. El cliente puede crear pedidos.
import { api, iniciarMarco, toast, el, chip, fecha, estadoVacio, mensajeError, ETIQUETAS } from "./comun.js";

const TEXTOS = {
  ADMIN: ["Panel de administración", "Supervisión de todos los pedidos. El avance lo hacen el cliente, el sistema y el repartidor."],
  CLIENTE: ["Mis pedidos", "Sigue el estado de tus pedidos. Para comprar, usa la tienda."],
  REPARTIDOR: ["Entregas", "Pedidos en tránsito listos para confirmar con el código del cliente."],
};

let sesion = null;
let estados = [];
let pedidos = [];
let firma = "";
let cargado = false;

const $ = (id) => document.getElementById(id);

function filtrados() {
  const texto = $("filtro-codigo").value.trim().toUpperCase().replace(":", "-");
  const estado = $("filtro-estado").value;
  return pedidos.filter((p) => (!texto || p.codigo.includes(texto)) && (!estado || p.estado === estado));
}

function dibujarResumen() {
  const conteo = Object.fromEntries(estados.map((e) => [e, 0]));
  pedidos.forEach((p) => { conteo[p.estado] = (conteo[p.estado] || 0) + 1; });
  const activo = $("filtro-estado").value;
  $("resumen").replaceChildren(...estados.map((e) => {
    const b = el("button", null, "kpi");
    b.type = "button";
    b.dataset.estado = e;
    b.setAttribute("aria-pressed", String(activo === e));
    b.append(el("span", String(conteo[e]), "numero"), el("span", ETIQUETAS[e], "nombre"));
    b.setAttribute("aria-label", `${ETIQUETAS[e]}: ${conteo[e]} pedidos. Filtrar por este estado`);
    b.addEventListener("click", () => {
      $("filtro-estado").value = activo === e ? "" : e;
      dibujar();
    });
    return b;
  }));
}

function dibujarTabla() {
  const lista = filtrados();
  const tbody = $("filas");
  tbody.replaceChildren(...lista.map((p) => {
    const tr = document.createElement("tr");
    const ver = el("td");
    const a = el("a", "Ver detalle", "ver");
    a.href = "index.html?codigo=" + encodeURIComponent(p.codigo);
    a.setAttribute("aria-label", "Ver detalle de " + p.codigo);
    ver.appendChild(a);
    const estado = el("td");
    estado.appendChild(chip(p.estado));
    tr.append(el("td", p.codigo, "codigo"), el("td", `${p.moneda} ${Number(p.total).toFixed(2)}`), el("td", p.cliente, "tenue col-cliente"),
      estado, el("td", fecha(p.actualizadoEn), "tenue"), ver);
    return tr;
  }));
  const vacio = $("vacio");
  $("envoltura-tabla").hidden = lista.length === 0;
  if (lista.length > 0) { vacio.hidden = true; return; }
  if (pedidos.length === 0) {
    const detalle = sesion.rol === "CLIENTE" ? "Crea tu primer pedido con «Nuevo pedido»." : "Cuando los clientes creen pedidos aparecerán aquí.";
    estadoVacio(vacio, "Aún no hay pedidos", detalle);
  } else {
    estadoVacio(vacio, "Sin resultados", "Ningún pedido coincide con el filtro.");
  }
}

function dibujar() {
  dibujarResumen();
  dibujarTabla();
}

async function cargar() {
  const r = await api("/pedidos");
  if (!r.ok) {
    if (!cargado) {
      $("envoltura-tabla").hidden = true;
      estadoVacio($("vacio"), "No se pudieron cargar los pedidos", mensajeError(r), true);
    }
    return;
  }
  cargado = true;
  const nueva = JSON.stringify(r.datos.map((p) => [p.codigo, p.estado, p.actualizadoEn]));
  if (nueva === firma) return;
  firma = nueva;
  pedidos = r.datos;
  dibujar();
}

function avisoDePago() {
  const q = new URLSearchParams(location.search);
  const pago = q.get("pago");
  if (!pago) return;
  const codigo = q.get("codigo") || "";
  if (pago === "ok") toast(`Pago enviado para ${codigo}. El estado cambia cuando la pasarela lo confirme.`, "ok");
  if (pago === "cancelado") toast(`Pago de ${codigo} cancelado. Puedes intentarlo desde el detalle del pedido.`, "info");
  history.replaceState(null, "", location.pathname);
}

async function iniciar() {
  sesion = await iniciarMarco();
  if (!sesion) return;
  const [t, sub] = TEXTOS[sesion.rol] || ["Pedidos", ""];
  $("titulo").textContent = t;
  $("subtitulo").textContent = sub;
  document.title = t + " · Grupo Colmacar";

  const a = await api("/automata");
  estados = a.ok ? a.datos.estados : Object.keys(ETIQUETAS);
  const sel = $("filtro-estado");
  estados.forEach((e) => { const o = el("option", ETIQUETAS[e] || e); o.value = e; sel.appendChild(o); });
  if (sesion.rol === "REPARTIDOR") sel.value = "EN_TRANSITO";

  $("form-filtro").addEventListener("submit", (e) => { e.preventDefault(); dibujar(); });
  $("filtro-codigo").addEventListener("input", dibujarTabla);
  sel.addEventListener("change", dibujar);
  $("limpiar").addEventListener("click", () => { $("filtro-codigo").value = ""; sel.value = ""; dibujar(); });

  if (sesion.rol === "CLIENTE") $("btn-nuevo").hidden = false;

  avisoDePago();
  await cargar();
  setInterval(cargar, 4000);
}

iniciar();
