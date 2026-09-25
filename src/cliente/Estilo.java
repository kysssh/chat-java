package cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Colores, fuentes y componentes con estilo propio del cliente (tema oscuro).
 * Los componentes se pintan a mano para verse igual en Windows, Linux y Mac.
 */
final class Estilo {

    private Estilo() {}

    // ---- Colores ----
    static final Color FONDO          = new Color(0x14161B);
    static final Color PANEL          = new Color(0x1B1E25);
    static final Color SUPERFICIE     = new Color(0x252932);
    static final Color SUPERFICIE_2   = new Color(0x2E333E);
    static final Color BORDE          = new Color(0x2A2F39);
    static final Color TEXTO          = new Color(0xE7E9ED);
    static final Color TEXTO_SUAVE    = new Color(0x8B93A1);
    static final Color ACENTO         = new Color(0x4F7CFF);
    static final Color PRIVADO        = new Color(0xF29A45);
    static final Color BURBUJA_OTRO   = new Color(0x262A33);
    static final Color BURBUJA_PRIV   = new Color(0x3B2C1D);
    static final Color EXITO          = new Color(0x3CCB7F);
    static final Color ESPERA         = new Color(0xE8B339);
    static final Color PELIGRO        = new Color(0xF2615E);
    static final Color FONDO_PELIGRO  = new Color(0x3A2023);

    private static final Color[] COLORES_AVATAR = {
        new Color(0xE0605E), new Color(0xE58A3C), new Color(0xD4AE2C), new Color(0x4DB86F),
        new Color(0x2FAFA5), new Color(0x4C8DF0), new Color(0x8C6CEB), new Color(0xD65DA8),
    };

    // ---- Fuentes ----
    private static final String FAMILIA = elegirFamilia("Segoe UI", "Helvetica Neue", "Ubuntu", "Arial");

    static Font fuente(int estilo, int tam) {
        return new Font(FAMILIA, estilo, tam);
    }

    /**
     * Igual que fuente(), pero si el texto trae algo que esa fuente no tiene (emojis 🎉,
     * otros alfabetos) usa "Dialog", la fuente lógica de Java que busca en otras fuentes del sistema.
     */
    static Font fuentePara(String texto, int estilo, int tam) {
        Font f = fuente(estilo, tam);
        if (texto == null || f.canDisplayUpTo(texto) == -1) {
            return f;
        }
        return new Font(Font.DIALOG, estilo, tam);
    }

    private static String elegirFamilia(String... opciones) {
        Set<String> instaladas = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : opciones) {
            if (instaladas.contains(f)) {
                return f;
            }
        }
        return Font.SANS_SERIF;
    }

    // ---- Ayudas de pintado ----

    /** Copia del Graphics con bordes suaves (antialias) y el suavizado de texto del sistema. */
    static Graphics2D suave(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Object pistas = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (pistas instanceof Map) {
            g2.addRenderingHints((Map<?, ?>) pistas);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        return g2;
    }

    /** Mezcla el color con blanco: cantidad 0 = igual, 1 = blanco. */
    static Color aclarar(Color c, float cantidad) {
        return new Color(
                Math.round(c.getRed() + (255 - c.getRed()) * cantidad),
                Math.round(c.getGreen() + (255 - c.getGreen()) * cantidad),
                Math.round(c.getBlue() + (255 - c.getBlue()) * cantidad));
    }

    static Color conAlfa(Color c, int alfa) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alfa);
    }

    // ---- Avatares (círculo de color con la inicial) ----

    /** Cada nombre tiene siempre el mismo color. */
    static Color colorDe(String nombre) {
        return COLORES_AVATAR[Math.floorMod(nombre.hashCode(), COLORES_AVATAR.length)];
    }

    static String inicial(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return "?";
        }
        return new String(Character.toChars(nombre.codePointAt(0))).toUpperCase();
    }

    static void pintarAvatar(Graphics2D g, String nombre, int x, int y, int d) {
        g.setColor(colorDe(nombre));
        g.fill(new Ellipse2D.Float(x, y, d, d));
        g.setColor(Color.WHITE);
        String ini = inicial(nombre);
        g.setFont(fuentePara(ini, Font.BOLD, Math.max(10, Math.round(d * 0.44f))));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ini, x + (d - fm.stringWidth(ini)) / 2f,
                y + (d - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent());
    }

    /** Avatar como componente, para ponerlo en un panel. */
    static class Avatar extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String nombre;
        private final int diametro;

        Avatar(String nombre, int diametro) {
            this.nombre = nombre;
            this.diametro = diametro;
            Dimension d = new Dimension(diametro, diametro);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            pintarAvatar(g2, nombre, 0, 0, diametro);
            g2.dispose();
        }
    }

    /** Ícono de la ventana (burbuja de chat sobre círculo azul), en vez de la taza de Java. */
    static java.util.List<Image> iconosVentana() {
        java.util.List<Image> lista = new java.util.ArrayList<>();
        for (int tam : new int[] {16, 32, 64}) {
            BufferedImage img = new BufferedImage(tam, tam, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = suave(img.getGraphics());
            g.setColor(ACENTO);
            g.fill(new Ellipse2D.Float(0, 0, tam, tam));
            float esc = tam / 18f * 0.62f;
            g.translate(tam * 0.19, tam * 0.2);
            g.scale(esc, esc);
            g.setColor(Color.WHITE);
            g.fill(Icono.formaChat());
            g.dispose();
            lista.add(img);
        }
        return lista;
    }

    // ---- Íconos dibujados con líneas (así no dependen de emojis ni de archivos) ----

    static class Icono implements Icon {
        enum Tipo {
            ENVIAR, IMAGEN, CAMARA, CANDADO, CHAT, MICROFONO, DETENER, PLAY, AJUSTES,
            VIDEOCAMARA, VIDEO_APAGADO, MIC_APAGADO, COLGAR, ESPEJO
        }

        private final Tipo tipo;
        private final int tam;

        Icono(Tipo tipo, int tam) {
            this.tipo = tipo;
            this.tam = tam;
        }

        @Override public int getIconWidth()  { return tam; }
        @Override public int getIconHeight() { return tam; }

        /** Toma el color del texto del componente (o gris si está desactivado). */
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color tinta = c == null ? TEXTO : c.isEnabled() ? c.getForeground() : TEXTO_SUAVE;
            Graphics2D g2 = suave(g);
            g2.translate(x, y);
            g2.scale(tam / 18.0, tam / 18.0);   // se dibuja en una cuadrícula de 18x18
            g2.setColor(tinta);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (tipo) {
                case ENVIAR: {
                    Path2D p = new Path2D.Float();
                    p.moveTo(2.5, 2.5);
                    p.lineTo(16, 9);
                    p.lineTo(2.5, 15.5);
                    p.lineTo(4.8, 9);
                    p.closePath();
                    g2.fill(p);
                    break;
                }
                case IMAGEN: {
                    g2.draw(new RoundRectangle2D.Float(2, 3, 14, 12, 3, 3));
                    g2.fill(new Ellipse2D.Float(5, 5.5f, 3, 3));
                    Path2D p = new Path2D.Float();
                    p.moveTo(2.5, 13.5);
                    p.lineTo(7.5, 9);
                    p.lineTo(10.5, 11.5);
                    p.lineTo(12.5, 10);
                    p.lineTo(15.5, 12.5);
                    g2.draw(p);
                    break;
                }
                case CAMARA: {
                    g2.draw(new RoundRectangle2D.Float(1.8f, 5, 14.4f, 10.5f, 3, 3));
                    Path2D p = new Path2D.Float();
                    p.moveTo(6, 5);
                    p.lineTo(7.2, 2.8);
                    p.lineTo(10.8, 2.8);
                    p.lineTo(12, 5);
                    g2.draw(p);
                    g2.draw(new Ellipse2D.Float(6.2f, 7.2f, 5.6f, 5.6f));
                    break;
                }
                case CANDADO: {
                    g2.fill(new RoundRectangle2D.Float(3.5f, 8, 11, 8.5f, 3, 3));
                    g2.draw(new Arc2D.Float(6, 2.5f, 6, 7, 0, 180, Arc2D.OPEN));
                    g2.draw(new java.awt.geom.Line2D.Float(6, 6, 6, 8.5f));
                    g2.draw(new java.awt.geom.Line2D.Float(12, 6, 12, 8.5f));
                    break;
                }
                case CHAT:
                    g2.fill(formaChat());
                    break;
                case MICROFONO:
                case MIC_APAGADO: {
                    g2.draw(new RoundRectangle2D.Float(6.5f, 1.5f, 5, 9.5f, 5, 5));
                    g2.draw(new Arc2D.Float(3.5f, 4, 11, 9.5f, 180, 180, Arc2D.OPEN));
                    g2.draw(new java.awt.geom.Line2D.Float(9, 13.5f, 9, 16.5f));
                    g2.draw(new java.awt.geom.Line2D.Float(6, 16.5f, 12, 16.5f));
                    if (tipo == Tipo.MIC_APAGADO) {
                        g2.draw(new java.awt.geom.Line2D.Float(2.5f, 2, 16, 16.5f));
                    }
                    break;
                }
                case VIDEOCAMARA:
                case VIDEO_APAGADO: {
                    g2.draw(new RoundRectangle2D.Float(1.5f, 5, 10.5f, 8.5f, 3, 3));
                    Path2D lente = new Path2D.Float();
                    lente.moveTo(12, 8);
                    lente.lineTo(16.5, 5.5);
                    lente.lineTo(16.5, 12.5);
                    lente.lineTo(12, 10.5);
                    lente.closePath();
                    g2.draw(lente);
                    if (tipo == Tipo.VIDEO_APAGADO) {
                        g2.draw(new java.awt.geom.Line2D.Float(2, 2, 16.5f, 16.5f));
                    }
                    break;
                }
                case COLGAR: {
                    // auricular de teléfono acostado
                    g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Arc2D.Float(2.5f, 6.5f, 13, 10, 20, 140, Arc2D.OPEN));
                    g2.fill(new RoundRectangle2D.Float(1.2f, 9.2f, 5, 3.6f, 2, 2));
                    g2.fill(new RoundRectangle2D.Float(11.8f, 9.2f, 5, 3.6f, 2, 2));
                    break;
                }
                case ESPEJO: {
                    // dos triángulos reflejados a cada lado de una línea
                    Path2D izq = new Path2D.Float();
                    izq.moveTo(7, 4);
                    izq.lineTo(7, 14);
                    izq.lineTo(2, 14);
                    izq.closePath();
                    g2.draw(izq);
                    Path2D der = new Path2D.Float();
                    der.moveTo(11, 4);
                    der.lineTo(11, 14);
                    der.lineTo(16, 14);
                    der.closePath();
                    g2.fill(der);
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1, new float[] {1.5f, 2}, 0));
                    g2.draw(new java.awt.geom.Line2D.Float(9, 2, 9, 16));
                    break;
                }
                case DETENER:
                    g2.fill(new RoundRectangle2D.Float(4, 4, 10, 10, 3, 3));
                    break;
                case PLAY: {
                    Path2D p = new Path2D.Float();
                    p.moveTo(5.5, 3);
                    p.lineTo(15, 9);
                    p.lineTo(5.5, 15);
                    p.closePath();
                    g2.fill(p);
                    break;
                }
                case AJUSTES: {
                    // tres perillas deslizables
                    float[] ys = {4, 9, 14};
                    float[] xs = {12, 6, 10.5f};
                    for (int i = 0; i < 3; i++) {
                        g2.draw(new java.awt.geom.Line2D.Float(2, ys[i], 16, ys[i]));
                    }
                    for (int i = 0; i < 3; i++) {
                        g2.fill(new Ellipse2D.Float(xs[i] - 2.3f, ys[i] - 2.3f, 4.6f, 4.6f));
                    }
                    break;
                }
            }
            g2.dispose();
        }

        /** Globo de diálogo con colita, en la cuadrícula de 18x18. */
        static Shape formaChat() {
            Path2D p = new Path2D.Float();
            p.append(new RoundRectangle2D.Float(1, 2, 16, 11, 6, 6), false);
            p.moveTo(4.5, 12);
            p.lineTo(4, 16.5);
            p.lineTo(9, 12);
            p.closePath();
            return p;
        }
    }

    /** Circulito de color para el estado de conexión. */
    static Icon punto(Color color) {
        return new Icon() {
            @Override public int getIconWidth()  { return 8; }
            @Override public int getIconHeight() { return 8; }
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = suave(g);
                g2.setColor(color);
                g2.fill(new Ellipse2D.Float(x, y, 8, 8));
                g2.dispose();
            }
        };
    }

    // ---- Botón redondeado ----

    static class Boton extends JButton {
        private static final long serialVersionUID = 1L;
        private static final int PAD_H = 14;
        private static final int PAD_V = 8;
        private static final int ESPACIO = 7;
        private Color fondo;

        Boton(String texto, Icon icono, Color fondo, Color colorTexto) {
            super(texto, icono);
            this.fondo = fondo;
            setForeground(colorTexto);
            setFont(fuente(Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Cambia el color de fondo (por ejemplo, rojo cuando el micrófono está silenciado). */
        void setFondo(Color fondo) {
            this.fondo = fondo;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            String t = getText() == null ? "" : getText();
            Icon ic = getIcon();
            int ancho = fm.stringWidth(t);
            int alto = fm.getHeight();
            if (ic != null) {
                ancho += ic.getIconWidth() + (t.isEmpty() ? 0 : ESPACIO);
                alto = Math.max(alto, ic.getIconHeight());
            }
            // Solo ícono: botón cuadrado
            if (t.isEmpty()) {
                int lado = alto + 2 * PAD_V + 2;
                return new Dimension(lado, lado);
            }
            return new Dimension(ancho + 2 * PAD_H, alto + 2 * PAD_V + 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            ButtonModel m = getModel();
            Color c;
            if (!isEnabled()) {
                c = SUPERFICIE;
            } else if (m.isPressed()) {
                c = fondo.darker();
            } else if (m.isRollover()) {
                c = aclarar(fondo, 0.12f);
            } else {
                c = fondo;
            }
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

            // Ícono + texto centrados
            FontMetrics fm = g2.getFontMetrics(getFont());
            String t = getText() == null ? "" : getText();
            Icon ic = getIcon();
            int anchoTotal = fm.stringWidth(t)
                    + (ic == null ? 0 : ic.getIconWidth() + (t.isEmpty() ? 0 : ESPACIO));
            int x = (getWidth() - anchoTotal) / 2;
            if (ic != null) {
                ic.paintIcon(this, g2, x, (getHeight() - ic.getIconHeight()) / 2);
                x += ic.getIconWidth() + ESPACIO;
            }
            if (!t.isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(isEnabled() ? getForeground() : TEXTO_SUAVE);
                g2.drawString(t, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    // ---- Campo de texto redondeado con texto de ayuda ----

    static class Campo extends JTextField {
        private static final long serialVersionUID = 1L;
        private String ayuda = "";

        Campo(String ayuda) {
            this.ayuda = ayuda;
            setOpaque(false);
            setFont(fuente(Font.PLAIN, 14));
            setBackground(SUPERFICIE);
            setForeground(TEXTO);
            setCaretColor(TEXTO);
            setSelectionColor(ACENTO);
            setSelectedTextColor(Color.WHITE);
            setDisabledTextColor(TEXTO_SUAVE);
            setBorder(new EmptyBorder(9, 14, 9, 14));
            // Si se escribe un emoji, cambiar a una fuente que lo tenga
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { ajustarFuente(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { ajustarFuente(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            // repintar el borde al entrar/salir del campo
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e)   { repaint(); }
            });
        }

        private void ajustarFuente() {
            Font nueva = fuentePara(getText(), Font.PLAIN, 14);
            if (!nueva.getFamily().equals(getFont().getFamily())) {
                SwingUtilities.invokeLater(() -> setFont(nueva));   // no se puede cambiar dentro del aviso
            }
        }

        void setAyuda(String ayuda) {
            this.ayuda = ayuda;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(getBackground());
            g2.fill(forma);
            g2.setColor(isFocusOwner() ? ACENTO : BORDE);
            g2.draw(forma);
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && ayuda != null && !ayuda.isEmpty()) {
                Graphics2D g3 = suave(g);
                g3.setColor(TEXTO_SUAVE);
                g3.setFont(getFont());
                Insets in = getInsets();
                FontMetrics fm = g3.getFontMetrics();
                g3.drawString(ayuda, in.left + 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g3.dispose();
            }
        }
    }

    // ---- Barra de desplazamiento fina ----

    static class ScrollFino extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = SUPERFICIE_2;
        }

        @Override protected JButton createDecreaseButton(int orientacion) { return sinBoton(); }
        @Override protected JButton createIncreaseButton(int orientacion) { return sinBoton(); }

        private static JButton sinBoton() {
            JButton b = new JButton();
            Dimension cero = new Dimension(0, 0);
            b.setPreferredSize(cero);
            b.setMinimumSize(cero);
            b.setMaximumSize(cero);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // sin carril: solo se ve la barrita
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = suave(g);
            g2.setColor(isThumbRollover() ? aclarar(thumbColor, 0.15f) : thumbColor);
            int ancho = 6;
            g2.fill(new RoundRectangle2D.Float(r.x + (r.width - ancho) / 2f, r.y + 2,
                    ancho, r.height - 4, ancho, ancho));
            g2.dispose();
        }
    }

    /** JScrollPane sin bordes, con barra fina y solo desplazamiento vertical. */
    static JScrollPane scroll(Component vista, Color fondo) {
        JScrollPane sp = new JScrollPane(vista,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        sp.getViewport().setBackground(fondo);
        sp.setBackground(fondo);
        JScrollBar barra = sp.getVerticalScrollBar();
        barra.setUI(new ScrollFino());
        barra.setOpaque(false);
        barra.setBackground(fondo);
        barra.setPreferredSize(new Dimension(10, 0));
        barra.setUnitIncrement(16);
        return sp;
    }

    // ---- Lista desplegable oscura ----

    /** JComboBox con el tema oscuro (el del sistema en Windows no deja cambiarle los colores). */
    static <T> JComboBox<T> combo() {
        JComboBox<T> c = new JComboBox<>();
        c.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton b = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.SOUTH,
                        SUPERFICIE, SUPERFICIE, TEXTO_SUAVE, SUPERFICIE);
                b.setBorder(new EmptyBorder(0, 4, 0, 8));
                return b;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean foco) {
                g.setColor(SUPERFICIE);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
        c.setBackground(SUPERFICIE);
        c.setForeground(TEXTO);
        c.setFont(fuente(Font.PLAIN, 13));
        c.setBorder(BorderFactory.createLineBorder(BORDE));
        c.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int i,
                                                          boolean elegido, boolean foco) {
                super.getListCellRendererComponent(lista, valor, i, elegido, false);
                setBorder(new EmptyBorder(7, 10, 7, 10));
                setFont(fuentePara(String.valueOf(valor), Font.PLAIN, 13));
                setBackground(elegido && i >= 0 ? ACENTO : SUPERFICIE);
                setForeground(elegido && i >= 0 ? Color.WHITE : TEXTO);
                return this;
            }
        });
        return c;
    }

    // ---- Medidor de volumen ----

    /** Barra que muestra el volumen del micrófono (0 a 1). Baja suave para que se lea bien. */
    static class Medidor extends JComponent {
        private static final long serialVersionUID = 1L;
        private double nivel = 0;

        Medidor(int ancho, int alto) {
            setPreferredSize(new Dimension(ancho, alto));
        }

        void setNivel(double nuevo) {
            // Sube al instante y baja de a poco
            nivel = Math.max(nuevo, nivel * 0.8);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            int alto = getHeight();
            g2.setColor(SUPERFICIE_2);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), alto, alto, alto));
            // raíz cuadrada: la voz normal queda a media barra y no casi vacía
            double visible = Math.sqrt(Math.max(0, Math.min(1, nivel)));
            int lleno = (int) Math.round(getWidth() * visible);
            if (lleno > 0) {
                g2.setColor(visible > 0.92 ? PELIGRO : EXITO);
                g2.fill(new RoundRectangle2D.Float(0, 0, Math.max(lleno, alto), alto, alto, alto));
            }
            g2.dispose();
        }
    }

    /** Etiqueta con fuente y color en una línea. */
    static JLabel etiqueta(String texto, int estilo, int tam, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(fuentePara(texto, estilo, tam));
        l.setForeground(color);
        return l;
    }
}
