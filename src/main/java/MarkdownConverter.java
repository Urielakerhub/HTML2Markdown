import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MarkdownConverter {


    private static final char BR = '\uE000';

    private static final Set<String> OMITIDAS = Set.of(
            "script", "style", "head", "noscript", "template", "title",
            "svg", "meta", "link", "base");

    private static final Set<String> BLOQUES = Set.of(
            "html", "body", "div", "section", "article", "main", "aside", "header",
            "footer", "nav", "address", "figure", "figcaption", "form", "fieldset",
            "center", "dl", "dt", "dd", "table", "thead", "tbody", "tfoot", "tr",
            "td", "th", "caption", "p", "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "blockquote", "pre", "hr", "menu", "dir");

    private static final Pattern IDIOMA = Pattern.compile("(?:language|lang)-([\\w+#.-]+)");

    private URL base;


    public MarkdownConverter(URL urlBase) {
        this.base = urlBase;
    }


    public String convertir(HtmlParser.Nodo raiz) {
        actualizarBaseSiExiste(raiz);
        List<String> bloques = bloquesDe(raiz);
        return bloques.isEmpty() ? "" : String.join("\n\n", bloques) + "\n";
    }



    private List<String> bloquesDe(HtmlParser.Nodo contenedor) {
        List<String> bloques = new ArrayList<>();
        StringBuilder enLinea = new StringBuilder();
        for (HtmlParser.Nodo hijo : contenedor.hijos()) {
            if (esOmitido(hijo)) continue;
            if (esBloque(hijo)) {
                cerrarParrafo(enLinea, bloques);
                bloques.addAll(convertirBloque(hijo));
            } else {
                enLinea.append(convertirEnLinea(hijo));
            }
        }
        cerrarParrafo(enLinea, bloques);
        return bloques;
    }

    private List<String> convertirBloque(HtmlParser.Nodo n) {
        String etiqueta = n.etiqueta();
        switch (etiqueta) {
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6": {
                int nivel = etiqueta.charAt(1) - '0';
                String titulo = finalizarEnLinea(hijosEnLinea(n), " ");
                return titulo.isEmpty() ? List.of() : List.of("#".repeat(nivel) + " " + titulo);
            }
            case "ul": case "ol": {
                String lista = lista(n);
                return lista.isEmpty() ? List.of() : List.of(lista);
            }
            case "blockquote":
                return cita(n);
            case "pre":
                return bloqueDeCodigo(n);
            case "hr":
                return List.of("---");
            case "tr":
                return List.of(filaDeTabla(n)).stream().filter(s -> !s.isEmpty()).toList();
            default:
                // p, div, section, body, li suelto, td... : contenedores
                return bloquesDe(n);
        }
    }

    private List<String> cita(HtmlParser.Nodo n) {
        List<String> internos = bloquesDe(n);
        if (internos.isEmpty()) return List.of();
        StringBuilder sb = new StringBuilder();
        for (String linea : String.join("\n\n", internos).split("\n", -1)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(linea.isEmpty() ? ">" : "> " + linea);
        }
        return List.of(sb.toString());
    }

    private List<String> bloqueDeCodigo(HtmlParser.Nodo pre) {
        String codigo = textoCrudo(pre).replace("\r\n", "\n").replace('\r', '\n');
        if (codigo.startsWith("\n")) codigo = codigo.substring(1);
        codigo = codigo.stripTrailing();
        if (codigo.isBlank()) return List.of();
        String valla = "`".repeat(Math.max(3, rachaMaximaDeAcentos(codigo) + 1));
        return List.of(valla + idioma(pre) + "\n" + codigo + "\n" + valla);
    }


    private String idioma(HtmlParser.Nodo n) {
        if (!n.esTexto()) {
            String clase = n.atributo("class");
            if (clase != null && ("pre".equals(n.etiqueta()) || "code".equals(n.etiqueta()))) {
                Matcher m = IDIOMA.matcher(clase);
                if (m.find()) return m.group(1);
            }
            for (HtmlParser.Nodo h : n.hijos()) {
                String r = idioma(h);
                if (!r.isEmpty()) return r;
            }
        }
        return "";
    }


    private String filaDeTabla(HtmlParser.Nodo tr) {
        List<String> celdas = new ArrayList<>();
        for (HtmlParser.Nodo celda : tr.hijos()) {
            if (celda.esTexto() || esOmitido(celda)) continue;
            String t = finalizarEnLinea(hijosEnLinea(celda), " ");
            if (!t.isEmpty()) celdas.add(t);
        }
        return String.join(" | ", celdas);
    }



    private String lista(HtmlParser.Nodo lista) {
        boolean ordenada = "ol".equals(lista.etiqueta());
        int numero = 1;
        if (ordenada && lista.atributo("start") != null) {
            try {
                numero = Integer.parseInt(lista.atributo("start").trim());
            } catch (NumberFormatException ignorada) {

            }
        }
        List<String> items = new ArrayList<>();
        for (HtmlParser.Nodo hijo : lista.hijos()) {
            if (hijo.esTexto() || !"li".equals(hijo.etiqueta())) continue;
            String marcador = ordenada ? (numero++) + ". " : "- ";
            items.add(item(hijo, marcador));
        }
        return String.join("\n", items);
    }


    private String item(HtmlParser.Nodo li, String marcador) {
        String sangria = " ".repeat(marcador.length());
        StringBuilder texto = new StringBuilder();
        List<String> sublistas = new ArrayList<>();
        for (HtmlParser.Nodo h : li.hijos()) {
            if (esOmitido(h)) continue;
            if (!h.esTexto() && ("ul".equals(h.etiqueta()) || "ol".equals(h.etiqueta()))) {
                String sub = lista(h);
                if (!sub.isEmpty()) sublistas.add(sub);
            } else {
                texto.append(convertirEnLinea(h));
            }
        }
        String[] lineas = escaparInicios(finalizarEnLinea(texto.toString(), "  \n")).split("\n", -1);
        StringBuilder sb = new StringBuilder(marcador).append(lineas[0]);
        for (int i = 1; i < lineas.length; i++) {
            sb.append('\n').append(sangria).append(lineas[i]);
        }
        for (String sub : sublistas) {
            for (String linea : sub.split("\n", -1)) {
                sb.append('\n').append(sangria).append(linea);
            }
        }
        return lineas.length == 1 && lineas[0].isEmpty() && sublistas.isEmpty()
                ? marcador.stripTrailing() : sb.toString();
    }



    private String hijosEnLinea(HtmlParser.Nodo n) {
        StringBuilder sb = new StringBuilder();
        for (HtmlParser.Nodo h : n.hijos()) {
            if (!esOmitido(h)) sb.append(convertirEnLinea(h));
        }
        return sb.toString();
    }

    private String convertirEnLinea(HtmlParser.Nodo n) {
        if (n.esTexto()) {
            return escapar(colapsarEspacios(n.texto()));
        }
        if (esOmitido(n)) return "";
        switch (n.etiqueta()) {
            case "strong": case "b":
                return envolver(hijosEnLinea(n), "**");
            case "em": case "i":
                return envolver(hijosEnLinea(n), "*");
            case "code":
                return codigoEnLinea(n);
            case "a":
                return enlace(n);
            case "img":
                return imagen(n);
            case "br":
                return String.valueOf(BR);
            default:

                return esBloque(n) ? " " + hijosEnLinea(n) + " " : hijosEnLinea(n);
        }
    }

    private String enlace(HtmlParser.Nodo a) {
        String texto = hijosEnLinea(a);
        String href = a.atributo("href");
        if (href == null || href.isBlank() || href.trim().regionMatches(true, 0, "javascript:", 0, 11)) {
            return texto;
        }
        String nucleo = texto.strip();
        if (nucleo.isEmpty()) return texto;
        int ini = texto.indexOf(nucleo.charAt(0));
        String antes = texto.substring(0, ini);
        String despues = texto.substring(ini + nucleo.length());
        return antes + "[" + nucleo + "](" + resolver(href.trim()) + ")" + despues;
    }

    private String imagen(HtmlParser.Nodo img) {
        String src = img.atributo("src");
        if (src == null || src.isBlank() || src.trim().regionMatches(true, 0, "data:", 0, 5)) {
            return "";
        }
        String alt = img.atributo("alt");
        alt = alt == null ? "" : escapar(colapsarEspacios(alt)).strip();
        return "![" + alt + "](" + resolver(src.trim()) + ")";
    }

    private String codigoEnLinea(HtmlParser.Nodo code) {
        String t = colapsarEspacios(textoCrudo(code)).strip();
        if (t.isEmpty()) return "";
        String valla = "`".repeat(rachaMaximaDeAcentos(t) == 0 ? 1 : rachaMaximaDeAcentos(t) + 1);
        boolean relleno = t.startsWith("`") || t.endsWith("`");
        return valla + (relleno ? " " : "") + t + (relleno ? " " : "") + valla;
    }


    private static String envolver(String s, String delimitador) {
        String nucleo = s.strip();
        if (nucleo.isEmpty()) return s;
        int ini = s.indexOf(nucleo.charAt(0));
        return s.substring(0, ini) + delimitador + nucleo + delimitador
                + s.substring(ini + nucleo.length());
    }



    @SuppressWarnings("deprecation")
    private void actualizarBaseSiExiste(HtmlParser.Nodo n) {
        if (!n.esTexto()) {
            if ("base".equals(n.etiqueta()) && n.atributo("href") != null) {
                try {
                    base = new URL(base, n.atributo("href").trim());
                } catch (MalformedURLException ignorada) {

                }
                return;
            }
            for (HtmlParser.Nodo h : n.hijos()) actualizarBaseSiExiste(h);
        }
    }

    /** Convierte una referencia relativa en absoluta respecto a la URL base. */
    @SuppressWarnings("deprecation")
    private String resolver(String referencia) {
        if (referencia.startsWith("#")) {
            return codificarDestino(referencia);
        }
        try {
            return codificarDestino(new URL(base, referencia).toString());
        } catch (MalformedURLException e) {

            return codificarDestino(referencia);
        }
    }

    private static String codificarDestino(String url) {
        return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }



    private static boolean esOmitido(HtmlParser.Nodo n) {
        return !n.esTexto() && OMITIDAS.contains(n.etiqueta());
    }

    private static boolean esBloque(HtmlParser.Nodo n) {

        boolean parrafoImplicito = n.esImplicito() && "p".equals(n.etiqueta());
        return !n.esTexto() && !parrafoImplicito && BLOQUES.contains(n.etiqueta());
    }

    private static String colapsarEspacios(String s) {
        return s.replace('\u00a0', ' ').replaceAll("[ \\t\\r\\n\\f]+", " ");
    }


    private static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '*' || c == '_' || c == '`' || c == '[' || c == ']' || c == '<') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }


    private static String escaparInicios(String s) {
        s = s.replaceAll("(?m)^([ ]{0,3})(#{1,6}(?:\\s|$))", "$1\\\\$2");
        s = s.replaceAll("(?m)^([ ]{0,3})([-+*]\\s)", "$1\\\\$2");
        s = s.replaceAll("(?m)^([ ]{0,3})(\\d+)([.)]\\s)", "$1$2\\\\$3");
        s = s.replaceAll("(?m)^([ ]{0,3})>", "$1\\\\>");
        return s;
    }


    private static String finalizarEnLinea(String s, String salto) {
        String br = String.valueOf(BR);
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll(" ?" + br + " ?", br);
        s = s.replaceAll("^[ " + br + "]+|[ " + br + "]+$", "");
        return s.replace(br, salto);
    }

    private void cerrarParrafo(StringBuilder enLinea, List<String> bloques) {
        String p = escaparInicios(finalizarEnLinea(enLinea.toString(), "  \n"));
        if (!p.isEmpty()) bloques.add(p);
        enLinea.setLength(0);
    }


    private String textoCrudo(HtmlParser.Nodo n) {
        if (n.esTexto()) return n.texto();
        if (esOmitido(n)) return "";
        if ("br".equals(n.etiqueta())) return "\n";
        StringBuilder sb = new StringBuilder();
        for (HtmlParser.Nodo h : n.hijos()) sb.append(textoCrudo(h));
        return sb.toString();
    }

    private static int rachaMaximaDeAcentos(String s) {
        int max = 0, actual = 0;
        for (int i = 0; i < s.length(); i++) {
            actual = s.charAt(i) == '`' ? actual + 1 : 0;
            max = Math.max(max, actual);
        }
        return max;
    }
}
