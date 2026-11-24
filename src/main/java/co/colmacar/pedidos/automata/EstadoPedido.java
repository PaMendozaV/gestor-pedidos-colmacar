package co.colmacar.pedidos.automata;

/**
 * Conjunto de estados Q del AFD M = (Q, Sigma, delta, q0, F).
 */
public enum EstadoPedido {
    CARRITO,
    PAGO_PENDIENTE,
    PAGO_RECHAZADO,
    PREPARANDO_INVENTARIO,
    EN_TRANSITO,
    ENTREGADO;

    /** q0: estado inicial. */
    public static final EstadoPedido INICIAL = CARRITO;

    /** F: estados de aceptacion (el pedido completo termino su ciclo). */
    public boolean esFinal() {
        return this == ENTREGADO;
    }
}
