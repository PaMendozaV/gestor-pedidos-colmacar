// /checkout: envia {productoId, cantidad} del localStorage y muestra lo que calculo el servidor.
(() => {
  const CLAVE = "colmacar_carrito";
  const estado = document.getElementById("carrito-estado");
  const tabla = document.getElementById("tabla-lineas");
  const confirmar = document.getElementById("btn-confirmar");
  const csrf = () => {
    const c = document.cookie.split("; ").find((x) => x.startsWith("XSRF-TOKEN="));
    return c ? decodeURIComponent(c.slice(11)) : "";
  };
  const fmt = (v) => "$" + Number(v).toFixed(2);
  const td = (t) => { const e = document.createElement("td"); e.textContent = t; return e; };

  function local() {
    try { return new Map(JSON.parse(localStorage.getItem(CLAVE) || "[]")); } catch (e) { return new Map(); }
  }

  function pintar(c) {
    const tbody = document.getElementById("lineas");
    tbody.replaceChildren(...c.lineas.map((l) => {
      const tr = document.createElement("tr");
      tr.append(td(l.nombre), td(String(l.cantidad)),
        td(fmt(l.precioUnitario) + (l.mayorista ? " (mayor)" : "")), td(fmt(l.subtotal)));
      return tr;
    }));
    document.getElementById("total").textContent = fmt(c.total) + " " + c.moneda;
    tabla.hidden = c.lineas.length === 0;
    confirmar.disabled = c.lineas.length === 0;
    estado.textContent = c.lineas.length === 0
      ? "Tu carrito está vacío. Agrega productos desde el catálogo."
      : `${c.unidades} unidad(es). Precios confirmados por la tienda.`;
    if (c.omitidos && c.omitidos.length) {
      estado.textContent += " Se quitaron productos que ya no están disponibles.";
    }
    // el carrito del navegador queda igual al del servidor (mismas cantidades, sin omitidos)
    const m = new Map();
    c.lineas.forEach((l) => m.set(String(l.productoId), {
      nombre: l.nombre, precioDetal: Number(l.precioUnitario), precioMayor: null, cantidad: l.cantidad,
    }));
    const previo = local();
    m.forEach((v, k) => { if (previo.has(k)) { v.precioDetal = previo.get(k).precioDetal; v.precioMayor = previo.get(k).precioMayor; } });
    try { localStorage.setItem(CLAVE, JSON.stringify([...m])); } catch (e) { /* sin almacenamiento */ }
  }

  async function cargar() {
    const lineas = [...local()].map(([id, i]) => ({ productoId: Number(id), cantidad: Number(i.cantidad) }))
      .filter((l) => l.productoId > 0 && l.cantidad > 0);
    try {
      // sin lineas locales no se borra el carrito del servidor (puede venir de otro dispositivo)
      const r = lineas.length
        ? await fetch("/api/carrito/sincronizar", {
            method: "POST", credentials: "same-origin",
            headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf() },
            body: JSON.stringify({ lineas }),
          })
        : await fetch("/api/carrito", { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (r.status === 401) { location.href = "/login"; return; }
      const datos = await r.json();
      if (!r.ok) { estado.textContent = datos.error || "No se pudo calcular el carrito."; return; }
      pintar(datos);
    } catch (e) {
      estado.textContent = "Sin conexión con el servidor.";
    }
  }

  document.getElementById("form-checkout").addEventListener("submit", () => { confirmar.disabled = true; });
  cargar();
})();
