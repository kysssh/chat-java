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

    // ---- Videollamada (entre dos personas del mismo servidor, como PRIV) ----
    public static final String LLAMADA  = "LLAMADA";  // LLAMADA|para|accion  →  LLAMADA|de|accion
    public static final String VIDEO    = "VIDEO";    // VIDEO|para|base64 (un cuadro JPG)  →  VIDEO|de|base64
    public static final String VOZ      = "VOZ";      // VOZ|para|base64 (audio en vivo)   →  VOZ|de|base64

    // Acciones de LLAMADA
    public static final String INVITAR       = "INVITAR";        // quiero llamarte
    public static final String ACEPTAR       = "ACEPTAR";        // contesto
    public static final String RECHAZAR      = "RECHAZAR";       // no contesto
    public static final String OCUPADO       = "OCUPADO";        // ya estoy en otra llamada
    public static final String COLGAR        = "COLGAR";         // termino (o cancelo antes de que conteste)
    public static final String NO_DISPONIBLE = "NO_DISPONIBLE";  // (del servidor) esa persona no está conectada
    public static final String CAMARA_ON     = "CAMARA_ON";      // prendí / apagué mi cámara
    public static final String CAMARA_OFF    = "CAMARA_OFF";
    public static final String MICROFONO_ON  = "MICROFONO_ON";   // activé / silencié mi micrófono
    public static final String MICROFONO_OFF = "MICROFONO_OFF";

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