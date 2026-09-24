package comun;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Reglas comunes del chat. Lo usan TANTO el servidor (A) como el cliente (B).
 * No se ejecuta solo: solo guarda las etiquetas y ayuda a armar/separar mensajes.
 */
public final class Protocolo {

    private Protocolo() {} // nadie crea objetos de esta clase; es solo de apoyo

    // ---- Valores por defecto ----
    public static final int    PUERTO_POR_DEFECTO = 5000;
    public static final String SEP = "|";   // separador entre las partes de un mensaje

    // ---- Etiquetas (el "idioma" entre cliente y servidor) ----
    public static final String NOMBRE   = "NOMBRE";   // cliente → servidor: me presento
    public static final String MSG      = "MSG";      // mensaje para todos
    public static final String PRIV     = "PRIV";     // mensaje privado
    public static final String IMG      = "IMG";      // imagen (Nivel 3)
    public static final String AUDIO    = "AUDIO";    // nota de voz: WAV en Base64 (Nivel 3)
    public static final String SALIR    = "SALIR";    // me desconecto
    public static final String INFO     = "INFO";     // aviso del sistema (entró/salió alguien)
    public static final String USUARIOS = "USUARIOS"; // lista de conectados
    public static final String ERROR    = "ERROR";    // algo salió mal
    public static final String SERVIDOR = "SERVIDOR"; // servidor → servidor (Nivel 2)

    /**
     * Une varias partes con "|".
     * Ejemplo: armar("MSG", "juan", "hola")  ->  "MSG|juan|hola"
     */
    public static String armar(String... partes) {
        return String.join(SEP, partes);
    }

    /**
     * Separa una línea en como máximo 'max' partes.
     * Así un "|" dentro del texto no rompe nada.
     * Ejemplo: partir("MSG|juan|hola|chau", 3)  ->  ["MSG", "juan", "hola|chau"]
     * Para saber solo el tipo del mensaje: partir(linea, 2)[0]
     */
    public static String[] partir(String linea, int max) {
        return linea.split("\\|", max);
    }

    /**
     * Crea el "lector" de un socket, para leer línea por línea.
     * Usa UTF-8 para que las tildes y la ñ funcionen.
     */
    public static BufferedReader lector(Socket s) throws IOException {
        return new BufferedReader(
            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Crea el "escritor" de un socket, para enviar líneas.
     * El 'true' hace que se envíe al instante (no se queda en el buffer).
     */
    public static PrintWriter escritor(Socket s) throws IOException {
        return new PrintWriter(
            new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
    }
}