package cliente;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
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

    /** Abre y prende el micrófono (null = el predeterminado) con el primer formato que acepte. */
    private static TargetDataLine abrirMicrofono(Mixer.Info microfono) throws LineUnavailableException {
        Exception ultimoError = null;
        for (AudioFormat f : formatosPosibles()) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
            try {
                TargetDataLine l = (TargetDataLine) (microfono == null
                        ? AudioSystem.getLine(info)
                        : AudioSystem.getMixer(microfono).getLine(info));
                l.open(f);
                l.start();
                return l;
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                ultimoError = e;   // este formato no: se prueba el siguiente
            }
        }
        throw new LineUnavailableException("no se pudo abrir el micrófono"
                + (ultimoError == null ? "" : " (" + ultimoError.getMessage() + ")"));
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
            TargetDataLine l = abrirMicrofono(microfono);
            return new Grabacion(l, l.getFormat(), alNivel);
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
        short[] mono = aMono(pcm, pcm.length, f.getChannels());

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

    /** Bytes de 16 bits (little endian) con uno o más canales → muestras mono (promedio). */
    private static short[] aMono(byte[] pcm, int largo, int canales) {
        int cuadros = largo / (2 * canales);
        short[] mono = new short[cuadros];
        for (int i = 0; i < cuadros; i++) {
            int suma = 0;
            for (int c = 0; c < canales; c++) {
                int k = (i * canales + c) * 2;
                suma += (short) ((pcm[k + 1] << 8) | (pcm[k] & 0xff));
            }
            mono[i] = (short) (suma / canales);
        }
        return mono;
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

    // ================================================================
    //  Audio en vivo (videollamada)
    // ================================================================

    /** 40 ms a 8000 Hz: cada trozo son 320 bytes en μ-law (unas 25 líneas VOZ por segundo). */
    private static final double SEGUNDOS_TROZO = 0.04;

    /**
     * El micrófono abierto durante la videollamada. Desde su propio hilo entrega trozos de 40 ms
     * ya pasados a 8000 Hz mono y comprimidos en μ-law, listos para VOZ|para|base64.
     */
    public static final class Microfono {
        private final TargetDataLine linea;
        private final Thread hilo;
        private volatile boolean activo = true;
        private volatile boolean silenciado = false;

        /** alNivel (opcional) recibe el volumen de 0 a 1 en cada trozo, también estando silenciado. */
        public static Microfono abrir(Mixer.Info microfono, Consumer<byte[]> alTrozo, DoubleConsumer alNivel)
                throws LineUnavailableException {
            return new Microfono(abrirMicrofono(microfono), alTrozo, alNivel);
        }

        private Microfono(TargetDataLine linea, Consumer<byte[]> alTrozo, DoubleConsumer alNivel) {
            this.linea = linea;
            this.hilo = new Thread(() -> leer(alTrozo, alNivel), "hilo-microfono-en-vivo");
            hilo.setDaemon(true);
            hilo.start();
        }

        /** Silenciado: se sigue leyendo (si no, el audio viejo se acumula) pero no se envía nada. */
        public void setSilenciado(boolean silenciado) {
            this.silenciado = silenciado;
        }

        public void cerrar() {
            activo = false;
            linea.stop();
            linea.close();
        }

        private void leer(Consumer<byte[]> alTrozo, DoubleConsumer alNivel) {
            AudioFormat f = linea.getFormat();
            int cuadros = Math.max(1, (int) Math.round(f.getFrameRate() * SEGUNDOS_TROZO));
            byte[] buffer = new byte[cuadros * f.getFrameSize()];
            while (activo) {
                int n = linea.read(buffer, 0, buffer.length);   // espera hasta tener los 40 ms
                if (n <= 0) {
                    continue;
                }
                if (alNivel != null) {
                    alNivel.accept(volumen(buffer, n));
                }
                if (!silenciado && activo) {
                    short[] ocho = remuestrear(aMono(buffer, n, f.getChannels()), f.getSampleRate(), TASA);
                    alTrozo.accept(aUlaw(ocho));
                }
            }
        }
    }

    /**
     * Los parlantes durante la videollamada: reproduce los trozos μ-law que van llegando.
     * Si la red se atrasa, descarta audio viejo en vez de acumular retraso.
     */
    public static final class Parlante {
        private static final double RETRASO_MAX = 0.35;         // segundos en cola antes de descartar
        private static final int TROZOS_ANTES_DE_EMPEZAR = 3;   // ~120 ms de colchón contra cortes
        private final SourceDataLine linea;
        private final AudioFormat formato;
        private int recibidos = 0;

        /** Abre los parlantes predeterminados con el primer formato que acepten. */
        public static Parlante abrir() throws LineUnavailableException {
            Exception ultimoError = null;
            for (float tasa : new float[] {8000f, 16000f, 44100f, 48000f}) {
                for (int canales = 1; canales <= 2; canales++) {
                    AudioFormat f = new AudioFormat(tasa, 16, canales, true, false);
                    try {
                        SourceDataLine l = AudioSystem.getSourceDataLine(f);
                        l.open(f, (int) (tasa * 0.6) * f.getFrameSize());
                        return new Parlante(l, f);
                    } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                        ultimoError = e;
                    }
                }
            }
            throw new LineUnavailableException("no se pudieron abrir los parlantes"
                    + (ultimoError == null ? "" : " (" + ultimoError.getMessage() + ")"));
        }

        private Parlante(SourceDataLine linea, AudioFormat formato) {
            this.linea = linea;
            this.formato = formato;
        }

        /** Se llama desde el hilo de red: nunca se queda esperando (si no hay espacio, descarta). */
        public synchronized void reproducir(byte[] ulaw) {
            if (!linea.isOpen() || ulaw.length == 0) {
                return;
            }
            byte[] datos = adaptar(deUlaw(ulaw));
            int bytesPorSegundo = (int) (formato.getFrameRate() * formato.getFrameSize());
            int enCola = linea.getBufferSize() - linea.available();
            if (enCola > RETRASO_MAX * bytesPorSegundo || linea.available() < datos.length) {
                return;   // vamos atrasados: mejor saltar este trozo que acumular retraso
            }
            linea.write(datos, 0, datos.length);
            if (++recibidos == TROZOS_ANTES_DE_EMPEZAR) {
                linea.start();
            }
        }

        public synchronized void cerrar() {
            linea.stop();
            linea.flush();
            linea.close();
        }

        /** 8000 Hz mono → la tasa y canales de la línea (interpolación lineal), en bytes little endian. */
        private byte[] adaptar(short[] ocho) {
            float tasa = formato.getSampleRate();
            int canales = formato.getChannels();
            int n = (int) Math.round(ocho.length * (double) tasa / TASA);
            byte[] salida = new byte[n * canales * 2];
            double paso = TASA / tasa;
            for (int j = 0; j < n; j++) {
                double pos = j * paso;
                int i = (int) pos;
                double t = pos - i;
                int a = ocho[Math.min(i, ocho.length - 1)];
                int b = ocho[Math.min(i + 1, ocho.length - 1)];
                short v = (short) Math.round(a + (b - a) * t);
                for (int c = 0; c < canales; c++) {
                    int k = (j * canales + c) * 2;
                    salida[k] = (byte) v;
                    salida[k + 1] = (byte) (v >> 8);
                }
            }
            return salida;
        }
    }

    // ---- μ-law (G.711): 16 bits → 8 bits y de vuelta. Es lo que usan las llamadas telefónicas. ----

    private static final int ULAW_SESGO = 0x84;
    private static final int ULAW_TOPE = 32635;

    static byte[] aUlaw(short[] muestras) {
        byte[] salida = new byte[muestras.length];
        for (int i = 0; i < muestras.length; i++) {
            int m = muestras[i];
            int signo = m < 0 ? 0x80 : 0;
            if (m < 0) {
                m = -m;
            }
            m = Math.min(m, ULAW_TOPE) + ULAW_SESGO;
            int exponente = 7;
            for (int mascara = 0x4000; (m & mascara) == 0 && exponente > 0; mascara >>= 1) {
                exponente--;
            }
            int mantisa = (m >> (exponente + 3)) & 0x0F;
            salida[i] = (byte) ~(signo | (exponente << 4) | mantisa);
        }
        return salida;
    }

    static short[] deUlaw(byte[] ulaw) {
        short[] salida = new short[ulaw.length];
        for (int i = 0; i < ulaw.length; i++) {
            int u = ~ulaw[i] & 0xFF;
            int exponente = (u >> 4) & 0x07;
            int mantisa = u & 0x0F;
            int m = (((mantisa << 3) + ULAW_SESGO) << exponente) - ULAW_SESGO;
            salida[i] = (short) ((u & 0x80) != 0 ? -m : m);
        }
        return salida;
    }

    // ================================================================
    //  Timbre de llamada
    // ================================================================

    /**
     * Suena mientras te llaman (entrante) o mientras esperas que contesten (saliente).
     * Si no hay parlantes, simplemente no suena.
     */
    public static final class Timbre {
        private static final float TASA_TIMBRE = 16000f;
        /** {frecuencia en Hz (0 = silencio), duración en segundos}. */
        private static final double[][] ENTRANTE = {{784, 0.18}, {0, 0.06}, {988, 0.28}, {0, 1.3}};
        /** Tono de "está sonando" como en los teléfonos del Perú: 425 Hz, 1 s sí, 4 s no. */
        private static final double[][] SALIENTE = {{425, 1.0}, {0, 4.0}};

        private volatile boolean activo = true;
        private volatile SourceDataLine linea;

        private Timbre() {}

        public static Timbre sonar(boolean entrante) {
            Timbre t = new Timbre();
            Thread hilo = new Thread(() -> t.tocar(entrante ? ENTRANTE : SALIENTE), "hilo-timbre");
            hilo.setDaemon(true);
            hilo.start();
            return t;
        }

        public void detener() {
            activo = false;
            SourceDataLine l = linea;
            if (l != null) {
                l.stop();
                l.flush();
            }
        }

        private void tocar(double[][] patron) {
            AudioFormat f = new AudioFormat(TASA_TIMBRE, 16, 1, true, false);
            try (SourceDataLine l = AudioSystem.getSourceDataLine(f)) {
                l.open(f);
                linea = l;
                l.start();
                while (activo) {
                    for (double[] tramo : patron) {
                        if (!activo) {
                            break;
                        }
                        escribirTono(l, tramo[0], tramo[1]);
                    }
                }
                l.stop();
                l.flush();
            } catch (Exception sinParlantes) {
                // no hay parlantes o están ocupados: la llamada sigue, solo que sin timbre
            }
        }

        /** Escribe el tono de a pocos (50 ms) para poder cortarlo al instante. */
        private void escribirTono(SourceDataLine l, double frecuencia, double segundos) {
            int total = (int) (TASA_TIMBRE * segundos);
            int rampa = (int) (TASA_TIMBRE * 0.01);   // 10 ms de subida y bajada: evita el "clic"
            byte[] trozo = new byte[(int) (TASA_TIMBRE * 0.05) * 2];
            int i = 0;
            while (i < total && activo) {
                int n = Math.min(trozo.length / 2, total - i);
                for (int k = 0; k < n; k++, i++) {
                    double v = 0;
                    if (frecuencia > 0) {
                        double envolvente = Math.min(1, Math.min(i, total - i) / (double) rampa);
                        v = Math.sin(2 * Math.PI * frecuencia * i / TASA_TIMBRE) * 0.22 * envolvente;
                    }
                    short m = (short) (v * 32767);
                    trozo[2 * k] = (byte) m;
                    trozo[2 * k + 1] = (byte) (m >> 8);
                }
                l.write(trozo, 0, n * 2);
            }
        }
    }
}
