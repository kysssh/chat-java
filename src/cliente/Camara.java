package cliente;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Ayuda para las imágenes del chat (Nivel 3): pasar una imagen a texto y de texto a imagen,
 * y tomar una foto con la cámara. La usa la ventana del cliente (B).
 */
public class Camara {

    private static final int ANCHO_MAX = 320;
    private static final int ALTO_MAX = 240;
    private static final float CALIDAD_JPG = 0.75f;

    private Camara() {}

    /**
     * Achica la imagen a máximo 320x240 (sin deformarla ni agrandarla), la pasa a JPG y la
     * convierte a texto Base64 en UNA sola línea, lista para enviarla como IMG|texto.
     * Devuelve null si la imagen es null o no se pudo convertir.
     */
    public static String aBase64(BufferedImage img) {
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
            return null;
        }
        try {
            double escala = Math.min(1.0, Math.min(
                    (double) ANCHO_MAX / img.getWidth(),
                    (double) ALTO_MAX / img.getHeight()));
            int ancho = Math.max(1, (int) Math.round(img.getWidth() * escala));
            int alto = Math.max(1, (int) Math.round(img.getHeight() * escala));

            // TYPE_INT_RGB: sin canal alfa; si no, los PNG con transparencia fallan al guardar como JPG
            BufferedImage copia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copia.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, ancho, alto, java.awt.Color.WHITE, null);   // fondo blanco bajo la transparencia
            g.dispose();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
            try {
                ImageWriteParam param = escritor.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(CALIDAD_JPG);
                escritor.setOutput(new MemoryCacheImageOutputStream(bytes));
                escritor.write(null, new IIOImage(copia, null, null), param);
            } finally {
                escritor.dispose();
            }
            // getEncoder() (no getMimeEncoder) no mete saltos de línea: cabe en una sola línea del protocolo
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** Lo contrario de aBase64: de texto Base64 a imagen. Devuelve null si falla. */
    public static BufferedImage deBase64(String texto) {
        if (texto == null || texto.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(texto.trim());
            return ImageIO.read(new ByteArrayInputStream(bytes));   // null si los bytes no son una imagen
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Abre la cámara, toma una foto, la cierra y la devuelve.
     * Devuelve null si no hay cámara, la librería no carga o algo falla: NUNCA lanza excepción.
     * Puede tardar 1–2 s: llamarla desde un hilo aparte, no desde el de la ventana.
     */
    public static BufferedImage tomarFoto() {
        Webcam cam = null;
        try {
            cam = Webcam.getDefault();
            if (cam == null) {
                return null;
            }
            try {
                cam.setViewSize(WebcamResolution.VGA.getSize());
            } catch (Exception e) {
                // esa cámara no acepta VGA: se usa la resolución que traiga
            }
            cam.open();
            // Las primeras imágenes suelen salir oscuras (la cámara aún ajusta la exposición)
            BufferedImage foto = null;
            for (int i = 0; i < 5; i++) {
                foto = cam.getImage();
                Thread.sleep(100);
            }
            return foto;
        } catch (Throwable t) {   // incluye NoClassDefFoundError si falta la librería en lib/
            return null;
        } finally {
            if (cam != null) {
                try {
                    cam.close();
                } catch (Throwable ignorada) {
                    // ya estaba cerrada
                }
            }
        }
    }
}
