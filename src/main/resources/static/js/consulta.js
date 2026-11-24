// Detalle del pedido: stepper, tarjeta de siguiente paso con las acciones del rol, historial y reglas.
import { api, iniciarMarco, toast, el, chip, fecha, icono, estadoVacio, mensajeError, ETIQUETAS, ROLES } from "./comun.js";

const PASOS = [
  { texto: "Pedido", icono: "carrito" },
  { texto: "Pago", icono: "pago" },
  { texto: "Preparación", icono: "caja" },
  { texto: "En tránsito", icono: "camion" },
  { texto: "Entregado", icono: "check" },
];

/** Indice del paso actual por estado del AFD (los anteriores quedan hechos). */
const PASO_ACTUAL = { CARRITO: 0, PAGO_PENDIENTE: 1, PAGO_RECHAZADO: 1, PREPARANDO_INVENTARIO: 2, EN_TRANSITO: 3, ENTREGADO: 5 };

const QUIEN = {
  CARRITO: "El cliente debe confirmar el pedido para pasar al pago.",
  PAGO_PENDIENTE: "La pasarela de pagos confirma el resultado automáticamente.",
  PAGO_RECHAZADO: "El pago fue rechazado. El cliente puede reintentarlo.",
  PREPARANDO_INVENTARIO: "La bodega prepara el pedido; avanza al escanear el despacho.",
  EN_TRANSITO: "El repartidor confirma la entrega con el código de 4 dígitos del cliente.",
  ENTREGADO: "Pedido entregado. El ciclo terminó.",
};

const ACCIONES = {
  CONFIRMAR_PEDIDO: "Confirmar y pagar",
  REINTENTAR_PAGO: "Reintentar pago",
  ENTREGA_CONFIRMADA: "Confirmar entrega",
};

const $ = (id) => document.getElementById(id);
let sesion = null;
let rolPorEvento = {};
let actual = null;      // codigo consultado
let firma = "";
let temporizador = null;

function dibujarStepper(estado) {
  const idx = PASO_ACTUAL[estado] ?? 0;
  const rechazado = estado === "PAGO_RECHAZADO";
  $("stepper").replaceChildren(...PASOS.map((p, i) => {
    let clase = i < idx ? "hecho" : i === idx ? "actual" : "pendiente";
    if (rechazado && i === 1) clase = "error";
    const li = el("li", null, "paso " + clase);
    if (clase === "actual" || clase === "error") li.setAttribute("aria-current", "step");
    const ic = el("span", null, "icono");
    ic.appendChild(icono(clase === "error" ? "equis" : p.icono));
    const etiqueta = { hecho: "Completado", actual: "En curso", error: "Rechazado", pendiente: "Pendiente" }[clase];
    li.append(ic, el("span", p.texto, "texto"), el("span", etiqueta, "estado-paso"));
    return li;
  }));
}

function dibujarSiguiente(p) {
  $("quien").textContent = QUIEN[p.estado] || "";
  const cont = $("acciones");
  cont.replaceChildren();
  const mias = p.eventosPermitidos.filter((ev) => rolPorEvento[ev] === sesion.rol);

  mias.forEach((ev) => {
    if (ev === "CONFIRMAR_PEDIDO") {   // se confirma en /checkout (lineas y datos de entrega)
      const a = el("a", "Ir a pagar", "btn btn-primario");
      a.href = "/checkout";
      cont.appendChild(a);
      return;
    }
    if (ev === "ENTREGA_CONFIRMADA") {
      const form = el("form", null, "acciones");
      form.style.marginTop = "0";
      const campo = el("div", null, "campo");
      const lbl = el("label", "Código de entrega");
      lbl.htmlFor = "codigo-cliente";
      const input = document.createElement("input");
      Object.assign(input, { id: "codigo-cliente", inputMode: "numeric", maxLength: 4, required: true, autocomplete: "off", placeholder: "0000" });
      input.pattern = "\\d{4}";
      campo.append(lbl, input);
      const b = el("button", ACCIONES[ev], "btn btn-primario");
      b.type = "submit";
      form.append(campo, b);
      form.addEventListener("submit", (e) => {
        e.preventDefault();
        if (!/^\d{4}$/.test(input.value)) { toast("El código de entrega tiene 4 dígitos.", "error"); input.focus(); return; }
        enviar(ev, input.value, b);
      });
      cont.appendChild(form);
    } else {
      const b = el("button", ACCIONES[ev] || ev, "btn btn-primario");
      b.type = "button";
      b.addEventListener("click", () => enviar(ev, null, b));
      cont.appendChild(b);
    }
  });

  if (p.urlPago && p.estado === "PAGO_PENDIENTE") {
    const a = el("a", "Ir a pagar", "btn btn-primario");
    a.href = p.urlPago;
    cont.appendChild(a);
  }
  if (!mias.length && !p.estadoFinal && !(p.urlPago && p.estado === "PAGO_PENDIENTE")) {
    const resp = ROLES[p.responsable] || (p.responsable === "SISTEMA" ? "el sistema" : p.responsable);
    cont.appendChild(el("p", `No requiere acción de tu parte. Responsable: ${resp}.`, "ayuda"));
  }

  const ce = $("codigo-entrega");
  ce.replaceChildren();
  ce.className = "";
  if (p.codigoEntrega && !p.estadoFinal) {
    ce.className = "codigo-entrega";
    ce.append(el("span", "Tu código de entrega:"), el("strong", p.codigoEntrega),
      el("span", "Dáselo al repartidor solo al recibir el pedido.", "ayuda"));
  }
}

async function dibujarHistorial(codigo) {
  const r = await api(`/pedidos/${encodeURIComponent(codigo)}/historial`);
  const tbody = $("historial");
  if (!r.ok) { tbody.replaceChildren(); $("h-contador").textContent = "(no disponible)"; return; }
  $("h-contador").textContent = `(${r.datos.length})`;
  tbody.replaceChildren(...r.datos.slice().reverse().map((h) => {
    const tr = document.createElement("tr");
    tr.title = h.detalle || "";
    tr.append(el("td", fecha(h.fecha), "tenue"), el("td", ETIQUETAS[h.estadoOrigen]), el("td", h.evento, "codigo"),
      el("td", ROLES[h.rol] || "Sistema"), el("td", ETIQUETAS[h.estadoDestino]),
      el("td", h.aceptado ? "Aceptado" : "Rechazado", h.aceptado ? "si" : "no"));
    return tr;
  }));
}

function dibujarReglas(automata) {
  $("reglas").replaceChildren(...automata.transiciones.map((t) => {
    const tr = document.createElement("tr");
    const rol = automata.rolPorEvento[t.evento];
    tr.append(el("td", ETIQUETAS[t.origen]), el("td", t.evento, "codigo"),
      el("td", ROLES[rol] || "Sistema"), el("td", ETIQUETAS[t.destino]));
    return tr;
  }));
}

async function consultar(codigo, silencioso = false) {
  const r = await api(`/pedidos/${encodeURIComponent(codigo)}`);
  if (!r.ok) {
    if (silencioso) return;
    $("detalle").hidden = true;
    const titulo = r.status === 404 ? "Pedido no encontrado" : "No se pudo consultar";
    const detalle = r.status === 404 ? `No hay un pedido ${codigo} visible para tu usuario.` : mensajeError(r);
    estadoVacio($("sin-pedido"), titulo, detalle, true);
    return;
  }
  const p = r.datos;
  actual = p.codigo;
  const nueva = [p.codigo, p.estado, p.actualizadoEn, p.urlPago].join("|");
  if (silencioso && nueva === firma) return;
  firma = nueva;

  $("sin-pedido").hidden = true;
  $("detalle").hidden = false;
  $("d-codigo").textContent = p.codigo;
  $("d-meta").textContent = `${p.moneda} ${Number(p.total).toFixed(2)} · ${p.cliente} · actualizado ${fecha(p.actualizadoEn)}`;
  $("d-chip").replaceChildren(chip(p.estado));
  dibujarStepper(p.estado);
  dibujarSiguiente(p);
  dibujarHistorial(p.codigo);

  clearInterval(temporizador);
  if (!p.estadoFinal) temporizador = setInterval(() => consultar(actual, true), 4000);
}

async function enviar(evento, codigoEntrega, boton) {
  boton.disabled = true;
  const body = codigoEntrega ? { evento, codigoEntrega } : { evento };
  const r = await api(`/pedidos/${encodeURIComponent(actual)}/eventos`, { method: "POST", body });
  boton.disabled = false;
  if (r.ok) {
    if (r.datos.pedido.urlPago) { location.href = r.datos.pedido.urlPago; return; }
    toast(`${ACCIONES[evento] || evento}: listo. Nuevo estado ${ETIQUETAS[r.datos.pedido.estado]}.`, "ok");
  } else if (r.status === 422) {
    toast("Código de entrega incorrecto.", "error");
  } else {
    toast(mensajeError(r), "error");
  }
  firma = "";
  consultar(actual, true);
}

async function iniciar() {
  sesion = await iniciarMarco();
  if (!sesion) return;
  const a = await api("/automata");
  if (a.ok) { rolPorEvento = a.datos.rolPorEvento; dibujarReglas(a.datos); }

  const campo = $("codigo");
  $("form-consulta").addEventListener("submit", (e) => {
    e.preventDefault();
    const c = campo.value.trim().toUpperCase().replace(":", "-");
    if (!c) { toast("Escribe el código del pedido.", "error"); campo.focus(); return; }
    history.replaceState(null, "", "?codigo=" + encodeURIComponent(c));
    firma = "";
    consultar(c);
  });

  const inicial = new URLSearchParams(location.search).get("codigo");
  if (inicial) { campo.value = inicial; consultar(inicial); }
  else estadoVacio($("sin-pedido"), "Consulta un pedido", "Escribe su código o ábrelo desde el listado de pedidos.");
}

iniciar();
