package co.colmacar.pedidos.web;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.automata.TablaTransiciones;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.RegistroTransicion;
import co.colmacar.pedidos.servicio.Motivo;
import co.colmacar.pedidos.servicio.ResultadoTransicion;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Objetos de entrada y salida de la API REST. */
public final class Dtos {

    private Dtos() {
    }

    /** codigoEntrega solo se usa con ENTREGA_CONFIRMADA. */
    public record EventoRequest(@NotNull(message = "evento es obligatorio") EventoPedido evento,
                                String codigoEntrega) {
    }

    /**
     * responsable: rol que debe mover el pedido desde su estado actual (null si ya es final).
     * codigoEntrega y urlPago solo se envian al cliente duenio del pedido (null para los demas).
     */
    public record PedidoDto(String codigo, String cliente, String moneda, BigDecimal total, EstadoPedido estado,
                            boolean estadoFinal, LocalDateTime actualizadoEn, Rol responsable,
                            List<EventoPedido> eventosPermitidos, String codigoEntrega, String urlPago) {
        public static PedidoDto de(Pedido p, boolean esDuenio) {
            return new PedidoDto(p.getCodigo(), p.getCliente(), p.getMoneda(), p.getTotal(), p.getEstado(),
                    p.getEstado().esFinal(), p.getActualizadoEn(),
                    TablaTransiciones.responsable(p.getEstado()).orElse(null),
                    List.copyOf(TablaTransiciones.eventosPermitidos(p.getEstado())),
                    esDuenio ? p.getCodigoEntrega() : null,
                    esDuenio ? p.getUrlPago() : null);
        }
    }

    public record ResultadoDto(boolean aceptado, Motivo motivo, String mensaje, EstadoPedido estadoAnterior,
                               EventoPedido evento, PedidoDto pedido) {
        public static ResultadoDto de(ResultadoTransicion r, boolean esDuenio) {
            return new ResultadoDto(r.aceptado(), r.motivo(), r.mensaje(), r.estadoAnterior(), r.evento(),
                    PedidoDto.de(r.pedido(), esDuenio));
        }
    }

    public record SesionDto(String username, Rol rol) {
    }

    public record RegistroDto(LocalDateTime fecha, EstadoPedido estadoOrigen, EventoPedido evento,
                              String fuente, Rol rol, EstadoPedido estadoDestino, boolean aceptado, String detalle) {
        public static RegistroDto de(RegistroTransicion r) {
            return new RegistroDto(r.getFecha(), r.getEstadoOrigen(), r.getEvento(), r.getFuente().name(), r.getRol(),
                    r.getEstadoDestino(), r.isAceptado(), r.getDetalle());
        }
    }

    public record TransicionDto(EstadoPedido origen, EventoPedido evento, EstadoPedido destino) {
    }

    public record AutomataDto(List<EstadoPedido> estados, List<EventoPedido> alfabeto,
                              EstadoPedido estadoInicial, List<EstadoPedido> estadosFinales,
                              List<TransicionDto> transiciones,
                              Map<EventoPedido, Rol> rolPorEvento, Map<EstadoPedido, Rol> responsablePorEstado) {
        public static AutomataDto actual() {
            List<TransicionDto> ts = new ArrayList<>();
            TablaTransiciones.tabla().forEach((origen, fila) ->
                    fila.forEach((ev, destino) -> ts.add(new TransicionDto(origen, ev, destino))));
            Map<EventoPedido, Rol> roles = new EnumMap<>(EventoPedido.class);
            for (EventoPedido e : EventoPedido.values()) {
                roles.put(e, e.getRol());
            }
            Map<EstadoPedido, Rol> responsables = new EnumMap<>(EstadoPedido.class);
            for (EstadoPedido e : EstadoPedido.values()) {
                TablaTransiciones.responsable(e).ifPresent(r -> responsables.put(e, r));
            }
            return new AutomataDto(
                    Arrays.asList(EstadoPedido.values()),
                    Arrays.asList(EventoPedido.values()),
                    EstadoPedido.INICIAL,
                    Arrays.stream(EstadoPedido.values()).filter(EstadoPedido::esFinal).toList(),
                    ts, roles, responsables);
        }
    }
}
