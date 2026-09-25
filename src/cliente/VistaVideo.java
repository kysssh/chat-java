package cliente;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Recuadro que muestra video en vivo o una foto, con esquinas redondeadas.
 * Si no hay imagen, muestra el avatar de alguien y un texto ("Llamando…", "Cámara apagada").
 *
 * mostrar() se puede llamar desde cualquier hilo (el de la cámara o el de la red):
 * solo guarda el cuadro y pide repintar.
 */
class VistaVideo extends JComponent {

    private static final long serialVersionUID = 1L;

    private final int radio;
    /** true: la imagen llena el recuadro (recortando bordes). false: se ve entera (con franjas). */
    private final boolean llenar;

    private volatile BufferedImage imagen;
    private volatile boolean espejo = false;
    private volatile String avatar;          // nombre para el avatar cuando no hay imagen (o null)
    private volatile String texto = "";
    private volatile String etiqueta;        // nombre abajo a la izquierda (o null)
    private volatile boolean silenciado;     // micrófono tachado junto a la etiqueta

    private float destello = 0;              // flash blanco al tomar la foto (1 → 0)
    private Timer relojDestello;

    VistaVideo(int ancho, int alto, int radio, boolean llenar) {
        this.radio = radio;
        this.llenar = llenar;
        setPreferredSize(new Dimension(ancho, alto));
    }

    /** Muestra un cuadro. Desde cualquier hilo. */
    void mostrar(BufferedImage cuadro) {
        imagen = cuadro;
        repaint();
    }

    /** Quita la imagen y muestra el avatar de 'nombre' (o ninguno si es null) con un texto. */
    void sinImagen(String nombre, String texto) {
        this.imagen = null;
        this.avatar = nombre;
        this.texto = texto == null ? "" : texto;
        repaint();
    }

    boolean tieneImagen() {
        return imagen != null;
    }

    /** Voltea la imagen al dibujarla, como un espejo (la propia cámara se ve así). */
    void setEspejo(boolean espejo) {
        this.espejo = espejo;
        repaint();
    }

    void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
        repaint();
    }

    void setSilenciado(boolean silenciado) {
        this.silenciado = silenciado;
        repaint();
    }

    /** Flash blanco corto: confirma que la foto se tomó. Solo desde el hilo de la ventana. */
    void destellar() {
        destello = 1;
        if (relojDestello != null) {
            relojDestello.stop();
        }
        relojDestello = new Timer(16, e -> {
            destello = Math.max(0, destello - 0.08f);
            repaint();
            if (destello == 0) {
                ((Timer) e.getSource()).stop();
            }
        });
        relojDestello.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Estilo.suave(g);
        int w = getWidth();
        int h = getHeight();
        RoundRectangle2D forma = new RoundRectangle2D.Float(0, 0, w, h, radio, radio);
        g2.setColor(Estilo.FONDO);
        g2.fill(forma);
        g2.clip(forma);

        BufferedImage img = imagen;   // una sola lectura: otro hilo puede cambiarla mientras tanto
        if (img != null) {
            double escala = llenar
                    ? Math.max((double) w / img.getWidth(), (double) h / img.getHeight())
                    : Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
            int iw = (int) Math.round(img.getWidth() * escala);
            int ih = (int) Math.round(img.getHeight() * escala);
            int x = (w - iw) / 2;
            int y = (h - ih) / 2;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (espejo) {
                g2.drawImage(img, x + iw, y, -iw, ih, null);
            } else {
                g2.drawImage(img, x, y, iw, ih, null);
            }
        } else {
            pintarSinImagen(g2, w, h);
        }

        // Sin video el nombre ya se ve en el centro: la etiqueta solo hace falta para el micrófono tachado
        if (img != null || silenciado) {
            pintarEtiqueta(g2, h);
        }

        if (destello > 0) {
            g2.setColor(new Color(1f, 1f, 1f, Math.min(1f, destello)));
            g2.fillRect(0, 0, w, h);
        }
        g2.dispose();
    }

    private void pintarSinImagen(Graphics2D g2, int w, int h) {
        String nombre = avatar;
        String t = texto;
        int d = Math.max(28, Math.min(120, (int) (Math.min(w, h) * 0.32)));
        FontMetrics fm = g2.getFontMetrics(Estilo.fuentePara(t, Font.PLAIN, d > 60 ? 15 : 12));
        int altoTexto = t.isEmpty() ? 0 : fm.getHeight() + 12;
        int altoTotal = (nombre == null ? 0 : d) + altoTexto;
        int y = (h - altoTotal) / 2;
        if (nombre != null) {
            Estilo.pintarAvatar(g2, nombre, (w - d) / 2, y, d);
            y += d + 12;
        }
        if (!t.isEmpty()) {
            g2.setFont(fm.getFont());
            g2.setColor(Estilo.TEXTO_SUAVE);
            // si no entra en una línea, se recorta con "…"
            String visible = t;
            while (fm.stringWidth(visible) > w - 24 && visible.length() > 1) {
                visible = visible.substring(0, visible.length() - 2) + "…";
            }
            g2.drawString(visible, (w - fm.stringWidth(visible)) / 2, y + fm.getAscent());
        }
    }

    /** Pastilla oscura abajo a la izquierda con el nombre y, si corresponde, el micrófono tachado. */
    private void pintarEtiqueta(Graphics2D g2, int h) {
        String e = etiqueta;
        if (e == null && !silenciado) {
            return;
        }
        Font f = Estilo.fuentePara(e, Font.BOLD, 12);
        FontMetrics fm = g2.getFontMetrics(f);
        int icono = silenciado ? 16 : 0;
        int ancho = 16 + (e == null ? 0 : fm.stringWidth(e)) + (icono > 0 ? icono + (e == null ? 0 : 6) : 0);
        int alto = 24;
        int x = 10;
        int y = h - alto - 10;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fill(new RoundRectangle2D.Float(x, y, ancho, alto, alto, alto));
        int cx = x + 8;
        if (silenciado) {
            JLabel tinta = new JLabel();
            tinta.setForeground(Estilo.PELIGRO);
            new Estilo.Icono(Estilo.Icono.Tipo.MIC_APAGADO, icono).paintIcon(tinta, g2, cx, y + (alto - icono) / 2);
            cx += icono + 6;
        }
        if (e != null) {
            g2.setFont(f);
            g2.setColor(Color.WHITE);
            g2.drawString(e, cx, y + (alto - fm.getHeight()) / 2 + fm.getAscent());
        }
    }
}
