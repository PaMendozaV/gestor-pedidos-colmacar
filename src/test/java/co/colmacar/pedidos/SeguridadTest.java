package co.colmacar.pedidos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import co.colmacar.pedidos.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Login real contra la tabla usuario (DatosDemo crea los 3 usuarios), CSRF y rol por usuario autenticado. */
@SpringBootTest(properties = {
        "colmacar.datos-demo=true",
        "colmacar.modo=simulado",
        "colmacar.motor.habilitado=false",
        "colmacar.demo.clave-admin=clave-admin-test",
        "colmacar.demo.clave-cliente=clave-cliente-test",
        "colmacar.demo.clave-repartidor=clave-repartidor-test",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SeguridadTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;

    @BeforeEach
    void usuarios() {
        UsuariosPrueba.asegurar(usuarios, "cliente-nuevo");
    }

    @Test
    void loginCorrectoDaElRolDelUsuario() throws Exception {
        mvc.perform(formLogin("/login").user("cliente").password("clave-cliente-test"))
                .andExpect(redirectedUrl("/")).andExpect(authenticated().withUsername("cliente").withRoles("CLIENTE"));
        mvc.perform(formLogin("/login").user("repartidor").password("clave-repartidor-test"))
                .andExpect(authenticated().withRoles("REPARTIDOR"));
        mvc.perform(formLogin("/login").user("admin").password("clave-admin-test"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void loginIncorrectoDa401() throws Exception {
        mvc.perform(formLogin("/login").user("cliente").password("otra"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void sinSesionLaApiDa401YLasPaginasRedirigenAlLogin() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/pedidos.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @WithMockUser(username = "cliente", roles = "CLIENTE")
    void elClienteSoloVeSusPedidosYSabeQuienEs() throws Exception {
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.username").value("cliente"));
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @WithMockUser(username = "otro", roles = "CLIENTE")
    void otroClienteNoVeLosPedidosDemo() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/pedidos/PED-0001")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "cliente-nuevo", roles = "CLIENTE")
    void sinTokenCsrfSeRechaza() throws Exception {
        mvc.perform(post("/api/pedidos"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").with(csrf())).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void elAdministradorVeTodoPeroNoMueveNada() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
        mvc.perform(post("/api/pedidos/PED-0004/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"evento\":\"CONFIRMAR_PEDIDO\"}")).andExpect(status().isForbidden());
    }
}
