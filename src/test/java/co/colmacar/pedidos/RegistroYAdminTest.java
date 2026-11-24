package co.colmacar.pedidos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.seguridad.InicializadorAdmin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Registro de clientes y clave inicial del admin (migracion del hash scrypt de Flask). */
@SpringBootTest(properties = {
        "colmacar.motor.habilitado=false",
        "spring.datasource.url=jdbc:h2:mem:t${random.uuid};DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class RegistroYAdminTest {

    static final String SCRYPT = "scrypt:32768:8:1$xmHxDS2pwj93doRu$41ebc769e0ed44ac";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    private MockHttpServletRequestBuilder registro(String usuario, String email, String clave, String confirmacion) {
        return post("/registro").with(csrf()).param("username", usuario).param("nombre", "Ana Perez")
                .param("email", email).param("telefono", "").param("password", clave)
                .param("confirmacion", confirmacion);
    }

    @Test
    void registroCreaClienteConBcryptYPuedeEntrar() throws Exception {
        mvc.perform(registro("ana.perez", "Ana@Correo.com", "claveSegura1", "claveSegura1"))
                .andExpect(redirectedUrl("/login?registrado"));
        Usuario u = usuarios.findByUsername("ana.perez").orElseThrow();
        assertEquals(Rol.CLIENTE, u.getRol());
        assertEquals("ana@correo.com", u.getEmail());
        assertTrue(u.getPassword().startsWith("$2"));
        assertTrue(u.isActivo());
        mvc.perform(formLogin("/login").user("ana.perez").password("claveSegura1"))
                .andExpect(authenticated().withRoles("CLIENTE"));
    }

    @Test
    void registroRechazaDuplicadosYDatosInvalidos() throws Exception {
        mvc.perform(registro("beto", "beto@correo.com", "claveSegura1", "claveSegura1"));
        mvc.perform(registro("BETO", "otro@correo.com", "claveSegura1", "claveSegura1"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "username"));
        mvc.perform(registro("beto2", "BETO@correo.com", "claveSegura1", "claveSegura1"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
        mvc.perform(registro("carla", "carla@correo.com", "corta", "corta"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
        mvc.perform(registro("dani", "dani@correo.com", "claveSegura1", "otraClave1"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmacion"));
        mvc.perform(registro("a b", "e@correo.com", "claveSegura1", "claveSegura1"))
                .andExpect(model().attributeHasFieldErrors("form", "username"));
        assertFalse(usuarios.existsByUsername("dani"));
    }

    @Test
    void registroSinCsrfSeRechaza() throws Exception {
        mvc.perform(post("/registro").param("username", "x")).andExpect(status().isForbidden());
    }

    @Test
    void elAdminConHashDeFlaskSeMigraYSeActiva() throws Exception {
        Usuario viejo = new Usuario("admin-flask", SCRYPT, Rol.ADMIN);
        viejo.setActivo(false);   // asi lo deja la V2
        usuarios.save(viejo);
        mvc.perform(formLogin("/login").user("admin-flask").password("ClaveAdmin2026")).andExpect(unauthenticated());

        new InicializadorAdmin(usuarios, encoder, "admin-flask", "ClaveAdmin2026").run();

        mvc.perform(formLogin("/login").user("admin-flask").password("ClaveAdmin2026"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void unaClaveBcryptValidaNoSeSobrescribe() {
        String hash = encoder.encode("ClaveActual2026");
        usuarios.save(new Usuario("admin-ok", hash, Rol.ADMIN));
        new InicializadorAdmin(usuarios, encoder, "admin-ok", "OtraClave2026").run();
        assertEquals(hash, usuarios.findByUsername("admin-ok").orElseThrow().getPassword());
    }

    @Test
    void creaElAdminSiNoExisteYNoEscalaAUnCliente() {
        new InicializadorAdmin(usuarios, encoder, "admin-nuevo", "ClaveAdmin2026").run();
        assertEquals(Rol.ADMIN, usuarios.findByUsername("admin-nuevo").orElseThrow().getRol());

        Usuario cliente = new Usuario("jefe", SCRYPT, Rol.CLIENTE);
        usuarios.save(cliente);
        new InicializadorAdmin(usuarios, encoder, "jefe", "ClaveAdmin2026").run();
        Usuario despues = usuarios.findByUsername("jefe").orElseThrow();
        assertEquals(Rol.CLIENTE, despues.getRol());
        assertEquals(SCRYPT, despues.getPassword());
    }

    @Test
    void sinClaveInicialNoHaceNada() {
        new InicializadorAdmin(usuarios, encoder, "admin-vacio", "").run();
        assertFalse(usuarios.existsByUsername("admin-vacio"));
    }
}
