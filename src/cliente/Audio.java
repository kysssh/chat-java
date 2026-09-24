package cliente;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Notas de voz (Nivel 3): grabar del micrófono elegido, pasar a WAV, a texto Base64 y reproducir.
 * Solo usa javax.sound (viene con Java): no hace falta ninguna librería.
 *
 * El audio se envía a 8000 Hz, mono y en μ-law (8 bits por muestra), que es la calidad de una
 * llamada telefónica: 1 segundo pesa 8 KB, así que una nota de 30 s son unos 320 KB en Base64.
 */
public final class Audio {

    public static final int SEGUNDOS_MAX = 30;
    private static final float TASA = 8000f;
    private static final AudioFormat PCM_8K = new AudioFormat(TASA, 16, 1, true, false);
    private static final AudioFormat ULAW_8K =
            new AudioFormat(AudioFormat.Encoding.ULAW, TASA, 8, 1, 1, TASA, false);
    private static final double SEGUNDOS_MIN = 0.4;

    private Audio() {}

    // ================================================================
    //  Micrófonos
    // ================================================================

    /** Los micrófonos (dispositivos que pueden grabar) conectados a la PC. */
    public static List<Mixer.Info> microfonos() {
        List<Mixer.Info> lista = new ArrayList<>();
        Line.Info grabar = new Line.Info(TargetDataLine.class);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(info).getTargetLineInfo(grabar).length > 0) {
                    lista.add(info);
                }
            } catch (Exception ignorada) {
                // dispositivo que falla al consultarlo: no se ofrece
            }
        }
        return lista;
    }

    /**
     * Formatos a probar, del más liviano al más pesado. No todos los micrófonos aceptan 8000 Hz;
     * lo que se grabe de más se reduce después a 8000 Hz mono.
     */
    private static List<AudioFormat> formatosPosibles() {
        List<AudioFormat> formatos = new ArrayList<>();
        for (float tasa : new float[] {8000f, 16000f, 44100f, 48000f}) {
            for (int canales = 1; canales <= 2; canales++) {
                formatos.add(new AudioFormat(tasa, 16, canales, true, false));
            }
        }
        return formatos;
    }

    // ================================================================
    //  Grabación
    // ================================================================

    /**
     * Una grabación en curso. Se crea con iniciar(), se termina con detener() (devuelve el WAV)
     * o con cancelar(). También sirve para probar el micrófono: avisa el volumen mientras graba.
     */
    public static class Grabacion {
        private final TargetDataLine linea;
        private final AudioFormat formato;
        private final ByteArrayOutputStream datos = new ByteArrayOutputStream();
        private final int bytesMax;
        private final Thread hilo;
        private volatile boolean activa = true;

        /**
         * Abre el micrófono (null = el predeterminado) y empieza a grabar en un hilo aparte.
         * alNivel recibe el volumen (0 a 1) unas 20 veces por segundo, desde ese hilo.
         */
        public static Grabacion iniciar(Mixer.Info microfono, DoubleConsumer alNivel)
                throws LineUnavailableException {
            Exception ultimoError = null;
            for (AudioFormat f : formatosPosibles()) {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
                try {
                    TargetDataLine l = (TargetDataLine) (microfono == null
                            ? AudioSystem.getLine(info)
                            : AudioSystem.getMixer(microfono).getLine(info));
                    l.open(f);
                    l.start();
                    return new Grabacion(l, f, alNivel);
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                    ultimoError = e;   // este formato no: se prueba el siguiente
                }
            }
            throw new LineUnavailableException("no se pudo abrir el micrófono"
                    + (ultimoError == null ? "" : " (" + ultimoError.getMessage() + ")"));
        }

        private Grabacion(TargetDataLine linea, AudioFormat formato, DoubleConsumer alNivel) {
            this.linea = linea;
            this.formato = formato;
            this.bytesMax = (int) (formato.getFrameRate() * formato.getFrameSize() * SEGUNDOS_MAX);
            this.hilo = new Thread(() -> leer(alNivel), "hilo-microfono");
            hilo.setDaemon(true);
            hilo.start();
        }

        private void leer(DoubleConsumer alNivel) {
            // Trozos de 50 ms
            int tam = Math.max(formato.getFrameSize(),
                    (int) (formato.getFrameRate() / 20) * formato.getFrameSize());
            byte[] buffer = new byte[tam];
            while (activa) {
                int n = linea.read(buffer, 0, buffer.length);
                if (n <= 0) {
                    continue;
                }
                synchronized (datos) {
                    if (datos.size() < bytesMax) {
                        datos.write(buffer, 0, Math.min(n, bytesMax - datos.size()));
                    }
                }
                if (alNivel != null) {
                    alNivel.accept(volumen(buffer, n));
                }
            }
        }

        /** Cuántos segundos lleva grabados. */
        public double segundos() {
            synchronized (datos) {
                return datos.size() / (double) (formato.getFrameRate() * formato.getFrameSize());
            }
        }

        public boolean llena() {
            synchronized (datos) {
                return datos.size() >= bytesMax;
            }
        }

        /** Termina y devuelve el WAV listo para enviar, o null si quedó demasiado corto. */
        public byte[] detener() throws IOException {
            cerrar();
            byte[] pcm;
            synchronized (datos) {
                pcm = datos.toByteArray();
            }
            if (pcm.length / (double) (formato.getFrameRate() * formato.getFrameSize()) < SEGUNDOS_MIN) {
                return null;
            }
            return aWav(pcm, formato);
        }

        /** Termina y descarta lo grabado. */
        public void cancelar() {
            cerrar();
        }

        private void cerrar() {
            activa = false;
            linea.stop();
            linea.close();
            try {
                hilo.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Volumen del trozo: el pico más alto, de 0 a 1 (muestras de 16 bits, little endian). */
    private static double volumen(byte[] b, int n) {
        int pico = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            int muestra = (short) ((b[i + 1] << 8) | (b[i] & 0xff));
            pico = Math.max(pico, Math.abs(muestra));
        }
        return Math.min(1.0, pico / 32768.0);
    }

    // ================================================================
    //  Conversión: lo grabado → WAV de 8000 Hz mono μ-law
    // ================================================================

    private static byte[] aWav(byte[] pcm, AudioFormat f) throws IOException {
        // 1) Pasar a mono (promedio de los canales)
        int canales = f.getChannels();
        int cuadros = pcm.length / (2 * canales);
        short[] mono = new short[cuadros];
        for (int i = 0; i < cuadros; i++) {
            int suma = 0;
            for (int c = 0; c < canales; c++) {
                int k = (i * canales + c) * 2;
                suma += (short) ((pcm[k + 1] << 8) | (pcm[k] & 0xff));
            }
            mono[i] = (short) (suma / canales);
        }

        // 2) Bajar a 8000 Hz: cada muestra nueva es el promedio de las que caen en su tramo
        short[] ocho = remuestrear(mono, f.getSampleRate(), TASA);
        byte[] bytes = new byte[ocho.length * 2];
        for (int i = 0; i < ocho.length; i++) {
            bytes[2 * i] = (byte) ocho[i];
            bytes[2 * i + 1] = (byte) (ocho[i] >> 8);
        }

        // 3) Comprimir a μ-law (8 bits) y guardar como WAV
        AudioInputStream pcmStream = new AudioInputStream(new ByteArrayInputStream(bytes), PCM_8K, ocho.length);
        AudioInputStream ulaw = AudioSystem.getAudioInputStream(ULAW_8K, pcmStream);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        AudioSystem.write(ulaw, AudioFileFormat.Type.WAVE, salida);
        return salida.toByteArray();
    }

    private static short[] remuestrear(short[] entrada, float desde, float hasta) {
        if (desde == hasta) {
            return entrada;
        }
        double paso = desde / hasta;
        int n = (int) (entrada.length / paso);
        short[] salida = new short[n];
        for (int j = 0; j < n; j++) {
            int ini = (int) (j * paso);
            int fin = Math.min(entrada.length, Math.max(ini + 1, (int) ((j + 1) * paso)));
            long suma = 0;
            for (int i = ini; i < fin; i++) {
                suma += entrada[i];
            }
            salida[j] = (short) (suma / (fin - ini));
        }
        return salida;
    }

    // ================================================================
    //  Texto Base64 y reproducción
    // ================================================================

    /** WAV → texto Base64 en UNA línea, listo para AUDIO|texto. */
    public static String aBase64(byte[] wav) {
        return Base64.getEncoder().encodeToString(wav);
    }

    /** Texto Base64 → WAV. Devuelve null si no es un audio válido. */
    public static byte[] deBase64(String texto) {
        try {
            byte[] wav = Base64.getDecoder().decode(texto.trim());
            return duracion(wav) > 0 ? wav : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Duración en segundos (0 si no se puede leer). */
    public static double duracion(byte[] wav) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            return in.getFrameLength() / (double) in.getFormat().getFrameRate();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Empieza a sonar la nota por los parlantes y devuelve el Clip (para pararlo o ver por dónde va).
     * El Clip se cierra solo al terminar.
     */
    public static Clip reproducir(byte[] wav) throws Exception {
        AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
        AudioFormat origen = in.getFormat();
        AudioFormat pcm = new AudioFormat(origen.getSampleRate(), 16, origen.getChannels(), true, false);
        AudioInputStream convertido = AudioSystem.getAudioInputStream(pcm, in);
        Clip clip = AudioSystem.getClip();
        clip.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP) {
                clip.close();
            }
        });
        clip.open(convertido);
        clip.start();
        return clip;
    }
}
