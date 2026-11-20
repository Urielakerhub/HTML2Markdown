import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Analiza HTML con la clase estándar {@link ParserDelegator} y construye un
 * árbol de {@link Nodo}. El árbol es lo que luego recorre {@link MarkdownConverter}.
 *
 * <p>Se usa un árbol (y no reemplazos con expresiones regulares) porque el HTML
 * es una estructura anidada: para saber dónde termina un enlace o una lista
 * hay que respetar la jerarquía de etiquetas.</p>
 *
 * <p>Limitación: ParserDelegator es un analizador heredado (HTML 3.2). Las
 * etiquetas que no conoce (por ejemplo {@code <section>}) se ignoran, pero su
 * texto y el de sus hijos se conserva.</p>
 */
public class HtmlParser {

    /** Nodo del árbol: o es un elemento (etiqueta + atributos + hijos) o es texto. */
    public static final class Nodo {
        private final String etiqueta;
        private final String texto;
        private final Map<String, String> atributos;
        private final boolean implicito;
        private final List<Nodo> hijos = new ArrayList<>();

        private Nodo(String etiqueta, Map<String, String> atributos, boolean implicito) {
            this.etiqueta = etiqueta;
            this.texto = null;
            this.atributos = atributos;
            this.implicito = implicito;
        }

        private Nodo(String texto) {
            this.etiqueta = null;
            this.texto = texto;
            this.atributos = Collections.emptyMap();
            this.implicito = false;
        }

        public boolean esTexto() { return etiqueta == null; }
        public String etiqueta() { return etiqueta; }
        public String texto() { return texto; }
        public boolean esImplicito() { return implicito; }
        public List<Nodo> hijos() { return hijos; }


        public String atributo(String nombre) { return atributos.get(nombre); }


        public String textoPlano() {
            if (esTexto()) return texto;
            StringBuilder sb = new StringBuilder();
            for (Nodo h : hijos) sb.append(h.textoPlano());
            return sb.toString();
        }
    }

    private static final Pattern BLOQUES_IGNORADOS =
            Pattern.compile("<(script|style|svg|template)\\b[^>]*>.*?</\\1\\s*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMENTARIOS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /**
     @param html documento HTML completo
     * @return nodo raíz ("#documento") con todo el árbol
     */
    public Nodo analizar(String html) throws IOException {
        Nodo raiz = new Nodo("#documento", Collections.emptyMap(), false);
        if (html == null || html.isBlank()) {
            return raiz;
        }
        new ParserDelegator().parse(new StringReader(limpiar(html)), new Constructor(raiz), true);
        return raiz;
    }

    /**
     * Prelimpieza con expresiones regulares elimina comentarios y bloques de script/style, ya que el contenido podría confundir al analizador heredado.
     */
    private static String limpiar(String html) {
        String s = html.startsWith("\uFEFF") ? html.substring(1) : html;
        s = COMENTARIOS.matcher(s).replaceAll("");
        return BLOQUES_IGNORADOS.matcher(s).replaceAll("");
    }


    private static final class Constructor extends HTMLEditorKit.ParserCallback {
        private final Deque<Nodo> pila = new ArrayDeque<>();

        Constructor(Nodo raiz) {
            pila.push(raiz);
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            Nodo nodo = new Nodo(t.toString(), copiar(a), a.isDefined(HTMLEditorKit.ParserCallback.IMPLIED));
            pila.peek().hijos.add(nodo);
            pila.push(nodo);
        }

        @Override
        public void handleEndTag(HTML.Tag t, int pos) {
            String nombre = t.toString();

            Iterator<Nodo> it = pila.iterator();
            boolean encontrada = false;
            while (it.hasNext()) {
                Nodo n = it.next();
                if (nombre.equals(n.etiqueta)) {
                    encontrada = true;
                    break;
                }
            }
            if (!encontrada) return;
            while (pila.size() > 1) {
                Nodo sacado = pila.pop();
                if (nombre.equals(sacado.etiqueta)) break;
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {

            pila.peek().hijos.add(new Nodo(t.toString(), copiar(a), false));
        }

        @Override
        public void handleText(char[] data, int pos) {
            pila.peek().hijos.add(new Nodo(new String(data)));
        }

        private static Map<String, String> copiar(MutableAttributeSet a) {
            Map<String, String> mapa = new LinkedHashMap<>();
            Enumeration<?> nombres = a.getAttributeNames();
            while (nombres.hasMoreElements()) {
                Object nombre = nombres.nextElement();
                Object valor = a.getAttribute(nombre);
                mapa.put(nombre.toString().toLowerCase(), String.valueOf(valor));
            }
            return mapa;
        }
    }
}
