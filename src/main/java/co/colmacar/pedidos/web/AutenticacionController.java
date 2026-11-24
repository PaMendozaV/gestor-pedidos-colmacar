package co.colmacar.pedidos.web;

import co.colmacar.pedidos.seguridad.RegistroForm;
import co.colmacar.pedidos.seguridad.RegistroService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Paginas Thymeleaf de login y registro, y la entrada "/" que redirige por rol. */
@Controller
public class AutenticacionController {

    private final RegistroService registro;

    public AutenticacionController(RegistroService registro) {
        this.registro = registro;
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        return conSesion(auth) ? "redirect:/" : "login";
    }

    @GetMapping("/registro")
    public String registroForm(Authentication auth, Model model) {
        if (conSesion(auth)) {
            return "redirect:/";
        }
        model.addAttribute("form", new RegistroForm());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("form") RegistroForm form, BindingResult errores) {
        try {
            if (!registro.registrar(form, errores)) {
                return "registro";
            }
        } catch (DataIntegrityViolationException e) {   // dos altas simultaneas con el mismo usuario/correo
            errores.reject("duplicado", "El usuario o el correo ya existen");
            return "registro";
        }
        return "redirect:/login?registrado";
    }

    private static boolean conSesion(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
