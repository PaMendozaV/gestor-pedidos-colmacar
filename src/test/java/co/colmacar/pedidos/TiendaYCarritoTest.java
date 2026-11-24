package co.colmacar.pedidos;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.dominio.Pago;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.repositorio.PagoRepository;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.tienda.ImageKitService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/** Tienda publica, admin de productos, carrito sincronizado y checkout hasta CONFIRMAR_PEDIDO. Umbral = 10. */
@SpringBootTest(properties = {
        "colmacar.motor.habilitado=false",
        "colmacar.mayorista.umbral=10",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class TiendaYCarritoTest {

    static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;
    @Autowired PagoRepository pagos;
    @Autowired TransactionTemplate tx;
    @MockBean ImageKitService imageKit;

    Producto taza;      // 4.00 / 3.40 mayor
    Producto agenda;    // 10.00 sin precio mayor
    String cliente;
    RequestPostProcessor yo;

    @BeforeEach
    void datos() {
        taza = UsuariosPrueba.producto(productos, "Taza", "4.00", "3.40");
        agenda = UsuariosPrueba.producto(productos, "Agenda", "10.00", null);
        cliente = "c-" + UUID.randomUUID().toString().substring(0, 8);
        UsuariosPrueba.asegurar(usuarios, cliente);
        yo = user(cliente).roles("CLIENTE");
    }

    private ResultActions sincronizar(String json) throws Exception {
        return mvc.perform(post("/api/carrito/sincronizar").with(yo).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":" + json + "}"));
    }

    private String linea(Producto p, int cantidad) {
        return "{\"productoId\":" + p.getId() + ",\"cantidad\":" + cantidad + ",\"precio\":0.01}";
    }

    private ResultActions checkout(String telefono) throws Exception {
        return mvc.perform(post("/checkout").with(yo).with(csrf()).param("nombre", "Ana Perez")
                .param("telefono", telefono).param("direccion", "Calle 10 # 5-20, Cucuta").param("nota", ""));
    }

    // ---------- tienda publica ----------

    @Test
    void paginasPublicasYBusquedaSoloActivos() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Taza")));
        mvc.perform(get("/catalogo")).andExpect(status().isOk());
        mvc.perform(get("/nosotros")).andExpect(status().isOk());
        Producto baja = UsuariosPrueba.producto(productos, "Taza vieja", "1.00", null);
        baja.setActivo(false);
        productos.save(baja);
        mvc.perform(get("/productos/buscar").param("q", "taza"))
                .andExpect(jsonPath("$[*].nombre", hasItem("Taza")))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Taza vieja"))));
    }

    @Test
    void adminSoloParaAdmin() throws Exception {
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin").with(yo)).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/checkout").with(ADMIN)).andExpect(status().isForbidden());
    }

    // ---------- admin de productos ----------

    @Test
    void adminCreaConImagenYDaDeBajaLogica() throws Exception {
        when(imageKit.subir(any())).thenReturn("https://ik.imagekit.io/x/productos/llavero.png");
        var imagen = new MockMultipartFile("imagen", "llavero.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0});
        mvc.perform(multipart("/admin/productos/nuevo").file(imagen).with(ADMIN).with(csrf())
                        .param("nombre", "Llavero nuevo").param("precio", "3.50").param("precioMayor", "2.80")
                        .param("disponible", "true"))
                .andExpect(redirectedUrl("/admin"));
        Producto p = productos.findTop50ByNombreContainingIgnoreCaseOrderByIdDesc("Llavero nuevo").get(0);
        assertEquals("https://ik.imagekit.io/x/productos/llavero.png", p.getImagen());
        assertTrue(p.isActivo());

        mvc.perform(post("/admin/productos/" + p.getId() + "/eliminar").with(ADMIN).with(csrf()))
                .andExpect(redirectedUrl("/admin"));
        assertFalse(productos.findById(p.getId()).orElseThrow().isActivo());   // sigue en la BD
        mvc.perform(get("/productos/buscar").param("q", "Llavero nuevo")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void adminValidaPrecios() throws Exception {
        mvc.perform(post("/admin/productos/nuevo").with(ADMIN).with(csrf())
                        .param("nombre", "X").param("precio", "2.00").param("precioMayor", "5.00"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "precioMayor"));
        mvc.perform(post("/admin/productos/nuevo").with(ADMIN).with(csrf()).param("nombre", "").param("precio", "-1"))
                .andExpect(model().attributeHasFieldErrors("form", "nombre", "precio"));
    }

    // ---------- carrito ----------

    @Test
    void elServidorRecalculaPreciosYMayorista() throws Exception {
        // 4 + 6 = 10 tazas (alcanza umbral 10 -> 3.40) y 2 agendas a 10.00; el "precio" enviado se ignora
        sincronizar("[" + linea(taza, 4) + "," + linea(taza, 6) + "," + linea(agenda, 2) + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineas", hasSize(2)))
                .andExpect(jsonPath("$.lineas[?(@.nombre=='Taza')].precioUnitario", contains(3.40)))
                .andExpect(jsonPath("$.lineas[?(@.nombre=='Taza')].mayorista", contains(true)))
                .andExpect(jsonPath("$.lineas[?(@.nombre=='Agenda')].mayorista", contains(false)))
                .andExpect(jsonPath("$.total", closeTo(54.0, 0.001)))   // 10*3.40 + 2*10
                .andExpect(jsonPath("$.moneda").value("USD"));
        sincronizar("[" + linea(taza, 9) + "]").andExpect(jsonPath("$.total", closeTo(36.0, 0.001)));
    }

    @Test
    void omiteProductosDadosDeBajaYUnSoloCarrito() throws Exception {
        Producto baja = UsuariosPrueba.producto(productos, "Retirado", "1.00", null);
        baja.setActivo(false);
        productos.save(baja);
        String json = sincronizar("[" + linea(taza, 1) + "," + linea(baja, 1) + "," + "{\"productoId\":999999,\"cantidad\":1}]")
                .andExpect(jsonPath("$.lineas", hasSize(1)))
                .andExpect(jsonPath("$.omitidos", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String c1 = JsonPath.read(json, "$.codigo");
        String c2 = JsonPath.read(sincronizar("[" + linea(agenda, 1) + "]").andReturn().getResponse()
                .getContentAsString(), "$.codigo");
        assertEquals(c1, c2);
        mvc.perform(get("/api/carrito").with(yo)).andExpect(jsonPath("$.codigo").value(c1))
                .andExpect(jsonPath("$.lineas", hasSize(1)));
    }

    @Test
    void sincronizarExigeCsrfRolClienteYDatosValidos() throws Exception {
        mvc.perform(post("/api/carrito/sincronizar").with(yo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lineas\":[]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/carrito/sincronizar").with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lineas\":[]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/carrito/sincronizar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lineas\":[]}")).andExpect(status().isUnauthorized());
        sincronizar("[" + linea(taza, 0) + "]").andExpect(status().isBadRequest());
        sincronizar("[" + linea(taza, 10001) + "]").andExpect(status().isBadRequest());
    }

    // ---------- checkout ----------

    @Test
    void checkoutConfirmaCongelaYRegistraPago() throws Exception {
        sincronizar("[" + linea(taza, 10) + "]");
        String codigo = pedidos.findFirstByUsuarioUsernameAndEstado(cliente, EstadoPedido.CARRITO).orElseThrow().getCodigo();

        checkout("3001234567").andExpect(redirectedUrl("/mis-pedidos?confirmado=" + codigo));

        Pedido p = pedidos.findByCodigo(codigo).orElseThrow();
        assertEquals(EstadoPedido.PAGO_PENDIENTE, p.getEstado());
        assertTrue(p.isCongelado());
        assertEquals(0, new BigDecimal("34.00").compareTo(p.getTotal()));
        assertEquals("Calle 10 # 5-20, Cucuta", p.getDireccionEntrega());
        var pagosPedido = pagos.findByPedidoIdOrderByIdAsc(p.getId());
        assertEquals(1, pagosPedido.size());
        assertEquals(Pago.Estado.PENDIENTE, pagosPedido.get(0).getEstado());
        assertEquals("SANDBOX", pagosPedido.get(0).getProveedor());

        // subir el precio despues de confirmar no cambia el pedido congelado
        taza.setPrecioMayor(new BigDecimal("9.99"));
        productos.save(taza);
        tx.executeWithoutResult(s -> {
            Pedido congelado = pedidos.findByCodigo(codigo).orElseThrow();
            assertEquals(0, new BigDecimal("3.40").compareTo(congelado.getDetalles().get(0).getPrecioUnitario()));
            assertThrows(IllegalStateException.class, () -> congelado.getDetalles().get(0).recalcular(1, 10));
        });

        // el siguiente sincronizar abre OTRO carrito; el confirmado no se toca
        String nuevo = JsonPath.read(sincronizar("[" + linea(agenda, 1) + "]").andReturn().getResponse()
                .getContentAsString(), "$.codigo");
        assertNotEquals(codigo, nuevo);
        mvc.perform(get("/mis-pedidos").with(yo)).andExpect(status().isOk())
                .andExpect(content().string(containsString(codigo)));
    }

    @Test
    void checkoutValidaDatosYCarritoVacio() throws Exception {
        checkout("3001234567").andExpect(status().isOk()).andExpect(model().attributeExists("errorCheckout"));
        sincronizar("[" + linea(taza, 1) + "]");
        checkout("abc").andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "telefono"));
        sincronizar("[]");
        checkout("3001234567").andExpect(model().attribute("errorCheckout", containsString("vacio")));
    }

    @Test
    void laApiNoPuedeSaltarseElCheckout() throws Exception {
        String json = sincronizar("[" + linea(taza, 1) + "]").andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(json, "$.codigo");
        mvc.perform(post("/api/pedidos/" + codigo + "/eventos").with(yo).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"evento\":\"CONFIRMAR_PEDIDO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("PEDIDO_INVALIDO"));
        assertEquals(EstadoPedido.CARRITO, pedidos.findByCodigo(codigo).orElseThrow().getEstado());
    }
}
