// Menu movil de la tienda.
(() => {
  const b = document.getElementById("btn-menu-movil");
  const menu = document.getElementById("menu-movil");
  if (!b || !menu) return;
  b.addEventListener("click", () => {
    const abierto = menu.classList.toggle("abierto");
    b.classList.toggle("activo", abierto);
    b.setAttribute("aria-expanded", String(abierto));
  });
})();
