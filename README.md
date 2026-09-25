# Chat cliente–servidor en Java

Chat simple con sockets, sin base de datos ni contraseñas. Funciona por niveles:

1. **Nivel 1:** un servidor y varios clientes; todos ven los mensajes de todos.
2. **Nivel 2:** varios servidores conectados entre sí; un cliente de un servidor habla con uno de otro.
3. **Nivel 3:** envío de imágenes (desde archivo), fotos tomadas con la cámara y notas de voz.
4. **Videollamada:** cámara y micrófono en vivo entre dos personas del mismo servidor.

## Estructura

```
chat-java/
├── lib/                       librerías (.jar) de la cámara
├── src/
│   ├── comun/                 Protocolo (formato de los mensajes) y Red (IPs de esta PC)
│   ├── servidor/              Servidor, ManejadorCliente, ConexionServidor
│   ├── cliente/               AppCliente, DialogoConexion, VentanaChat, PanelMensajes,
│   │                          DialogoDispositivos, DialogoFoto, VentanaLlamada, VistaVideo,
│   │                          Estilo, ClienteRed, OyenteMensajes, Camara, Audio
│   └── pruebas/ClienteConsola.java   cliente de texto para probar sin ventana
└── guia-chat-java.md          guía de trabajo y descripción del protocolo
```

## Requisitos

- **JDK 8 o superior.** Si tu `java` y tu `javac` son de versiones distintas (compruébalo con
  `java -version` y `javac -version`), compila con `--release 8` para que las clases corran en ambos.
- Para la cámara: una webcam. Sin webcam el chat funciona igual; solo no hay foto ni video
  (en la videollamada se ve tu avatar y el otro te escucha igual).
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
| `LLAMADA\|para\|accion` | `LLAMADA\|de\|accion` | (no viajan entre servidores) |
| `VIDEO\|para\|base64` | `VIDEO\|de\|base64` | |
| `VOZ\|para\|base64` | `VOZ\|de\|base64` | |
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

El botón de la cámara abre un **espejo**: te ves en vivo para acomodarte, pulsas **Tomar foto**
(o Espacio) y la foto queda congelada para revisarla. Solo se envía si pulsas **Enviar foto**;
**Repetir** vuelve a la cámara. Ahí mismo puedes cambiar de cámara y quitar el espejo
(con el espejo activo, la foto sale tal cual te viste).

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

- **Cámara:** al elegirla se ve en vivo, así sabes cuál es.
- **Micrófono:** al elegirlo, la barra verde se mueve con tu voz.
- *Predeterminado del sistema* usa el que tenga marcado Windows / Mac / Linux.

También se puede cambiar de cámara en el espejo de la foto, y de cámara o micrófono en plena
videollamada (botón **Dispositivos** de la llamada), sin cortarla. La elección dura mientras el
chat esté abierto.

## Videollamada

Elige a alguien en la lista de la derecha y pulsa **Videollamada**. A la otra persona le suena y
le aparece **Contestar** / **Rechazar**, y tú ves *Sonando…*; si nadie contesta en 40 s, la llamada
se cancela.

> **Los tres tienen que estar al día:** el servidor y los dos chats. Después de actualizar el código
> hay que **volver a compilar y cerrar y abrir de nuevo el servidor y los clientes**: un programa que
> ya estaba abierto sigue usando la versión con la que arrancó.

- Ves al otro en grande y a ti abajo a la derecha (como en un espejo; al otro le llegas sin voltear).
- **Micrófono** silencia, **Cámara** la apaga (el otro ve tu avatar y un micrófono tachado si
  estás en silencio), **Dispositivos** cambia de cámara o micrófono y **Colgar** termina.
- Al terminar queda en el chat, por ejemplo: *Videollamada con ana · 3:12*.
- Si te llaman estando en otra llamada, al otro le sale *está en otra llamada*.
- **Usa audífonos:** sin cancelación de eco, lo que sale por tus parlantes vuelve a entrar por tu micrófono.

Cómo viaja: todo pasa por el servidor, igual que el resto del chat. La cámara manda unos
12 cuadros por segundo, cada uno un JPG de 400×300 en Base64 (`VIDEO|para|…`, unos 10–20 KB).
El micrófono manda trozos de 40 ms a 8000 Hz en μ-law (`VOZ|para|…`, 25 por segundo) y los
parlantes descartan el audio que llega atrasado para que la voz no se retrase. En total son
unos 150–250 KB/s por persona: sin problema en WiFi de casa o de un celular.

Los avisos de la llamada van en `LLAMADA|para|accion`, con `accion` = `INVITAR`, `SONANDO`, `ACEPTAR`,
`RECHAZAR`, `OCUPADO`, `COLGAR`, `CAMARA_ON` / `CAMARA_OFF`, `MICROFONO_ON` / `MICROFONO_OFF`.
Si esa persona no está conectada, el servidor responde `LLAMADA|para|NO_DISPONIBLE`.

## Limitaciones conocidas

- La lista de usuarios (`USUARIOS`), los mensajes privados (`PRIV`) y las videollamadas
  (`LLAMADA`, `VIDEO`, `VOZ`) son solo del servidor local.
- La videollamada es de dos personas y no cancela el eco: con parlantes se oye repetido.
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
| Al llamar: *El servidor no tiene videollamadas* (antes: `Error del servidor: comando desconocido`) | El servidor es de una versión anterior: ciérralo, vuelve a compilar y ábrelo de nuevo. |
| Al otro no le suena (te sale *Si a … no le suena, debe abrir la versión nueva del chat*) | Su chat es de antes: que actualice el código, compile y vuelva a abrir el chat. |
| En la llamada se oye eco o un pitido | Usa audífonos (el micrófono capta lo que sale por los parlantes). |
| El video de la llamada va lento o a saltos | Red lenta: acérquense al WiFi o usen el hotspot de un celular. El audio sigue aunque el video se trabe. |
| "No se pudo abrir la cámara" en la llamada | Otro programa (o el espejo de la foto) la está usando: ciérralo o elige otra en **Dispositivos**. |
