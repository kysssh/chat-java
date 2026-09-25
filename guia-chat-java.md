# 💬 Chat Cliente–Servidor en Java — Guía de trabajo (2 personas)

> **Meta:** terminar HOY, de inicio a fin.
> **Regla principal:** cada archivo tiene **un solo dueño**. Nadie edita el archivo del otro.
> Así los dos avanzan en paralelo sin chocar, y se juntan solo en los **puntos de control**.

| Rol | Persona | Se encarga de |
|---|---|---|
| **A — Backend** | ______________ | El **servidor** (recibe a los clientes y reparte los mensajes) y la conexión entre **varios servidores** |
| **B — Frontend** | ______________ | El **cliente**: la ventana del chat y la conexión del cliente al servidor |

---

## 1. ¿Qué vamos a construir?

Un chat **simple**. Nada de base de datos, ni login con contraseña, ni historial. Solo sockets y mensajes.

Lo hacemos por niveles. **Cada nivel funciona por sí solo**: si se acaba el tiempo, se entrega el último nivel que funcione.

| Nivel | Qué hace | ¿Obligatorio? |
|---|---|---|
| **Nivel 1** | 1 servidor y varios clientes. Todos ven los mensajes de todos. | ✅ Sí |
| **Nivel 2** | Varios servidores conectados entre sí. Un cliente en el servidor 1 habla con uno en el servidor 2. | ✅ Sí |
| **Nivel 3** | Enviar una imagen (desde archivo) y una foto tomada con la cámara. | ⭐ Si da el tiempo |

### Nivel 1 — un servidor, varios clientes

```
   [Cliente Juan]      [Cliente Ana]      [Cliente Pedro]
          \                 |                  /
           \                |                 /
            +-------- [ SERVIDOR :5000 ] ----+
```

Juan escribe "hola" → el servidor lo recibe → el servidor lo reenvía a **todos** (incluido Juan).

### Nivel 2 — varios servidores, varios clientes

```
 [Juan] [Ana]                [Pedro]                [Lucía]
    \   /                       |                      |
 [SERVIDOR 1 :5000] <------> [SERVIDOR 2 :5001]    [SERVIDOR 3 :5002]
         ^                                             |
         +---------------------------------------------+
```

Cada servidor nuevo se conecta a **uno** que ya exista. Cuando un servidor recibe un mensaje, se lo pasa a sus clientes **y** a los otros servidores (menos al que se lo mandó). Así el mensaje llega a todos sin dar vueltas infinitas.

---

## 2. Estructura del proyecto y dueño de cada archivo

```
chat-java/
├── lib/                          ← (Nivel 3) librería de la cámara (.jar)
├── src/
│   ├── comun/
│   │   └── Protocolo.java        ← LOS DOS (se escribe juntos al inicio, luego se congela)
│   ├── servidor/
│   │   ├── Servidor.java         ← A
│   │   ├── ManejadorCliente.java ← A
│   │   └── ConexionServidor.java ← A (Nivel 2)
│   ├── pruebas/
│   │   └── ClienteConsola.java   ← A (cliente de texto para probar sin ventana)
│   └── cliente/
│       ├── AppCliente.java       ← B
│       ├── VentanaChat.java      ← B
│       ├── ClienteRed.java       ← B
│       ├── OyenteMensajes.java   ← B
│       └── Camara.java           ← A (Nivel 3)
├── .gitignore
└── README.md                     ← A (al final)
```

### 🚦 Reglas de oro para no chocar

1. **Solo editas tus archivos.** Si necesitas un cambio en un archivo del otro, se lo pides por WhatsApp/Discord.
2. **`Protocolo.java` está congelado** después de la Fase 0. Solo se cambia si **los dos** están de acuerdo.
3. Antes de subir cambios: `git pull` → compilar → `git push`. Como no tocan los mismos archivos, no habrá conflictos.
4. Commits con tu letra adelante: `[A] servidor básico`, `[B] ventana del chat`.
5. **No subir** la carpeta `out/` ni archivos `.class` (van en `.gitignore`).

---

## 3. El contrato: el Protocolo (lo más importante)

El servidor y el cliente **se hablan con líneas de texto**. Cada línea es un mensaje. Las partes se separan con `|`.

Si los dos respetan este formato, el código de A y el de B van a encajar aunque se escriban por separado.

### Del cliente al servidor

```
MENSAJE              EJEMPLO                  PARTES   SIGNIFICADO
NOMBRE|nombre        NOMBRE|juan              2        Primera línea al conectarse. "Me llamo juan".
MSG|texto            MSG|hola a todos         2        Mensaje para todos.
PRIV|para|texto      PRIV|ana|hola ana        3        Mensaje privado (solo mismo servidor).
IMG|base64           IMG|/9j/4AAQSk...        2        Imagen convertida a texto (Nivel 3).
AUDIO|base64         AUDIO|UklGRi...          2        Nota de voz: WAV convertido a texto (Nivel 3).
LLAMADA|para|accion  LLAMADA|ana|INVITAR      3        Videollamada: invitar, aceptar, colgar… (ver abajo).
VIDEO|para|base64    VIDEO|ana|/9j/4AAQ...    3        Un cuadro de la cámara en vivo (JPG 400x300).
VOZ|para|base64      VOZ|ana|/f7+/v...        3        40 ms de micrófono en vivo (μ-law, 8000 Hz).
SALIR                SALIR                    1        Me desconecto.
```

### Del servidor al cliente

```
MENSAJE              EJEMPLO                  PARTES   SIGNIFICADO
MSG|de|texto         MSG|juan|hola a todos    3        Alguien escribió a todos.
PRIV|de|texto        PRIV|juan|hola ana       3        Te escribieron en privado.
IMG|de|base64        IMG|juan|/9j/4AAQSk...   3        Alguien mandó una imagen.
AUDIO|de|base64      AUDIO|juan|UklGRi...     3        Alguien mandó una nota de voz.
INFO|texto           INFO|juan se unió        2        Aviso del sistema (entró / salió alguien).
USUARIOS|a,b,c       USUARIOS|ana,juan        2        Lista de conectados en ESTE servidor.
LLAMADA|de|accion    LLAMADA|juan|INVITAR     3        Aviso de videollamada de "de".
VIDEO|de|base64      VIDEO|juan|/9j/4AAQ...   3        Cuadro de video de "de".
VOZ|de|base64        VOZ|juan|/f7+/v...       3        Audio en vivo de "de".
ERROR|texto          ERROR|nombre en uso      2        Algo salió mal.
```

**Videollamada.** `LLAMADA`, `VIDEO` y `VOZ` se reparten como `PRIV`: solo a `para`, y solo dentro
del mismo servidor. Las acciones de `LLAMADA` son `INVITAR`, `SONANDO`, `ACEPTAR`, `RECHAZAR`, `OCUPADO`,
`COLGAR`, `CAMARA_ON`, `CAMARA_OFF`, `MICROFONO_ON` y `MICROFONO_OFF`. Si `para` no está conectado,
el servidor le responde al que llama `LLAMADA|para|NO_DISPONIBLE`.

```
juan → servidor :  LLAMADA|ana|INVITAR
servidor → ana  :  LLAMADA|juan|INVITAR      ← a ana le suena
ana  → servidor :  LLAMADA|juan|SONANDO      ← juan ve "Sonando…" (si no llega, el chat de ana es de antes)
servidor → juan :  LLAMADA|ana|SONANDO
ana  → servidor :  LLAMADA|juan|ACEPTAR
servidor → juan :  LLAMADA|ana|ACEPTAR       ← empieza la llamada
juan → servidor :  VIDEO|ana|/9j/...  y  VOZ|ana|/f7+...   (muchas veces por segundo, los dos)
ana  → servidor :  LLAMADA|juan|COLGAR
servidor → juan :  LLAMADA|ana|COLGAR
```

### Entre servidores (Nivel 2)

```
SERVIDOR|id          SERVIDOR|5001            2        Primera línea: "no soy cliente, soy otro servidor".
```

Después de esa línea, los servidores simplemente se pasan las líneas `MSG|...`, `INFO|...`, `IMG|...` y `AUDIO|...` **tal cual**.
(No se pasan `USUARIOS`, `PRIV`, `LLAMADA`, `VIDEO`, `VOZ` ni `ERROR`: esos son solo locales.)

### Ejemplo de una conversación completa

```
juan  → servidor :  NOMBRE|juan
servidor → juan  :  INFO|Bienvenido, juan
servidor → todos :  INFO|juan se unió al chat
servidor → todos :  USUARIOS|ana,juan
juan  → servidor :  MSG|hola
servidor → todos :  MSG|juan|hola          ← Juan también lo recibe
juan  → servidor :  SALIR
servidor → todos :  INFO|juan salió del chat
servidor → todos :  USUARIOS|ana
```

### Acuerdos importantes

- **Tu propio mensaje te llega de vuelta** desde el servidor. B **no** debe pintar el mensaje al enviarlo; se pinta cuando vuelve. (Así no sale duplicado y se confirma que llegó.)
- Los nombres **no pueden** tener `|` ni `,` ni espacios. B lo valida al pedir el nombre.
- Todo en **UTF-8** (para que funcionen tildes y ñ). Por eso los dos usan `Protocolo.lector()` y `Protocolo.escritor()`.
- Nombre repetido en el mismo servidor → el servidor responde `ERROR|nombre en uso` y cierra la conexión.

### `Protocolo.java` (se escribe en la Fase 0, entre los dos)

```java
package comun;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class Protocolo {
    private Protocolo() {}

    public static final int PUERTO_POR_DEFECTO = 5000;
    public static final String SEP = "|";

    // Tipos de mensaje
    public static final String NOMBRE   = "NOMBRE";
    public static final String MSG      = "MSG";
    public static final String PRIV     = "PRIV";
    public static final String IMG      = "IMG";
    public static final String AUDIO    = "AUDIO";   // agregado después: notas de voz
    public static final String SALIR    = "SALIR";
    public static final String INFO     = "INFO";
    public static final String USUARIOS = "USUARIOS";
    public static final String ERROR    = "ERROR";
    public static final String SERVIDOR = "SERVIDOR";

    /** armar("MSG", "juan", "hola")  →  "MSG|juan|hola" */
    public static String armar(String... partes) {
        return String.join(SEP, partes);
    }

    /**
     * Separa una línea en como máximo 'max' partes (ver columna PARTES).
     * partir("MSG|juan|hola|chau", 3) → ["MSG", "juan", "hola|chau"]
     * Para saber solo el tipo: partir(linea, 2)[0]
     */
    public static String[] partir(String linea, int max) {
        return linea.split("\\|", max);
    }

    /** Para leer líneas de un socket (con tildes y ñ). */
    public static BufferedReader lector(Socket s) throws IOException {
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    /** Para escribir líneas en un socket. Se envía al instante (autoflush). */
    public static PrintWriter escritor(Socket s) throws IOException {
        return new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
    }
}
```

---

## 4. Plan del día

Las horas son relativas al momento en que empiecen (Hora 0 = cuando se sientan a trabajar). Total ≈ 6 h 30 min.

| Hora | Fase | Persona A (Backend) | Persona B (Frontend) |
|---|---|---|---|
| 0:00 – 0:30 | **Fase 0** (juntos) | Crear repo, carpetas, `.gitignore`, `Protocolo.java` | Ídem, y clonar el repo |
| 0:30 – 2:30 | **Fase 1** | Servidor básico + `ClienteConsola` | Ventana del chat + `ClienteRed` |
| 2:30 – 3:00 | 🔵 **Punto de control 1** | Probar juntos el Nivel 1 | |
| 3:00 – 4:30 | **Fase 2** | Varios servidores (`ConexionServidor`) | Lista de usuarios, privados, desconexión |
| 4:30 – 4:50 | 🔵 **Punto de control 2** | Probar juntos el Nivel 2 | |
| 4:50 – 6:00 | **Fase 3** | `Camara.java` + `README.md` | Botones de imagen y cámara, mostrar imágenes |
| 6:00 – 6:30 | 🟢 **Punto de control final** | Demo completa | |

> ⏰ **Si una fase se atrasa más de 30 min:** se avisa por el grupo. El Nivel 1 y 2 tienen prioridad sobre el 3.

---

## 5. Fase 0 — Los dos juntos (30 min)

- [ ] A crea el repositorio en GitHub y agrega a B como colaborador.
- [ ] Crear las carpetas `src/comun`, `src/servidor`, `src/pruebas`, `src/cliente`, `lib`.
- [ ] Crear `.gitignore` con:
  ```
  out/
  *.class
  .idea/
  *.iml
  .vscode/
  ```
- [ ] Copiar `Protocolo.java` de esta guía en `src/comun/`.
- [ ] Leer juntos la **sección 3** (protocolo) y la **conversación de ejemplo**. Que los dos la entiendan igual.
- [ ] Los dos clonan el repo y compilan una vez (ver **sección 9**).
- [ ] Anotar los nombres en la tabla del inicio de esta guía.

✅ **Listo cuando:** los dos tienen el repo clonado y `Protocolo.java` compila.

---

## 6. Tareas de Persona A — Backend (Servidor)

### Fase 1 — Servidor básico (2 h)

> 🎯 **Primer objetivo (en ~1 h):** subir un servidor mínimo que acepte `NOMBRE` y reparta `MSG`. **Avisar a B apenas esté subido**, porque B lo necesita para probar su cliente.

#### Archivo `src/servidor/Servidor.java`

Es "el jefe": guarda la lista de clientes conectados y reparte los mensajes.

```java
package servidor;

public class Servidor {
    private final int puerto;
    private final Map<String, ManejadorCliente> clientes = new HashMap<>();
    private final List<ConexionServidor> vecinos = new ArrayList<>();   // Fase 2

    public static void main(String[] args)
    public Servidor(int puerto)
    public void iniciar()
    public synchronized boolean registrarCliente(String nombre, ManejadorCliente m)
    public synchronized void quitarCliente(String nombre)
    public synchronized void difundirLocal(String linea)
    public synchronized void difundir(String linea, ConexionServidor origen)
    public synchronized boolean enviarPrivado(String de, String para, String texto)
    public synchronized String listaUsuarios()
    // Fase 2:
    public void conectarAVecino(String host, int puertoVecino)
    public synchronized void agregarVecino(ConexionServidor c)
    public synchronized void quitarVecino(ConexionServidor c)
}
```

| Función | Qué hace (en simple) |
|---|---|
| `main(args)` | Lee el puerto de `args[0]` (o usa 5000). Crea el servidor y llama `iniciar()`. |
| `iniciar()` | Abre un `ServerSocket` y se queda esperando clientes para siempre. Por cada uno que llega crea un `ManejadorCliente` y lo lanza en un **hilo nuevo** (`new Thread(...).start()`). |
| `registrarCliente(nombre, m)` | Guarda al cliente en el mapa. Devuelve `false` si el nombre ya existe. |
| `quitarCliente(nombre)` | Lo borra del mapa. |
| `difundirLocal(linea)` | Envía la línea a **todos los clientes de este servidor**. |
| `difundir(linea, origen)` | En Fase 1: solo llama `difundirLocal(linea)`. En Fase 2: además la envía a los servidores vecinos (menos a `origen`). |
| `enviarPrivado(de, para, texto)` | Busca a `para` y le envía `PRIV|de|texto`. Devuelve `false` si no está. |
| `listaUsuarios()` | Devuelve `"USUARIOS|ana,juan,pedro"` con los nombres del mapa. |

> 💡 **Truco para no romper nada en Fase 2:** desde el inicio, `ManejadorCliente` siempre llama a `servidor.difundir(linea, null)`. En Fase 2 solo cambias **por dentro** `difundir`, y nada más se toca.
> Para que compile en Fase 1, crea `ConexionServidor.java` como una clase vacía (`public class ConexionServidor {}`).

#### Archivo `src/servidor/ManejadorCliente.java`

Uno por cada cliente conectado. Corre en su propio hilo y escucha lo que ese cliente manda.

```java
package servidor;

public class ManejadorCliente implements Runnable {
    public ManejadorCliente(Socket socket, Servidor servidor)
    public void run()
    private void procesarLinea(String linea)
    public synchronized void enviar(String linea)
    public String getNombre()
    private void cerrar()
}
```

| Función | Qué hace (en simple) |
|---|---|
| `run()` | 1) Abre lector y escritor con `Protocolo.lector/escritor`. 2) Lee la **primera línea**: debe ser `NOMBRE|x`. 3) Si `registrarCliente` da `false` → envía `ERROR|nombre en uso` y cierra. 4) Si todo bien → envía `INFO|Bienvenido, x`, luego `difundir("INFO|x se unió al chat")` y `difundirLocal(listaUsuarios())`. 5) Bucle: mientras `readLine()` no sea `null`, llama `procesarLinea`. 6) Al salir del bucle → `cerrar()`. |
| `procesarLinea(linea)` | Mira el tipo (`partir(linea, 2)[0]`): **MSG** → `difundir("MSG|nombre|texto", null)`. **PRIV** → `enviarPrivado(...)`; si da `false`, responde `ERROR|usuario no encontrado`. **IMG** → `difundir("IMG|nombre|base64", null)`. **SALIR** → `cerrar()`. Otro → `ERROR|comando desconocido`. |
| `enviar(linea)` | `escritor.println(linea)`. Es `synchronized` para que dos hilos no escriban mezclado. |
| `cerrar()` | `quitarCliente(nombre)`, `difundir("INFO|nombre salió del chat")`, `difundirLocal(listaUsuarios())`, y cierra el socket. Que no se ejecute dos veces (usar un `boolean cerrado`). |

> ⚠️ Envuelve todo `run()` en `try/catch`: si un cliente cierra de golpe, ese hilo termina pero **el servidor sigue vivo**.

#### Archivo `src/pruebas/ClienteConsola.java`

Un cliente de texto para que A pruebe su servidor **sin esperar la ventana de B**.

```java
package pruebas;

public class ClienteConsola {
    public static void main(String[] args)   // args: host puerto nombre
}
```

- Se conecta, envía `NOMBRE|nombre`.
- Lanza un hilo que imprime en pantalla todo lo que llega.
- Lee del teclado: lo que escribas se envía como `MSG|texto`. Si escribes `/p ana hola` envía `PRIV|ana|hola`. Si escribes `/salir` envía `SALIR`.

#### ✅ Cómo sabe A que la Fase 1 está bien

- [ ] Abro el servidor y 3 `ClienteConsola` con nombres distintos.
- [ ] Lo que escribe uno le llega a los 3.
- [ ] Al entrar/salir alguien, los demás ven el `INFO` y la nueva lista `USUARIOS`.
- [ ] Un cuarto cliente con nombre repetido recibe `ERROR|nombre en uso`.
- [ ] Cierro un cliente a la fuerza (Ctrl+C) y el servidor **no se cae**.
- [ ] Un mensaje con tildes y ñ ("año, canción") llega bien.
- [ ] Subido al repo con commit `[A] servidor nivel 1`.

---

### Fase 2 — Varios servidores (1 h 30 min)

**Cómo se arrancan:**

```
Servidor 1:  java -cp out servidor.Servidor 5000
Servidor 2:  java -cp out servidor.Servidor 5001 localhost 5000
Servidor 3:  java -cp out servidor.Servidor 5002 localhost 5000
```

Los argumentos son: `puertoPropio [hostVecino puertoVecino]`. Si hay 3 argumentos, antes de `iniciar()` se llama a `conectarAVecino(host, puerto)`.

> 📐 **Regla para evitar bucles:** cada servidor nuevo se conecta a **un solo** servidor existente. Nunca se cierra un círculo (1→2→3→1). Así, reenviando "a todos menos al que me lo mandó", cada mensaje llega una sola vez.

#### Archivo `src/servidor/ConexionServidor.java`

Representa la conexión con **otro servidor** (un "vecino").

```java
package servidor;

public class ConexionServidor implements Runnable {
    public ConexionServidor(Socket socket, BufferedReader lector, PrintWriter escritor, Servidor servidor)
    public void run()
    public synchronized void enviar(String linea)
    private void cerrar()
}
```

| Función | Qué hace (en simple) |
|---|---|
| `run()` | Por cada línea que llega del vecino → `servidor.difundir(linea, this)`. |
| `enviar(linea)` | `escritor.println(linea)`. |
| `cerrar()` | `servidor.quitarVecino(this)` y cierra el socket. |

#### Cambios en archivos de A

| Dónde | Cambio |
|---|---|
| `Servidor.difundir(linea, origen)` | `difundirLocal(linea)` + para cada vecino que **no sea** `origen`: `vecino.enviar(linea)`. |
| `Servidor.conectarAVecino(host, p)` | Abre `new Socket(host, p)`, envía `SERVIDOR|<miPuerto>`, crea un `ConexionServidor`, lo agrega con `agregarVecino` y lo lanza en un hilo. |
| `ManejadorCliente.run()` | Si la primera línea empieza con `SERVIDOR` → crea un `ConexionServidor` con **el mismo** socket, lector y escritor, lo agrega con `agregarVecino`, lo lanza en un hilo y **termina `run()` sin cerrar el socket**. |

> Nota: `USUARIOS` y `PRIV` siguen siendo **solo del servidor local**. Esto es una limitación aceptada para mantenerlo simple.

#### ✅ Cómo sabe A que la Fase 2 está bien

- [ ] Servidor 1 (5000) y Servidor 2 (5001 conectado a 5000).
- [ ] `ClienteConsola` "juan" en 5000 y "ana" en 5001: se ven los mensajes mutuamente.
- [ ] Agrego Servidor 3 (5002 conectado a 5000) con "pedro": los 3 se leen entre sí.
- [ ] Ningún mensaje sale repetido.
- [ ] Si "ana" se va, "juan" y "pedro" ven `INFO|ana salió del chat`.
- [ ] Subido con commit `[A] multiservidor`.

---

### Fase 3 — Cámara e imágenes (1 h)

> El servidor **ya reenvía `IMG`** desde la Fase 1 (y entre servidores también, porque usa `difundir`). No hay que tocar el servidor.

#### Archivo `src/cliente/Camara.java` (dueño: A)

```java
package cliente;

public class Camara {
    public static String aBase64(BufferedImage img)
    public static BufferedImage deBase64(String texto)
    public static BufferedImage tomarFoto()
}
```

| Función | Qué hace (en simple) |
|---|---|
| `aBase64(img)` | Achica la imagen a máximo **320×240**, la pasa a JPG y la convierte a texto Base64 (una sola línea). |
| `deBase64(texto)` | Lo contrario: de texto Base64 a imagen. Devuelve `null` si falla. |
| `tomarFoto()` | Abre la cámara, toma una foto, la cierra y la devuelve. Devuelve `null` si no hay cámara o falla. **Nunca lanza excepción** (atrapar `Throwable`). |

**Orden de trabajo:**

1. **Primero (15 min):** `aBase64` y `deBase64` (solo Java normal, sin librerías). Subir y **avisar a B**, porque B los usa.
2. **Después:** `tomarFoto()` con la librería **webcam-capture** (de *sarxos*, en GitHub):
   - Descargar el `.zip` de la librería con sus dependencias y poner todos los `.jar` en `lib/`.
   - **Subir los `.jar` al repo**, para que B también compile.
   - Uso básico: `Webcam cam = Webcam.getDefault(); cam.open(); BufferedImage img = cam.getImage(); cam.close();`
3. **Al final:** escribir `README.md` con cómo compilar y ejecutar (copiar la sección 9).

> 💡 Al convertir a JPG, crea la copia como `BufferedImage.TYPE_INT_RGB`. Si no, las imágenes PNG con transparencia fallan al guardarse como JPG.
> 💡 En Mac, la primera vez hay que dar permiso de cámara a la terminal o al IDE.

#### ✅ Cómo sabe A que la Fase 3 está bien

- [ ] `deBase64(aBase64(img))` devuelve una imagen que se ve bien.
- [ ] El texto de `aBase64` pesa menos de ~50 KB.
- [ ] `tomarFoto()` devuelve una imagen en una PC con cámara, y `null` (sin caerse) en una sin cámara.
- [ ] `README.md` subido.

---

## 7. Tareas de Persona B — Frontend (Cliente)

### Fase 1 — Ventana y conexión (2 h)

> 🎯 **Orden recomendado:** primero la **ventana sin red** (≈45 min). Para cuando termines, A ya habrá subido el servidor mínimo y conectas `ClienteRed`.

#### Archivo `src/cliente/OyenteMensajes.java`

Es el "puente" entre la red y la ventana: `ClienteRed` recibe líneas del servidor y avisa a la ventana llamando a estos métodos.

```java
package cliente;

public interface OyenteMensajes {
    void alRecibirMensaje(String de, String texto);
    void alRecibirPrivado(String de, String texto);
    void alRecibirImagen(String de, BufferedImage imagen);
    void alRecibirInfo(String texto);
    void alActualizarUsuarios(String[] usuarios);
    void alRecibirError(String texto);
    void alDesconectarse();
}
```

#### Archivo `src/cliente/ClienteRed.java`

Todo lo que tiene que ver con el socket del lado del cliente. **No tiene nada de ventana.**

```java
package cliente;

public class ClienteRed {
    public ClienteRed(OyenteMensajes oyente)
    public boolean conectar(String host, int puerto, String nombre)
    public void enviarMensaje(String texto)
    public void enviarPrivado(String para, String texto)
    public void enviarImagen(BufferedImage imagen)     // Fase 3
    public void desconectar()
    private void escuchar()
    private void procesarLinea(String linea)
}
```

| Función | Qué hace (en simple) |
|---|---|
| `conectar(host, puerto, nombre)` | Abre el `Socket`, crea lector/escritor con `Protocolo`, envía `NOMBRE|nombre` y lanza un hilo con `escuchar()`. Devuelve `false` si no pudo conectar. |
| `enviarMensaje(texto)` | Envía `MSG|texto`. |
| `enviarPrivado(para, texto)` | Envía `PRIV|para|texto`. |
| `enviarImagen(img)` | Envía `IMG|` + `Camara.aBase64(img)`. |
| `desconectar()` | Envía `SALIR` y cierra el socket. |
| `escuchar()` | Bucle: `readLine()` → `procesarLinea`. Si llega `null` o hay error → `oyente.alDesconectarse()`. |
| `procesarLinea(linea)` | Mira el tipo y llama al método del oyente que toca: `MSG` → `alRecibirMensaje`, `PRIV` → `alRecibirPrivado`, `IMG` → `Camara.deBase64` + `alRecibirImagen`, `INFO` → `alRecibirInfo`, `USUARIOS` → separar por `,` + `alActualizarUsuarios`, `ERROR` → `alRecibirError`. |

#### Archivo `src/cliente/VentanaChat.java`

La ventana (Swing). Implementa `OyenteMensajes`.

```java
package cliente;

public class VentanaChat extends JFrame implements OyenteMensajes {
    public VentanaChat(String nombre, String host, int puerto)
    private void construirInterfaz()
    private void accionEnviar()
    private void agregarLinea(String texto)
    // + los 7 métodos de OyenteMensajes
    // Fase 2:
    private void accionPrivado()
    // Fase 3:
    private void accionEnviarArchivoImagen()
    private void accionEnviarFoto()
    private void mostrarImagen(String de, BufferedImage img)
}
```

**Diseño de la ventana (simple):**

```
+-----------------------------------------------+
|  Chat — juan @ localhost:5000                 |
+-------------------------------+---------------+
|  [INFO] ana se unió al chat   |  Conectados   |
|  juan: hola                   |  ─────────    |
|  ana: qué tal                 |  ana          |
|  (privado) ana: psst          |  juan         |
|                               |               |
|        (JTextArea, solo       |   (JList)     |
|         lectura, con scroll)  |   ← Fase 2    |
+-------------------------------+---------------+
| [ escribe aquí...         ] [Enviar] [Privado]|
|                        [🖼 Imagen] [📷 Cámara] |  ← Fase 3
+-----------------------------------------------+
```

| Función | Qué hace (en simple) |
|---|---|
| Constructor | Guarda datos, llama `construirInterfaz()`, crea `ClienteRed(this)` y llama `conectar`. Si falla, muestra un mensaje y cierra. |
| `construirInterfaz()` | Arma la ventana: área de mensajes, campo de texto, botón Enviar. **Enter** también envía. |
| `accionEnviar()` | Toma el texto del campo; si no está vacío → `clienteRed.enviarMensaje(texto)` y limpia el campo. **No pinta el mensaje** (se pinta cuando vuelve del servidor). |
| `agregarLinea(texto)` | Agrega una línea al área de mensajes y baja el scroll al final. |
| Métodos del oyente | `alRecibirMensaje` → `agregarLinea(de + ": " + texto)`. `alRecibirInfo` → `agregarLinea("[INFO] " + texto)`. `alRecibirError` → ventana emergente (`JOptionPane`). `alDesconectarse` → aviso y desactivar el botón Enviar. |

> ⚠️ **Muy importante:** los métodos del oyente se llaman desde el **hilo de red**, no desde el de la ventana. Todo lo que cambie la ventana debe ir dentro de `SwingUtilities.invokeLater(() -> { ... });` o la ventana se congela o falla al azar.

#### Archivo `src/cliente/AppCliente.java`

```java
package cliente;

public class AppCliente {
    public static void main(String[] args)
}
```

- Pide con `JOptionPane` el **nombre**, el **host** (por defecto `localhost`) y el **puerto** (por defecto `5000`).
- Valida el nombre: no vacío, sin `|`, sin `,`, sin espacios.
- Abre `new VentanaChat(nombre, host, puerto)`.

> Poder elegir host y puerto es clave para el Nivel 2 (conectarse al servidor 5000 o al 5001).

#### ✅ Cómo sabe B que la Fase 1 está bien

- [ ] La ventana abre y se ve bien aunque no haya servidor (muestra error, no se cae).
- [ ] Con el servidor de A: abro 2 ventanas con nombres distintos y se hablan.
- [ ] Enter envía. El campo se limpia. El mensaje propio aparece **una sola vez**.
- [ ] Veo los avisos `[INFO]` cuando alguien entra o sale.
- [ ] Nombre repetido → sale el error en ventana emergente.
- [ ] Subido con commit `[B] cliente nivel 1`.

---

### Fase 2 — Lista, privados y desconexión (1 h 30 min)

| Tarea | Detalle |
|---|---|
| Lista de conectados | `JList` a la derecha. `alActualizarUsuarios` reemplaza su contenido. |
| Mensajes privados | Botón **Privado**: envía el texto del campo al usuario seleccionado en la lista con `enviarPrivado`. Si no hay nadie seleccionado, avisa. `alRecibirPrivado` pinta `(privado) de: texto`. |
| Cerrar bien | Al cerrar la ventana (X) → `clienteRed.desconectar()` antes de salir (`addWindowListener`). |
| Servidor caído | Si el servidor se apaga → aviso "Se perdió la conexión" y se desactiva Enviar. |
| Título | La ventana muestra `Chat — nombre @ host:puerto`. |

#### ✅ Cómo sabe B que la Fase 2 está bien

- [ ] La lista se actualiza sola cuando alguien entra o sale.
- [ ] Privado: solo lo ve el destinatario (y no los demás).
- [ ] Cerrar la ventana con la X hace que los demás vean "salió del chat".
- [ ] Apagar el servidor no congela la ventana.
- [ ] Subido con commit `[B] lista y privados`.

---

### Fase 3 — Imágenes y cámara (1 h)

> Necesitas `Camara.aBase64` y `Camara.deBase64` de A (los sube en los primeros 15 min de la fase). Mientras tanto, arma los botones y `mostrarImagen`.

| Función | Qué hace (en simple) |
|---|---|
| `accionEnviarArchivoImagen()` | Botón **🖼 Imagen**: abre un `JFileChooser`, lee la imagen con `ImageIO.read(archivo)` y llama `clienteRed.enviarImagen(img)`. |
| `accionEnviarFoto()` | Botón **📷 Cámara**: llama `Camara.tomarFoto()`. Si devuelve `null` → aviso "No se encontró cámara". Si no → `clienteRed.enviarImagen(img)`. |
| `alRecibirImagen(de, img)` | Pinta `[imagen de juan]` en el chat y llama `mostrarImagen`. |
| `mostrarImagen(de, img)` | Abre una ventanita pequeña (`JFrame` con un `JLabel(new ImageIcon(img))`) titulada "Imagen de juan". |

> 💡 La cámara puede tardar 1–2 segundos en abrir. Llama a `tomarFoto()` dentro de un hilo nuevo para que la ventana no se congele.

#### ✅ Cómo sabe B que la Fase 3 está bien

- [ ] Envío una imagen desde archivo y **todos** la ven (incluso los conectados a otro servidor).
- [ ] En una PC con cámara, la foto llega a todos.
- [ ] En una PC sin cámara, sale el aviso y el chat sigue funcionando.
- [ ] Subido con commit `[B] imágenes y cámara`.

---

## 8. Puntos de control (juntarse y probar)

### 🔵 Punto de control 1 — Nivel 1 (a las ~2:30)

- [ ] `git pull` los dos y compilar todo junto sin errores.
- [ ] Servidor en la PC de A.
- [ ] 2 ventanas de B + 1 `ClienteConsola` de A, conectados al mismo servidor.
- [ ] Todos ven los mensajes de todos (ventana ↔ consola también).
- [ ] Entradas/salidas se avisan. Nombre repetido rechazado.
- [ ] Tildes y ñ se ven bien.
- [ ] Probar **desde las dos PCs** (ver sección 9, "Probar en dos computadoras").

### 🔵 Punto de control 2 — Nivel 2 (a las ~4:30)

- [ ] 2 servidores (5000 y 5001→5000). Ventana en cada uno: se hablan.
- [ ] Agregar un 3er servidor (5002→5000): todos se hablan, sin mensajes repetidos.
- [ ] Lista de usuarios y privados funcionan dentro del mismo servidor.
- [ ] Cerrar un cliente o un servidor no tumba a los demás.

### 🟢 Punto de control final (a las ~6:00)

- [ ] Todo lo anterior sigue funcionando.
- [ ] Imagen desde archivo llega a todos (también entre servidores).
- [ ] Foto de cámara (si hay cámara).
- [ ] `README.md` actualizado.
- [ ] Ensayar el **guion de demo** (sección 11).

---

## 9. Cómo compilar y ejecutar

### Opción fácil: IDE (IntelliJ / NetBeans / VS Code)

Abrir la carpeta `chat-java`, marcar `src` como carpeta de código y ejecutar los `main` directamente.
En IntelliJ, para abrir **varios clientes a la vez**: *Run → Edit Configurations → Modify options → Allow multiple instances*.

### Opción terminal

```bash
# Compilar (desde la carpeta chat-java)
javac -encoding UTF-8 -cp "lib/*" -d out src/comun/*.java src/servidor/*.java src/pruebas/*.java src/cliente/*.java

# Ejecutar (Linux / Mac usan ":"   —   Windows usa ";")
java -cp "out:lib/*" servidor.Servidor 5000
java -cp "out:lib/*" servidor.Servidor 5001 localhost 5000
java -cp "out:lib/*" cliente.AppCliente
java -cp "out:lib/*" pruebas.ClienteConsola localhost 5000 juan

# En Windows, cambiar "out:lib/*" por "out;lib/*"
```

### Probar en dos computadoras

1. Las dos PCs en la **misma red WiFi**.
2. En la PC del servidor, ver su IP: `ipconfig` (Windows) o `ip a` / `ifconfig` (Linux/Mac). Ejemplo: `192.168.1.35`.
3. En el cliente, poner esa IP como host.
4. Si no conecta: permitir Java en el **firewall** de la PC del servidor.
5. Si aún no conecta (pasa en WiFi de universidad): usar el **hotspot de un celular**.

---

## 10. Git sin chocar

```bash
git pull                      # 1. traer lo del compañero
# ... compilar y probar ...   # 2. que todo compile
git add src/servidor/         # 3. agregar SOLO tus archivos
git commit -m "[A] servidor nivel 1"
git push                      # 4. subir
```

- Trabajen los dos en `main`: como cada uno toca archivos distintos, no habrá conflictos.
- **Nunca subas código que no compila.** Si lo tuyo está a medias, comenta la parte rota o no la subas todavía.
- Si aparece un conflicto, es porque alguien tocó un archivo que no era suyo → revisar la tabla de la sección 2.

---

## 11. Guion de la demo final (≈5 min)

1. Levantar **Servidor 1** (5000) y **Servidor 2** (5001 → 5000).
2. Abrir **juan** y **ana** en el Servidor 1, **pedro** en el Servidor 2.
3. juan saluda → le llega a ana y a pedro (explicar que pasó por dos servidores).
4. Mostrar la lista de conectados y un **mensaje privado** entre juan y ana.
5. pedro envía una **imagen desde archivo** → la ven todos.
6. Tomar una **foto con la cámara** y enviarla.
7. Cerrar la ventana de ana → todos ven "ana salió del chat" y la lista se actualiza.
8. Explicar en una frase cada parte: *"el servidor tiene un hilo por cliente; los servidores se reenvían los mensajes entre ellos sin repetirlos; el cliente separa la red (`ClienteRed`) de la ventana (`VentanaChat`)"*.

---

## 12. Si algo sale mal (plan B)

| Problema | Qué hacer |
|---|---|
| La cámara no funciona / la librería no carga | Se deja solo **"Imagen desde archivo"**. Ya cumple la parte de imágenes. |
| El multiservidor da mensajes repetidos | Revisar que nadie conecte servidores en círculo y que `difundir` no reenvíe al `origen`. |
| La ventana se congela | Falta `SwingUtilities.invokeLater` en algún método del oyente, o algo lento (cámara) corre en el hilo de la ventana. |
| Tildes salen raras (`Ã±`) | Alguien no usó `Protocolo.lector/escritor`, o faltó `-encoding UTF-8` al compilar. |
| "Connection refused" | El servidor no está prendido, el puerto está mal, o el firewall lo bloquea. |
| "Address already in use" | Ya hay un servidor en ese puerto. Cerrarlo o usar otro puerto. |
| Se acaba el tiempo | Entregar el último **nivel** que funcione completo. Nivel 1 + 2 es la entrega mínima. |
