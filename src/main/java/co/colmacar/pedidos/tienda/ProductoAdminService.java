package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.dominio.Categoria;
import co.colmacar.pedidos.dominio.Producto;
import co.colmacar.pedidos.repositorio.CategoriaRepository;
import co.colmacar.pedidos.repositorio.ProductoRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

/** CRUD de productos del admin. Eliminar = baja logica (fkcods = ELIMINADO): las lineas de pedidos siguen validas. */
@Service
public class ProductoAdminService {

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ImageKitService imagenes;

    public ProductoAdminService(ProductoRepository productos, CategoriaRepository categorias,
                                ImageKitService imagenes) {
        this.productos = productos;
        this.categorias = categorias;
        this.imagenes = imagenes;
    }

    @Transactional(readOnly = true)
    public Producto obtener(Integer id) {
        Producto p = productos.findById(id).orElseThrow(() -> new NoSuchElementException("Producto " + id));
        if (p.getCategoria() != null) {
            p.getCategoria().getNombre();   // inicializa la categoria para la vista
        }
        return p;
    }

    /** Reglas que no expresan las anotaciones. Devuelve true si el formulario es valido. */
    public boolean validar(ProductoForm f, BindingResult errores) {
        if (f.getPrecio() != null && f.getPrecioMayor() != null && f.getPrecioMayor().compareTo(f.getPrecio()) > 0) {
            errores.rejectValue("precioMayor", "mayor", "El precio mayorista no puede superar al de detal");
        }
        if (f.getCategoriaId() != null && !categorias.existsById(f.getCategoriaId())) {
            errores.rejectValue("categoriaId", "inexistente", "Categoria no valida");
        }
        return !errores.hasErrors();
    }

    /** id null = crear. La imagen (opcional) se sube antes de tocar la BD. */
    @Transactional
    public Producto guardar(Integer id, ProductoForm f, MultipartFile imagen) {
        String url = (imagen != null && !imagen.isEmpty()) ? imagenes.subir(imagen) : null;
        Categoria cat = f.getCategoriaId() == null ? null : categorias.getReferenceById(f.getCategoriaId());
        Producto p;
        if (id == null) {
            p = new Producto(f.getNombre(), f.getPrecio(), f.getPrecioMayor(), url, cat);
        } else {
            p = productos.findById(id).orElseThrow(() -> new NoSuchElementException("Producto " + id));
            p.setNombre(f.getNombre());
            p.setPrecio(f.getPrecio());
            p.setPrecioMayor(f.getPrecioMayor());
            p.setCategoria(cat);
            if (url != null) {
                p.setImagen(url);
            }
        }
        p.setActivo(f.isDisponible());
        return productos.save(p);
    }

    @Transactional
    public void cambiarEstado(Integer id, boolean activo) {
        Producto p = productos.findById(id).orElseThrow(() -> new NoSuchElementException("Producto " + id));
        p.setActivo(activo);
    }
}
