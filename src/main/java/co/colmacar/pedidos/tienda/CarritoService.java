package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.PedidoDetalle;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.servicio.PedidoService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrito = el unico pedido del cliente en estado CARRITO. El navegador solo envia {productoId, cantidad};
 * nombre, precio (detal o mayorista) y total se calculan aqui. Fuera de CARRITO las lineas no se tocan.
 */
@Service
public class CarritoService {

    public static final int MAX_LINEAS = 100;
    public static final int MAX_CANTIDAD = 10_000;

    public record LineaPedida(Integer productoId, Integer cantidad) {
    }

    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final PedidoService servicio;
    private final int umbral;

    public CarritoService(PedidoRepository pedidos, ProductoRepository productos, PedidoService servicio,
                          @Value("${colmacar.mayorista.umbral:50}") int umbral) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.servicio = servicio;
        this.umbral = umbral;
    }

    public int umbral() {
        return umbral;
    }

    @Transactional(readOnly = true)
    public CarritoDto actual(String username) {
        return pedidos.findFirstByUsuarioUsernameAndEstado(username, EstadoPedido.CARRITO)
                .map(p -> CarritoDto.de(p, umbral, List.of()))
                .orElse(CarritoDto.vacio(umbral));
    }

    /**
     * Reemplaza las lineas del carrito por las recibidas. Duplicados se suman; productos inexistentes o
     * dados de baja se omiten y se informan en "omitidos". Lanza IllegalArgumentException si la entrada es invalida.
     */
    @Transactional
    public CarritoDto sincronizar(String username, List<LineaPedida> recibidas) {
        if (recibidas == null || recibidas.size() > MAX_LINEAS) {
            throw new IllegalArgumentException("El carrito admite entre 0 y " + MAX_LINEAS + " lineas");
        }
        Map<Integer, Integer> pedidas = new LinkedHashMap<>();
        for (LineaPedida l : recibidas) {
            if (l == null || l.productoId() == null || l.cantidad() == null || l.cantidad() < 1) {
                throw new IllegalArgumentException("Cada linea necesita productoId y cantidad >= 1");
            }
            int suma = pedidas.merge(l.productoId(), l.cantidad(), Integer::sum);
            if (suma > MAX_CANTIDAD) {
                throw new IllegalArgumentException("Cantidad maxima por producto: " + MAX_CANTIDAD);
            }
        }

        Pedido carrito = servicio.crear(username);   // obtiene o crea el unico carrito
        carrito.exigirEditable();
        Map<Integer, Producto> vigentes = productos.findAllById(pedidas.keySet()).stream()
                .filter(Producto::isActivo)
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<Integer> omitidos = new ArrayList<>(pedidas.keySet());
        omitidos.removeAll(vigentes.keySet());

        carrito.getDetalles().removeIf(d -> !vigentes.containsKey(d.getProducto().getId()));
        Map<Integer, PedidoDetalle> actuales = carrito.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getProducto().getId(), Function.identity()));
        vigentes.forEach((id, producto) -> {
            int cantidad = pedidas.get(id);
            PedidoDetalle d = actuales.get(id);
            if (d != null) {
                d.recalcular(cantidad, umbral);
            } else {
                carrito.getDetalles().add(new PedidoDetalle(carrito, producto, cantidad, umbral));
            }
        });
        carrito.recalcularTotales();
        pedidos.save(carrito);
        return CarritoDto.de(carrito, umbral, omitidos);
    }

    /** Guarda los datos de entrega en el carrito abierto. */
    @Transactional
    public Pedido guardarDatosEntrega(String username, String nombre, String telefono, String direccion, String nota) {
        Pedido carrito = pedidos.findFirstByUsuarioUsernameAndEstado(username, EstadoPedido.CARRITO)
                .orElseThrow(() -> new IllegalStateException("No tienes un carrito abierto"));
        carrito.datosEntrega(nombre, telefono, direccion, nota);
        return pedidos.save(carrito);
    }
}
