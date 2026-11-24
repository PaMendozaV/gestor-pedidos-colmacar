// Utilidades compartidas: API con sesion y CSRF, marco de la pagina, toasts y formato.

export const ETIQUETAS = {
  CARRITO: "Carrito",
  PAGO_PENDIENTE: "Pago pendiente",
  PAGO_RECHAZADO: "Pago rechazado",
  PREPARANDO_INVENTARIO: "Preparando",
  EN_TRANSITO: "En tránsito",
  ENTREGADO: "Entregado",
};

export const ROLES = { ADMIN: "Administrador", CLIENTE: "Cliente", REPARTIDOR: "Repartidor" };

const TRAZOS = {
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  lista: '<path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"/>',
  buscar: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  salir: '<path d="M9 4H5v16h4M14 8l4 4-4 4M18 12H9"/>',
  carrito: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.5 12h11.5l2-8H6.2"/>',
  pago: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20M6 15h4"/>',
  caja: '<path d="m3 7 9-4 9 4v10l-9 4-9-4z"/><path d="m3 7 9 4 9-4M12 11v10"/>',
  camion: '<path d="M1 6h13v10H1zM14 10h4l3 3v3h-7"/><circle cx="5.5" cy="18" r="2"/><circle cx="17.5" cy="18" r="2"/>',
  check: '<path d="m4 12 5 5L20 6"/>',
  equis: '<path d="M6 6l12 12M18 6 6 18"/>',
};

/** Devuelve un <svg> con uno de los iconos propios. */
export function icono(nombre) {
  const plantilla = document.createElement("template");
  plantilla.innerHTML = `<svg class="ico" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${TRAZOS[nombre] || ""}</svg>`;
  return plantilla.content.firstChild;
}

function leerCookie(nombre) {
  const fila = document.cookie.split("; ").find((c) => c.startsWith(nombre + "="));
  return fila ? decodeURIComponent(fila.slice(nombre.length + 1)) : "";
}

/** Cabeceras para peticiones que cambian algo (CSRF de Spring Security). */
export function cabecerasCsrf() {
  return { "X-XSRF-TOKEN": leerCookie("XSRF-TOKEN") };
}

/**
 * Llama a la API y devuelve {ok, status, datos}. Nunca lanza por errores HTTP.
 * Si la sesion expiro (401) manda al login.
 */
export async function api(ruta, { method = "GET", body } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") Object.assign(headers, cabecerasCsrf());
  let res;
  try {
    res = await fetch("/api" + ruta, {
      method, headers, credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    return { ok: false, status: 0, datos: { error: "Sin conexión con el servidor" } };
  }
  if (res.status === 401) {
    location.href = "/login";
    return { ok: false, status: 401, datos: null };
  }
  let datos = null;
  try { datos = await res.json(); } catch (e) { /* sin cuerpo */ }
  return { ok: res.ok, status: res.status, datos };
}

/** Mensaje legible de una respuesta fallida. */
export function mensajeError(r, porDefecto = "No se pudo completar la acción") {
  if (r.status === 0) return "Sin conexión con el servidor";
  if (r.status === 404) return "Pedido no encontrado";
  return (r.datos && (r.datos.error || r.datos.mensaje)) || porDefecto;
}

/** Monta usuario, menu y salida. Devuelve la sesion {username, rol}. */
export async function iniciarMarco() {
  const r = await api("/me");
  if (!r.ok) return null;
  const s = r.datos;
  document.getElementById("u-nombre").textContent = s.username;
  document.getElementById("u-rol").textContent = ROLES[s.rol] || s.rol;
  document.body.dataset.rol = s.rol;

  const boton = document.getElementById("btn-menu");
  const velo = document.getElementById("velo");
  const escritorio = window.matchMedia("(min-width: 960px)");
  const cerrarMovil = () => { document.body.classList.remove("menu-abierto"); velo.hidden = true; boton.setAttribute("aria-expanded", "false"); };
  try { if (localStorage.getItem("lateral") === "mini") document.body.classList.add("colapsado"); } catch (e) { /* sin almacenamiento */ }
  boton.setAttribute("aria-expanded", String(escritorio.matches ? !document.body.classList.contains("colapsado") : false));
  boton.addEventListener("click", () => {
    if (escritorio.matches) {
      const mini = document.body.classList.toggle("colapsado");
      boton.setAttribute("aria-expanded", String(!mini));
      try { localStorage.setItem("lateral", mini ? "mini" : "completo"); } catch (e) { /* sin almacenamiento */ }
    } else {
      const abierto = document.body.classList.toggle("menu-abierto");
      velo.hidden = !abierto;
      boton.setAttribute("aria-expanded", String(abierto));
    }
  });
  velo.addEventListener("click", cerrarMovil);
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") cerrarMovil(); });
  escritorio.addEventListener("change", cerrarMovil);

  document.getElementById("btn-salir").addEventListener("click", async () => {
    try { await fetch("/logout", { method: "POST", headers: cabecerasCsrf(), credentials: "same-origin" }); } catch (e) { /* igual salimos */ }
    location.href = "/login?salir";
  });
  return s;
}

/** Aviso temporal no bloqueante. tipo: info | ok | error */
export function toast(texto, tipo = "info") {
  const cont = document.getElementById("toasts");
  const t = el("div", texto, "toast " + tipo);
  t.setAttribute("role", tipo === "error" ? "alert" : "status");
  cont.appendChild(t);
  setTimeout(() => { t.classList.add("saliendo"); setTimeout(() => t.remove(), 200); }, tipo === "error" ? 6000 : 4000);
}

/** Crea un elemento con texto seguro (nunca interpreta HTML de la API). */
export function el(etiqueta, texto, clase) {
  const e = document.createElement(etiqueta);
  if (texto !== undefined && texto !== null) e.textContent = texto;
  if (clase) e.className = clase;
  return e;
}

export function chip(estado) {
  const c = el("span", ETIQUETAS[estado] || estado, "chip");
  c.dataset.estado = estado;
  return c;
}

const dos = (n) => String(n).padStart(2, "0");

/** 05/11/2026 10:53 */
export function fecha(iso) {
  const d = new Date(iso);
  return `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()} ${dos(d.getHours())}:${dos(d.getMinutes())}`;
}

/** Estado vacio o de error dentro de un contenedor. */
export function estadoVacio(cont, titulo, detalle, esError = false) {
  cont.replaceChildren();
  cont.className = "vacio" + (esError ? " error" : "");
  cont.append(icono(esError ? "equis" : "caja"), el("strong", titulo), el("span", detalle));
  cont.hidden = false;
}
