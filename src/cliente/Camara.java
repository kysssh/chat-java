package cliente;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Utilidades para convertir imágenes a/desde Base64 y tomar fotos con la cámara.
 * Dueño: Persona A (Backend), pero B la usa para enviar/recibir imágenes.
 */
public class Camara {

    /**
     * Achica la imagen a máximo 320×240, la pasa a JPG
     * y la convierte a texto Base64 (una sola línea).
     */
    public static String aBase64(BufferedImage img) {
        try {
            // Achicar a máximo 320×240 manteniendo proporción
            int anchoMax = 320;
            int altoMax = 240;
            int anchoOriginal = img.getWidth();
            int altoOriginal = img.getHeight();

            double escala = Math.min(
                (double) anchoMax / anchoOriginal,
                (double) altoMax / altoOriginal
            );

            // Solo achicar, nunca agrandar
            if (escala < 1.0) {
                int nuevoAncho = (int) (anchoOriginal * escala);
                int nuevoAlto = (int) (altoOriginal * escala);

                BufferedImage reducida = new BufferedImage(nuevoAncho, nuevoAlto,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = reducida.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, nuevoAncho, nuevoAlto, null);
                g.dispose();
                img = reducida;
            } else {
                // Convertir a RGB (por si es PNG con transparencia)
                BufferedImage rgb = new BufferedImage(anchoOriginal, altoOriginal,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                img = rgb;
            }

            // Convertir a JPG en memoria
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            baos.flush();

            // Convertir bytes a Base64 (sin saltos de línea)
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Lo contrario: de texto Base64 a imagen.
     * Devuelve null si falla.
     */
    public static BufferedImage deBase64(String texto) {
        try {
            byte[] bytes = Base64.getDecoder().decode(texto);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return ImageIO.read(bais);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Abre la cámara, toma una foto, la cierra y la devuelve.
     * Devuelve null si no hay cámara o falla.
     * NOTA: Requiere la librería webcam-capture (sarxos) en lib/.
     *       Por ahora devuelve null hasta que se agreguen los .jar.
     */
    public static BufferedImage tomarFoto() {
        try {
            // Se necesita: Webcam cam = Webcam.getDefault();
            // cam.open(); BufferedImage img = cam.getImage(); cam.close();
            // Por ahora devuelve null (se implementa cuando estén los .jar)
            return null;
        } catch (Throwable t) {
            // Nunca lanza excepción
            return null;
        }
    }
}
