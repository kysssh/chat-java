# Guion de Exposición: Chat Cliente-Servidor en Java

Este documento contiene la explicación paso a paso de cómo funciona el proyecto por debajo, ideal para guiarte durante tu exposición o presentación técnica.

---

## 1. Introducción (Qué es y qué hace)

**Concepto principal:**
El proyecto es un sistema de **Chat Cliente-Servidor** en tiempo real desarrollado en Java. 

**¿Qué hace?**
Permite la comunicación sin necesidad de una base de datos. Toda la información fluye directamente a través de la red local. 
- **Funcionalidades:** Soporta mensajes de texto grupales y privados.
- **Multimedia:** Permite el envío de imágenes (desde archivo o tomando foto con la cámara) y llamadas de audio/notas de voz en tiempo real.

---

## 2. Sockets y TCP (La columna vertebral del Chat)

Para que las computadoras puedan "hablarse", utilizamos **Sockets** operando bajo el protocolo **TCP**.

**TCP (Transmission Control Protocol)** garantiza que los mensajes lleguen íntegros y en el orden correcto. Antes de enviar nada, el cliente y el servidor hacen un *handshake* (se dan la mano) y abren un túnel de conexión permanente.

### ¿Cómo se ve esto en el código?
En la arquitectura, esto se divide en dos:
1. **El Servidor (`ServerSocket`)**: Abre las puertas en un puerto específico (ej. `5000`) y se queda congelado esperando a que alguien toque.
2. **El Cliente (`Socket`)**: Sabe a qué IP y puerto dirigirse para conectarse al servidor.

```java
// En el Servidor (Servidor.java):
// El servidor abre el puerto 5000
ServerSocket serverSocket = new ServerSocket(5000);
System.out.println("Esperando clientes...");

// El programa se detiene aquí hasta que un cliente se conecta
Socket socketCliente = serverSocket.accept(); 
```

```java
// En el Cliente (ClienteRed.java / AppCliente):
// El cliente intenta conectar a la IP del servidor en el puerto 5000
Socket socket = new Socket("192.168.1.10", 5000);
```

---

## 3. Hilos (Threads): Evitando que la app se congele

Un chat necesita hacer varias cosas a la vez: permitirte escribir en la interfaz y estar atento a nuevos mensajes. Si hiciéramos todo esto en un solo proceso, la ventana se congelaría cada vez que espera un mensaje.

Para solucionarlo usamos **Hilos (Threads)**, que nos permiten hacer tareas en paralelo (concurrencia):
- **En el Servidor:** Cada vez que un cliente se conecta, se crea un nuevo hilo `ManejadorCliente`. Si hay 10 personas conectadas, hay 10 hilos trabajando a la vez.
- **En el Cliente:** Hay un hilo para dibujar la interfaz gráfica (Swing) y otro hilo secundario (`OyenteMensajes`) que tiene un bucle infinito escuchando al servidor.

### Ejemplo de código:
```java
// En el Servidor, por cada cliente que llega se lanza un hilo:
Thread hiloCliente = new Thread(new ManejadorCliente(socketCliente));
hiloCliente.start(); // Inicia el proceso en paralelo

// En el Cliente, el hilo que escucha los mensajes sin congelar la ventana:
Thread hiloEscucha = new Thread(() -> {
    while (true) {
        String mensaje = lector.readLine(); // Se queda esperando el mensaje
        mostrarEnPantalla(mensaje);         // Lo manda a la interfaz
    }
});
hiloEscucha.start();
```

---

## 4. ¿Dónde entra UDP? (El truco de la red)

En aplicaciones multimedia convencionales se usa **UDP** porque es más rápido (no verifica que todo llegue perfecto). Sin embargo, en nuestro chat enviamos el audio codificado en Base64 a través de nuestro mismo túnel **TCP**, asegurando que los mensajes no se pierdan.

Aun así, **sí utilizamos UDP (DatagramSocket)** para un truco ingenioso en la clase `Red.java`. Cuando el usuario necesita saber su IP, enviar un paquete falso por UDP hacia afuera (ej. a Google `8.8.8.8`) obliga al sistema operativo de Windows a decidir qué tarjeta de red va a usar. Gracias a esto, atrapamos esa tarjeta de red y mostramos la **IP local correcta**.

### El truco en el código (`Red.java`):
```java
try (DatagramSocket s = new DatagramSocket()) {
    // "Conectamos" de mentira por UDP.
    // Al ser UDP no hay handshake, pero nos obliga a usar un adaptador de red.
    s.connect(InetAddress.getByName("8.8.8.8"), 53);
    
    // Devolvemos la IP del adaptador que Windows decidió usar.
    return s.getLocalAddress().getHostAddress(); 
}
```

---

## 5. Multimedia: Micrófono y Audio en Vivo (Nivel 3)

Manejar el micrófono y la tarjeta de sonido también requiere sus propios hilos para no trabar la aplicación principal. 

Cuando iniciamos una nota de voz o videollamada, la clase `Audio.java` arranca un **hilo demonio** (`hilo-microfono-en-vivo`) que extrae audio del hardware cada 40 milisegundos.

Ese audio crudo es muy pesado, por lo que:
1. Se pasa a modo **mono**.
2. Se resamplea a **8000 Hz**.
3. Se comprime usando el formato telefónico **μ-law (G.711)** a 8 bits.
4. Finalmente, se envía al servidor como texto (Base64) usando TCP.

### Fragmento del flujo en `Audio.java`:
```java
// Se crea un hilo exclusivo para capturar el micrófono
this.hilo = new Thread(() -> leer(alTrozo, alNivel), "hilo-microfono-en-vivo");
hilo.setDaemon(true); // Se cerrará solo cuando la app se cierre
hilo.start();

// Dentro de la función leer(), lee 40ms de audio:
int n = linea.read(buffer, 0, buffer.length); 

// Luego se procesa y se codifica para mandarlo por la red
short[] ocho = remuestrear(aMono(buffer, n, f.getChannels()), f.getSampleRate(), 8000f);
alTrozo.accept(aUlaw(ocho)); // Dispara el envío
```

---
**Cierre de exposición sugerido:**
*"Como han podido ver, este chat es un excelente ejemplo de cómo combinar la arquitectura cliente-servidor (con Sockets y TCP), la programación concurrente (con Hilos) para mantener la fluidez, y el procesamiento de hardware para enriquecer la experiencia de usuario. Muchas gracias."*
