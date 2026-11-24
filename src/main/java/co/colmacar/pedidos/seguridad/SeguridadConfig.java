package co.colmacar.pedidos.seguridad;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import co.colmacar.pedidos.automata.Rol;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Login unico /login (Thymeleaf) con sesion HTTP y BCrypt sobre tmusuario (sin JWT).
 * - CSRF activo con cookie XSRF-TOKEN legible por JS; el JS la reenvia en X-XSRF-TOKEN.
 * - /api/webhooks/** (firma Stripe) y /api/bodega/** (clave de API) no usan sesion ni CSRF:
 *   se protegen en su propio controlador.
 * - /api/** sin sesion responde 401; las paginas redirigen a /login. /registro es publico.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c
                // Cookie XSRF-TOKEN legible por JS (fetch del carrito) + campo _csrf en formularios Thymeleaf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(antMatcher("/api/webhooks/**"), antMatcher("/api/bodega/**")))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // tras el login se vuelve a la pagina pedida, nunca a una llamada /api/ que dio 401
            .requestCache(rc -> rc.requestCache(cachePaginas()))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers(antMatcher("/login"), antMatcher("/registro"), antMatcher("/css/**"),
                        antMatcher("/js/**"), antMatcher("/img/**"), antMatcher("/favicon.ico"),
                        antMatcher("/error")).permitAll()
                // tienda publica
                .requestMatchers(antMatcher(HttpMethod.GET, "/"), antMatcher(HttpMethod.GET, "/catalogo"),
                        antMatcher(HttpMethod.GET, "/nosotros"), antMatcher(HttpMethod.GET, "/productos/buscar"))
                    .permitAll()
                .requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN")
                .requestMatchers(antMatcher("/checkout"), antMatcher("/mis-pedidos"), antMatcher("/api/carrito/**"))
                    .hasRole("CLIENTE")
                .requestMatchers(antMatcher("/api/webhooks/**"), antMatcher("/api/bodega/**")).permitAll()
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")              // GET: plantilla Thymeleaf; POST: lo procesa Spring Security
                .successHandler(exitoPorRol())    // pagina pedida o destino segun el rol
                .failureUrl("/login?error")
                .permitAll())
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?salir"))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                if (req.getRequestURI().startsWith(req.getContextPath() + "/api/")) {
                    json(res, HttpStatus.UNAUTHORIZED, "{\"error\":\"Inicia sesion\"}");
                } else {
                    res.sendRedirect(req.getContextPath() + "/login");
                }
            }));
        return http.build();
    }

    /** Sin pagina pendiente: ADMIN -> /admin, REPARTIDOR -> panel de entregas, CLIENTE -> tienda. */
    private static SavedRequestAwareAuthenticationSuccessHandler exitoPorRol() {
        SavedRequestAwareAuthenticationSuccessHandler h = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            protected String determineTargetUrl(HttpServletRequest req, HttpServletResponse res, Authentication a) {
                var rol = UsuarioActual.de(a).map(UsuarioActual::rol).orElse(null);
                if (rol == Rol.ADMIN) {
                    return "/admin";
                }
                return rol == Rol.REPARTIDOR ? "/pedidos.html" : "/";
            }
        };
        h.setRequestCache(cachePaginas());
        return h;
    }

    private static HttpSessionRequestCache cachePaginas() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(antMatcher(HttpMethod.GET, "/**"),
                new NegatedRequestMatcher(antMatcher("/api/**"))));
        return cache;
    }

    private static void json(HttpServletResponse res, HttpStatus status, String cuerpo) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(cuerpo);
    }

    /** Fuerza a generar el token en cada peticion para que la cookie XSRF-TOKEN exista desde el login. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(req, res);
        }
    }
}
