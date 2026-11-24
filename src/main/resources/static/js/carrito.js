// Carrito del navegador (localStorage). Solo sirve para elegir productos y mostrar un subtotal ESTIMADO:
// al ir a pagar se envian {productoId, cantidad} y el servidor recalcula precios y total.
(() => {
  const CLAVE = "colmacar_carrito";
  const MAX = 10000;
  const barra = document.getElementById("barra-pedido");
  const UMBRAL = (barra && parseInt(barra.dataset.umbral, 10)) || 50;
  const WHATSAPP = barra ? barra.dataset.whatsapp || "" : "";

  function leer() {
    try {
      const m = new Map(JSON.parse(localStorage.getItem(CLAVE) || "[]"));
      for (const [id, item] of m) {
        const c = parseInt(item && item.cantidad, 10);
        if (!/^\d+$/.test(String(id)) || !(c >= 1)) m.delete(id);
        else item.cantidad = Math.min(c, MAX);
      }
      return m;
    } catch (e) { return new Map(); }
  }
  let carrito = leer();
  const guardar = () => { try { localStorage.setItem(CLAVE, JSON.stringify([...carrito])); } catch (e) { /* sin almacenamiento */ } };
  const precioUnit = (i) => (i.precioMayor && i.cantidad >= UMBRAL ? i.precioMayor : i.precioDetal) || 0;
  const fmt = (v) => "$" + v.toFixed(2);

  function actualizarBarra() {
    if (!barra) return;
    let unidades = 0, total = 0;
    carrito.forEach((i) => { unidades += i.cantidad; total += i.cantidad * precioUnit(i); });
    document.getElementById("subtotal-label").textContent = `Subtotal (${unidades} producto${unidades === 1 ? "" : "s"})`;
    document.getElementById("subtotal-monto").textContent = fmt(total);
    const pagar = document.getElementById("btn-pagar");
    pagar.setAttribute("aria-disabled", String(unidades === 0));
    const wsp = document.getElementById("btn-whatsapp");
    if (wsp) {
      wsp.setAttribute("aria-disabled", String(unidades === 0));
      const lineas = ["Hola, quisiera hacer este pedido:", ""];
      carrito.forEach((i) => lineas.push(`• ${i.nombre} x${i.cantidad} — ${fmt(precioUnit(i) * i.cantidad)}`));
      lineas.push("", `Subtotal estimado: ${fmt(total)}`);
      wsp.href = unidades ? `https://wa.me/${encodeURIComponent(WHATSAPP)}?text=${encodeURIComponent(lineas.join("\n"))}` : "#";
    }
  }

  function boton(texto, accion, etiqueta) {
    const b = document.createElement("button");
    b.type = "button"; b.textContent = texto; b.dataset.accion = accion;
    if (etiqueta) b.setAttribute("aria-label", etiqueta);
    return b;
  }

  /** Dibuja "Añadir al pedido" o el selector de cantidad dentro de la tarjeta. */
  function control(cont) {
    const id = cont.dataset.id;
    const item = carrito.get(id);
    cont.replaceChildren();
    if (!item) {
      const b = boton("Añadir al pedido", "agregar");
      b.className = "btn-agregar";
      cont.appendChild(b);
      return;
    }
    const sel = document.createElement("div");
    sel.className = "selector-cantidad";
    const input = document.createElement("input");
    Object.assign(input, { type: "number", min: 1, max: MAX, value: item.cantidad });
    input.dataset.accion = "fijar";
    input.setAttribute("aria-label", "Cantidad");
    sel.append(boton("−", "menos", "Quitar uno"), input, boton("+", "mas", "Agregar uno"));
    cont.appendChild(sel);
  }

  function sincronizarBotones() {
    document.querySelectorAll(".card-producto__accion[data-id]").forEach(control);
  }

  function cambiar(cont, nuevaCantidad) {
    const id = cont.dataset.id;
    if (nuevaCantidad <= 0) carrito.delete(id);
    else carrito.get(id).cantidad = Math.min(nuevaCantidad, MAX);
    guardar(); control(cont); actualizarBarra();
  }

  document.addEventListener("click", (e) => {
    const pagar = e.target.closest("#btn-pagar, #btn-whatsapp");
    if (pagar && pagar.getAttribute("aria-disabled") === "true") { e.preventDefault(); return; }
    const b = e.target.closest("button[data-accion]");
    if (!b) return;
    const cont = b.closest(".card-producto__accion");
    if (!cont) return;
    if (b.dataset.accion === "agregar") {
      const card = b.closest(".card-producto");
      carrito.set(cont.dataset.id, {
        nombre: card.dataset.nombre,
        precioDetal: parseFloat(card.dataset.precio) || 0,
        precioMayor: card.dataset.precioMayor ? parseFloat(card.dataset.precioMayor) : null,
        cantidad: 1,
      });
      guardar(); control(cont); actualizarBarra();
      cont.querySelector("input").focus();
    } else if (carrito.has(cont.dataset.id)) {
      const actual = carrito.get(cont.dataset.id).cantidad;
      cambiar(cont, b.dataset.accion === "mas" ? actual + 1 : actual - 1);
    }
  });

  document.addEventListener("change", (e) => {
    if (e.target.dataset.accion !== "fijar") return;
    const cont = e.target.closest(".card-producto__accion");
    const n = parseInt(e.target.value, 10);
    cambiar(cont, isNaN(n) ? 1 : n);
  });

  // otra pestaña cambio el carrito
  window.addEventListener("storage", (e) => { if (e.key === CLAVE) { carrito = leer(); sincronizarBotones(); actualizarBarra(); } });

  function animar() {
    const els = document.querySelectorAll(".card-producto:not(.visible), .galeria__grid img:not(.visible)");
    if (!("IntersectionObserver" in window) || matchMedia("(prefers-reduced-motion: reduce)").matches) {
      els.forEach((el) => el.classList.add("visible"));
      return;
    }
    const obs = new IntersectionObserver((entradas) => entradas.forEach((en) => {
      if (en.isIntersecting) { en.target.classList.add("visible"); obs.unobserve(en.target); }
    }), { threshold: 0.15, rootMargin: "0px 0px -40px 0px" });
    els.forEach((el, i) => { el.style.transitionDelay = `${(i % 4) * 90}ms`; obs.observe(el); });
  }

  window.colmacar = { sincronizarBotones, animar };
  sincronizarBotones();
  actualizarBarra();
  animar();
})();
