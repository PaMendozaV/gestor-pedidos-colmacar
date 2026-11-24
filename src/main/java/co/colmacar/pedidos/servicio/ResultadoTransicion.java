package co.colmacar.pedidos.servicio;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.dominio.Pedido;

/** Resultado de aplicar un evento a un pedido. */
public record ResultadoTransicion(
        Motivo motivo,
        Pedido pedido,
        EstadoPedido estadoAnterior,
        EventoPedido evento,
        String mensaje) {

    public boolean aceptado() {
        return motivo == Motivo.ACEPTADO;
    }
}
