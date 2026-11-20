import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


public class MarkdownWriter {


    public void guardar(Path destino, String contenido) throws IOException {
        Path padre = destino.toAbsolutePath().getParent();
        if (padre != null) {
            Files.createDirectories(padre);
        }
        Files.writeString(destino, contenido, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }


    public long tamano(Path archivo) throws IOException {
        return Files.size(archivo);
    }


    public static Path nombrePredeterminado(URL url) {
        String ruta = url.getPath() == null ? "" : url.getPath();
        while (ruta.endsWith("/")) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }
        String nombre = ruta.substring(ruta.lastIndexOf('/') + 1);
        int punto = nombre.lastIndexOf('.');
        if (punto > 0) {
            nombre = nombre.substring(0, punto);
        }
        if (nombre.isBlank()) {
            nombre = url.getHost();
        }
        nombre = nombre.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (nombre.isBlank()) {
            nombre = "salida";
        }
        return Path.of(nombre + ".md");
    }
}
