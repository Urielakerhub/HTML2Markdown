# Evidencias de funcionamiento

Salidas reales de la aplicación (servidor local de prueba con `python3 -m http.server`
y la opción `-Dhtml2md.permitirLocal=true`). Sustituye o complementa estas evidencias con
capturas de **tu** equipo (visor Markdown de IntelliJ o GitHub) antes de entregar.

## Prueba de conversión sin red (13/13)

```
OK       anidados
OK       codigo
OK       documento_completo
OK       ejemplo_enunciado
OK       encabezados
OK       enlaces
OK       entidades
OK       formato
OK       html5_moderno
OK       imagenes
OK       listas
OK       script_style
OK       vacio

Resultado: 13 correctas, 0 con fallo
```

## Ejecución exitosa (nombre de salida predeterminado)

```
$ java HTML2Markdown http://localhost:8765/documento_completo.html
HTML2Markdown
--------------------------------
URL: http://localhost:8765/documento_completo.html
Conectando con el servidor...
Respuesta HTTP: 200
Analizando HTML...
Convirtiendo a Markdown...
Archivo generado: documento_completo.md
Tamaño: 729 bytes
Conversión finalizada.
```

## Casos de error (P01, P02, P03, P12 y otros)

| Caso | Resultado observado | Código de salida |
|------|--------------------|:---:|
| P01 sin argumentos | Mensaje `Uso: java HTML2Markdown <url> [archivo-salida.md]` | 1 |
| P02 URL inválida (`htp:/esto no es url`) | `Error: URL inválida: Illegal character in path ...` | 1 |
| Esquema no permitido (`ftp://...`) | `Error: URL inválida: solo se admiten direcciones http o https` | 1 |
| Destino interno (`localhost`) sin permiso | `Error de conexión: Destino interno no permitido: localhost (127.0.0.1)` | 2 |
| P03 página inexistente | `Respuesta HTTP: 404` + `Respuesta HTTP no exitosa: 404 File not found` | 2 |
| Contenido no HTML (`.md`) | `El contenido no es HTML (Content-Type: text/markdown)` | 2 |
| Servidor inaccesible (puerto cerrado) | `servidor inaccesible (Connection refused ...)` | 2 |
| P12 página vacía | Aviso informativo y archivo de `0 bytes` | 0 |
| Error de escritura (ruta bajo un archivo) | `Error al escribir el archivo: FileAlreadyExistsException ...` | 4 |
| Documento de 6 MB | `El documento excede el límite de 5242880 bytes` | 2 |
| Bucle de redirecciones | `Demasiadas redirecciones (máximo 5)` | 2 |

## Otros comportamientos verificados

- Redirección 302 seguida; los enlaces relativos se resuelven contra la **URL final**.
- Página en `ISO-8859-1` (declarada con `<meta>`) decodificada bien y guardada en **UTF-8**
  (`ó` = bytes `c3 b3`).
- Respuesta con `Content-Encoding: gzip` descomprimida.
- Creación automática de directorios de destino (`/tmp/nueva/sub/salida.md`).
