package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.servicio.PedidoService;
import co.colmacar.pedidos.servicio.ResultadoTransicion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Ir a pagar": guarda datos de entrega y dispara CONFIRMAR_PEDIDO por el automata (delta intacta). */
@Service
public class CheckoutService {

    public record Resultado(boolean ok, String codigo, String urlPago, String error) {
    }

    private final PedidoRepository pedidos;
    private final PedidoService servicio;

    public CheckoutService(PedidoRepository pedidos, PedidoService servicio) {
        this.pedidos = pedidos;
        this.servicio = servicio;
    }

    @Transactional
    public Resultado confirmar(String username, CheckoutForm f) {
        Pedido carrito = pedidos.findFirstByUsuarioUsernameAndEstado(username, EstadoPedido.CARRITO).orElse(null);
        if (carrito == null) {
            return new Resultado(false, null, null, "Tu carrito esta vacio.");
        }
        carrito.datosEntrega(f.getNombre(), f.getTelefono(), f.getDireccion(), f.getNota());
        pedidos.save(carrito);
        ResultadoTransicion r = servicio.procesarEvento(carrito.getCodigo(), EventoPedido.CONFIRMAR_PEDIDO,
                Rol.CLIENTE);
        if (!r.aceptado()) {
            return new Resultado(false, carrito.getCodigo(), null, r.mensaje());
        }
        return new Resultado(true, carrito.getCodigo(), r.pedido().getUrlPago(), null);
    }
}
