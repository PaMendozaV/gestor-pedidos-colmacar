package co.colmacar.pedidos.automata;

/** Sistema o persona que origina un evento del alfabeto, y el rol que la representa. */
public enum FuenteEvento {
    CLIENTE(Rol.CLIENTE),
    PASARELA_PAGOS(Rol.SISTEMA),
    BODEGA(Rol.SISTEMA),
    REPARTIDOR(Rol.REPARTIDOR);

    private final Rol rol;

    FuenteEvento(Rol rol) {
        this.rol = rol;
    }

    public Rol getRol() {
        return rol;
    }
}
