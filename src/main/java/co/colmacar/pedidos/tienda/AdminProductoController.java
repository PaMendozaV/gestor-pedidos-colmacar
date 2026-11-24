package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.repositorio.CategoriaRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Panel de productos. Protegido con ROLE_ADMIN en SeguridadConfig (/admin/**). */
@Controller
@RequestMapping("/admin")
public class AdminProductoController {

    public record ProductoAdminDto(Integer id, String nombre, BigDecimal precio, BigDecimal precioMayor,
                                   String imagen, String categoria, boolean activo) {
        static ProductoAdminDto de(Producto p) {
            return new ProductoAdminDto(p.getId(), p.getNombre(), p.getPrecio(), p.getPrecioMayor(), p.getImagen(),
                    p.getCategoria() == null ? null : p.getCategoria().getNombre(), p.isActivo());
        }
    }

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ProductoAdminService servicio;

    public AdminProductoController(ProductoRepository productos, CategoriaRepository categorias,
                                   ProductoAdminService servicio) {
        this.productos = productos;
        this.categorias = categorias;
        this.servicio = servicio;
    }

    @GetMapping({"", "/"})
    public String panel(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int pagina,
                        Model model) {
        model.addAttribute("q", q);
        model.addAttribute("pagina", productos.findByNombreContainingIgnoreCase(q.strip(),
                PageRequest.of(Math.max(pagina, 1) - 1, 20, Sort.by(Sort.Direction.DESC, "id"))));
        return "admin/productos";
    }

    @GetMapping("/productos/buscar")
    @ResponseBody
    public List<ProductoAdminDto> buscar(@RequestParam(defaultValue = "") String q) {
        return productos.findTop50ByNombreContainingIgnoreCaseOrderByIdDesc(q.strip())
                .stream().map(ProductoAdminDto::de).toList();
    }

    @GetMapping("/productos/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new ProductoForm(), null, null);
    }

    @PostMapping("/productos/nuevo")
    public String crear(@Valid @ModelAttribute("form") ProductoForm form, BindingResult errores,
                        @RequestParam(value = "imagen", required = false) MultipartFile imagen, Model model,
                        RedirectAttributes flash) {
        return guardar(null, form, errores, imagen, model, flash);
    }

    @GetMapping("/productos/{id}/editar")
    public String editar(@PathVariable Integer id, Model model) {
        Producto p = servicio.obtener(id);
        return formulario(model, ProductoForm.de(p), id, p.getImagen());
    }

    @PostMapping("/productos/{id}/editar")
    public String actualizar(@PathVariable Integer id, @Valid @ModelAttribute("form") ProductoForm form,
                             BindingResult errores, @RequestParam(value = "imagen", required = false) MultipartFile imagen,
                             Model model, RedirectAttributes flash) {
        return guardar(id, form, errores, imagen, model, flash);
    }

    /** Baja logica: el producto pasa a ELIMINADO y deja de verse en la tienda. */
    @PostMapping("/productos/{id}/eliminar")
    public String eliminar(@PathVariable Integer id, RedirectAttributes flash) {
        servicio.cambiarEstado(id, false);
        flash.addFlashAttribute("aviso", "Producto dado de baja.");
        return "redirect:/admin";
    }

    @PostMapping("/productos/{id}/activar")
    public String activar(@PathVariable Integer id, RedirectAttributes flash) {
        servicio.cambiarEstado(id, true);
        flash.addFlashAttribute("aviso", "Producto reactivado.");
        return "redirect:/admin";
    }

    private String guardar(Integer id, ProductoForm form, BindingResult errores, MultipartFile imagen, Model model,
                           RedirectAttributes flash) {
        String imagenActual = id == null ? null : servicio.obtener(id).getImagen();
        if (!servicio.validar(form, errores)) {
            return formulario(model, form, id, imagenActual);
        }
        try {
            servicio.guardar(id, form, imagen);
        } catch (ImagenException e) {
            model.addAttribute("errorImagen", e.getMessage());
            return formulario(model, form, id, imagenActual);
        }
        flash.addFlashAttribute("aviso", id == null ? "Producto creado." : "Producto actualizado.");
        return "redirect:/admin";
    }

    private String formulario(Model model, ProductoForm form, Integer id, String imagenActual) {
        model.addAttribute("form", form);
        model.addAttribute("productoId", id);
        model.addAttribute("imagenActual", imagenActual);
        model.addAttribute("categorias", categorias.findAll(Sort.by("nombre")));
        return "admin/producto-form";
    }
}
