package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.automata.EstadoPedido;
import co.colmacar.pedidos.dominio.Pedido;
import co.colmacar.pedidos.repositorio.PedidoRepository;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import co.colmacar.pedidos.servicio.PasarelaNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** /checkout y /mis-pedidos (ROLE_CLIENTE). */
@Controller
public class CheckoutController {

    private static final Logger LOG = LoggerFactory.getLogger(CheckoutController.class);

    private final CheckoutService checkout;
    private final PedidoRepository pedidos;
    private final UsuarioRepository usuarios;

    public CheckoutController(CheckoutService checkout, PedidoRepository pedidos, UsuarioRepository usuarios) {
        this.checkout = checkout;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
    }

    /** Las lineas las dibuja checkout.js tras sincronizar; aqui solo se prellenan los datos de entrega. */
    @GetMapping("/checkout")
    public String ver(Authentication auth, Model model) {
        CheckoutForm f = new CheckoutForm();
        pedidos.findFirstByUsuarioUsernameAndEstado(auth.getName(), EstadoPedido.CARRITO).ifPresent(p -> {
            f.setNombre(p.getNombreContacto());
            f.setTelefono(p.getTelefonoContacto());
            f.setDireccion(p.getDireccionEntrega());
            f.setNota(p.getNota());
        });
        if (f.getNombre() == null) {
            usuarios.findByUsername(auth.getName()).ifPresent(u -> {
                f.setNombre(u.getNombre());
                f.setTelefono(u.getTelefono());
            });
        }
        model.addAttribute("form", f);
        return "tienda/checkout";
    }

    @PostMapping("/checkout")
    public String confirmar(@Valid @ModelAttribute("form") CheckoutForm form, BindingResult errores,
                            Authentication auth, Model model) {
        if (errores.hasErrors()) {
            return "tienda/checkout";
        }
        CheckoutService.Resultado r;
        try {
            r = checkout.confirmar(auth.getName(), form);   // en modo real abre la Checkout Session de Stripe
        } catch (PasarelaNoDisponibleException e) {        // la transaccion se revierte: el pedido sigue en CARRITO
            LOG.warn("Checkout de {}: {}", auth.getName(), e.getMessage());
            model.addAttribute("errorCheckout", "No se pudo iniciar el pago. Intenta de nuevo en unos minutos.");
            return "tienda/checkout";
        }
        if (!r.ok()) {
            model.addAttribute("errorCheckout", r.error());
            return "tienda/checkout";
        }
        return r.urlPago() != null ? "redirect:" + r.urlPago() : "redirect:/mis-pedidos?confirmado=" + r.codigo();
    }

    @GetMapping("/mis-pedidos")
    @Transactional(readOnly = true)
    public String misPedidos(Authentication auth, Model model) {
        var lista = pedidos.findByUsuarioUsernameAndEstadoNotOrderByIdDesc(auth.getName(), EstadoPedido.CARRITO);
        lista.forEach(p -> p.getDetalles().size());
        model.addAttribute("pedidos", lista);
        model.addAttribute("carrito", pedidos.findFirstByUsuarioUsernameAndEstado(auth.getName(),
                EstadoPedido.CARRITO).map(Pedido::getCodigo).orElse(null));
        return "tienda/mis-pedidos";
    }
}
