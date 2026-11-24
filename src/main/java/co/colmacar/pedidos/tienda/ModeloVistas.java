package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.seguridad.UsuarioActual;
import co.colmacar.pedidos.web.AutenticacionController;
import java.time.Year;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Datos comunes de las vistas Thymeleaf (navegacion, pie, umbral mayorista). */
@ControllerAdvice(assignableTypes = {TiendaController.class, AdminProductoController.class,
        CheckoutController.class, AutenticacionController.class})
public class ModeloVistas {

    private final int umbral;
    private final String whatsapp;

    public ModeloVistas(@Value("${colmacar.mayorista.umbral:50}") int umbral,
                        @Value("${colmacar.tienda.whatsapp:}") String whatsapp) {
        this.umbral = umbral;
        this.whatsapp = whatsapp;
    }

    @ModelAttribute("sesion")
    public UsuarioActual sesion() {
        return UsuarioActual.de(SecurityContextHolder.getContext().getAuthentication()).orElse(null);
    }

    @ModelAttribute("anioActual")
    public int anioActual() {
        return Year.now().getValue();
    }

    @ModelAttribute("umbralMayorista")
    public int umbralMayorista() {
        return umbral;
    }

    @ModelAttribute("whatsapp")
    public String whatsapp() {
        return whatsapp;
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado() {
        return "error/404";
    }
}
