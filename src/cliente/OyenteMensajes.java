package cliente;

import java.awt.image.BufferedImage;

/**
 * Puente entre la red y la ventana.
 * ClienteRed recibe líneas del servidor y avisa a la ventana
 * llamando a estos métodos.
 */
public interface OyenteMensajes {
    /** Llega un mensaje público: "MSG|de|texto" */
    void alRecibirMensaje(String de, String texto);

    /** Llega un mensaje privado: "PRIV|de|texto" */
    void alRecibirPrivado(String de, String texto);

    /** Llega una imagen: "IMG|de|base64" (ya decodificada) */
    void alRecibirImagen(String de, BufferedImage imagen);

    /** Aviso del sistema: "INFO|texto" */
    void alRecibirInfo(String texto);

    /** Lista actualizada de conectados: "USUARIOS|a,b,c" */
    void alActualizarUsuarios(String[] usuarios);

    /** Error del servidor: "ERROR|texto" */
    void alRecibirError(String texto);

    /** Se perdió la conexión con el servidor */
    void alDesconectarse();
}
