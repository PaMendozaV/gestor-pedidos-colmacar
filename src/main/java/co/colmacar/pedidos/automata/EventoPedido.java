package co.colmacar.pedidos.automata;

/**
 * Alfabeto Sigma del AFD. Cada evento tiene UN rol autorizado a emitirlo:
 * CLIENTE (confirmar, reintentar), SISTEMA (pasarela de pagos y bodega)
 * o REPARTIDOR (confirmar la entrega).
 */
public enum EventoPedido {
    CONFIRMAR_PEDIDO(FuenteEvento.CLIENTE),
    PAGO_APROBADO(FuenteEvento.PASARELA_PAGOS),
    PAGO_RECHAZADO(FuenteEvento.PASARELA_PAGOS),
    REINTENTAR_PAGO(FuenteEvento.CLIENTE),
    DESPACHO_ESCANEADO(FuenteEvento.BODEGA),
    ENTREGA_CONFIRMADA(FuenteEvento.REPARTIDOR);

    private final FuenteEvento fuente;

    EventoPedido(FuenteEvento fuente) {
        this.fuente = fuente;
    }

    public FuenteEvento getFuente() {
        return fuente;
    }

    /** Unico rol autorizado para emitir este evento. */
    public Rol getRol() {
        return fuente.getRol();
    }
}
