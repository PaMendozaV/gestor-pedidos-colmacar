// Busqueda en vivo del catalogo publico (GET /productos/buscar). Construye las tarjetas con el DOM, sin innerHTML.
(() => {
  const input = document.getElementById("buscador-tienda");
  const grid = document.getElementById("grid-productos");
  if (!input || !grid) return;
  const original = [...grid.children];
  const paginacion = document.querySelector(".paginacion");
  const barra = document.getElementById("barra-pedido");
  const UMBRAL = (barra && barra.dataset.umbral) || "50";
  let temporizador = null, peticion = null;

  const el = (tag, clase, texto) => {
    const e = document.createElement(tag);
    if (clase) e.className = clase;
    if (texto != null) e.textContent = texto;
    return e;
  };
  const precio = (v) => "$" + Number(v).toFixed(2);

  function tarjeta(p) {
    const card = el("div", "card-producto visible");
    Object.assign(card.dataset, { id: p.id, nombre: p.nombre, precio: p.precio, precioMayor: p.precioMayor ?? "" });
    const img = el("div", "card-producto__img");
    if (p.imagen && /^https:\/\//.test(p.imagen)) {
      const i = el("img"); i.src = p.imagen; i.alt = p.nombre; i.loading = "lazy"; img.appendChild(i);
    } else img.appendChild(el("span", "card-producto__img--vacio", "Sin imagen"));
    const info = el("div", "card-producto__info");
    if (p.categoria) info.appendChild(el("span", "card-producto__categoria", p.categoria));
    info.appendChild(el("span", "card-producto__nombre", p.nombre));
    const precios = el("div", "card-producto__precios");
    const detal = el("div", "card-producto__linea-precio");
    detal.append(el("span", "card-producto__etiqueta", "Detal"), el("span", "card-producto__valor", precio(p.precio)));
    precios.appendChild(detal);
    if (p.precioMayor != null) {
      const may = el("div", "card-producto__linea-precio card-producto__linea-precio--mayor");
      may.append(el("span", "card-producto__etiqueta", `Mayor (${UMBRAL}+)`), el("span", "card-producto__valor", precio(p.precioMayor)));
      precios.appendChild(may);
    }
    const accion = el("div", "card-producto__accion");
    accion.dataset.id = p.id;
    info.append(precios, accion);
    card.append(img, info);
    return card;
  }

  async function buscar(termino) {
    if (!termino) {
      grid.replaceChildren(...original);
      if (paginacion) paginacion.hidden = false;
      return;
    }
    if (peticion) peticion.abort();
    peticion = new AbortController();
    try {
      const r = await fetch("/productos/buscar?q=" + encodeURIComponent(termino), { signal: peticion.signal });
      const lista = r.ok ? await r.json() : [];
      grid.replaceChildren(...(lista.length ? lista.map(tarjeta) : [el("p", "sin-resultados", "No se encontraron productos.")]));
      if (paginacion) paginacion.hidden = true;
      if (window.colmacar) window.colmacar.sincronizarBotones();
    } catch (e) {
      if (e.name !== "AbortError") grid.replaceChildren(el("p", "sin-resultados", "No se pudo buscar. Intenta de nuevo."));
    }
  }

  const limpiarBtn = document.getElementById("buscador-limpiar");
  input.addEventListener("input", () => {
    if (limpiarBtn) limpiarBtn.classList.toggle("visible", input.value !== "");
    clearTimeout(temporizador);
    temporizador = setTimeout(() => buscar(input.value.trim()), 300);
  });
  const limpiar = document.getElementById("buscador-limpiar");
  if (limpiar) limpiar.addEventListener("click", () => { input.value = ""; limpiar.classList.remove("visible"); buscar(""); input.focus(); });
})();
