package co.colmacar.pedidos;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.colmacar.pedidos.automata.EventoPedido;
import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.tienda.CarritoService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;

import co.colmacar.pedidos.servicio.MotorAutomatico;
import co.colmacar.pedidos.servicio.Motivo;
import co.colmacar.pedidos.servicio.PasarelaPagos;
import co.colmacar.pedidos.servicio.PedidoService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pasarela siempre aprueba y sin esperas; el motor se ejecuta a mano (ejecutarCiclo)
 * para que las pruebas no dependan del reloj. El rol sale del usuario autenticado.
 */
@SpringBootTest(properties = {
        "colmacar.datos-demo=false",
        "colmacar.modo=simulado",
        "colmacar.motor.habilitado=false",
        "colmacar.motor.pago-ms=0",
        "colmacar.motor.preparacion-ms=0",
        "colmacar.pasarela.probabilidad-aprobacion=1.0",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class PedidoApiTest {

    static final RequestPostProcessor ANA = user("ana").roles("CLIENTE");
    static final RequestPostProcessor BETO = user("beto").roles("CLIENTE");
    static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    static final RequestPostProcessor REPARTIDOR = user("reparto").roles("REPARTIDOR");
    static final RequestPostProcessor SISTEMA = user("intruso").roles("SISTEMA");

    @Autowired MockMvc mvc;
    @Autowired MotorAutomatico motor;
    @Autowired PedidoService servicio;
    @Autowired PedidoRepository repo;
    @Autowired PasarelaPagos pasarela;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired CarritoService carritos;

    /** Un cliente nuevo por pedido: cada cliente tiene a lo sumo un carrito abierto. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private final Map<String, RequestPostProcessor> duenios = new HashMap<>();

    private RequestPostProcessor duenio(String codigo) {
        return duenios.get(codigo);
    }

    @BeforeEach
    void usuarios() {
        UsuariosPrueba.asegurar(usuarios, "ana", "beto");
    }

    private String crear() throws Exception {
        String cliente = "cli-" + SECUENCIA.incrementAndGet();
        UsuariosPrueba.carritoListo(carritos, productos, usuarios, cliente);
        RequestPostProcessor quien = user(cliente).roles("CLIENTE");
        String json = mvc.perform(post("/api/pedidos").with(quien).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CARRITO"))
                .andExpect(jsonPath("$.cliente").value(cliente))
                .andExpect(jsonPath("$.moneda").value("USD"))
                .andExpect(jsonPath("$.total", closeTo(8.0, 0.001)))
                .andExpect(jsonPath("$.codigoEntrega", matchesPattern("\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(json, "$.codigo");
        duenios.put(codigo, quien);
        return codigo;
    }

    private ResultActions enviar(String codigo, String evento, RequestPostProcessor quien) throws Exception {
        return enviar(codigo, evento, quien, null);
    }

    private ResultActions enviar(String codigo, String evento, RequestPostProcessor quien, String codigoEntrega)
            throws Exception {
        String cuerpo = codigoEntrega == null ? "{\"evento\":\"" + evento + "\"}"
                : "{\"evento\":\"" + evento + "\",\"codigoEntrega\":\"" + codigoEntrega + "\"}";
        var req = post("/api/pedidos/" + codigo + "/eventos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (quien != null) req = req.with(quien);
        return mvc.perform(req);
    }

    private void estadoEs(String codigo, String estado) throws Exception {
        mvc.perform(get("/api/pedidos/" + codigo).with(ADMIN)).andExpect(jsonPath("$.estado").value(estado));
    }

    private String codigoEntrega(String codigo) {
        return repo.findByCodigo(codigo).orElseThrow().getCodigoEntrega();
    }

    private String enTransito() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c)).andExpect(status().isOk());
        motor.ejecutarCiclo();
        motor.ejecutarCiclo();
        estadoEs(c, "EN_TRANSITO");
        return c;
    }

    // ---------- el sistema avanza solo (modo simulado) ----------

    @Test
    void elMotorMueveSoloLosEstadosDelSistemaYSeDetieneEnElRepartidor() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c)).andExpect(status().isOk());
        estadoEs(c, "PAGO_PENDIENTE");
        motor.ejecutarCiclo();
        estadoEs(c, "PREPARANDO_INVENTARIO");
        motor.ejecutarCiclo();
        estadoEs(c, "EN_TRANSITO");
        motor.ejecutarCiclo();
        estadoEs(c, "EN_TRANSITO");
        mvc.perform(get("/api/pedidos/" + c).with(ADMIN)).andExpect(jsonPath("$.responsable").value("REPARTIDOR"));

        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR, codigoEntrega(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido.estadoFinal").value(true));
    }

    @Test
    void enModoRealElMotorNoDecideNada() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c)).andExpect(status().isOk());
        MotorAutomatico motorReal = new MotorAutomatico(repo, servicio, pasarela, 0, 0, "real");
        assertEquals(0, motorReal.ejecutarCiclo());
        estadoEs(c, "PAGO_PENDIENTE");
    }

    @Test
    void elMotorNoTocaLosPedidosQueEsperanAUnaPersona() throws Exception {
        String carrito = crear();
        motor.ejecutarCiclo();
        estadoEs(carrito, "CARRITO");
    }

    @Test
    void elMotorRegistraAlSistemaComoActor() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c));
        motor.ejecutarCiclo();
        mvc.perform(get("/api/pedidos/" + c + "/historial").with(duenio(c)))
                .andExpect(jsonPath("$[0].rol").value("CLIENTE"))
                .andExpect(jsonPath("$[1].rol").value("SISTEMA"))
                .andExpect(jsonPath("$[1].fuente").value("PASARELA_PAGOS"));
    }

    // ---------- roles ----------

    @Test
    void elAdministradorNoPuedeEmitirNingunEvento() throws Exception {
        String c = crear();
        for (EventoPedido e : EventoPedido.values()) {
            enviar(c, e.name(), ADMIN).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.motivo").value("NO_AUTORIZADO"))
                    .andExpect(jsonPath("$.pedido.estado").value("CARRITO"));
        }
        mvc.perform(get("/api/pedidos/" + c + "/historial").with(ADMIN))
                .andExpect(jsonPath("$", hasSize(EventoPedido.values().length)))
                .andExpect(jsonPath("$[0].aceptado").value(false))
                .andExpect(jsonPath("$[0].rol").value("ADMIN"));
    }

    @Test
    void soloElRepartidorConfirmaLaEntrega() throws Exception {
        String c = enTransito();
        enviar(c, "ENTREGA_CONFIRMADA", ADMIN, codigoEntrega(c)).andExpect(status().isForbidden());
        enviar(c, "ENTREGA_CONFIRMADA", duenio(c), codigoEntrega(c)).andExpect(status().isForbidden());
        estadoEs(c, "EN_TRANSITO");
        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR, codigoEntrega(c)).andExpect(status().isOk());
        estadoEs(c, "ENTREGADO");
    }

    @Test
    void ningunaPersonaPuedeAprobarUnPagoYSistemaNoEntraPorHttp() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c));
        enviar(c, "PAGO_APROBADO", duenio(c)).andExpect(status().isForbidden());
        enviar(c, "PAGO_APROBADO", REPARTIDOR).andExpect(status().isForbidden());
        enviar(c, "PAGO_APROBADO", SISTEMA).andExpect(status().isForbidden());
        estadoEs(c, "PAGO_PENDIENTE");
    }

    @Test
    void elEncabezadoXRolYaNoTieneEfecto() throws Exception {
        String c = enTransito();
        mvc.perform(post("/api/pedidos/" + c + "/eventos").with(duenio(c)).with(csrf()).header("X-Rol", "REPARTIDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ENTREGA_CONFIRMADA\",\"codigoEntrega\":\"" + codigoEntrega(c) + "\"}"))
                .andExpect(status().isForbidden());
        estadoEs(c, "EN_TRANSITO");
    }

    @Test
    void sinSesionDa401() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", null).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        estadoEs(c, "CARRITO");
    }

    @Test
    void soloUnClienteCreaPedidos() throws Exception {
        mvc.perform(post("/api/pedidos").with(ADMIN).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------- propiedad del pedido ----------

    @Test
    void elClienteNoVeNiMuevePedidosAjenos() throws Exception {
        String c = crear();
        mvc.perform(get("/api/pedidos/" + c).with(BETO)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pedidos/" + c + "/historial").with(BETO)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pedidos").with(BETO).param("codigo", c)).andExpect(jsonPath("$", hasSize(0)));
        enviar(c, "CONFIRMAR_PEDIDO", BETO).andExpect(status().isNotFound());
        estadoEs(c, "CARRITO");
        mvc.perform(get("/api/pedidos").with(duenio(c)).param("codigo", c)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void elCodigoDeEntregaSoloLoVeSuCliente() throws Exception {
        String c = crear();
        mvc.perform(get("/api/pedidos/" + c).with(duenio(c))).andExpect(jsonPath("$.codigoEntrega").value(codigoEntrega(c)));
        mvc.perform(get("/api/pedidos/" + c).with(REPARTIDOR)).andExpect(jsonPath("$.codigoEntrega").value(nullValue()));
        mvc.perform(get("/api/pedidos/" + c).with(ADMIN)).andExpect(jsonPath("$.codigoEntrega").value(nullValue()));
        mvc.perform(get("/api/pedidos").with(REPARTIDOR).param("codigo", c))
                .andExpect(jsonPath("$[0].codigoEntrega").value(nullValue()));
    }

    @Test
    void codigoDeEntregaIncorrectoOAusenteNoEntrega() throws Exception {
        String c = enTransito();
        String bueno = codigoEntrega(c);
        String malo = bueno.equals("0000") ? "1111" : "0000";
        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR, malo).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("CODIGO_ENTREGA_INVALIDO"));
        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR).andExpect(status().isUnprocessableEntity());
        estadoEs(c, "EN_TRANSITO");
        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR, bueno).andExpect(status().isOk());
        estadoEs(c, "ENTREGADO");
    }

    // ---------- delta sigue mandando ----------

    @Test
    void rolCorrectoPeroTransicionNoDefinidaDa409() throws Exception {
        String c = crear();
        enviar(c, "ENTREGA_CONFIRMADA", REPARTIDOR, codigoEntrega(c)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivo").value("TRANSICION_NO_DEFINIDA"));
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c)).andExpect(status().isOk());
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c)).andExpect(status().isConflict());
        estadoEs(c, "PAGO_PENDIENTE");
    }

    @Test
    void elServicioRechazaEventosDelSistemaFueraDeOrden() throws Exception {
        String c = crear();
        var r = servicio.procesarEvento(c, EventoPedido.DESPACHO_ESCANEADO, Rol.SISTEMA);
        assertEquals(Motivo.TRANSICION_NO_DEFINIDA, r.motivo());
        estadoEs(c, "CARRITO");
    }

    // ---------- consultas ----------

    @Test
    void pedidoInexistenteDa404() throws Exception {
        mvc.perform(get("/api/pedidos/PED-9999").with(ADMIN)).andExpect(status().isNotFound());
        enviar("PED-9999", "CONFIRMAR_PEDIDO", ANA).andExpect(status().isNotFound());
    }

    @Test
    void eventoFueraDelAlfabetoDa400() throws Exception {
        String c = crear();
        enviar(c, "TELETRANSPORTAR", duenio(c)).andExpect(status().isBadRequest());
    }

    @Test
    void listadoFiltraPorEstado() throws Exception {
        String c = crear();
        enviar(c, "CONFIRMAR_PEDIDO", duenio(c));
        mvc.perform(get("/api/pedidos").with(ADMIN).param("estado", "PAGO_PENDIENTE").param("codigo", c))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value(c));
        mvc.perform(get("/api/pedidos").with(ADMIN).param("estado", "ENTREGADO").param("codigo", c))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void endpointAutomataExponeDefinicionYResponsables() throws Exception {
        mvc.perform(get("/api/automata").with(ANA))
                .andExpect(jsonPath("$.estados", hasSize(6)))
                .andExpect(jsonPath("$.estadoInicial").value("CARRITO"))
                .andExpect(jsonPath("$.estadosFinales[0]").value("ENTREGADO"))
                .andExpect(jsonPath("$.transiciones", hasSize(6)))
                .andExpect(jsonPath("$.rolPorEvento.ENTREGA_CONFIRMADA").value("REPARTIDOR"))
                .andExpect(jsonPath("$.responsablePorEstado.PAGO_PENDIENTE").value("SISTEMA"));
    }
}
