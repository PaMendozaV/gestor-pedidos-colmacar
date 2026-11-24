package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.repositorio.CategoriaRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Vistas publicas de la tienda (portadas de Flask) y busqueda en vivo. */
@Controller
public class TiendaController {

    public record ProductoPublicoDto(Integer id, String nombre, BigDecimal precio, BigDecimal precioMayor,
                                     String imagen, String categoria) {
        static ProductoPublicoDto de(Producto p) {
            return new ProductoPublicoDto(p.getId(), p.getNombre(), p.getPrecio(), p.getPrecioMayor(), p.getImagen(),
                    p.getCategoria() == null ? null : p.getCategoria().getNombre());
        }
    }

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;

    public TiendaController(ProductoRepository productos, CategoriaRepository categorias) {
        this.productos = productos;
        this.categorias = categorias;
    }

    @GetMapping("/")
    public String inicio(@RequestParam(defaultValue = "1") int pagina, Model model) {
        model.addAttribute("categorias", categorias.findAll(Sort.by("nombre")));
        model.addAttribute("pagina", productos.findByEstado(Usuario.ACTIVO, pagina(pagina, 10)));
        return "tienda/inicio";
    }

    @GetMapping("/catalogo")
    public String catalogo(@RequestParam(required = false) Integer categoria,
                           @RequestParam(defaultValue = "1") int pagina, Model model) {
        Page<Producto> p = categoria == null
                ? productos.findByEstado(Usuario.ACTIVO, pagina(pagina, 12))
                : productos.findByEstadoAndCategoriaId(Usuario.ACTIVO, categoria, pagina(pagina, 12));
        model.addAttribute("categorias", categorias.findAll(Sort.by("nombre")));
        model.addAttribute("categoriaActual", categoria);
        model.addAttribute("pagina", p);
        return "tienda/catalogo";
    }

    @GetMapping("/nosotros")
    public String nosotros() {
        return "tienda/nosotros";
    }

    /** Busqueda en vivo: solo productos ACTIVO, maximo 40. */
    @GetMapping("/productos/buscar")
    @ResponseBody
    public List<ProductoPublicoDto> buscar(@RequestParam(defaultValue = "") String q) {
        String termino = q.strip();
        if (termino.length() > 100) {
            termino = termino.substring(0, 100);
        }
        return productos.findTop40ByEstadoAndNombreContainingIgnoreCaseOrderByNombreAsc(Usuario.ACTIVO, termino)
                .stream().map(ProductoPublicoDto::de).toList();
    }

    static PageRequest pagina(int pagina, int tamano) {
        return PageRequest.of(Math.max(pagina, 1) - 1, tamano, Sort.by("id"));
    }
}
