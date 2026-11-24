package co.colmacar.pedidos;

import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.servicio.PedidoService;
import co.colmacar.pedidos.tienda.CarritoService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea los 3 usuarios demo (claves desde application.properties, guardadas con BCrypt) y,
 * si no hay pedidos, carga pedidos de ejemplo aplicando eventos reales para que la auditoria cuadre.
 * Es idempotente: con una base persistente no duplica nada al reiniciar.
 */
@Component
@ConditionalOnProperty(name = "colmacar.datos-demo", havingValue = "true")
public class DatosDemo implements CommandLineRunner {

    static final String CLIENTE = "cliente";

    private final PedidoService servicio;
    private final PedidoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final CarritoService carritos;
    private final PasswordEncoder encoder;
    private final String claveAdmin;
    private final String claveCliente;
    private final String claveRepartidor;

    public DatosDemo(PedidoService servicio, PedidoRepository pedidos, UsuarioRepository usuarios,
                     ProductoRepository productos, CarritoService carritos, PasswordEncoder encoder,
                     @Value("${colmacar.demo.clave-admin}") String claveAdmin,
                     @Value("${colmacar.demo.clave-cliente}") String claveCliente,
                     @Value("${colmacar.demo.clave-repartidor}") String claveRepartidor) {
        this.servicio = servicio;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.productos = productos;
        this.carritos = carritos;
        this.encoder = encoder;
        this.claveAdmin = claveAdmin;
        this.claveCliente = claveCliente;
        this.claveRepartidor = claveRepartidor;
    }

    @Override
    public void run(String... args) {
        usuario("admin", claveAdmin, Rol.ADMIN);
        usuario(CLIENTE, claveCliente, Rol.CLIENTE);
        usuario("repartidor", claveRepartidor, Rol.REPARTIDOR);

        if (pedidos.count() > 0) {
            return;
        }
        if (productos.count() == 0) {   // BD vacia (pruebas): un producto para poder armar carritos
            productos.save(new Producto("Taza demo 11 oz", new BigDecimal("4.00"), new BigDecimal("3.40"), null, null));
        }
        demoProducto = productos.findAll().stream().filter(Producto::isActivo).findFirst().orElseThrow().getId();
        // PED-0001: ciclo completo, ya entregado
        crear("Ref: 342", EventoPedido.CONFIRMAR_PEDIDO, EventoPedido.PAGO_APROBADO,
                EventoPedido.DESPACHO_ESCANEADO, EventoPedido.ENTREGA_CONFIRMADA);
        // PED-0002: en transito, esperando al repartidor
        crear("Ref: 523", EventoPedido.CONFIRMAR_PEDIDO, EventoPedido.PAGO_APROBADO,
                EventoPedido.DESPACHO_ESCANEADO);
        // PED-0003: pago rechazado, esperando que el cliente reintente
        crear("Ref: 717", EventoPedido.CONFIRMAR_PEDIDO, EventoPedido.PAGO_RECHAZADO);
        // PED-0004: esperando pago (en modo simulado el motor lo mueve solo)
        crear("Ref: 118", EventoPedido.CONFIRMAR_PEDIDO);
        // PED-0005: carrito abierto (va de ultimo: solo se permite un carrito por cliente)
        crear("Ref: 905");
    }

    private void usuario(String username, String clave, Rol rol) {
        if (!usuarios.existsByUsername(username)) {
            usuarios.save(new Usuario(username, encoder.encode(clave), rol));
        }
    }

    /** Cada evento se aplica con el rol que le corresponde, igual que en el funcionamiento real. */
    private Integer demoProducto;

    /** Carrito con una linea y datos de entrega (requisitos de CONFIRMAR_PEDIDO) y luego los eventos. */
    private void crear(String nota, EventoPedido... eventos) {
        carritos.sincronizar(CLIENTE, List.of(new CarritoService.LineaPedida(demoProducto, 2)));
        Pedido p = carritos.guardarDatosEntrega(CLIENTE, "Cliente demo", "3001234567", "Calle 1 # 2-3, Cucuta", nota);
        for (EventoPedido e : eventos) {
            servicio.procesarEvento(p.getCodigo(), e, e.getRol(), p.getCodigoEntrega());
        }
    }
}
