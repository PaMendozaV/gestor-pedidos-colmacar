package co.colmacar.pedidos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.colmacar.pedidos.servicio.MotorAutomatico;
import com.jayway.jsonpath.JsonPath;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.tienda.CarritoService;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** La pasarela simulada rechaza siempre: el motor deja el pedido en PAGO_RECHAZADO y espera al cliente. */
@SpringBootTest(properties = {
        "colmacar.datos-demo=false",
        "colmacar.modo=simulado",
        "colmacar.motor.habilitado=false",
        "colmacar.motor.pago-ms=0",
        "colmacar.pasarela.probabilidad-aprobacion=0.0",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class PagoRechazadoTest {

    @Autowired MockMvc mvc;
    @Autowired MotorAutomatico motor;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired CarritoService carritos;

    @BeforeEach
    void usuarios() {
        UsuariosPrueba.carritoListo(carritos, productos, usuarios, "ana");
    }

    @Test
    @WithMockUser(username = "ana", roles = "CLIENTE")
    void rechazoDeLaPasarelaDejaAlClienteReintentar() throws Exception {
        String json = mvc.perform(post("/api/pedidos").with(csrf())).andReturn().getResponse().getContentAsString();
        String c = JsonPath.read(json, "$.codigo");

        mvc.perform(post("/api/pedidos/" + c + "/eventos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"evento\":\"CONFIRMAR_PEDIDO\"}"));
        motor.ejecutarCiclo();
        mvc.perform(get("/api/pedidos/" + c)).andExpect(jsonPath("$.estado").value("PAGO_RECHAZADO"))
                .andExpect(jsonPath("$.responsable").value("CLIENTE"));

        motor.ejecutarCiclo();   // el motor no reintenta por el cliente
        mvc.perform(get("/api/pedidos/" + c)).andExpect(jsonPath("$.estado").value("PAGO_RECHAZADO"));

        mvc.perform(post("/api/pedidos/" + c + "/eventos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"evento\":\"REINTENTAR_PAGO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pedidos/" + c)).andExpect(jsonPath("$.estado").value("PAGO_PENDIENTE"));
    }
}
