package co.colmacar.pedidos.tienda;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/** Sube imagenes de productos a ImageKit (API de subida con la llave privada en Basic Auth). */
@Service
public class ImageKitService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final RestClient http;
    private final String llavePrivada;
    private final String carpeta;

    public ImageKitService(RestClient.Builder builder,
                           @Value("${imagekit.private-key:}") String llavePrivada,
                           @Value("${imagekit.upload-url:https://upload.imagekit.io/api/v1/files/upload}") String url,
                           @Value("${imagekit.carpeta:/productos}") String carpeta) {
        this.http = builder.baseUrl(url).build();
        this.llavePrivada = llavePrivada;
        this.carpeta = carpeta;
    }

    /** Devuelve la URL publica de la imagen subida. */
    public String subir(MultipartFile archivo) {
        if (llavePrivada == null || llavePrivada.isBlank()) {
            throw new ImagenException("ImageKit no esta configurado (falta IMAGEKIT_PRIVATE_KEY).");
        }
        byte[] bytes = validar(archivo);
        String nombre = nombreSeguro(archivo.getOriginalFilename());

        HttpHeaders cabeceraArchivo = new HttpHeaders();
        cabeceraArchivo.setContentType(MediaType.parseMediaType(archivo.getContentType()));
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return nombre;
            }
        }, cabeceraArchivo));
        partes.add("fileName", nombre);
        partes.add("folder", carpeta);
        partes.add("useUniqueFileName", "true");

        try {
            Map<?, ?> respuesta = http.post()
                    .headers(h -> h.setBasicAuth(llavePrivada, ""))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(Map.class);
            Object url = respuesta == null ? null : respuesta.get("url");
            if (!(url instanceof String s) || !s.startsWith("https://") || s.length() > 255) {
                throw new ImagenException("ImageKit devolvio una respuesta inesperada.");
            }
            return s;
        } catch (RestClientException e) {
            throw new ImagenException("No se pudo subir la imagen a ImageKit. Intenta de nuevo.");
        }
    }

    /** Tipo declarado + firma real del archivo (no basta con la extension). */
    static byte[] validar(MultipartFile archivo) {
        if (archivo.getSize() > MAX_BYTES) {
            throw new ImagenException("La imagen supera 5 MB.");
        }
        if (archivo.getContentType() == null || !TIPOS.contains(archivo.getContentType())) {
            throw new ImagenException("Formato no permitido: usa JPG, PNG, WEBP o GIF.");
        }
        byte[] b;
        try {
            b = archivo.getBytes();
        } catch (IOException e) {
            throw new ImagenException("No se pudo leer la imagen.");
        }
        boolean firma = (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8)                 // JPEG
                || (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')   // PNG
                || (b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F')                            // GIF
                || (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[8] == 'W' && b[9] == 'E');           // WEBP
        if (!firma) {
            throw new ImagenException("El archivo no es una imagen valida.");
        }
        return b;
    }

    static String nombreSeguro(String original) {
        String base = original == null ? "imagen" : original.replaceAll("^.*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.startsWith(".")) {
            base = "imagen" + base;
        }
        return base.length() > 80 ? base.substring(base.length() - 80) : base;
    }
}
