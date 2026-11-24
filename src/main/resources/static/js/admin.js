// Admin de productos: busqueda en vivo, confirmacion de baja y vista previa de imagen.
(() => {
  const csrf = () => {
    const c = document.cookie.split("; ").find((x) => x.startsWith("XSRF-TOKEN="));
    return c ? decodeURIComponent(c.slice(11)) : "";
  };
  const el = (tag, clase, texto) => {
    const e = document.createElement(tag);
    if (clase) e.className = clase;
    if (texto != null) e.textContent = texto;
    return e;
  };
  const precio = (v) => (v == null ? "—" : "$" + Number(v).toFixed(2));

  function formulario(accion, texto, confirmacion) {
    const f = el("form", "form-en-linea");
    f.method = "post"; f.action = accion;
    if (confirmacion) f.dataset.confirmar = confirmacion;
    const t = el("input"); t.type = "hidden"; t.name = "_csrf"; t.value = csrf();
    const b = el("button", null, texto); b.type = "submit";
    f.append(t, b);
    return f;
  }

  function fila(p) {
    const tr = el("tr");
    const nombre = el("td", "tabla-productos__nombre");
    if (p.imagen && /^https:\/\//.test(p.imagen)) { const i = el("img"); i.src = p.imagen; i.alt = ""; nombre.appendChild(i); }
    else nombre.appendChild(el("span", "tabla-productos__miniatura-vacia"));
    nombre.appendChild(el("span", null, p.nombre));
    const estado = el("td");
    estado.appendChild(el("span", "estado-pill" + (p.activo ? "" : " estado-pill--baja"), p.activo ? "Activo" : "Eliminado"));
    const acciones = el("td", "tabla-productos__acciones");
    const editar = el("a", null, "Editar"); editar.href = `/admin/productos/${p.id}/editar`;
    acciones.append(editar, p.activo
      ? formulario(`/admin/productos/${p.id}/eliminar`, "Dar de baja", "¿Dar de baja este producto?")
      : formulario(`/admin/productos/${p.id}/activar`, "Reactivar"));
    tr.append(nombre, el("td", "tabla-productos__precio", precio(p.precio)), el("td", null, precio(p.precioMayor)), estado, acciones);
    return tr;
  }

  const input = document.getElementById("buscador-admin");
  const lista = document.getElementById("lista-productos-admin");
  if (input && lista) {
    let t = null, peticion = null;
    input.addEventListener("input", () => {
      clearTimeout(t);
      t = setTimeout(async () => {
        if (peticion) peticion.abort();
        peticion = new AbortController();
        try {
          const r = await fetch("/admin/productos/buscar?q=" + encodeURIComponent(input.value.trim()), { signal: peticion.signal });
          const datos = r.ok ? await r.json() : [];
          lista.replaceChildren(...datos.map(fila));
          const pag = document.querySelector(".paginacion");
          if (pag) pag.hidden = input.value.trim() !== "";
        } catch (e) { /* abortada */ }
      }, 300);
    });
  }

  document.addEventListener("submit", (e) => {
    const msg = e.target.dataset && e.target.dataset.confirmar;
    if (msg && !confirm(msg)) e.preventDefault();
  });

  const archivo = document.getElementById("imagen");
  if (archivo) {
    archivo.addEventListener("change", () => {
      const f = archivo.files && archivo.files[0];
      if (!f) return;
      if (f.size > 5 * 1024 * 1024) { alert("La imagen supera 5 MB."); archivo.value = ""; return; }
      const img = el("img", "subida-imagen__preview"); img.alt = "Vista previa"; img.id = "vista-previa";
      img.src = URL.createObjectURL(f);
      document.getElementById("vista-previa").replaceWith(img);
    });
  }
})();
