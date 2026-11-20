import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pruebas de conversión sin red: lee cada tests/*.html, lo convierte con
 * HtmlParser + MarkdownConverter y lo compara con tests/esperado/NOMBRE.md.
 *
 * Uso (desde la raíz del proyecto):  java -cp out PruebasConversion [--mostrar]
 * Con --mostrar imprime la salida obtenida (útil para crear los esperados).
 */
public class PruebasConversion {

    private static final String BASE = "https://ejemplo.org/docs/pagina.html";

    public static void main(String[] args) throws Exception {
        boolean mostrar = args.length > 0 && args[0].equals("--mostrar");
        Path dirPruebas = Path.of("tests");
        List<Path> archivos;
        try (Stream<Path> s = Files.list(dirPruebas)) {
            archivos = s.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
        URL base = URI.create(BASE).toURL();
        int ok = 0, fallos = 0;
        for (Path html : archivos) {
            String nombre = html.getFileName().toString().replace(".html", "");
            String obtenido = convertir(html, base);
            Path esperadoPath = dirPruebas.resolve("esperado").resolve(nombre + ".md");
            if (mostrar) {
                System.out.println("===== " + nombre + " =====");
                System.out.print(obtenido);
                System.out.println("<<fin>>");
                continue;
            }
            if (!Files.exists(esperadoPath)) {
                System.out.println("OMITIDA  " + nombre + " (no existe " + esperadoPath + ")");
                continue;
            }
            String esperado = Files.readString(esperadoPath, StandardCharsets.UTF_8);
            if (esperado.equals(obtenido)) {
                System.out.println("OK       " + nombre);
                ok++;
            } else {
                System.out.println("FALLO    " + nombre);
                System.out.println("--- esperado ---\n" + esperado + "--- obtenido ---\n" + obtenido + "----------------");
                fallos++;
            }
        }
        if (!mostrar) {
            System.out.printf("%nResultado: %d correctas, %d con fallo%n", ok, fallos);
            System.exit(fallos == 0 ? 0 : 1);
        }
    }

    private static String convertir(Path html, URL base) throws IOException {
        String contenido = Files.readString(html, StandardCharsets.UTF_8);
        return new MarkdownConverter(base).convertir(new HtmlParser().analizar(contenido));
    }
}
