package com.dji.sdk.sample.demo.missionoperator;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.VideoFeedView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;

/**
 * Visualização de transmissão ao vivo para a aplicação de inspeção de estruturas.
 * Oferece uma interface moderna com inicio automático e controles mínimos.
 */
public class StructureLiveStreamView extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "StructureLiveStream";
    private String liveShowUrl = "rtmp://your-streaming-server-url.com/live/drone";

    // Componentes da UI
    private VideoFeedView primaryVideoFeedView;
    private EditText urlInputEdit;
    private Button closeButton;
    private ToggleButton soundToggleButton;
    private TextView streamInfoText;
    private TextView streamStatusText;
    private TextView streamQualityText;

    // Componentes da transmissão
    private LiveStreamManager.OnLiveChangeListener listener;
    private LiveStreamManager.LiveStreamVideoSource currentVideoSource = LiveStreamManager.LiveStreamVideoSource.Primary;
    private static final String URL_KEY = "sp_structure_stream_url";

    // Timer para atualizar a duração da transmissão
    private Timer streamDurationTimer;
    private long streamStartTimeMillis = 0;
    private Handler uiHandler = new Handler();

    public interface OnCloseListener {
        void onClose();
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.closeListener = listener;
    }

    private OnCloseListener closeListener;

    public StructureLiveStreamView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        try {
            Log.d(TAG, "Initializing LiveStreamView");

            // Determine current orientation
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                inflate(context, R.layout.view_structure_live_stream_land, this);
                Log.d(TAG, "Inflated landscape live stream layout");
            } else {
                inflate(context, R.layout.view_structure_live_stream, this);
                Log.d(TAG, "Inflated portrait live stream layout");
            }

            // Find views - with error checking
            primaryVideoFeedView = findViewById(R.id.video_view_primary_video_feed);
            urlInputEdit = findViewById(R.id.edit_live_show_url_input);
            closeButton = findViewById(R.id.btn_close_live_stream);
            soundToggleButton = findViewById(R.id.toggle_sound);
            streamInfoText = findViewById(R.id.text_stream_info);
            streamStatusText = findViewById(R.id.text_stream_status);
            streamQualityText = findViewById(R.id.text_stream_quality);

            // Critical view check
            if (primaryVideoFeedView == null) {
                Log.e(TAG, "Video feed view not found!");
            }

            if (closeButton == null) {
                Log.e(TAG, "Close button not found!");
            }

            // Load saved URL
            String savedUrl = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE)
                    .getString(URL_KEY, liveShowUrl);
            liveShowUrl = savedUrl;

            if (urlInputEdit != null) {
                urlInputEdit.setText(liveShowUrl);
            }

            // Set up click listeners
            if (closeButton != null) {
                // Always ensure we clear any existing listeners
                closeButton.setOnClickListener(null);
                closeButton.setOnClickListener(this);
            }

            if (soundToggleButton != null) {
                soundToggleButton.setOnClickListener(this);
                soundToggleButton.setChecked(false);
            }

            // Set up video feed
            if (primaryVideoFeedView != null) {
                try {
                    primaryVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);
                    Log.d(TAG, "Video feed registered");
                } catch (Exception e) {
                    Log.e(TAG, "Error registering video feed", e);
                }
            }

            // Initialize stream manager
            initLiveStreamManager();

            Log.d(TAG, "LiveStreamView initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Fatal error during LiveStreamView initialization", e);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.d(TAG, "LiveStreamView - Configuration changed to: " +
                (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait"));

        try {
            // Save critical state
            boolean wasStreaming = false;
            boolean isMuted = false;
            String currentUrl = liveShowUrl;

            if (isLiveStreamManagerAvailable()) {
                wasStreaming = DJISDKManager.getInstance().getLiveStreamManager().isStreaming();
                if (soundToggleButton != null) {
                    isMuted = soundToggleButton.isChecked();
                }
            }

            Log.d(TAG, "Saved state - streaming: " + wasStreaming + ", muted: " + isMuted);

            // CRITICAL: DON'T stop the stream - just pause UI updates
            // Save stream timer state
            boolean wasTimerRunning = (streamDurationTimer != null);
            long startTimeMillis = streamStartTimeMillis;

            // Stop timer temporarily
            if (streamDurationTimer != null) {
                streamDurationTimer.cancel();
                streamDurationTimer = null;
            }

            // Recreate the view
            removeAllViews();
            init(getContext());

            // Restore URL and state
            liveShowUrl = currentUrl;
            if (urlInputEdit != null) {
                urlInputEdit.setText(currentUrl);
            }

            if (soundToggleButton != null) {
                soundToggleButton.setChecked(isMuted);
            }

            // Restore timer if it was running
            if (wasTimerRunning) {
                streamStartTimeMillis = startTimeMillis;
                startStreamDurationTimer();
            }

            // Update UI to reflect current status
            if (wasStreaming && streamStatusText != null) {
                streamStatusText.setText("Transmitindo");
                if (streamQualityText != null) {
                    updateStreamQuality();
                }
            }

            Log.d(TAG, "LiveStreamView configuration change completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during LiveStreamView configuration change", e);
        }
    }

    private void initLiveStreamManager() {
        // Inicializar listener para mudanças de status
        listener = status -> {
            String statusText;

            // Mapear o código de status para texto significativo
            // Os códigos específicos podem variar, então usamos uma abordagem mais genérica
            if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
                statusText = "Transmitindo";
                startStreamDurationTimer();
            } else if (status < 0) {
                statusText = "Falha na conexão (Código: " + status + ")";
                stopStreamDurationTimer();
            } else if (status == 0) {
                statusText = "Desconectado";
                stopStreamDurationTimer();
            } else {
                statusText = "Conectando... (Status: " + status + ")";
            }

            updateStatus(statusText);
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Registrar listener
        if (isLiveStreamManagerAvailable()) {
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);

            // Iniciar automaticamente quando a view é mostrada
            startLiveShow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Parar transmissão e limpar recursos
        stopLiveShow();
        stopStreamDurationTimer();

        // Remover listener
        if (isLiveStreamManagerAvailable()) {
            DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
        }

        super.onDetachedFromWindow();
    }

    /**
     * Inicia a transmissão ao vivo automaticamente
     */
    private void startLiveShow() {
        Log.d(TAG, "Iniciando transmissão ao vivo");

        if (!isLiveStreamManagerAvailable()) {
            ToastUtils.setResultToToast("Live Stream Manager não disponível");
            return;
        }

        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            Log.d(TAG, "Transmissão já iniciada");
            return;
        }

        // Iniciar transmissão em uma thread em segundo plano
        new Thread(() -> {
            try {
                // Configurar transmissão
                LiveStreamManager liveStreamManager = DJISDKManager.getInstance().getLiveStreamManager();
                liveStreamManager.setLiveUrl(liveShowUrl);

                // Configurar video encoding (importante para evitar erros)
                liveStreamManager.setVideoEncodingEnabled(true);

                // Salvar URL para uso futuro
                getContext().getSharedPreferences(getContext().getPackageName(), Context.MODE_PRIVATE)
                        .edit().putString(URL_KEY, liveShowUrl).apply();

                // Iniciar transmissão
                int result = liveStreamManager.startStream();
                liveStreamManager.setStartTime();

                // Atualizar qualidade da transmissão
                uiHandler.post(() -> updateStreamQuality());

                Log.d(TAG, "Resultado do início da transmissão: " + result);

                if (result != 0) {
                    ToastUtils.setResultToToast("Falha ao iniciar transmissão: " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar transmissão", e);
                ToastUtils.setResultToToast("Erro ao iniciar transmissão: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Para a transmissão ao vivo
     */
    private void stopLiveShow() {
        if (!isLiveStreamManagerAvailable()) {
            return;
        }

        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        }

        stopStreamDurationTimer();
    }

    /**
     * Alterna o áudio (mudo/som)
     */
    private void toggleSound(boolean mute) {
        if (!isLiveStreamManagerAvailable()) {
            return;
        }

        DJISDKManager.getInstance().getLiveStreamManager().setAudioMuted(mute);
        ToastUtils.setResultToToast(mute ? "Áudio desativado" : "Áudio ativado");
    }

    /**
     * Atualiza o texto de status da transmissão
     */
    private void updateStatus(final String status) {
        uiHandler.post(() -> {
            if (streamStatusText != null) {
                streamStatusText.setText(status);
            }
        });
    }

    /**
     * Atualiza as informações de qualidade da transmissão
     */
    private void updateStreamQuality() {
        if (!isLiveStreamManagerAvailable() || !DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            return;
        }

        LiveStreamManager liveStreamManager = DJISDKManager.getInstance().getLiveStreamManager();

        // Vamos apenas mostrar a informação de bitrate, que está disponível
        int bitRate = liveStreamManager.getLiveVideoBitRate();
        String qualityText = bitRate + " kbps";

        uiHandler.post(() -> {
            if (streamQualityText != null) {
                streamQualityText.setText(qualityText);
            }
        });
    }

    /**
     * Inicia o timer para acompanhar a duração da transmissão
     */
    private void startStreamDurationTimer() {
        if (streamDurationTimer != null) {
            stopStreamDurationTimer();
        }

        streamStartTimeMillis = System.currentTimeMillis();
        streamDurationTimer = new Timer();
        streamDurationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateStreamDuration();
            }
        }, 0, 1000);
    }

    /**
     * Para o timer de duração da transmissão
     */
    private void stopStreamDurationTimer() {
        if (streamDurationTimer != null) {
            streamDurationTimer.cancel();
            streamDurationTimer = null;
        }
    }

    /**
     * Atualiza o texto de duração da transmissão
     */
    private void updateStreamDuration() {
        if (streamStartTimeMillis <= 0) {
            return;
        }

        long durationMillis = System.currentTimeMillis() - streamStartTimeMillis;
        long seconds = durationMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        String durationText = String.format(Locale.getDefault(),
                "%02d:%02d:%02d",
                hours % 24, minutes % 60, seconds % 60);

        uiHandler.post(() -> {
            if (streamInfoText != null) {
                streamInfoText.setText(durationText);
            }
        });
    }

    /**
     * Verifica se o LiveStreamManager está disponível
     */
    private boolean isLiveStreamManagerAvailable() {
        BaseProduct product = DJISampleApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            ToastUtils.setResultToToast("Dispositivo não conectado");
            return false;
        }

        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            ToastUtils.setResultToToast("Live Stream Manager não disponível");
            return false;
        }

        return true;
    }

    @Override
    public void onClick(View v) {
        try {
            int id = v.getId();
            Log.d(TAG, "Button clicked with ID: " + id);

            if (id == R.id.btn_close_live_stream) {
                Log.d(TAG, "Close button clicked");

                // Stop live show
                stopLiveShow();

                // Explicitly notify listener
                if (closeListener != null) {
                    closeListener.onClose();
                    Log.d(TAG, "Close listener notified");
                } else {
                    Log.e(TAG, "Close listener is null!");

                    // Try fallback approach
                    ViewParent parent = getParent();
                    if (parent instanceof ViewGroup) {
                        Log.d(TAG, "Using fallback - setting parent visibility to GONE");
                        ((ViewGroup) parent).setVisibility(GONE);
                    } else if (parent != null) {
                        Log.d(TAG, "Parent is not a ViewGroup: " + parent.getClass().getName());
                    } else {
                        Log.d(TAG, "Parent is null!");
                    }
                }
            } else if (id == R.id.toggle_sound) {
                // Toggle sound
                if (soundToggleButton != null) {
                    toggleSound(soundToggleButton.isChecked());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onClick", e);
        }
    }

    /**
     * Método para limpeza e encerramento da live stream
     */
    public void cleanup() {
        Log.d(TAG, "cleanup called");
        try {
            // Stop streaming if active
            stopLiveShow();

            // Remove listeners
            if (isLiveStreamManagerAvailable()) {
                DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
            }

            Log.d(TAG, "LiveStreamView cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during LiveStreamView cleanup", e);
        }
    }
}