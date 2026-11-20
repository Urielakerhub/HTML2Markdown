import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;


public class HTML2Markdown {

    static final int EXITO = 0;
    static final int ERROR_ARGUMENTOS = 1;
    static final int ERROR_RED = 2;
    static final int ERROR_CONVERSION = 3;
    static final int ERROR_ESCRITURA = 4;

    public static void main(String[] args) {
        System.exit(ejecutar(args));
    }

    static int ejecutar(String[] args) {
        if (args.length < 1 || args.length > 2) {
            mostrarUso();
            return ERROR_ARGUMENTOS;
        }

        System.out.println("HTML2Markdown");
        System.out.println("____________________________");

        URL url;
        try {
            url = interpretarUrl(args[0]);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: URL inválida: " + e.getMessage());
            return ERROR_ARGUMENTOS;
        }
        System.out.println("URL: " + url);

        Path destino;
        try {
            destino = args.length == 2 ? Path.of(args[1]) : MarkdownWriter.nombrePredeterminado(url);
            if (Files.isDirectory(destino)) {
                destino = destino.resolve(MarkdownWriter.nombrePredeterminado(url));
            }
        } catch (InvalidPathException e) {
            System.err.println("Error: ruta de salida inválida: " + e.getMessage());
            return ERROR_ARGUMENTOS;
        }

        // 1) Descarga (cliente HTTP)
        WebClient cliente = new WebClient(Boolean.getBoolean("html2md.permitirLocal"));
        System.out.println("Conectando con el servidor...");
        String html;
        try {
            html = cliente.descargarHTML(url);
        } catch (IOException e) {
            if (cliente.getCodigoHttp() > 0) {
                System.out.println("Respuesta HTTP: " + cliente.getCodigoHttp());
            }
            System.err.println("Error de conexión: " + describir(e));
            return ERROR_RED;
        }
        System.out.println("Respuesta HTTP: " + cliente.getCodigoHttp());

        // 2) Análisis y conversión
        String markdown;
        try {
            System.out.println("Analizando HTML...");
            HtmlParser.Nodo documento = new HtmlParser().analizar(html);
            System.out.println("Convirtiendo a Markdown...");
            markdown = new MarkdownConverter(cliente.getUrlFinal()).convertir(documento);
        } catch (IOException | RuntimeException e) {
            System.err.println("Error de conversión: " + e);
            return ERROR_CONVERSION;
        }
        if (markdown.isBlank()) {
            System.out.println("Aviso: la página no contiene contenido convertible; se generará un archivo vacío.");
        }

        // 3) Escritura
        try {
            MarkdownWriter escritor = new MarkdownWriter();
            escritor.guardar(destino, markdown);
            System.out.println("Archivo generado: " + destino);
            System.out.println("Tamaño: " + String.format(Locale.US, "%,d bytes", escritor.tamano(destino)));
        } catch (IOException | RuntimeException e) {
            System.err.println("Error al escribir el archivo: " + e);
            return ERROR_ESCRITURA;
        }

        System.out.println("Conversión finalizada.");
        return EXITO;
    }

    private static void mostrarUso() {
        System.err.println("Uso: java HTML2Markdown <url> [archivo-salida.md]");
        System.err.println("  <url>             dirección http:// o https:// de la página");
        System.err.println("  archivo-salida.md ruta del Markdown (opcional; por defecto, nombre tomado de la URL)");
        System.err.println("Ejemplo: java HTML2Markdown https://ejemplo.org/articulo.html salida.md");
    }

    // Acepta solo http/https con nombre de servidor.
    static URL interpretarUrl(String texto) {
        try {
            URI uri = new URI(texto.trim());
            String esquema = uri.getScheme();
            if (esquema == null || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("solo se admiten direcciones http o https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("falta el nombre del servidor");
            }
            return uri.toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static String describir(IOException e) {
        if (e instanceof UnknownHostException) {
            return "no se pudo resolver el servidor \"" + e.getMessage() + "\"";
        }
        if (e instanceof SocketTimeoutException) {
            return "tiempo de espera agotado";
        }
        if (e instanceof ConnectException) {
            return "servidor inaccesible (" + e.getMessage() + ")";
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
