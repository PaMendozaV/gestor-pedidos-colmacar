package co.colmacar.pedidos.servicio;

/** Sesion de cobro abierta en la pasarela: URL a la que va el cliente y referencia externa (id de Stripe). */
public record SesionPago(String url, String referencia) {
}
