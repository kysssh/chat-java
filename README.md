# Chat cliente–servidor en Java

Chat simple con sockets, sin base de datos ni contraseñas. Funciona por niveles:

1. **Nivel 1:** un servidor y varios clientes; todos ven los mensajes de todos.
2. **Nivel 2:** varios servidores conectados entre sí; un cliente de un servidor habla con uno de otro.
3. **Nivel 3:** envío de imágenes (desde archivo), fotos tomadas con la cámara y notas de voz.

## Estructura

```
chat-java/
├── lib/                       librerías (.jar) de la cámara
├── src/
│   ├── comun/                 Protocolo (formato de los mensajes) y Red (IPs de esta PC)
│   ├── servidor/              Servidor, ManejadorCliente, ConexionServidor
│   ├── cliente/               AppCliente, DialogoConexion, VentanaChat, PanelMensajes,
│   │                          DialogoDispositivos, Estilo, ClienteRed, OyenteMensajes,
│   │                          Camara, Audio
│   └── pruebas/ClienteConsola.java   cliente de texto para probar sin ventana
└── guia-chat-java.md          guía de trabajo y descripción del protocolo
```

## Requisitos

- **JDK 8 o superior.** Si tu `java` y tu `javac` son de versiones distintas (compruébalo con
  `java -version` y `javac -version`), compila con `--release 8` para que las clases corran en ambos.
- Para la cámara: una webcam. Sin webcam el chat funciona igual y solo falla el botón de foto.
- Para las notas de voz: un micrófono y parlantes o audífonos. No hace falta ninguna librería
  (se usa `javax.sound`, que viene con Java).

## Compilar

Desde la carpeta `chat-java`:

```bash
javac -encoding UTF-8 -cp "lib/*" -d out src/comun/*.java src/servidor/*.java src/pruebas/*.java src/cliente/*.java
```

La carpeta `out/` no se sube al repositorio.

## Ejecutar

En **Windows** el separador del classpath es `;`; en **Linux / Mac** es `:`.
Los ejemplos usan Windows; en Linux / Mac cambia `"out;lib/*"` por `"out:lib/*"`.

```bash
# Servidor (puerto por defecto 5000)
java -cp "out;lib/*" servidor.Servidor 5000

# Segundo servidor (5001) conectado al primero
java -cp "out;lib/*" servidor.Servidor 5001 localhost 5000

# Cliente con ventana (pantalla de conexión: nombre, host y puerto)
java -cp "out;lib/*" cliente.AppCliente

# Cliente de texto: host puerto nombre
java -cp "out;lib/*" pruebas.ClienteConsola localhost 5000 juan
```

Argumentos del servidor: `puertoPropio [hostVecino puertoVecino]`.

Al arrancar, el servidor muestra las IPs de la PC a las que se pueden conectar los demás:

```
Servidor escuchando en el puerto 5000
Los clientes pueden conectarse a:
   192.168.1.5 : 5000   (Wi-Fi)
   localhost : 5000   (desde esta misma PC)
```

- Cada servidor nuevo se conecta a **uno solo** que ya exista. No conectes servidores en círculo
  (1→2→3→1) o los mensajes darán vueltas y saldrán repetidos.
- Si el vecino indicado no responde, el servidor avisa y se cierra.

### Comandos de `ClienteConsola`

| Escribes | Se envía |
|---|---|
| `hola a todos` | `MSG\|hola a todos` |
| `/p ana hola` | `PRIV\|ana\|hola` |
| `/salir` | `SALIR` |

### Desde un IDE

Abre la carpeta, marca `src` como carpeta de código, agrega los `.jar` de `lib/` a las librerías
del proyecto y ejecuta los `main`. En IntelliJ, para abrir varios clientes a la vez:
*Run → Edit Configurations → Modify options → Allow multiple instances*.

## Probar en dos computadoras

1. Las dos PCs en la **misma red WiFi**.
2. En la PC del servidor, mira su IP: la escribe el servidor al arrancar, y también sale en la
   pantalla de conexión y arriba a la derecha de la ventana del chat (**Tu IP**; clic para copiarla).
3. En el cliente, usa esa IP como host.
4. Si no conecta, permite Java en el **firewall** de la PC del servidor.
5. Si aún no conecta (pasa en WiFi de universidad), usa el **hotspot de un celular**.

## Protocolo (resumen)

Cada línea es un mensaje; las partes se separan con `|`. Todo en UTF-8.

| Cliente → servidor | Servidor → cliente | Entre servidores |
|---|---|---|
| `NOMBRE\|nombre` | `MSG\|de\|texto` | `SERVIDOR\|puerto` (primera línea) |
| `MSG\|texto` | `PRIV\|de\|texto` | luego se pasan `MSG`, `INFO`, `IMG` y `AUDIO` tal cual |
| `PRIV\|para\|texto` | `IMG\|de\|base64` | |
| `IMG\|base64` | `AUDIO\|de\|base64` | |
| `AUDIO\|base64` | `INFO\|texto` | |
| `SALIR` | `USUARIOS\|a,b,c` | |
| | `ERROR\|texto` | |

Detalles completos en la sección 3 de [guia-chat-java.md](guia-chat-java.md).

## La ventana del chat

- **Pantalla de conexión:** nombre, servidor y puerto en una sola ventana; los errores salen ahí mismo.
- **Mensajes en burbujas:** los tuyos a la derecha (azul), los de los demás a la izquierda con su avatar
  y hora. Los mensajes seguidos de una misma persona se agrupan.
- **Privados:** elige a alguien en la lista de la derecha y pulsa **Privado** (Enter envía a todos,
  Esc quita la selección). Se ven en color naranja, también los que tú envías.
- **Imágenes:** se ven dentro del chat; clic para abrirlas en grande.
- **Estado:** arriba se ve si estás *En línea*, *Conectando…* o *Desconectado*. Con la ventana en
  segundo plano, el título cuenta los mensajes sin leer: `(3) Chat — …`.
- Todo se dibuja con Swing (`cliente.Estilo`), sin librerías extra. Los emojis se ven en un solo color.

## Imágenes y cámara

`cliente.Camara` reduce la imagen a un máximo de 320×240, la pasa a JPG y la envía como texto Base64
en una sola línea (`IMG|...`), así que pesa unos 15–30 KB. La foto usa la librería
[webcam-capture](https://github.com/sarxos/webcam-capture) (jars en `lib/`). Tomar la foto puede
tardar unos segundos: hazlo fuera del hilo de la ventana. En Mac hay que dar permiso de cámara a la
terminal o al IDE la primera vez.

## Notas de voz

Botón del micrófono en la barra de abajo: un clic empieza a grabar (se ve **Grabando 0:07** y el
volumen), otro clic o **Enviar** la manda, **Esc** la cancela. Máximo 30 segundos.

`cliente.Audio` graba con el micrófono elegido, lo pasa a 8000 Hz mono en μ-law (calidad de llamada
telefónica, 8 KB por segundo) y lo envía como WAV en Base64 en una línea (`AUDIO|...`). Una nota de
30 s son unos 320 KB. Al recibirla se ve una burbuja con ▶, la barra de avance y la duración.

## Dispositivos (cámara y micrófono)

Botón **Dispositivos** arriba a la derecha. Lista las cámaras (librería webcam-capture) y los
micrófonos (`javax.sound`) conectados:

- **Cámara:** elígela y pulsa **Probar** para ver una foto de muestra.
- **Micrófono:** al elegirlo, la barra verde se mueve con tu voz.
- *Predeterminado del sistema* usa el que tenga marcado Windows / Mac / Linux.

La elección dura mientras el chat esté abierto.

## Limitaciones conocidas

- La lista de usuarios (`USUARIOS`) y los mensajes privados (`PRIV`) son solo del servidor local.
- Un nombre repetido solo se rechaza dentro del mismo servidor, no entre servidores distintos.
- Si un servidor se cae, los mensajes no cruzan hacia los servidores que dependían de él, y los demás
  no reciben el aviso de salida de sus clientes.

## Si algo sale mal

| Problema | Qué hacer |
|---|---|
| `Connection refused` | El servidor no está prendido, el puerto está mal o el firewall lo bloquea. |
| `Address already in use` | Ya hay un servidor en ese puerto: ciérralo o usa otro. |
| Tildes raras (`Ã±`) | Falta `-encoding UTF-8` al compilar, o algo no usa `Protocolo.lector/escritor`. |
| Mensajes repetidos entre servidores | Hay servidores conectados en círculo. |
| `UnsupportedClassVersionError` | Compilaste con un `javac` más nuevo que tu `java`: usa `--release 8`. |
| La foto no funciona | Se puede seguir enviando imagen desde archivo. Prueba otra cámara en **Dispositivos** (cierra otros programas que la usen). |
| La barra del micrófono no se mueve | Elige otro en **Dispositivos**. En Windows: Configuración › Privacidad › Micrófono → permitir a las apps de escritorio. |
| La nota de voz no suena | Revisa que haya parlantes o audífonos conectados y el volumen de la PC. |
