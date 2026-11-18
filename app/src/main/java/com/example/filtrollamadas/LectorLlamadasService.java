package com.example.filtrollamadas;

import android.app.Notification;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class LectorLlamadasService extends NotificationListenerService {

    private static final String TAG = "LectorLlamadas";
    private static LectorLlamadasService instanciaActiva;

    private TextToSpeech tts;
    private Handler handler;
    private Runnable tareaCapturaNombre;

    private static String mensajePendientePorAnunciar = null;
    private static boolean eraLlamadaEntrante = false;
    private static boolean yaContestoUsuario = false;
    private static boolean yaSeAnuncioEstaLlamada = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instanciaActiva = this;
        inicializarComponentes();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instanciaActiva = this;
        Log.d(TAG, "Listener conectado y listo");
        inicializarComponentes();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        requestRebind(new ComponentName(this, LectorLlamadasService.class));
    }

    private void inicializarComponentes() {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.forLanguageTag("es-CO"));
                    tts.setSpeechRate(1.20f);

                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    tts.setAudioAttributes(audioAttributes);
                }
            });
        }
    }

    public static void onTelefonoTimbrando() {
        eraLlamadaEntrante = true;
        yaContestoUsuario = false;
        yaSeAnuncioEstaLlamada = false;
        mensajePendientePorAnunciar = null;

        if (instanciaActiva != null) {
            instanciaActiva.programarLecturaNotificacion();
        }
    }

    public static void onTelefonoContestado() {
        if (!eraLlamadaEntrante || yaSeAnuncioEstaLlamada) return;

        yaContestoUsuario = true;

        // Si el nombre ya estaba listo antes de contestar, hablar ahora mismo
        if (mensajePendientePorAnunciar != null && instanciaActiva != null) {
            yaSeAnuncioEstaLlamada = true;
            String mensaje = mensajePendientePorAnunciar;
            mensajePendientePorAnunciar = null;
            instanciaActiva.hablarAlContestar(mensaje, 600);
        }
    }

    public static void onTelefonoColgado() {
        eraLlamadaEntrante = false;
        yaContestoUsuario = false;
        yaSeAnuncioEstaLlamada = false;
        mensajePendientePorAnunciar = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null || yaSeAnuncioEstaLlamada) return;

        Notification notificacion = sbn.getNotification();
        String paquete = sbn.getPackageName().toLowerCase();

        boolean esAppLlamadas = paquete.contains("callapp") ||
                paquete.contains("dialer") ||
                paquete.contains("telecom") ||
                Notification.CATEGORY_CALL.equals(notificacion.category);

        if (esAppLlamadas && eraLlamadaEntrante) {
            programarLecturaNotificacion();
        }
    }

    private void programarLecturaNotificacion() {
        if (handler == null || yaSeAnuncioEstaLlamada) return;
        if (tareaCapturaNombre != null) {
            handler.removeCallbacks(tareaCapturaNombre);
        }
        tareaCapturaNombre = this::guardarNombreDeNotificacionActiva;
        // Esperar 600 ms para capturar rápido el nombre
        handler.postDelayed(tareaCapturaNombre, 600);
    }

    private void guardarNombreDeNotificacionActiva() {
        if (yaSeAnuncioEstaLlamada) return;

        try {
            StatusBarNotification[] activas = getActiveNotifications();
            if (activas == null) return;

            String mejorTitulo = "";

            for (StatusBarNotification sbn : activas) {
                Notification notif = sbn.getNotification();
                String paquete = sbn.getPackageName().toLowerCase();

                boolean esLlamada = paquete.contains("callapp") ||
                        paquete.contains("dialer") ||
                        paquete.contains("telecom") ||
                        Notification.CATEGORY_CALL.equals(notif.category);

                if (esLlamada && notif.extras != null) {
                    String titulo = notif.extras.getString(Notification.EXTRA_TITLE, "").trim();
                    if (!titulo.isEmpty()) {
                        mejorTitulo = titulo;
                        if (!esSoloNumeroTelefonico(titulo)) {
                            break;
                        }
                    }
                }
            }

            if (!mejorTitulo.isEmpty()) {
                String mensajeConstruido;
                if (!esSoloNumeroTelefonico(mejorTitulo)) {
                    if (existeEnContactosPorNombre(mejorTitulo)) {
                        mensajeConstruido = "Llamada de " + mejorTitulo;
                    } else {
                        mensajeConstruido = "Desconocido: " + mejorTitulo;
                    }
                } else {
                    mensajeConstruido = "Llamada de número desconocido";
                }

                // Si el usuario YA contestó mientras leíamos, anunciar de inmediato
                if (yaContestoUsuario) {
                    yaSeAnuncioEstaLlamada = true;
                    hablarAlContestar(mensajeConstruido, 0);
                } else {
                    mensajePendientePorAnunciar = mensajeConstruido;
                    Log.i(TAG, "Nombre capturado y en espera: " + mensajePendientePorAnunciar);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error leyendo notificaciones: " + e.getMessage());
        }
    }

    private void hablarAlContestar(String mensaje, int retardoMs) {
        if (handler == null) return;
        handler.postDelayed(() -> {
            Log.i(TAG, "ANUNCIANDO AL CONTESTAR: " + mensaje);
            if (tts != null) {
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL);
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
                tts.speak(mensaje, TextToSpeech.QUEUE_FLUSH, params, "ID_LLAMADA");
            }
        }, retardoMs);
    }

    private boolean esSoloNumeroTelefonico(String texto) {
        String soloDigitos = texto.replaceAll("[^0-9]", "");
        return (soloDigitos.length() >= 7 && texto.replaceAll("[0-9+\\s()-]", "").isEmpty());
    }

    private boolean existeEnContactosPorNombre(String nombreBuscado) {
        ContentResolver resolver = getContentResolver();
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String[] proyeccion = new String[]{ContactsContract.Contacts.DISPLAY_NAME};
        String seleccion = ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?";
        String[] args = new String[]{"%" + nombreBuscado.trim() + "%"};

        try (Cursor cursor = resolver.query(uri, proyeccion, seleccion, args, null)) {
            return (cursor != null && cursor.moveToFirst());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        instanciaActiva = null;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
