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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ayuda para las imágenes del chat (Nivel 3): pasar una imagen a texto y de texto a imagen,
 * y tomar una foto con la cámara. La usa la ventana del cliente (B).
 */
public class Camara {

    private static final int ANCHO_MAX = 320;
    private static final int ALTO_MAX = 240;
    private static final float CALIDAD_JPG = 0.75f;
    private static final long ESPERA_MS = 5000;   // máximo para buscar cámaras (si no, podría colgarse)

    private Camara() {}

    /**
     * Achica la imagen a máximo 320x240 (sin deformarla ni agrandarla), la pasa a JPG y la
     * convierte a texto Base64 en UNA sola línea, lista para enviarla como IMG|texto.
     * Devuelve null si la imagen es null o no se pudo convertir.
     */
    public static String aBase64(BufferedImage img) {
        return aBase64(img, ANCHO_MAX, ALTO_MAX, CALIDAD_JPG);
    }

    /**
     * Igual que aBase64(img), pero eligiendo el tamaño máximo y la calidad del JPG (0 a 1).
     * La videollamada lo usa con cuadros un poco más grandes y algo más comprimidos.
     */
    public static String aBase64(BufferedImage img, int anchoMax, int altoMax, float calidad) {
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
            return null;
        }
        try {
            double escala = Math.min(1.0, Math.min(
                    (double) anchoMax / img.getWidth(),
                    (double) altoMax / img.getHeight()));
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
                param.setCompressionQuality(calidad);
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

    /** Copia volteada de izquierda a derecha, como se ve uno en un espejo. */
    public static BufferedImage espejo(BufferedImage img) {
        BufferedImage copia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copia.createGraphics();
        g.drawImage(img, img.getWidth(), 0, -img.getWidth(), img.getHeight(), null);
        g.dispose();
        return copia;
    }

    /** Copia independiente: algunas cámaras reutilizan la misma imagen para el cuadro siguiente. */
    public static BufferedImage copia(BufferedImage img) {
        BufferedImage copia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copia.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copia;
    }

    /**
     * Nombres de las cámaras conectadas (lista vacía si no hay o la librería no carga).
     * La primera vez tarda un poco: llamarla desde un hilo aparte.
     */
    public static List<String> camaras() {
        List<String> nombres = new ArrayList<>();
        try {
            for (Webcam w : Webcam.getWebcams(ESPERA_MS)) {
                nombres.add(w.getName());
            }
        } catch (Throwable t) {   // incluye NoClassDefFoundError si falta la librería en lib/
            // sin cámaras
        }
        return nombres;
    }

    /** Foto con la cámara predeterminada. */
    public static BufferedImage tomarFoto() {
        return tomarFoto(null);
    }

    /**
     * Abre la cámara con ese nombre (null = la predeterminada), toma una foto, la cierra y la devuelve.
     * Devuelve null si no hay cámara, la librería no carga o algo falla: NUNCA lanza excepción.
     * Puede tardar 1–2 s: llamarla desde un hilo aparte, no desde el de la ventana.
     * synchronized: la misma cámara no se puede abrir dos veces a la vez.
     */
    public static synchronized BufferedImage tomarFoto(String nombreCamara) {
        Webcam cam = null;
        try {
            cam = buscar(nombreCamara);
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

    private static Webcam buscar(String nombre) throws Exception {
        if (nombre != null) {
            for (Webcam w : Webcam.getWebcams(ESPERA_MS)) {
                if (w.getName().equals(nombre)) {
                    return w;
                }
            }
            // ya no está conectada: se usa la predeterminada
        }
        return Webcam.getDefault(ESPERA_MS);
    }

    // ================================================================
    //  Cámara en vivo (espejo de la foto, prueba en Dispositivos y videollamada)
    // ================================================================

    /**
     * La cámara prendida entregando cuadros sin parar. Abre la cámara en su propio hilo
     * (tarda 1–2 s) y le pasa cada cuadro a alCuadro DESDE ESE HILO.
     *
     * Solo una transmisión (o foto) usa la cámara a la vez: si otra la tiene abierta, esta espera
     * a que la suelte. Así, al cambiar de cámara basta con detener() la vieja e iniciar la nueva.
     */
    public static final class EnVivo {
        private volatile boolean activa = true;

        private EnVivo() {}

        /**
         * Prende la cámara con ese nombre (null = la predeterminada) a unos cuadrosPorSegundo.
         * alError recibe un texto para mostrar si no se pudo abrir (también desde el hilo de la cámara).
         */
        public static EnVivo iniciar(String nombreCamara, int cuadrosPorSegundo,
                                     Consumer<BufferedImage> alCuadro, Consumer<String> alError) {
            EnVivo envivo = new EnVivo();
            Thread hilo = new Thread(() -> envivo.correr(nombreCamara, cuadrosPorSegundo, alCuadro, alError),
                    "hilo-camara-en-vivo");
            hilo.setDaemon(true);
            hilo.start();
            return envivo;
        }

        /** Apaga la cámara. No espera: el hilo la suelta al terminar el cuadro que está leyendo. */
        public void detener() {
            activa = false;
        }

        private void correr(String nombre, int fps, Consumer<BufferedImage> alCuadro, Consumer<String> alError) {
            synchronized (Camara.class) {   // el mismo candado que tomarFoto: una sola a la vez
                if (!activa) {
                    return;   // la detuvieron mientras esperaba su turno
                }
                Webcam cam = null;
                try {
                    cam = buscar(nombre);
                    if (cam == null) {
                        alError.accept("No se encontró ninguna cámara.");
                        return;
                    }
                    try {
                        cam.setViewSize(WebcamResolution.VGA.getSize());
                    } catch (Exception e) {
                        // esa cámara no acepta VGA: se usa la resolución que traiga
                    }
                    cam.open();
                    long pausa = 1000L / Math.max(1, fps);
                    while (activa) {
                        long inicio = System.currentTimeMillis();
                        BufferedImage cuadro = cam.getImage();
                        if (cuadro != null && activa) {
                            alCuadro.accept(cuadro);
                        }
                        long resto = pausa - (System.currentTimeMillis() - inicio);
                        if (resto > 0) {
                            Thread.sleep(resto);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {   // incluye NoClassDefFoundError si falta la librería en lib/
                    if (activa) {
                        alError.accept("No se pudo abrir la cámara (¿la está usando otro programa?).");
                    }
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
    }
}
