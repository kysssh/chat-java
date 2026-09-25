package cliente;

import comun.Protocolo;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * La videollamada con otra persona del chat (del mismo servidor, como los privados).
 *
 * Una sola ventana hace todo el ciclo:
 *   saliente:  LLAMANDO  → (el otro contesta) → EN_CURSO → TERMINADA
 *   entrante:  ENTRANTE  → (contestas)        → EN_CURSO → TERMINADA
 *
 * Todo pasa por el servidor, como el resto del chat:
 *   - Video: la cámara manda unos 12 cuadros por segundo, cada uno un JPG chico (VIDEO|para|base64).
 *   - Audio: el micrófono manda trozos de 40 ms en μ-law (VOZ|para|base64) y los parlantes
 *     reproducen los del otro.
 *   - Avisos: LLAMADA|para|INVITAR, ACEPTAR, RECHAZAR, COLGAR… (ver Protocolo).
 */
class VentanaLlamada extends JFrame {

    private static final long serialVersionUID = 1L;

    enum Estado { LLAMANDO, ENTRANTE, EN_CURSO, TERMINADA }

    /** Lo que la ventana del chat necesita saber de la llamada. */
    interface Oyente {
        /** La llamada terminó. resumen: texto para dejar en el chat ("Videollamada con ana · 3:12"). */
        void alTerminarLlamada(VentanaLlamada llamada, String resumen);

        /** Se eligió otra cámara o micrófono desde la llamada (el chat lo recuerda para después). */
        void alCambiarDispositivos(String camara, Mixer.Info microfono);
    }

    private static final int CUADROS_POR_SEGUNDO = 12;
    private static final int VIDEO_ANCHO = 400;
    private static final int VIDEO_ALTO = 300;
    private static final float VIDEO_CALIDAD = 0.6f;
    private static final int SEGUNDOS_PARA_CONTESTAR = 40;
    /** Tiempo para que el chat del otro confirme que le suena (en la misma red tarda milisegundos). */
    private static final int SEGUNDOS_PARA_SONAR = 6;
    private static final int MS_ANTES_DE_CERRAR = 2000;

    private final ClienteRed red;
    private final String yo;
    private final String otro;
    private final Oyente oyente;
    private volatile Estado estado;

    // ---- Dispositivos (null = el predeterminado del sistema) ----
    private String camaraElegida;
    private Mixer.Info microfonoElegido;
    private Camara.EnVivo camara;
    private volatile Audio.Microfono microfono;
    private volatile Audio.Parlante parlante;
    private Audio.Timbre timbre;
    private volatile boolean camaraPrendida = true;
    private boolean microfonoActivo = true;
    private volatile boolean camaraDelOtroApagada = false;

    private long inicioMs;

    // ---- Interfaz ----
    private final VistaVideo remoto = new VistaVideo(760, 470, 18, true);
    private final VistaVideo propio = new VistaVideo(200, 150, 14, true);
    private final JLabel lblEstado = Estilo.etiqueta(" ", Font.PLAIN, 12, Estilo.TEXTO_SUAVE);
    private final JLabel lblAviso = Estilo.etiqueta(" ", Font.PLAIN, 12, Estilo.TEXTO_SUAVE);
    private final Estilo.Medidor medidor = new Estilo.Medidor(56, 5);
    private Estilo.Boton botonMicrofono;
    private Estilo.Boton botonCamara;
    private Estilo.Boton botonDispositivos;
    private Estilo.Boton botonColgar;
    private JPanel controles;                 // CardLayout: "llamada" | "entrante"
    private JPanel filaDispositivos;
    private final JComboBox<DialogoDispositivos.Opcion<String>> comboCamara = Estilo.combo();
    private final JComboBox<DialogoDispositivos.Opcion<Mixer.Info>> comboMicrofono = Estilo.combo();
    private boolean dispositivosCargados = false;
    private Timer reloj;
    private Timer esperaRespuesta;
    private Timer esperaSonando;              // si el otro no avisa SONANDO, quizás su chat es de antes
    private boolean leSono = false;           // llegó SONANDO: la invitación sí le llegó al otro

    // ================================================================
    //  Crear
    // ================================================================

    /** Llamar a 'otro': envía INVITAR y espera que conteste. */
    static VentanaLlamada llamar(ClienteRed red, String yo, String otro, String camara, Mixer.Info microfono,
                                 Oyente oyente) {
        VentanaLlamada v = new VentanaLlamada(red, yo, otro, camara, microfono, oyente);
        v.empezarSaliente();
        return v;
    }

    /** 'otro' nos está llamando: suena y muestra Contestar / Rechazar. */
    static VentanaLlamada recibir(ClienteRed red, String yo, String otro, String camara, Mixer.Info microfono,
                                  Oyente oyente) {
        VentanaLlamada v = new VentanaLlamada(red, yo, otro, camara, microfono, oyente);
        v.empezarEntrante();
        return v;
    }

    private VentanaLlamada(ClienteRed red, String yo, String otro, String camara, Mixer.Info microfono,
                           Oyente oyente) {
        super("Videollamada con " + otro);
        this.red = red;
        this.yo = yo;
        this.otro = otro;
        this.camaraElegida = camara;
        this.microfonoElegido = microfono;
        this.oyente = oyente;
        construirInterfaz();
    }

    String getOtro() {
        return otro;
    }

    boolean terminada() {
        return estado == Estado.TERMINADA;
    }

    // ================================================================
    //  Interfaz
    // ================================================================

    private void construirInterfaz() {
        setIconImages(Estilo.iconosVentana());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        // Que se pueda contestar aunque esté abierta otra ventana modal (el espejo de la foto, Dispositivos)
        setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                colgar();   // cerrar la ventana es colgar
            }
        });

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Estilo.FONDO);
        setContentPane(raiz);

        // ---- Arriba: con quién, estado y avisos ----
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setBackground(Estilo.PANEL);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Estilo.BORDE),
                new EmptyBorder(10, 16, 10, 16)));
        cabecera.add(new Estilo.Avatar(otro, 34), BorderLayout.WEST);
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 1));
        textos.setOpaque(false);
        textos.add(Estilo.etiqueta(otro, Font.BOLD, 15, Estilo.TEXTO));
        lblEstado.setIconTextGap(6);
        textos.add(lblEstado);
        cabecera.add(textos, BorderLayout.CENTER);
        lblAviso.setHorizontalAlignment(SwingConstants.RIGHT);
        cabecera.add(lblAviso, BorderLayout.EAST);
        raiz.add(cabecera, BorderLayout.NORTH);

        // ---- Centro: el video del otro y, encima, el propio ----
        propio.setEspejo(true);   // uno se ve como en un espejo (al otro le llega sin voltear)
        propio.setEtiqueta("Tú");
        remoto.setEtiqueta(otro);
        Escenario escenario = new Escenario();
        escenario.setBorder(new EmptyBorder(14, 14, 8, 14));
        raiz.add(escenario, BorderLayout.CENTER);

        // ---- Abajo: dispositivos (se despliega) y botones ----
        JPanel abajo = new JPanel(new BorderLayout(0, 0));
        abajo.setBackground(Estilo.PANEL);
        abajo.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Estilo.BORDE));
        filaDispositivos = crearFilaDispositivos();
        filaDispositivos.setVisible(false);
        abajo.add(filaDispositivos, BorderLayout.NORTH);
        controles = new JPanel(new CardLayout());
        controles.setOpaque(false);
        controles.add(crearControlesLlamada(), "llamada");
        controles.add(crearControlesEntrante(), "entrante");
        abajo.add(controles, BorderLayout.CENTER);
        raiz.add(abajo, BorderLayout.SOUTH);

        reloj = new Timer(1000, e -> actualizarReloj());

        setSize(860, 680);
        setMinimumSize(new Dimension(600, 480));
        setLocationByPlatform(true);
    }

    /** Micrófono, cámara, dispositivos y colgar. */
    private JComponent crearControlesLlamada() {
        botonMicrofono = new Estilo.Boton("Micrófono", new Estilo.Icono(Estilo.Icono.Tipo.MICROFONO, 18),
                Estilo.SUPERFICIE, Estilo.TEXTO);
        botonCamara = new Estilo.Boton("Cámara", new Estilo.Icono(Estilo.Icono.Tipo.VIDEOCAMARA, 18),
                Estilo.SUPERFICIE, Estilo.TEXTO);
        botonDispositivos = new Estilo.Boton("Dispositivos", new Estilo.Icono(Estilo.Icono.Tipo.AJUSTES, 16),
                Estilo.SUPERFICIE, Estilo.TEXTO);
        botonDispositivos.setToolTipText("Cambiar de cámara o de micrófono sin cortar la llamada");
        botonColgar = new Estilo.Boton("Colgar", new Estilo.Icono(Estilo.Icono.Tipo.COLGAR, 18),
                Estilo.PELIGRO, Color.WHITE);

        botonMicrofono.addActionListener(e -> alternarMicrofono());
        botonCamara.addActionListener(e -> alternarCamara());
        botonDispositivos.addActionListener(e -> alternarFilaDispositivos());
        botonColgar.addActionListener(e -> colgar());
        pintarBotonMicrofono();
        pintarBotonCamara();

        // El medidor debajo del botón del micrófono: se mueve si te está captando
        JPanel mic = new JPanel(new BorderLayout(0, 4));
        mic.setOpaque(false);
        mic.add(botonMicrofono, BorderLayout.CENTER);
        JPanel centrarMedidor = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centrarMedidor.setOpaque(false);
        centrarMedidor.add(medidor);
        mic.add(centrarMedidor, BorderLayout.SOUTH);

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        fila.setOpaque(false);
        fila.setBorder(new EmptyBorder(12, 12, 14, 12));
        fila.add(mic);
        fila.add(botonCamara);
        fila.add(botonDispositivos);
        fila.add(Box.createHorizontalStrut(18));
        fila.add(botonColgar);
        return fila;
    }

    /** Rechazar y contestar, mientras suena. */
    private JComponent crearControlesEntrante() {
        Estilo.Boton rechazar = new Estilo.Boton("Rechazar", new Estilo.Icono(Estilo.Icono.Tipo.COLGAR, 18),
                Estilo.PELIGRO, Color.WHITE);
        Estilo.Boton contestar = new Estilo.Boton("Contestar", new Estilo.Icono(Estilo.Icono.Tipo.VIDEOCAMARA, 18),
                Estilo.EXITO.darker(), Color.WHITE);
        rechazar.addActionListener(e -> colgar());
        contestar.addActionListener(e -> contestar());
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        fila.setOpaque(false);
        fila.setBorder(new EmptyBorder(14, 12, 16, 12));
        fila.add(rechazar);
        fila.add(contestar);
        return fila;
    }

    /** Las listas de cámaras y micrófonos para cambiar en plena llamada. */
    private JPanel crearFilaDispositivos() {
        JPanel fila = new JPanel(new GridBagLayout());
        fila.setOpaque(false);
        fila.setBorder(new EmptyBorder(12, 16, 0, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel lCam = Estilo.etiqueta("Cámara", Font.BOLD, 12, Estilo.TEXTO_SUAVE);
        lCam.setIcon(new Estilo.Icono(Estilo.Icono.Tipo.VIDEOCAMARA, 14));
        fila.add(lCam, c);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 18);
        fila.add(comboCamara, c);

        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 8);
        JLabel lMic = Estilo.etiqueta("Micrófono", Font.BOLD, 12, Estilo.TEXTO_SUAVE);
        lMic.setIcon(new Estilo.Icono(Estilo.Icono.Tipo.MICROFONO, 14));
        fila.add(lMic, c);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        fila.add(comboMicrofono, c);

        comboCamara.addActionListener(e -> {
            if (dispositivosCargados) {
                DialogoDispositivos.Opcion<String> o = comboCamara.getItemAt(comboCamara.getSelectedIndex());
                cambiarCamara(o == null ? null : o.valor);
            }
        });
        comboMicrofono.addActionListener(e -> {
            if (dispositivosCargados) {
                DialogoDispositivos.Opcion<Mixer.Info> o = comboMicrofono.getItemAt(comboMicrofono.getSelectedIndex());
                cambiarMicrofono(o == null ? null : o.valor);
            }
        });
        return fila;
    }

    /** El video del otro ocupa todo; el propio va encima, abajo a la derecha. */
    private class Escenario extends JPanel {
        private static final long serialVersionUID = 1L;

        Escenario() {
            super(null);
            setOpaque(false);
            add(propio);   // primero = se pinta encima
            add(remoto);
        }

        @Override
        public boolean isOptimizedDrawingEnabled() {
            return false;   // hay componentes encimados
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(760, 470);
        }

        @Override
        public void doLayout() {
            Insets in = getInsets();
            int w = getWidth() - in.left - in.right;
            int h = getHeight() - in.top - in.bottom;
            remoto.setBounds(in.left, in.top, w, h);
            int pw = Math.max(150, Math.min(260, w / 4));
            int ph = pw * 3 / 4;
            propio.setBounds(in.left + w - pw - 14, in.top + h - ph - 14, pw, ph);
        }
    }

    // ================================================================
    //  Ciclo de la llamada
    // ================================================================

    private void empezarSaliente() {
        estado = Estado.LLAMANDO;
        ponerEstado("Llamando…", Estilo.ESPERA);
        remoto.sinImagen(otro, "Llamando a " + otro + "…");
        botonColgar.setText("Cancelar");
        ((CardLayout) controles.getLayout()).show(controles, "llamada");
        if (camaraPrendida) {
            prenderCamara();   // mientras suena ya te ves, para acomodarte
        }
        timbre = Audio.Timbre.sonar(false);
        esperaRespuesta = unaVez(SEGUNDOS_PARA_CONTESTAR * 1000, () -> {
            if (estado == Estado.LLAMANDO) {
                red.enviarLlamada(otro, Protocolo.COLGAR);
                terminar(leSono ? otro + " no contestó"
                        : "La llamada no le llegó a " + otro + ": debe abrir la versión nueva del chat");
            }
        });
        // Un chat al día responde SONANDO al instante. Si no llega, avisamos que quizás el otro tiene
        // una versión anterior (o un cliente de consola). No se corta: las versiones que tienen
        // videollamada pero no SONANDO sí suenan, y cortarles sería peor.
        esperaSonando = unaVez(SEGUNDOS_PARA_SONAR * 1000, () -> {
            if (estado == Estado.LLAMANDO && !leSono) {
                remoto.sinImagen(otro, "Esperando que le suene a " + otro + "…");
                avisar("Si a " + otro + " no le suena, debe abrir la versión nueva del chat.", Estilo.ESPERA);
            }
        });
        setVisible(true);
        red.enviarLlamada(otro, Protocolo.INVITAR);
    }

    private void empezarEntrante() {
        estado = Estado.ENTRANTE;
        ponerEstado("Te está llamando", Estilo.ESPERA);
        remoto.sinImagen(otro, otro + " te está llamando");
        propio.setVisible(false);
        ((CardLayout) controles.getLayout()).show(controles, "entrante");
        timbre = Audio.Timbre.sonar(true);
        // Si nadie contesta, el que llama cuelga a los 40 s; esto es por si ese aviso no llega
        esperaRespuesta = unaVez((SEGUNDOS_PARA_CONTESTAR + 5) * 1000, () -> {
            if (estado == Estado.ENTRANTE) {
                red.enviarLlamada(otro, Protocolo.RECHAZAR);
                terminar("Llamada perdida de " + otro);
            }
        });
        setAlwaysOnTop(true);   // que se vea aunque el chat esté detrás de otras ventanas
        setVisible(true);
        toFront();
        red.enviarLlamada(otro, Protocolo.SONANDO);   // el que llama ve "Sonando…"
    }

    private void contestar() {
        if (estado != Estado.ENTRANTE) {
            return;
        }
        red.enviarLlamada(otro, Protocolo.ACEPTAR);
        enCurso();
    }

    /** Ya contestaron (o contestamos): prender todo. */
    private void enCurso() {
        estado = Estado.EN_CURSO;
        detenerEspera();
        setAlwaysOnTop(false);
        inicioMs = System.currentTimeMillis();
        reloj.start();
        actualizarReloj();
        botonColgar.setText("Colgar");
        ((CardLayout) controles.getLayout()).show(controles, "llamada");
        propio.setVisible(true);
        remoto.sinImagen(otro, "Conectando video…");
        avisar("Consejo: usa audífonos para que no haya eco.", Estilo.TEXTO_SUAVE);

        try {
            parlante = Audio.Parlante.abrir();
        } catch (LineUnavailableException | RuntimeException e) {
            avisar("No se pudieron abrir los parlantes: no vas a escuchar a " + otro + ".", Estilo.PELIGRO);
        }
        abrirMicrofono();
        if (camaraPrendida && camara == null) {
            prenderCamara();
        }
        // Si apagaste algo mientras sonaba, el otro debe saberlo desde el inicio
        if (!camaraPrendida) {
            red.enviarLlamada(otro, Protocolo.CAMARA_OFF);
        }
        if (!microfonoActivo) {
            red.enviarLlamada(otro, Protocolo.MICROFONO_OFF);
        }
    }

    /** Botón Colgar / Cancelar / Rechazar, o cerrar la ventana. */
    void colgar() {
        switch (estado) {
            case LLAMANDO:
                red.enviarLlamada(otro, Protocolo.COLGAR);
                terminar("Cancelaste la llamada");
                break;
            case ENTRANTE:
                red.enviarLlamada(otro, Protocolo.RECHAZAR);
                terminar("Rechazaste la llamada");
                break;
            case EN_CURSO:
                red.enviarLlamada(otro, Protocolo.COLGAR);
                terminar("Llamada terminada");
                break;
            default:
                dispose();
        }
    }

    /**
     * El servidor respondió "comando desconocido" mientras llamábamos: es una versión anterior,
     * sin videollamadas. Devuelve true si la llamada lo tomó como suyo (y terminó).
     */
    boolean servidorSinVideollamadas() {
        if (estado != Estado.LLAMANDO) {
            return false;
        }
        terminar("El servidor no tiene videollamadas: ciérralo y vuelve a abrirlo con la versión nueva");
        return true;
    }

    /** El otro salió del chat en plena llamada. */
    void otroSeDesconecto() {
        terminar(otro + " se desconectó");
    }

    /** Se cayó la conexión con el servidor. */
    void sinConexion() {
        terminar("Se perdió la conexión con el servidor");
    }

    /** Apaga cámara, micrófono y parlantes, avisa al chat y cierra la ventana en 2 s. */
    void terminar(String motivo) {
        if (estado == Estado.TERMINADA) {
            return;
        }
        boolean huboLlamada = estado == Estado.EN_CURSO;
        String duracion = huboLlamada ? duracionTexto() : null;
        estado = Estado.TERMINADA;
        setAlwaysOnTop(false);
        detenerEspera();
        reloj.stop();
        apagarCamara();
        cerrarMicrofono();
        Audio.Parlante p = parlante;
        parlante = null;
        if (p != null) {
            p.cerrar();
        }

        ponerEstado(huboLlamada ? motivo + "  ·  " + duracion : motivo, Estilo.TEXTO_SUAVE);
        remoto.sinImagen(otro, motivo);
        propio.setVisible(false);
        avisar(" ", Estilo.TEXTO_SUAVE);
        for (Component c : ((JComponent) controles.getComponent(0)).getComponents()) {
            c.setEnabled(false);
        }
        for (Component c : ((JComponent) controles.getComponent(1)).getComponents()) {
            c.setEnabled(false);
        }
        botonMicrofono.setEnabled(false);
        filaDispositivos.setVisible(false);

        oyente.alTerminarLlamada(this, huboLlamada
                ? "Videollamada con " + otro + "  ·  " + duracion
                : motivo);
        unaVez(MS_ANTES_DE_CERRAR, this::dispose);
    }

    // ================================================================
    //  Lo que llega de la red (se llama desde el hilo de red)
    // ================================================================

    void alRecibirLlamada(String accion) {
        SwingUtilities.invokeLater(() -> {
            if (estado == Estado.TERMINADA) {
                return;
            }
            switch (accion) {
                case Protocolo.SONANDO:
                    if (estado == Estado.LLAMANDO) {
                        leSono = true;
                        pararTimer(esperaSonando);
                        esperaSonando = null;
                        ponerEstado("Sonando…", Estilo.ESPERA);
                        remoto.sinImagen(otro, "Le está sonando a " + otro + "…");
                        avisar(" ", Estilo.TEXTO_SUAVE);
                    }
                    break;
                case Protocolo.ACEPTAR:
                    if (estado == Estado.LLAMANDO) {
                        enCurso();
                    }
                    break;
                case Protocolo.RECHAZAR:
                    terminar(otro + " rechazó la llamada");
                    break;
                case Protocolo.OCUPADO:
                    terminar(otro + " está en otra llamada");
                    break;
                case Protocolo.NO_DISPONIBLE:
                    terminar(otro + " no está conectado");
                    break;
                case Protocolo.COLGAR:
                    terminar(estado == Estado.ENTRANTE ? "Llamada perdida de " + otro : otro + " colgó");
                    break;
                case Protocolo.INVITAR:
                    // Nos llamamos los dos al mismo tiempo: en vez de sonar ambos, se une la llamada
                    if (estado == Estado.LLAMANDO) {
                        red.enviarLlamada(otro, Protocolo.ACEPTAR);
                        enCurso();
                    }
                    break;
                case Protocolo.CAMARA_OFF:
                    camaraDelOtroApagada = true;
                    remoto.sinImagen(otro, "La cámara de " + otro + " está apagada");
                    break;
                case Protocolo.CAMARA_ON:
                    camaraDelOtroApagada = false;
                    remoto.sinImagen(otro, "Conectando video…");
                    break;
                case Protocolo.MICROFONO_OFF:
                    remoto.setSilenciado(true);
                    break;
                case Protocolo.MICROFONO_ON:
                    remoto.setSilenciado(false);
                    break;
                default:
                    // acción desconocida: se ignora
                    break;
            }
        });
    }

    void alRecibirVideo(BufferedImage cuadro) {
        if (estado == Estado.EN_CURSO && !camaraDelOtroApagada) {
            remoto.mostrar(cuadro);   // seguro desde cualquier hilo
        }
    }

    void alRecibirVoz(byte[] ulaw) {
        Audio.Parlante p = parlante;
        if (estado == Estado.EN_CURSO && p != null) {
            p.reproducir(ulaw);   // no bloquea: si vamos atrasados, descarta
        }
    }

    // ================================================================
    //  Cámara
    // ================================================================

    private void prenderCamara() {
        apagarCamara();
        propio.sinImagen(null, "Abriendo la cámara…");
        camara = Camara.EnVivo.iniciar(camaraElegida, CUADROS_POR_SEGUNDO, this::alCuadroPropio,
                mensaje -> SwingUtilities.invokeLater(() -> camaraFallo(mensaje)));
    }

    /** No se pudo abrir la cámara: queda como apagada (el otro ve tu avatar) y se puede elegir otra. */
    private void camaraFallo(String mensaje) {
        if (estado == Estado.TERMINADA || !camaraPrendida) {
            return;
        }
        camara = null;
        camaraPrendida = false;
        propio.sinImagen(yo, "Sin cámara");
        avisar(mensaje, Estilo.PELIGRO);
        if (estado == Estado.EN_CURSO) {
            red.enviarLlamada(otro, Protocolo.CAMARA_OFF);
        }
        pintarBotonCamara();
    }

    private void apagarCamara() {
        if (camara != null) {
            camara.detener();
            camara = null;
        }
    }

    /** Desde el hilo de la cámara: se muestra aquí y, si la llamada está en curso, se envía. */
    private void alCuadroPropio(BufferedImage cuadro) {
        if (!camaraPrendida) {
            return;
        }
        propio.mostrar(cuadro);
        if (estado == Estado.EN_CURSO) {
            red.enviarVideo(otro, Camara.aBase64(cuadro, VIDEO_ANCHO, VIDEO_ALTO, VIDEO_CALIDAD));
        }
    }

    private void alternarCamara() {
        camaraPrendida = !camaraPrendida;
        if (camaraPrendida) {
            prenderCamara();
        } else {
            apagarCamara();
            propio.sinImagen(yo, "Cámara apagada");
        }
        if (estado == Estado.EN_CURSO) {
            red.enviarLlamada(otro, camaraPrendida ? Protocolo.CAMARA_ON : Protocolo.CAMARA_OFF);
        }
        pintarBotonCamara();
    }

    private void cambiarCamara(String nombre) {
        if (nombre == null ? camaraElegida == null : nombre.equals(camaraElegida)) {
            return;
        }
        camaraElegida = nombre;
        if (camaraPrendida) {
            prenderCamara();
        } else {
            alternarCamara();   // elegir una cámara es querer verse: se prende
        }
        avisar(" ", Estilo.TEXTO_SUAVE);
        oyente.alCambiarDispositivos(camaraElegida, microfonoElegido);
    }

    private void pintarBotonCamara() {
        botonCamara.setIcon(new Estilo.Icono(camaraPrendida ? Estilo.Icono.Tipo.VIDEOCAMARA
                : Estilo.Icono.Tipo.VIDEO_APAGADO, 18));
        botonCamara.setText(camaraPrendida ? "Cámara" : "Cámara apagada");
        botonCamara.setFondo(camaraPrendida ? Estilo.SUPERFICIE : Estilo.FONDO_PELIGRO);
        botonCamara.setForeground(camaraPrendida ? Estilo.TEXTO : Estilo.PELIGRO);
        botonCamara.setToolTipText(camaraPrendida ? "Apagar tu cámara" : "Prender tu cámara");
        botonCamara.revalidate();
    }

    // ================================================================
    //  Micrófono
    // ================================================================

    private void abrirMicrofono() {
        cerrarMicrofono();
        try {
            microfono = Audio.Microfono.abrir(microfonoElegido,
                    trozo -> {
                        if (estado == Estado.EN_CURSO) {
                            red.enviarVoz(otro, trozo);
                        }
                    },
                    nivel -> SwingUtilities.invokeLater(() -> medidor.setNivel(microfonoActivo ? nivel : 0)));
            microfono.setSilenciado(!microfonoActivo);
            botonMicrofono.setEnabled(true);
        } catch (LineUnavailableException | RuntimeException e) {
            avisar("No se pudo abrir el micrófono: elige otro en Dispositivos.", Estilo.PELIGRO);
        }
    }

    private void cerrarMicrofono() {
        Audio.Microfono m = microfono;
        microfono = null;
        if (m != null) {
            m.cerrar();
        }
        medidor.setNivel(0);
    }

    private void alternarMicrofono() {
        microfonoActivo = !microfonoActivo;
        Audio.Microfono m = microfono;
        if (m != null) {
            m.setSilenciado(!microfonoActivo);
        }
        propio.setSilenciado(!microfonoActivo);
        if (estado == Estado.EN_CURSO) {
            red.enviarLlamada(otro, microfonoActivo ? Protocolo.MICROFONO_ON : Protocolo.MICROFONO_OFF);
        }
        pintarBotonMicrofono();
    }

    private void cambiarMicrofono(Mixer.Info nuevo) {
        String antes = microfonoElegido == null ? null : microfonoElegido.getName();
        String ahora = nuevo == null ? null : nuevo.getName();
        if (antes == null ? ahora == null : antes.equals(ahora)) {
            return;
        }
        microfonoElegido = nuevo;
        if (estado == Estado.EN_CURSO) {
            abrirMicrofono();
        }
        oyente.alCambiarDispositivos(camaraElegida, microfonoElegido);
    }

    private void pintarBotonMicrofono() {
        botonMicrofono.setIcon(new Estilo.Icono(microfonoActivo ? Estilo.Icono.Tipo.MICROFONO
                : Estilo.Icono.Tipo.MIC_APAGADO, 18));
        botonMicrofono.setText(microfonoActivo ? "Micrófono" : "Silenciado");
        botonMicrofono.setFondo(microfonoActivo ? Estilo.SUPERFICIE : Estilo.FONDO_PELIGRO);
        botonMicrofono.setForeground(microfonoActivo ? Estilo.TEXTO : Estilo.PELIGRO);
        botonMicrofono.setToolTipText(microfonoActivo ? "Silenciar tu micrófono" : "Volver a activar tu micrófono");
        botonMicrofono.revalidate();
    }

    // ================================================================
    //  Dispositivos en plena llamada
    // ================================================================

    private void alternarFilaDispositivos() {
        boolean mostrar = !filaDispositivos.isVisible();
        filaDispositivos.setVisible(mostrar);
        botonDispositivos.setFondo(mostrar ? Estilo.SUPERFICIE_2 : Estilo.SUPERFICIE);
        if (mostrar && !dispositivosCargados) {
            cargarDispositivos();
        }
        revalidate();
    }

    private void cargarDispositivos() {
        comboCamara.addItem(new DialogoDispositivos.Opcion<>("Buscando cámaras…", null));
        comboCamara.setEnabled(false);
        comboMicrofono.setEnabled(false);
        new Thread(() -> {
            List<String> camaras = Camara.camaras();
            List<Mixer.Info> microfonos = Audio.microfonos();
            SwingUtilities.invokeLater(() -> {
                comboCamara.removeAllItems();
                comboCamara.addItem(new DialogoDispositivos.Opcion<>("Predeterminada", null));
                for (String nombre : camaras) {
                    comboCamara.addItem(new DialogoDispositivos.Opcion<>(nombre, nombre));
                    if (nombre.equals(camaraElegida)) {
                        comboCamara.setSelectedIndex(comboCamara.getItemCount() - 1);
                    }
                }
                comboMicrofono.addItem(new DialogoDispositivos.Opcion<>("Predeterminado", null));
                for (Mixer.Info info : microfonos) {
                    comboMicrofono.addItem(new DialogoDispositivos.Opcion<>(info.getName(), info));
                    if (microfonoElegido != null && info.getName().equals(microfonoElegido.getName())) {
                        comboMicrofono.setSelectedIndex(comboMicrofono.getItemCount() - 1);
                    }
                }
                comboCamara.setEnabled(!camaras.isEmpty());
                comboMicrofono.setEnabled(true);
                dispositivosCargados = true;   // desde aquí, elegir en la lista cambia el dispositivo
            });
        }, "hilo-dispositivos-llamada").start();
    }

    // ================================================================
    //  Ayudas
    // ================================================================

    private void ponerEstado(String texto, Color color) {
        lblEstado.setText(texto);
        lblEstado.setIcon(Estilo.punto(color));
    }

    private void avisar(String texto, Color color) {
        lblAviso.setText(texto);
        lblAviso.setForeground(color);
    }

    private void actualizarReloj() {
        if (estado == Estado.EN_CURSO) {
            ponerEstado("En llamada  ·  " + duracionTexto(), Estilo.EXITO);
        }
    }

    private String duracionTexto() {
        return PanelMensajes.formatoTiempo((System.currentTimeMillis() - inicioMs) / 1000.0);
    }

    private void detenerEspera() {
        if (timbre != null) {
            timbre.detener();
            timbre = null;
        }
        pararTimer(esperaRespuesta);
        esperaRespuesta = null;
        pararTimer(esperaSonando);
        esperaSonando = null;
    }

    private static void pararTimer(Timer t) {
        if (t != null) {
            t.stop();
        }
    }

    /** Timer de Swing que se ejecuta una sola vez. */
    private static Timer unaVez(int ms, Runnable accion) {
        Timer t = new Timer(ms, e -> accion.run());
        t.setRepeats(false);
        t.start();
        return t;
    }
}
