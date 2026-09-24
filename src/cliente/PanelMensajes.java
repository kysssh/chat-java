package cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * La conversación: burbujas de mensajes, imágenes y avisos del sistema, una debajo de otra.
 * Los mensajes propios van a la derecha (azul) y los de los demás a la izquierda, con avatar.
 * Solo se usa desde el hilo de la ventana.
 */
class PanelMensajes extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int AVATAR = 32;
    private static final int MARGEN = 18;
    private static final int MINIATURA_ANCHO = 260;
    private static final int MINIATURA_ALTO = 200;

    private final String miNombre;
    private final JScrollPane scroll;
    // Para agrupar: si el mismo autor manda varios mensajes seguidos, el nombre y avatar salen una vez
    private String ultimoGrupo = null;
    private int anchoAnterior = -1;

    PanelMensajes(String miNombre) {
        this.miNombre = miNombre;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Estilo.FONDO);
        setBorder(new EmptyBorder(8, 0, 14, 0));
        scroll = Estilo.scroll(this, Estilo.FONDO);

        // Al cambiar el ancho de la ventana, las burbujas se vuelven a acomodar
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() != anchoAnterior) {
                    anchoAnterior = getWidth();
                    invalidarTodo(PanelMensajes.this);
                    revalidate();
                }
            }
        });
    }

    JScrollPane getScroll() {
        return scroll;
    }

    // ================================================================
    //  Lo que la ventana puede agregar
    // ================================================================

    void agregarMensaje(String de, String texto) {
        boolean mio = de.equals(miNombre);
        Color fondo = mio ? Estilo.ACENTO : Estilo.BURBUJA_OTRO;
        agregarBurbuja(de, mio, crearTexto(texto, mio ? Color.WHITE : Estilo.TEXTO), fondo,
                null, "msg|" + de, mio);
    }

    void agregarPrivadoRecibido(String de, String texto) {
        agregarBurbuja(de, false, crearTexto(texto, Estilo.TEXTO), Estilo.BURBUJA_PRIV,
                "Privado", "priv|" + de, false);
    }

    void agregarPrivadoEnviado(String para, String texto) {
        agregarBurbuja(miNombre, true, crearTexto(texto, Estilo.TEXTO), Estilo.BURBUJA_PRIV,
                "Privado para " + para, "privA|" + para, true);
    }

    void agregarImagen(String de, BufferedImage img, Runnable alHacerClic) {
        boolean mio = de.equals(miNombre);
        Miniatura m = new Miniatura(img, alHacerClic);
        agregarBurbuja(de, mio, m, mio ? Estilo.ACENTO : Estilo.BURBUJA_OTRO, null, "msg|" + de, mio);
    }

    /** Nota de voz: botón para escucharla, barra de avance y duración. */
    void agregarAudio(String de, byte[] wav) {
        boolean mio = de.equals(miNombre);
        NotaVoz nota = new NotaVoz(wav, mio, this);
        agregarBurbuja(de, mio, nota, mio ? Estilo.ACENTO : Estilo.BURBUJA_OTRO, null, "msg|" + de, mio);
    }

    /** Aviso del sistema: una pastillita gris centrada. */
    void agregarAviso(String texto) {
        agregarPastilla(texto, Estilo.SUPERFICIE, Estilo.TEXTO_SUAVE);
    }

    /** Error: igual que el aviso, pero en rojo. */
    void agregarError(String texto) {
        agregarPastilla(texto, Estilo.FONDO_PELIGRO, Estilo.PELIGRO);
    }

    // ================================================================
    //  Armado de las filas
    // ================================================================

    private void agregarBurbuja(String autor, boolean mio, JComponent contenido, Color fondo,
                                String etiquetaPrivado, String grupo, boolean forzarScroll) {
        boolean continua = grupo.equals(ultimoGrupo);
        ultimoGrupo = grupo;
        boolean esPrivado = etiquetaPrivado != null;

        // Burbuja: contenido + hora abajo a la derecha
        Burbuja burbuja = new Burbuja(fondo, contenido instanceof Miniatura);
        burbuja.add(contenido, BorderLayout.CENTER);
        Color colorHora = fondo == Estilo.ACENTO ? Estilo.conAlfa(Color.WHITE, 170) : Estilo.TEXTO_SUAVE;
        JLabel hora = Estilo.etiqueta(LocalTime.now().format(HORA), Font.PLAIN, 11, colorHora);
        hora.setHorizontalAlignment(SwingConstants.RIGHT);
        if (contenido instanceof Miniatura) {
            hora.setBorder(new EmptyBorder(0, 0, 0, 4));
        }
        if (contenido instanceof NotaVoz) {
            burbuja.setBorder(new EmptyBorder(8, 8, 5, 12));
        }
        burbuja.add(hora, BorderLayout.SOUTH);

        // Columna: (nombre o "Privado") arriba + burbuja
        JPanel columna = transparente(new BorderLayout(0, 4));
        if (!continua) {
            String titulo;
            Color colorTitulo;
            if (esPrivado) {
                titulo = mio ? etiquetaPrivado : autor + "  ·  " + etiquetaPrivado;
                colorTitulo = Estilo.PRIVADO;
            } else {
                titulo = mio ? null : autor;
                colorTitulo = Estilo.colorDe(autor);
            }
            if (titulo != null) {
                JLabel lbl = Estilo.etiqueta(titulo, Font.BOLD, 12, colorTitulo);
                if (esPrivado) {
                    lbl.setIcon(new Estilo.Icono(Estilo.Icono.Tipo.CANDADO, 12));
                    lbl.setIconTextGap(5);
                }
                lbl.setHorizontalAlignment(mio ? SwingConstants.RIGHT : SwingConstants.LEFT);
                lbl.setBorder(new EmptyBorder(0, 4, 0, 4));
                columna.add(lbl, BorderLayout.NORTH);
            }
        }
        JPanel envoltura = transparente(new FlowLayout(mio ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        envoltura.add(burbuja);
        columna.add(envoltura, BorderLayout.CENTER);

        // Bloque: avatar (solo los demás) + columna
        JPanel bloque = transparente(new BorderLayout(10, 0));
        if (!mio) {
            JPanel lado = transparente(new BorderLayout());
            lado.setPreferredSize(new Dimension(AVATAR, AVATAR));
            if (!continua) {
                lado.add(new Estilo.Avatar(autor, AVATAR), BorderLayout.NORTH);
            }
            bloque.add(lado, BorderLayout.WEST);
        }
        bloque.add(columna, BorderLayout.CENTER);

        Fila fila = new Fila(new BorderLayout());
        fila.setBorder(new EmptyBorder(continua ? 3 : 12, MARGEN, 0, MARGEN));
        fila.add(bloque, mio ? BorderLayout.EAST : BorderLayout.WEST);
        agregarFila(fila, forzarScroll);
    }

    private void agregarPastilla(String texto, Color fondo, Color color) {
        ultimoGrupo = null;
        JLabel lbl = new JLabel(texto) {
            private static final long serialVersionUID = 1L;
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Estilo.suave(g);
                g2.setColor(fondo);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setFont(Estilo.fuentePara(texto, Font.PLAIN, 12));
        lbl.setForeground(color);
        lbl.setBorder(new EmptyBorder(4, 12, 4, 12));

        Fila fila = new Fila(new FlowLayout(FlowLayout.CENTER, 0, 0));
        fila.setBorder(new EmptyBorder(12, MARGEN, 0, MARGEN));
        fila.add(lbl);
        agregarFila(fila, false);
    }

    private void agregarFila(Fila fila, boolean forzarScroll) {
        JScrollBar barra = scroll.getVerticalScrollBar();
        // Solo bajar solo si ya estaba abajo (si estás leyendo algo viejo, no te mueve)
        boolean estabaAbajo = barra.getValue() + barra.getVisibleAmount() >= barra.getMaximum() - 60;
        fila.setAlignmentX(LEFT_ALIGNMENT);
        add(fila);
        revalidate();
        repaint();
        if (estabaAbajo || forzarScroll) {
            SwingUtilities.invokeLater(() -> {
                scroll.validate();
                barra.setValue(barra.getMaximum());
            });
        }
    }

    private static JPanel transparente(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private static void invalidarTodo(Container c) {
        for (Component hijo : c.getComponents()) {
            hijo.invalidate();
            if (hijo instanceof Container) {
                invalidarTodo((Container) hijo);
            }
        }
    }

    /** Ancho máximo del texto de una burbuja: unos tres cuartos del panel. */
    private int anchoMaximoTexto() {
        int ancho = getWidth() > 0 ? getWidth() : 600;
        return Math.max(160, (int) (ancho * 0.75) - AVATAR - MARGEN);
    }

    private TextoBurbuja crearTexto(String texto, Color color) {
        TextoBurbuja t = new TextoBurbuja(this);
        t.setText(texto);
        t.setFont(Estilo.fuentePara(texto, Font.PLAIN, 14));
        t.setForeground(color);
        return t;
    }

    // ================================================================
    //  Piezas
    // ================================================================

    /** Fila del chat: tan alta como su contenido (si no, BoxLayout la estira). */
    private static class Fila extends JPanel {
        private static final long serialVersionUID = 1L;

        Fila(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Fondo redondeado de un mensaje. */
    private static class Burbuja extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fondo;

        Burbuja(Color fondo, boolean conImagen) {
            super(new BorderLayout(0, 2));
            this.fondo = fondo;
            setOpaque(false);
            setBorder(conImagen ? new EmptyBorder(4, 4, 4, 4) : new EmptyBorder(7, 12, 5, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            g2.setColor(fondo);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g2.dispose();
        }
    }

    /**
     * Texto de la burbuja: se puede seleccionar y copiar, y se ajusta en varias líneas.
     * Su ancho es el del texto, sin pasar del máximo; el alto se calcula con ese ancho.
     */
    private static class TextoBurbuja extends JTextArea {
        private static final long serialVersionUID = 1L;
        private final PanelMensajes panel;

        TextoBurbuja(PanelMensajes panel) {
            this.panel = panel;
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(null);
            setFont(Estilo.fuente(Font.PLAIN, 14));
            setSelectionColor(Estilo.conAlfa(Color.WHITE, 80));
            setSelectedTextColor(Color.WHITE);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int natural = 0;
            for (String linea : getText().split("\n", -1)) {
                natural = Math.max(natural, fm.stringWidth(linea));
            }
            int ancho = Math.min(natural + 2, panel.anchoMaximoTexto());
            // Se mide con el mismo componente (misma fuente y escala de pantalla):
            // con ese ancho, JTextArea calcula cuántas líneas salen y cuánto alto ocupan
            if (getWidth() != ancho) {
                setSize(ancho, Short.MAX_VALUE);
            }
            return new Dimension(ancho, super.getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }

    /** Imagen pequeña con esquinas redondeadas; al hacer clic se abre en grande. */
    private static class Miniatura extends JComponent {
        private static final long serialVersionUID = 1L;
        private final BufferedImage img;
        private final int ancho;
        private final int alto;

        Miniatura(BufferedImage img, Runnable alHacerClic) {
            this.img = img;
            double escala = Math.min(1.0, Math.min(
                    (double) MINIATURA_ANCHO / img.getWidth(), (double) MINIATURA_ALTO / img.getHeight()));
            ancho = Math.max(1, (int) Math.round(img.getWidth() * escala));
            alto = Math.max(1, (int) Math.round(img.getHeight() * escala));
            setPreferredSize(new Dimension(ancho, alto));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Clic para ver en grande");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    alHacerClic.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.clip(new RoundRectangle2D.Float(0, 0, ancho, alto, 12, 12));
            g2.drawImage(img, 0, 0, ancho, alto, null);
            g2.dispose();
        }
    }

    /**
     * Nota de voz. Solo suena una a la vez: al darle play a otra, la anterior se detiene.
     */
    private static class NotaVoz extends JComponent {
        private static final long serialVersionUID = 1L;
        private static NotaVoz sonando;   // la que está sonando ahora (solo hilo de la ventana)
        private final byte[] wav;
        private final double segundos;
        private final boolean mio;
        private final PanelMensajes panel;
        private final Timer avance;
        private javax.sound.sampled.Clip clip;
        private double progreso = 0;   // 0 a 1

        NotaVoz(byte[] wav, boolean mio, PanelMensajes panel) {
            this.wav = wav;
            this.segundos = Audio.duracion(wav);
            this.mio = mio;
            this.panel = panel;
            setPreferredSize(new Dimension(230, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Nota de voz: clic para escuchar");
            // Mientras suena, la barra avanza
            avance = new Timer(50, e -> actualizar());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (clip != null) {
                        detener();
                    } else {
                        reproducir();
                    }
                }
            });
        }

        private void reproducir() {
            if (sonando != null) {
                sonando.detener();
            }
            try {
                clip = Audio.reproducir(wav);
                sonando = this;
                avance.start();
            } catch (Exception ex) {
                clip = null;
                panel.agregarError("No se pudo reproducir la nota de voz (¿hay parlantes o audífonos?).");
            }
            repaint();
        }

        private void detener() {
            if (clip != null) {
                clip.stop();   // al parar, Audio cierra el clip
                clip = null;
            }
            avance.stop();
            progreso = 0;
            if (sonando == this) {
                sonando = null;
            }
            repaint();
        }

        private void actualizar() {
            if (clip == null) {
                return;
            }
            long total = clip.getMicrosecondLength();
            if (!clip.isOpen() || (!clip.isRunning() && clip.getMicrosecondPosition() >= total)) {
                detener();   // terminó
                return;
            }
            progreso = total > 0 ? (double) clip.getMicrosecondPosition() / total : 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            int alto = getHeight();
            int d = 32;
            int y = (alto - d) / 2;

            // Botón redondo ▶ / ■
            g2.setColor(mio ? Color.WHITE : Estilo.ACENTO);
            g2.fillOval(0, y, d, d);
            JLabel tinta = new JLabel();
            tinta.setForeground(mio ? Estilo.ACENTO : Color.WHITE);
            new Estilo.Icono(clip != null ? Estilo.Icono.Tipo.DETENER : Estilo.Icono.Tipo.PLAY, 16)
                    .paintIcon(tinta, g2, (d - 16) / 2 + (clip != null ? 0 : 1), y + (d - 16) / 2);

            // Duración a la derecha
            double restante = clip != null ? segundos * (1 - progreso) : segundos;
            String tiempo = formatoTiempo(restante);
            g2.setFont(Estilo.fuente(Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int anchoTiempo = fm.stringWidth("0:00");
            Color suave = mio ? Estilo.conAlfa(Color.WHITE, 200) : Estilo.TEXTO_SUAVE;
            g2.setColor(suave);
            g2.drawString(tiempo, getWidth() - anchoTiempo - 2, (alto - fm.getHeight()) / 2 + fm.getAscent());

            // Barra de avance
            int x0 = d + 12;
            int ancho = getWidth() - x0 - anchoTiempo - 14;
            int grosor = 4;
            int yb = (alto - grosor) / 2;
            g2.setColor(mio ? Estilo.conAlfa(Color.WHITE, 90) : Estilo.SUPERFICIE_2);
            g2.fill(new RoundRectangle2D.Float(x0, yb, ancho, grosor, grosor, grosor));
            int lleno = (int) (ancho * progreso);
            g2.setColor(mio ? Color.WHITE : Estilo.ACENTO);
            if (lleno > 0) {
                g2.fill(new RoundRectangle2D.Float(x0, yb, lleno, grosor, grosor, grosor));
            }
            g2.fillOval(x0 + lleno - 5, alto / 2 - 5, 10, 10);
            g2.dispose();
        }
    }

    /** 75.4 → "1:15" */
    static String formatoTiempo(double segundos) {
        int s = (int) Math.ceil(Math.max(0, segundos));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ================================================================
    //  Scrollable: el panel ocupa todo el ancho visible y solo crece hacia abajo
    // ================================================================

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
