package pruebas;

import comun.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * Cliente de texto para probar el servidor sin la ventana.
 * Uso: java pruebas.ClienteConsola [host] [puerto] [nombre]
 *
 * Lo que escribas se envía como MSG|texto.
 *   /p ana hola  ->  PRIV|ana|hola
 *   /salir       ->  SALIR
 */
public class ClienteConsola {

    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "localhost";
        int puerto = Protocolo.PUERTO_POR_DEFECTO;
        if (args.length >= 2) {
            try {
                puerto = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido: " + args[1]);
                return;
            }
        }
        String nombre = args.length >= 3 ? args[2] : "consola";

        // Teclado y pantalla usan la codificación de la consola; la red siempre es UTF-8
        Charset consola = codificacionConsola();
        PrintStream salida;
        try {
            salida = new PrintStream(System.out, true, consola.name());
        } catch (UnsupportedEncodingException e) {
            salida = System.out;
        }
        final PrintStream pantalla = salida;
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, consola));

        try (Socket socket = new Socket(host, puerto)) {
            BufferedReader lector = Protocolo.lector(socket);
            PrintWriter escritor = Protocolo.escritor(socket);

            escritor.println(Protocolo.armar(Protocolo.NOMBRE, nombre));

            // Hilo que imprime todo lo que llega del servidor
            Thread oyente = new Thread(() -> {
                try {
                    String linea;
                    while ((linea = lector.readLine()) != null) {
                        pantalla.println(resumir(linea));
                    }
                } catch (IOException e) {
                    // conexión cerrada
                }
                pantalla.println("[conexión cerrada]");
                System.exit(0);
            });
            oyente.setDaemon(true);
            oyente.start();

            // Lo que se escribe en el teclado se envía al servidor
            String entrada;
            while ((entrada = teclado.readLine()) != null) {
                if (entrada.isEmpty()) {
                    continue;
                }
                if (entrada.equals("/salir")) {
                    escritor.println(Protocolo.SALIR);
                    break;
                } else if (entrada.startsWith("/p ")) {
                    String[] p = entrada.split(" ", 3);   // /p ana hola
                    if (p.length < 3) {
                        pantalla.println("Uso: /p usuario texto");
                    } else {
                        escritor.println(Protocolo.armar(Protocolo.PRIV, p[1], p[2]));
                    }
                } else {
                    escritor.println(Protocolo.armar(Protocolo.MSG, entrada));
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo conectar a " + host + ":" + puerto + " (" + e.getMessage() + ")");
        }
    }

    /** IMG y AUDIO traen cientos de KB en Base64: se muestra solo quién lo mandó y cuánto pesa. */
    private static String resumir(String linea) {
        String[] p = Protocolo.partir(linea, 3);
        if (p.length == 3 && (p[0].equals(Protocolo.IMG) || p[0].equals(Protocolo.AUDIO))) {
            return p[0] + "|" + p[1] + "|(" + (p[2].length() / 1024) + " KB en Base64)";
        }
        return linea;
    }

    /** En Windows la consola no suele ser UTF-8; usar la suya evita ver "Ã±" al probar. */
    private static Charset codificacionConsola() {
        String nombre = System.getProperty("sun.stdin.encoding");
        try {
            if (nombre != null) {
                return Charset.forName(nombre);
            }
        } catch (IllegalArgumentException ignorada) {
            // nombre no reconocido: se usa el de por defecto
        }
        return Charset.defaultCharset();
    }
}
