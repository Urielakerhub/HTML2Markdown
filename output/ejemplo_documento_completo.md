# Guía de *HTML2Markdown*

Este documento resume las **reglas de conversión** y enlaza a la [documentación](https://dev.java/) oficial.

## Elementos admitidos

- Encabezados, párrafos y **formato**
- Enlaces e imágenes
  - Absolutos como [aprender Java](https://dev.java/learn/)
  - Relativos como [la guía](http://localhost:8765/../recursos/guia.html)
- Listas ordenadas y no ordenadas

## Pasos

1. Compilar el proyecto
2. Ejecutar con una `URL`
3. Revisar el archivo *.md*

![Esquema cliente-servidor](http://localhost:8765/img/esquema.png)

> La salida conserva la estructura semántica.

```bash
java HTML2Markdown https://ejemplo.org/articulo.html
```

---

Última línea  
con salto — y acentos: canción, año.
