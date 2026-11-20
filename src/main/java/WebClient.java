import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;


public class WebClient {

    public static final int TIEMPO_CONEXION_MS = 10_000;
    public static final int TIEMPO_LECTURA_MS = 15_000;
    public static final int TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024;
    public static final int MAX_REDIRECCIONES = 5;

    private static final Pattern CHARSET_CABECERA =
            Pattern.compile("charset\\s*=\\s*[\"']?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_META =
            Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    private final boolean permitirDestinosInternos;
    private int codigoHttp;
    private URL urlFinal;

    public WebClient() {
        this(false);
    }


    public WebClient(boolean permitirDestinosInternos) {
        this.permitirDestinosInternos = permitirDestinosInternos;
    }

    public int getCodigoHttp() {
        return codigoHttp;
    }


    public URL getUrlFinal() {
        return urlFinal;
    }


    public String descargarHTML(URL url) throws IOException {
        codigoHttp = 0;
        urlFinal = url;
        URL actual = url;

        for (int salto = 0; salto <= MAX_REDIRECCIONES; salto++) {
            validarDestino(actual);

            URLConnection conexion = actual.openConnection();
            if (!(conexion instanceof HttpURLConnection http)) {
                throw new IOException("La URL no corresponde a una conexión HTTP");
            }
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(TIEMPO_CONEXION_MS);
            http.setReadTimeout(TIEMPO_LECTURA_MS);
            http.setRequestMethod("GET");
            http.setRequestProperty("User-Agent", "HTML2Markdown/1.0 (tarea academica; Java)");
            http.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1");
            http.setRequestProperty("Accept-Language", "es,en;q=0.8");

            try {
                codigoHttp = http.getResponseCode();

                if (esRedireccion(codigoHttp)) {
                    String destino = http.getHeaderField("Location");
                    if (destino == null || destino.isBlank()) {
                        throw new IOException("Redirección HTTP " + codigoHttp + " sin cabecera Location");
                    }
                    actual = resolverRedireccion(actual, destino);
                    urlFinal = actual;
                    continue;
                }
                if (codigoHttp < 200 || codigoHttp >= 300) {
                    throw new IOException("Respuesta HTTP no exitosa: " + codigoHttp
                            + (http.getResponseMessage() != null ? " " + http.getResponseMessage() : ""));
                }

                String tipo = http.getContentType();
                if (!esHtml(tipo)) {
                    throw new IOException("El contenido no es HTML (Content-Type: "
                            + (tipo == null ? "no indicado" : tipo) + ")");
                }
                return leerCuerpo(http, tipo);
            } finally {
                http.disconnect();
            }
        }
        throw new IOException("Demasiadas redirecciones (máximo " + MAX_REDIRECCIONES + ")");
    }



    private String leerCuerpo(HttpURLConnection http, String tipo) throws IOException {
        long declarado = http.getContentLengthLong();
        if (declarado > TAMANO_MAXIMO_BYTES) {
            throw new IOException("El documento (" + declarado + " bytes) excede el límite de "
                    + TAMANO_MAXIMO_BYTES + " bytes");
        }

        InputStream flujo = http.getInputStream();
        String codificacion = http.getContentEncoding();
        if (codificacion != null && codificacion.equalsIgnoreCase("gzip")) {
            flujo = new GZIPInputStream(flujo);
        }


        try (InputStream in = flujo) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int leidos;
            while ((leidos = in.read(buffer)) != -1) {
                total += leidos;
                if (total > TAMANO_MAXIMO_BYTES) {
                    throw new IOException("El documento excede el límite de " + TAMANO_MAXIMO_BYTES + " bytes");
                }
                salida.write(buffer, 0, leidos);
            }
            byte[] datos = salida.toByteArray();
            return new String(datos, detectarCodificacion(tipo, datos));
        }
    }


    static Charset detectarCodificacion(String contentType, byte[] datos) {
        if (contentType != null) {
            Matcher m = CHARSET_CABECERA.matcher(contentType);
            if (m.find()) {
                Charset cs = charsetSeguro(m.group(1));
                if (cs != null) return cs;
            }
        }

        String inicio = new String(datos, 0, Math.min(datos.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = CHARSET_META.matcher(inicio);
        if (m.find()) {
            Charset cs = charsetSeguro(m.group(1));
            if (cs != null) return cs;
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset charsetSeguro(String nombre) {
        try {
            return Charset.forName(nombre);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }

    private static boolean esHtml(String contentType) {
        if (contentType == null) return false;
        String t = contentType.toLowerCase(Locale.ROOT);
        return t.contains("text/html") || t.contains("application/xhtml+xml");
    }

    private static boolean esRedireccion(int codigo) {
        return codigo == 301 || codigo == 302 || codigo == 303 || codigo == 307 || codigo == 308;
    }

    private static URL resolverRedireccion(URL actual, String location) throws IOException {
        try {
            return actual.toURI().resolve(location.trim()).toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Redirección a una URL inválida: " + location);
        }
    }


    private void validarDestino(URL url) throws IOException {
        String esquema = url.getProtocol();
        if (!"http".equalsIgnoreCase(esquema) && !"https".equalsIgnoreCase(esquema)) {
            throw new IOException("Protocolo no permitido: " + esquema);
        }
        if (permitirDestinosInternos) return;

        for (InetAddress a : InetAddress.getAllByName(url.getHost())) {
            byte[] b = a.getAddress();
            boolean ipv6ULA = b.length == 16 && (b[0] & 0xFE) == 0xFC;
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress() || ipv6ULA) {
                throw new IOException("Destino interno no permitido: " + url.getHost()
                        + " (" + a.getHostAddress() + ")");
            }
        }
    }
}
