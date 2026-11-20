# HTML2Markdown


## Uso

```bash
java HTML2Markdown https://ejemplo.org/articulo.html            # genera articulo.md
java HTML2Markdown https://ejemplo.org/articulo.html salida.md  # ruta de salida opcional
```

Si la ruta de salida es un directorio existente, el archivo se crea dentro con el nombre
predeterminado. Los directorios intermedios se crean si no existen.

Códigos de salida: `0` éxito · `1` argumentos/URL · `2` red o HTTP · `3` conversión · `4` escritura.

## Compilación y ejecución

**Desde la terminal** (en la raíz del proyecto):

```bash
javac -encoding UTF-8 -d out src/main/java/*.java src/test/java/*.java
java -cp out HTML2Markdown https://ejemplo.org/articulo.html
```

**Desde IntelliJ IDEA:** abrir la carpeta como proyecto, marcar `src/main/java` como
*Sources Root* y `src/test/java` como *Test Sources Root*; crear una configuración de
ejecución para `HTML2Markdown` con los argumentos en *Program arguments*.

## Pruebas

**1. Conversión sin red** (compara cada `tests/*.html` con `tests/esperado/*.md`):

```bash
java -cp out PruebasConversion            # imprime OK / FALLO por documento
java -cp out PruebasConversion --mostrar  # muestra la salida obtenida
```

**2. De extremo a extremo con HTTP real**, usando un servidor local controlado:

```bash
cd tests && python3 -m http.server 8765        # en otra terminal
java -Dhtml2md.permitirLocal=true -cp out HTML2Markdown http://localhost:8765/documento_completo.html
```

Por seguridad, el cliente **rechaza destinos internos** (localhost, redes privadas); la
propiedad `-Dhtml2md.permitirLocal=true` lo permite solo para estas pruebas.

| Prueba | Documento / comando | Verificación |
|--------|---------------------|--------------|
| P01 | `java HTML2Markdown` | mensaje de uso |
| P02 | `java HTML2Markdown "htp:/x y"` | error controlado |
| P03 | URL inexistente (404) | error HTTP |
| P04 | `encabezados.html` | `#` a `######` |
| P05 | `formato.html` | negritas, cursivas, `<br>`, código |
| P06 | `enlaces.html` | absolutos, relativos, `../`, `//`, `#`, `mailto:` |
| P07 | `imagenes.html` | `![alt](URL)` |
| P08 | `listas.html` | ordenadas, no ordenadas, anidadas |
| P09 | `anidados.html` | jerarquía conservada |
| P10 | `script_style.html` | contenido excluido |
| P11 | `entidades.html` | acentos y entidades |
| P12 | `vacio.html` | archivo vacío + aviso |
| extra | `html5_moderno.html`, `codigo.html`, `documento_completo.html` | HTML5, bloques de código |

Evidencias en [`docs/evidencias.md`](docs/evidencias.md); ejemplo de salida en
[`output/ejemplo_documento_completo.md`](output/ejemplo_documento_completo.md).

## Arquitectura

```
HTML2Markdown ──► WebClient ──► HtmlParser ──► MarkdownConverter ──► MarkdownWriter
 (coordina)       (descarga)    (HTML → árbol)  (árbol → Markdown)    (archivo UTF-8)
```

| Clase | Responsabilidad |
|-------|-----------------|
| `HTML2Markdown` | Valida argumentos, coordina, informa y define códigos de salida |
| `WebClient` | HTTP: tiempos máximos, código y tipo de contenido, límite de 5 MB, charset, gzip, redirecciones (máx. 5) y bloqueo de destinos internos |
| `HtmlParser` | `ParserDelegator` + `ParserCallback` construyen un árbol de `Nodo` con una pila |
| `MarkdownConverter` | Recorre el árbol en modo *bloque* y modo *en línea*; escapa, resuelve URL, anida listas |
| `MarkdownWriter` | `Files`, `Path`, `StandardCharsets.UTF_8`; crea directorios; nombre predeterminado |

### Decisiones de diseño

- **Árbol en vez de regex.** El analizador emite eventos; una pila los convierte en un árbol y
  el conversor lo recorre recursivamente. Así el anidamiento se respeta de forma natural.
  Las expresiones regulares solo se usan de forma complementaria (prelimpieza de
  `<script>`/`<style>`/comentarios, colapso de espacios, escapes, detección de charset).
- **Bloques y línea.** Texto y elementos en línea consecutivos forman un párrafo; cada elemento
  de bloque genera el suyo; los bloques se separan con una línea en blanco.
- **`<br>`** se marca con un carácter temporal y se sustituye al cerrar el bloque
  (dos espacios + salto de línea), evitando espacios sobrantes.
- **Escapes.** Se escapan `\ * _ ` [ ] <` en el texto y los marcadores de bloque (`#`, `-`, `1.`, `>`)
  al inicio de línea; los delimitadores que genera el conversor nunca se escapan, porque
  el escape se aplica solo al texto original.
- **Enlaces e imágenes relativos** se resuelven con `new URL(base, ref)` contra la URL
  **final** (tras redirecciones) o contra `<base href>` si existe. `javascript:` y `data:` se omiten.
- **Seguridad:** solo http/https; redirecciones manuales validadas; destinos internos bloqueados;
  límite de tamaño y de tiempo. Nota: la comprobación de IP se hace antes de conectar, por lo
  que no cubre un ataque de *DNS rebinding*; para uso real haría falta fijar la IP resuelta.

### Alcance y limitaciones

Subconjunto soportado: `h1–h6`, `p`, `strong/b`, `em/i`, `a`, `img`, `ul/ol/li` (anidadas),
`br`, `blockquote`, `code`, `pre`, `hr`; se omiten `script`, `style` y similares. Las tablas no
se convierten a tabla Markdown (cada fila queda en una línea con `|`). No se ejecuta
JavaScript, no se descargan imágenes ni se siguen enlaces.

## Preguntas de reflexión

> Estas respuestas son un punto de partida. Revísalas, compleméntalas con lo que viste en
> clase y redáctalas con tus palabras.

**1. ¿Qué papel desempeña la aplicación dentro de una arquitectura cliente-servidor?**
Es un *cliente*: abre una conexión con un servidor web, envía una petición HTTP `GET` y
consume la respuesta. No publica contenido; lo pide, lo procesa localmente y produce un
resultado propio (el archivo `.md`).

**2. ¿Qué responsabilidades corresponden al servidor y cuáles al cliente?**
El servidor almacena o genera el documento, atiende la petición, decide el código de estado
(200, 404, 301…), el tipo de contenido y la codificación, y envía el cuerpo. El cliente
construye la petición, define sus tiempos de espera, interpreta el código de estado, sigue
(o rechaza) redirecciones, valida el tipo y el tamaño de lo recibido, y decide qué hacer con
los datos. Notablemente, el servidor no sabe nada de Markdown: la conversión es solo del cliente.

**3. ¿Qué diferencia existe entre recuperar HTML e interpretar su estructura?**
Recuperar es un problema de *comunicación*: obtener bytes y decodificarlos a texto
(HTTP, charset, límites). Interpretar es un problema de *análisis*: reconocer etiquetas,
atributos, texto y su jerarquía. Se pueden descargar los bytes perfectamente y aun así no
entender el documento; por eso son componentes (`WebClient` y `HtmlParser`) distintos.

**4. ¿Por qué no basta reemplazar etiquetas HTML con expresiones regulares?**
Porque HTML no es un lenguaje regular: admite anidamiento arbitrario, etiquetas sin cierre
(`<li>`, `<p>`), atributos con `>` dentro de comillas, mayúsculas/minúsculas, comentarios,
`<script>` con texto que parece HTML y entidades. Una regex como `<b>(.*?)</b>` falla con
`<b>a<b>b</b>c</b>` o con estructuras multilínea. Se necesita un analizador que lleve el
estado (qué etiquetas están abiertas).

**5. ¿Qué dificultades presentan los elementos anidados?**
Hay que conservar el orden y respetar los cierres: `**negrita y *cursiva* dentro**`,
un enlace con una imagen dentro (`[![alt](img)](url)`), o una lista dentro de un `<li>`.
Además las reglas dependen del contexto: la sangría de una sublista depende del ancho del
marcador del padre (`- ` = 2, `1. ` = 3), los espacios deben quedar *fuera* de los
delimitadores (`**texto** ` y no `** texto**`) y el escape no debe tocar lo que genera el conversor.
Un recorrido recursivo del árbol resuelve esto de forma natural.

**6. ¿Qué información puede perderse al convertir HTML a Markdown?**
Estilos (CSS), colores, tamaños, clases e identificadores, diseño/maquetación (columnas,
menús, posiciones), contenido generado por JavaScript, formularios y elementos interactivos,
multimedia (audio, vídeo, iframes), atributos como `title`, `width` o `target`, y
estructuras que Markdown básico no expresa (tablas complejas, celdas combinadas,
anidamiento arbitrario de bloques). También se pierden las diferencias semánticas HTML5 (`<section>`, `<nav>`…).

**7. ¿Por qué deben resolverse las URL relativas?**
Porque un enlace como `../img/foto.png` solo tiene sentido respecto a la URL de la página
original. El archivo `.md` se leerá en otro lugar (disco, GitHub); sin resolverlas, los enlaces
e imágenes quedarían rotos. Debe usarse la URL *final* tras redirecciones (y `<base href>` si existe).

**8. ¿Qué ventajas ofrece separar descarga, análisis y conversión en clases?**
Responsabilidad única, pruebas independientes (el conversor se prueba sin red, como en
`PruebasConversion`), sustituibilidad (cambiar `ParserDelegator` por otro analizador sin tocar
el conversor, o cambiar el destino de escritura), mejor depuración, legibilidad y reutilización.

**9. ¿Qué limitaciones presenta `ParserDelegator` con HTML moderno?**
Está basado en HTML 3.2: desconoce etiquetas HTML5 (`section`, `article`, `nav`, `figure`,
`main`…), las ignora (se conserva su texto pero no su semántica), puede interpretar mal
atributos modernos (`data-*`, `srcset`), no ejecuta JavaScript y a veces añade etiquetas
implícitas (`html`, `head`, `body`, `p` implícito) que hay que tratar con cuidado.
Pertenece al módulo `java.desktop`.

**10. ¿Qué modificaciones serían necesarias para convertir tablas HTML a Markdown?**
En `MarkdownConverter`: manejar `table/thead/tbody/tr/th/td` con una estructura de filas y
celdas; escapar `|` dentro de las celdas; tomar la primera fila (o `<th>`) como encabezado
y emitir la línea separadora `| --- | --- |`; convertir cada celda en línea (sin saltos de
línea; `<br>` → espacio); rellenar filas con menos columnas; decidir qué hacer con `colspan`/`rowspan`
(no tienen equivalente) y con tablas anidadas o de maquetación. Habría que agregar casos de
prueba (`tablas.html`) y su esperado.

## Historial de commits sugerido (desarrollo incremental)

1. `chore: estructura del proyecto, .gitignore y README inicial`
2. `feat: validación de argumentos y clase principal HTML2Markdown`
3. `feat: WebClient con URLConnection, tiempos máximos y verificación de código HTTP`
4. `feat: WebClient valida tipo de contenido, charset y límite de tamaño`
5. `feat: HtmlParser basado en ParserDelegator con árbol de nodos`
6. `feat: MarkdownConverter - encabezados, párrafos y formato de texto`
7. `feat: MarkdownConverter - enlaces, imágenes y resolución de URL relativas`
8. `feat: MarkdownConverter - listas ordenadas, no ordenadas y anidadas`
9. `feat: MarkdownConverter - citas, código en línea y bloques, hr`
10. `feat: MarkdownWriter en UTF-8 y nombre de salida predeterminado`
11. `feat: manejo de errores, redirecciones y bloqueo de destinos internos`
12. `test: documentos HTML de prueba, esperados y PruebasConversion`
13. `docs: evidencias, ejemplo de salida y respuestas de reflexión`

## Desafíos opcionales (no implementados)

Tablas, índice a partir de encabezados, varias URL desde CSV y reporte de elementos no
admitidos. La detección del lenguaje en bloques de código (`class="language-x"`) sí está incluida.
#
## cesar Uriel Aguiar Altamirano 
## Expediente a225204109@unison.mx
 
