package co.colmacar.pedidos;

import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.tienda.CarritoService;
import java.math.BigDecimal;
import java.util.List;

/** Datos de apoyo: los pedidos referencian tmusuario y CONFIRMAR_PEDIDO exige lineas y datos de entrega. */
final class UsuariosPrueba {

    private UsuariosPrueba() {
    }

    static void asegurar(UsuarioRepository repo, String... clientes) {
        for (String u : clientes) {
            if (!repo.existsByUsername(u)) {
                repo.save(new Usuario(u, "sin-login", Rol.CLIENTE));
            }
        }
    }

    static Producto producto(ProductoRepository repo, String nombre, String detal, String mayor) {
        return repo.save(new Producto(nombre, new BigDecimal(detal), mayor == null ? null : new BigDecimal(mayor),
                null, null));
    }

    /** Deja el carrito del cliente listo para CONFIRMAR_PEDIDO (1 linea + datos de entrega). Devuelve el codigo. */
    static String carritoListo(CarritoService carritos, ProductoRepository productos, UsuarioRepository usuarios,
                               String cliente) {
        asegurar(usuarios, cliente);
        Producto p = producto(productos, "Producto prueba", "4.00", "3.40");
        carritos.sincronizar(cliente, List.of(new CarritoService.LineaPedida(p.getId(), 2)));
        return carritos.guardarDatosEntrega(cliente, "Cliente", "3001234567", "Calle 1 # 2-3", null).getCodigo();
    }
}
