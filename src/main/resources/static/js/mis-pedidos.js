// Tras confirmar o volver de Stripe: vacia el carrito del navegador y, si un pago sigue pendiente,
// consulta su estado hasta que llegue el webhook (maximo ~1 minuto) para refrescar la pagina.
(() => {
  const q = new URLSearchParams(location.search);
  if (q.has("confirmado") || q.has("pago")) {
    try { localStorage.removeItem("colmacar_carrito"); } catch (e) { /* sin almacenamiento */ }
  }
  const codigo = q.get("codigo") || q.get("confirmado");
  const pendientes = (document.getElementById("mis-pedidos").dataset.pendientes || "").split(",");
  if (q.get("pago") !== "ok" || !codigo || !pendientes.includes(codigo)) return;
  let intentos = 0;
  const t = setInterval(async () => {
    intentos += 1;
    try {
      const r = await fetch("/api/pedidos/" + encodeURIComponent(codigo), { headers: { Accept: "application/json" } });
      const p = r.ok ? await r.json() : null;
      if (p && p.estado !== "PAGO_PENDIENTE") { clearInterval(t); location.replace("/mis-pedidos"); }
    } catch (e) { /* reintenta */ }
    if (intentos >= 20) clearInterval(t);
  }, 3000);
})();
