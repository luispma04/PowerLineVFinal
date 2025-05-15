package com.dji.sdk.sample.demo.missionoperator;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionmanager.MissionBaseView;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ToastUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJICameraError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;

public class StructureInspectionMissionView extends MissionBaseView {

    private static final String TAG = "StructureInspection";

    // Request codes for file selection
    private static final int REQUEST_STRUCTURES_CSV = 1001;
    private static final int REQUEST_PHOTO_POSITIONS_CSV = 1002;

    // Mission parameters
    private static final double ONE_METER_OFFSET = 0.00000899322; // Approximately 1 meter in GPS coordinates
    private static final float DEFAULT_ALTITUDE = 30.0f; // Default altitude in meters
    private static final float DEFAULT_SPEED = 5.0f; // Default speed in m/s
    private static final float SAFE_DISTANCE = 5.0f; // Safe distance from structures in meters

    // UI components
    private Button btnLoadStructures;
    private Button btnLoadPhotoPositions;
    private Button btnConfirmPhoto;
    private Button btnAdjustPosition;
    private Button btnTakePhoto;
    private Button btnStartMission;
    private Button btnPause;
    private Button btnStopMission;
    private Button btnSimulatePhoto; // New button for simulator mode
    private TextView csvInfoText;
    private TextView statusText;
    private TextView noImageText;
    private ImageView imagePreview;

    // Connection status components
    private TextView connectionStatusText;
    private TextView modelTextView;
    private TextView batteryText;
    private TextView droneLocationText;

    // Mission components
    private WaypointMissionOperator waypointMissionOperator;
    private FlightController flightController;
    private Camera camera;
    private WaypointMission mission;
    private WaypointMissionOperatorListener listener;
    private MediaManager mediaManager;
    private FetchMediaTaskScheduler scheduler;
    private Handler handler = new Handler(Looper.getMainLooper());
    private File mediaStoragePath;

    // Mission data
    private List<InspectionPoint> inspectionPoints;
    private List<RelativePhotoPoint> photoPoints;
    private int currentInspectionIndex = 0;
    private int currentPhotoIndex = 0;
    private boolean isWaitingForReview = false;
    private boolean isInManualMode = false;
    private Bitmap lastPhotoTaken = null;
    private SettingsDefinitions.StorageLocation storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;

    // Flag for simulator mode
    private boolean isSimulatorMode = false;
    // Store initial home location for return to home functionality
    private double initialHomeLat;
    private double initialHomeLon;
    private float initialHomeAlt;
    private boolean isMissionPaused = false;

    // Track created waypoints
    private int totalWaypointCount = 0;
    private boolean missionPausedForPhotoReview = false;

    // Data classes for storing mission information
    private class InspectionPoint {
        double latitude;
        double longitude;
        float groundAltitude;
        float structureHeight;

        InspectionPoint(double lat, double lon, float alt, float height) {
            latitude = lat;
            longitude = lon;
            groundAltitude = alt;
            structureHeight = height;
        }
    }

    private class RelativePhotoPoint {
        float offsetX; // meters east
        float offsetY; // meters north
        float offsetZ; // meters up
        float gimbalPitch; // degrees

        RelativePhotoPoint(float x, float y, float z, float pitch) {
            offsetX = x;
            offsetY = y;
            offsetZ = z;
            gimbalPitch = pitch;
        }
    }

    public StructureInspectionMissionView(Context context) {
        this(context, false);
    }

    public StructureInspectionMissionView(Context context, boolean simulatorMode) {
        super(context);
        this.isSimulatorMode = simulatorMode;
        init(context);
        mediaStoragePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/structure_inspection");
        if (!mediaStoragePath.exists()) {
            mediaStoragePath.mkdirs();
        }
    }

    private void init(Context context) {
        // Initialize UI components
        inflate(context, R.layout.view_structure_inspection_mission, this);

        btnLoadStructures = findViewById(R.id.btn_load_structures);
        btnLoadPhotoPositions = findViewById(R.id.btn_load_photo_positions);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
        btnAdjustPosition = findViewById(R.id.btn_adjust_position);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnStartMission = findViewById(R.id.btn_start_mission);
        btnPause = findViewById(R.id.btn_pause);
        btnStopMission = findViewById(R.id.btn_stop_mission);
        btnSimulatePhoto = findViewById(R.id.btn_simulate_photo); // Find the new button
        csvInfoText = findViewById(R.id.text_csv_info);
        statusText = findViewById(R.id.text_status);
        noImageText = findViewById(R.id.text_no_image);
        imagePreview = findViewById(R.id.image_preview);

        // Initialize connection status components
        connectionStatusText = findViewById(R.id.text_connection_status);
        modelTextView = findViewById(R.id.text_product_model);
        batteryText = findViewById(R.id.text_battery_info);
        droneLocationText = findViewById(R.id.text_drone_location);

        // Initialize connection status if not found in XML layout
        if (connectionStatusText == null) {
            connectionStatusText = new TextView(context);
            connectionStatusText.setId(View.generateViewId());
            addView(connectionStatusText, 0);
        }

        if (modelTextView == null) {
            modelTextView = new TextView(context);
            modelTextView.setId(View.generateViewId());
            addView(modelTextView, 1);
        }

        if (batteryText == null) {
            batteryText = new TextView(context);
            batteryText.setId(View.generateViewId());
            addView(batteryText, 2);
        }

        if (droneLocationText == null) {
            droneLocationText = new TextView(context);
            droneLocationText.setId(View.generateViewId());
            addView(droneLocationText, 3);
        }

        // Show or hide the simulate photo button based on simulator mode
        if (btnSimulatePhoto != null) {
            btnSimulatePhoto.setVisibility(isSimulatorMode ? View.VISIBLE : View.GONE);
            btnSimulatePhoto.setOnClickListener(this);
        }

        // Set click listeners
        btnLoadStructures.setOnClickListener(this);
        btnLoadPhotoPositions.setOnClickListener(this);
        btnConfirmPhoto.setOnClickListener(this);
        btnAdjustPosition.setOnClickListener(this);
        btnTakePhoto.setOnClickListener(this);
        btnStartMission.setOnClickListener(this);
        btnPause.setOnClickListener(this);
        btnStopMission.setOnClickListener(this);

        // Initialize mission data
        inspectionPoints = new ArrayList<>();
        photoPoints = new ArrayList<>();

        // Initialize button states
        btnConfirmPhoto.setEnabled(false);
        btnAdjustPosition.setEnabled(false);
        btnTakePhoto.setEnabled(false);
        btnStartMission.setEnabled(false);
        btnPause.setEnabled(false);
        btnStopMission.setEnabled(false);
        if (btnSimulatePhoto != null) {
            btnSimulatePhoto.setEnabled(false);
        }

        // Initialize connection status
        updateConnectionStatus();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.btn_load_structures) {
            openFilePicker(REQUEST_STRUCTURES_CSV);
        } else if (id == R.id.btn_load_photo_positions) {
            openFilePicker(REQUEST_PHOTO_POSITIONS_CSV);
        } else if (id == R.id.btn_confirm_photo) {
            if (isWaitingForReview) {
                proceedToNextPhoto();
            }
        } else if (id == R.id.btn_adjust_position) {
            if (isWaitingForReview) {
                enterManualAdjustmentMode();
            }
        } else if (id == R.id.btn_take_photo) {
            if (isInManualMode) {
                takeManualPhoto();
            }
        } else if (id == R.id.btn_simulate_photo) {
            if (isSimulatorMode && isWaitingForReview) {
                simulatePhoto();
                updateStatus("Foto simulada gerada");
                enablePhotoReviewButtons();
            }
        } else if (id == R.id.btn_start_mission) {
            if (isMissionPaused || missionPausedForPhotoReview) {
                resumeMission();
            } else if (mission != null) {
                uploadAndStartMission();
            } else {
                createInspectionMission();
            }
        } else if (id == R.id.btn_pause) {
            pauseMission();
        } else if (id == R.id.btn_stop_mission) {
            emergencyAbortMission();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Initialize product connection status
        updateConnectionStatus();

        // Start initialization if needed
        initializeProductAndSDK();
    }

    private void initializeProductAndSDK() {
        // Get product instance and set up flight controller
        Aircraft aircraft = DJISampleApplication.getAircraftInstance();

        if (aircraft == null || !aircraft.isConnected()) {
            updateStatus("Aeronave não conectada");
            updateConnectionStatus();
            return;
        }

        flightController = aircraft.getFlightController();
        camera = aircraft.getCamera();

        // Initialize the MediaManager correctly
        if (camera != null) {
            mediaManager = camera.getMediaManager();

            if (mediaManager != null) {
                scheduler = mediaManager.getScheduler();

                // Get the current storage location
                camera.getStorageLocation(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.StorageLocation>() {
                    @Override
                    public void onSuccess(SettingsDefinitions.StorageLocation value) {
                        storageLocation = value;
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        updateStatus("Failed to get storage location: " + djiError.getDescription());
                    }
                });
            }
        }

        if (flightController != null) {
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    homeLatitude = flightControllerState.getHomeLocation().getLatitude();
                    homeLongitude = flightControllerState.getHomeLocation().getLongitude();
                    flightState = flightControllerState.getFlightMode();

                    // Store the initial home location when first connecting
                    if (initialHomeLat == 0 && initialHomeLon == 0) {
                        initialHomeLat = homeLatitude;
                        initialHomeLon = homeLongitude;
                        initialHomeAlt = flightControllerState.getAircraftLocation().getAltitude();
                    }

                    // Update drone location in UI
                    final double latitude = flightControllerState.getAircraftLocation().getLatitude();
                    final double longitude = flightControllerState.getAircraftLocation().getLongitude();
                    final float altitude = flightControllerState.getAircraftLocation().getAltitude();

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (droneLocationText != null) {
                                droneLocationText.setText("Location: Lat: " + latitude + ", Lon: " + longitude + ", Alt: " + altitude + "m");
                            }
                        }
                    });
                }
            });
        }

        waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        setUpListener();
    }

    // Method to be called when product connects (from MainActivity)
    public void onProductConnected() {
        post(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
                initializeProductAndSDK();
            }
        });
    }

    // Update connection status UI
    public void updateConnectionStatus() {
        post(new Runnable() {
            @Override
            public void run() {
                BaseProduct product = DJISampleApplication.getProductInstance();

                if (connectionStatusText != null) {
                    if (product != null && product.isConnected()) {
                        connectionStatusText.setText("Conectado");
                        connectionStatusText.setTextColor(Color.parseColor("#4CAF50")); // Green
                    } else {
                        connectionStatusText.setText("Desconectado");
                        connectionStatusText.setTextColor(Color.parseColor("#F44336")); // Red
                    }
                }

                if (modelTextView != null) {
                    if (product != null) {
                        modelTextView.setText("Modelo: " + (product.getModel() != null ? product.getModel().getDisplayName() : "Desconhecido"));
                    } else {
                        modelTextView.setText("Modelo: N/A");
                    }
                }

                if (batteryText != null) {
                    if (product instanceof Aircraft) {
                        Aircraft aircraft = (Aircraft) product;
                        if (aircraft.getBattery() != null) {
                            aircraft.getBattery().setStateCallback(batteryState -> {
                                post(() -> {
                                    if (batteryText != null && batteryState != null) {
                                        batteryText.setText("Bateria: " + batteryState.getChargeRemainingInPercent() + "%");
                                    }
                                });
                            });
                        } else {
                            batteryText.setText("Bateria: N/A");
                        }
                    } else {
                        batteryText.setText("Bateria: N/A");
                    }
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        tearDownListener();

        if (flightController != null) {
            flightController.setStateCallback(null);
        }

        super.onDetachedFromWindow();
    }

    private void openFilePicker(int requestCode) {
        try {
            if (getContext() instanceof FilePickerCallback) {
                ((FilePickerCallback) getContext()).openFilePicker(requestCode);
            } else {
                updateStatus("Erro: Activity não implementa FilePickerCallback");
            }
        } catch (Exception e) {
            updateStatus("Erro ao abrir seletor de arquivos: " + e.getMessage());
            Log.e(TAG, "Error opening file picker", e);
        }
    }

    public interface FilePickerCallback {
        void openFilePicker(int requestCode);
    }

    public void onFileSelected(int requestCode, Uri fileUri) {
        if (fileUri == null) {
            updateStatus("Nenhum arquivo selecionado");
            return;
        }

        try {
            // Check if it's a CSV file
            String fileName = getFileNameFromUri(fileUri);
            if (!fileName.toLowerCase().endsWith(".csv")) {
                updateStatus("Por favor, selecione apenas arquivos CSV");
                return;
            }

            // Process the appropriate file
            if (requestCode == REQUEST_STRUCTURES_CSV) {
                loadStructuresFromCSV(fileUri);
            } else if (requestCode == REQUEST_PHOTO_POSITIONS_CSV) {
                loadPhotoPositionsFromCSV(fileUri);
            }
        } catch (Exception e) {
            updateStatus("Erro ao processar arquivo: " + e.getMessage());
            Log.e(TAG, "Error processing file", e);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void loadStructuresFromCSV(Uri fileUri) {
        inspectionPoints.clear();

        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(fileUri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] values = line.split(",");
                if (values.length >= 4) {
                    double lat = Double.parseDouble(values[0]);
                    double lon = Double.parseDouble(values[1]);
                    float alt = Float.parseFloat(values[2]);
                    float height = Float.parseFloat(values[3]);

                    inspectionPoints.add(new InspectionPoint(lat, lon, alt, height));
                }
            }

            reader.close();

            post(new Runnable() {
                @Override
                public void run() {
                    csvInfoText.setText(inspectionPoints.size() + " estruturas carregadas");

                    if (!inspectionPoints.isEmpty() && !photoPoints.isEmpty()) {
                        btnStartMission.setEnabled(true);
                    }

                    updateStatus("Carregado " + inspectionPoints.size() + " estruturas");
                }
            });

        } catch (IOException e) {
            final String errorMsg = e.getMessage();
            updateStatus("Erro ao carregar arquivo de estruturas: " + errorMsg);
            Log.e(TAG, "Error reading structures CSV file", e);
        }
    }

    private void loadPhotoPositionsFromCSV(Uri fileUri) {
        photoPoints.clear();

        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(fileUri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] values = line.split(",");
                if (values.length >= 4) {
                    float x = Float.parseFloat(values[0]);
                    float y = Float.parseFloat(values[1]);
                    float z = Float.parseFloat(values[2]);
                    float pitch = Float.parseFloat(values[3]);

                    photoPoints.add(new RelativePhotoPoint(x, y, z, pitch));
                }
            }

            reader.close();

            post(new Runnable() {
                @Override
                public void run() {
                    String currentText = csvInfoText.getText().toString();
                    if (currentText.contains("estruturas carregadas")) {
                        csvInfoText.setText(currentText + ", " + photoPoints.size() + " posições de foto");
                    } else {
                        csvInfoText.setText(photoPoints.size() + " posições de foto carregadas");
                    }

                    if (!inspectionPoints.isEmpty() && !photoPoints.isEmpty()) {
                        btnStartMission.setEnabled(true);
                    }

                    updateStatus("Carregado " + photoPoints.size() + " posições de foto");
                }
            });

        } catch (IOException e) {
            final String errorMsg = e.getMessage();
            updateStatus("Erro ao carregar arquivo de posições de foto: " + errorMsg);
            Log.e(TAG, "Error reading photo positions CSV file", e);
        }
    }

    private void createInspectionMission() {
        if (inspectionPoints.isEmpty() || photoPoints.isEmpty()) {
            updateStatus("Nenhum ponto de inspeção ou foto carregado");
            return;
        }

        // Reset current indices
        currentInspectionIndex = 0;
        currentPhotoIndex = 0;
        isMissionPaused = false;
        missionPausedForPhotoReview = false;

        // Create complete mission with all inspection points and their photos
        createCompleteMission();
    }

    // Create a complete mission with all inspection points and photo positions
    private void createCompleteMission() {
        // Create a mission builder
        WaypointMission.Builder builder = new WaypointMission.Builder();

        // Set mission parameters
        builder.autoFlightSpeed(DEFAULT_SPEED);
        builder.maxFlightSpeed(DEFAULT_SPEED * 2);
        builder.setExitMissionOnRCSignalLostEnabled(false);
        builder.finishedAction(WaypointMissionFinishedAction.NO_ACTION);
        builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        builder.headingMode(WaypointMissionHeadingMode.AUTO);
        builder.setGimbalPitchRotationEnabled(true);

        totalWaypointCount = 0;

        // For each inspection point, add all its photo positions
        for (int i = 0; i < inspectionPoints.size(); i++) {
            InspectionPoint point = inspectionPoints.get(i);

            // Calculate safe altitude for the structure
            float safeAltitude = point.groundAltitude + point.structureHeight + SAFE_DISTANCE;

            // First waypoint: Go to the inspection point at safe altitude
            Waypoint initialWaypoint = new Waypoint(
                    point.latitude,
                    point.longitude,
                    safeAltitude
            );
            builder.addWaypoint(initialWaypoint);
            totalWaypointCount++;

            // Add all photo waypoints for this inspection point
            for (int j = 0; j < photoPoints.size(); j++) {
                RelativePhotoPoint photoPoint = photoPoints.get(j);

                // Calculate absolute coordinates from relative offsets
                double photoLatitude = point.latitude + (photoPoint.offsetY * ONE_METER_OFFSET);
                double photoLongitude = point.longitude + (photoPoint.offsetX * ONE_METER_OFFSET);
                float photoAltitude = point.groundAltitude + point.structureHeight + photoPoint.offsetZ;

                // Create the waypoint
                Waypoint photoWaypoint = new Waypoint(photoLatitude, photoLongitude, photoAltitude);

                // Add gimbal pitch action
                photoWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, Math.round(photoPoint.gimbalPitch)));

                // Add photo action
                photoWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));

                // Add waypoint to the mission builder
                builder.addWaypoint(photoWaypoint);
                totalWaypointCount++;
            }
        }

        // Build and load the mission
        mission = builder.build();

        if (waypointMissionOperator == null) {
            updateStatus("WaypointMissionOperator não está inicializado.");
            return;
        }

        DJIError error = waypointMissionOperator.loadMission(mission);
        final String errorMsg = (error != null) ? error.getDescription() : null;

        post(new Runnable() {
            @Override
            public void run() {
                if (errorMsg == null) {
                    updateStatus("Missão completa carregada com " + totalWaypointCount + " waypoints");
                    btnStartMission.setEnabled(true);
                } else {
                    updateStatus("Erro ao carregar missão: " + errorMsg);
                }
            }
        });
    }

    private void uploadAndStartMission() {
        if (waypointMissionOperator != null &&
                (WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState()) ||
                        WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState()))) {

            waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão enviada com sucesso");

                                waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(final DJIError djiError) {
                                        post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (djiError == null) {
                                                    updateStatus("Missão iniciada");
                                                    btnPause.setEnabled(true);
                                                    btnStopMission.setEnabled(true);
                                                    btnStartMission.setEnabled(false);
                                                    isMissionPaused = false;
                                                    missionPausedForPhotoReview = false;
                                                } else {
                                                    updateStatus("Falha ao iniciar missão: " + djiError.getDescription());
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                updateStatus("Falha ao enviar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        } else {
            updateStatus("Missão não está pronta para envio. Estado atual: " +
                    (waypointMissionOperator != null ? waypointMissionOperator.getCurrentState().getName() : "Operator not initialized"));
        }
    }

    private void pauseMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão pausada");
                                btnPause.setEnabled(false);
                                btnStartMission.setEnabled(true); // Use same button to resume
                                btnStartMission.setText("Continuar Missão");
                                isMissionPaused = true;
                            } else {
                                updateStatus("Falha ao pausar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private void resumeMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão retomada");
                                btnPause.setEnabled(true);
                                btnStartMission.setEnabled(false);
                                btnStartMission.setText("Iniciar Missão");
                                isMissionPaused = false;

                                // Reset photo review flags when resuming
                                if (missionPausedForPhotoReview) {
                                    missionPausedForPhotoReview = false;
                                    isWaitingForReview = false;
                                    btnConfirmPhoto.setEnabled(false);
                                    btnAdjustPosition.setEnabled(false);
                                    if (btnSimulatePhoto != null) {
                                        btnSimulatePhoto.setEnabled(false);
                                    }
                                }
                            } else {
                                updateStatus("Falha ao retomar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private void emergencyAbortMission() {
        updateStatus("Encerrar missão e regressar ao ponto de partida...");
        if (waypointMissionOperator != null) {
            waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        updateStatus("Missão parada com sucesso. Subindo à altitude segura...");
                        goToSafeAltitudeAndReturnHome();
                    } else {
                        updateStatus("Erro ao parar missão: " + djiError.getDescription());
                    }
                }
            });
        } else {
            goToSafeAltitudeAndReturnHome();
        }

        // Desativa os botões para evitar múltiplos cliques
        btnPause.setEnabled(false);
        btnStopMission.setEnabled(false);
        btnStartMission.setEnabled(true);
    }

    private void goToSafeAltitudeAndReturnHome() {
        if (flightController == null) {
            updateStatus("Controlador de voo indisponível");
            return;
        }

        final int ALTURA_SEGURA = 50; // metros
        flightController.setGoHomeHeightInMeters(ALTURA_SEGURA, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    updateStatus("Altitude de retorno definida para " + ALTURA_SEGURA + "m. Iniciando retorno...");
                    flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                updateStatus("Drone a regressar ao ponto de partida.");
                            } else {
                                updateStatus("Erro ao iniciar retorno: " + djiError.getDescription());
                            }
                        }
                    });
                } else {
                    updateStatus("Erro ao definir altitude de retorno: " + djiError.getDescription());
                }
            }
        });
    }

    private void createReturnToHomeMission() {
        // Create a simple mission to return to home
        WaypointMission.Builder builder = new WaypointMission.Builder();

        // Set mission parameters for return to home
        builder.autoFlightSpeed(DEFAULT_SPEED);
        builder.maxFlightSpeed(DEFAULT_SPEED * 2);
        builder.setExitMissionOnRCSignalLostEnabled(false);
        builder.finishedAction(WaypointMissionFinishedAction.GO_HOME);
        builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        builder.headingMode(WaypointMissionHeadingMode.AUTO);

        // Add home location as the destination waypoint
        // Use a safe altitude first to avoid obstacles
        float returnAltitude = Math.max(initialHomeAlt + 20, 30); // At least 30m or 20m above takeoff

        // First waypoint: Rise to safe altitude
        Waypoint currentLocationWaypoint = new Waypoint(
                homeLatitude, // Current drone latitude
                homeLongitude, // Current drone longitude
                returnAltitude
        );
        builder.addWaypoint(currentLocationWaypoint);

        // Second waypoint: Go to home position at safe altitude
        Waypoint homeWaypoint = new Waypoint(
                initialHomeLat,
                initialHomeLon,
                returnAltitude
        );
        builder.addWaypoint(homeWaypoint);

        // Build and load the return mission
        WaypointMission returnMission = builder.build();

        if (waypointMissionOperator != null) {
            DJIError error = waypointMissionOperator.loadMission(returnMission);

            if (error == null) {
                // Start the return mission
                waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError == null) {
                                        updateStatus("Retornando para casa...");
                                    } else {
                                        updateStatus("Falha ao iniciar retorno: " + djiError.getDescription());
                                    }
                                }
                            });
                        } else {
                            updateStatus("Falha ao enviar missão de retorno: " + djiError.getDescription());

                            // Alternative: use go home command if mission fails
                            if (flightController != null) {
                                flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError == null) {
                                            updateStatus("Go Home iniciado.");
                                        } else {
                                            updateStatus("Falha ao iniciar Go Home: " + djiError.getDescription());
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            } else {
                updateStatus("Falha ao criar missão de retorno: " + error.getDescription());

                // Alternative: use go home command if mission fails
                if (flightController != null) {
                    flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                updateStatus("Go Home iniciado.");
                            } else {
                                updateStatus("Falha ao iniciar Go Home: " + djiError.getDescription());
                            }
                        }
                    });
                }
            }
        }
    }

    private void setUpListener() {
        listener = new WaypointMissionOperatorListener() {
            @Override
            public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent event) {
                // Not using download in this implementation
            }

            @Override
            public void onUploadUpdate(@NonNull final WaypointMissionUploadEvent event) {
                if (event.getProgress() != null && event.getProgress().isSummaryUploaded &&
                        event.getProgress().uploadedWaypointIndex == (event.getProgress().totalWaypointCount - 1)) {
                    updateStatus("Missão enviada com sucesso");
                }
            }

            @Override
            public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent event) {
                if (event.getProgress() != null) {
                    final int currentWaypointIndex = event.getProgress().targetWaypointIndex;

                    // Determine if this is a photo waypoint (every even index after the first)
                    // These calculations depend on your waypoint structure (one positioning waypoint followed by photo waypoints)
                    boolean isPhotoWaypoint = currentWaypointIndex % 2 == 1; // If odd index, it's a photo waypoint in our structure

                    // Calculate the current inspection point and photo indices
                    if (photoPoints.size() > 0) {
                        int photosPerInspection = photoPoints.size();
                        currentInspectionIndex = (currentWaypointIndex - 1) / (photosPerInspection + 1);
                        if (isPhotoWaypoint) {
                            currentPhotoIndex = (currentWaypointIndex - 1) % (photosPerInspection + 1);
                            if (currentPhotoIndex >= photosPerInspection) {
                                currentPhotoIndex = 0;
                            }
                        }
                    }

                    // Update status with current progress
                    updateStatus("Waypoint: " + currentWaypointIndex + "/" + totalWaypointCount +
                            " | Estrutura: " + (currentInspectionIndex+1) + "/" + inspectionPoints.size() +
                            " | Foto: " + (currentPhotoIndex+1) + "/" + photoPoints.size());

                    // If this is a photo waypoint and the photo has been taken
                    if (isPhotoWaypoint &&
                            event.getProgress().isWaypointReached &&
                            !missionPausedForPhotoReview &&
                            event.getCurrentState() == WaypointMissionState.EXECUTING) {

                        // Automatically pause for photo review
                        pauseMissionForPhotoReview();
                    }
                }
            }

            @Override
            public void onExecutionStart() {
                updateStatus("Execução da missão iniciada");
            }

            @Override
            public void onExecutionFinish(@Nullable final DJIError error) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            updateStatus("Missão concluída com sucesso");

                            // Reset UI
                            btnPause.setEnabled(false);
                            btnStopMission.setEnabled(false);
                            btnStartMission.setEnabled(true);
                            btnStartMission.setText("Iniciar Missão");
                            isMissionPaused = false;
                            missionPausedForPhotoReview = false;

                            // Reset photo review states
                            isWaitingForReview = false;
                            isInManualMode = false;
                            btnConfirmPhoto.setEnabled(false);
                            btnAdjustPosition.setEnabled(false);
                            btnTakePhoto.setEnabled(false);
                            if (btnSimulatePhoto != null) {
                                btnSimulatePhoto.setEnabled(false);
                            }

                        } else {
                            updateStatus("Falha na execução da missão: " + error.getDescription());
                        }
                    }
                });
            }
        };

        if (waypointMissionOperator != null) {
            waypointMissionOperator.addListener(listener);
        }
    }

    private void pauseMissionForPhotoReview() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        missionPausedForPhotoReview = true;
                        isMissionPaused = true;
                        updateStatus("Missão pausada para revisão de foto");

                        // Update UI
                        post(new Runnable() {
                            @Override
                            public void run() {
                                btnPause.setEnabled(false);
                                btnStartMission.setEnabled(false); // Will be enabled after photo review
                                btnStartMission.setText("Continuar Missão");
                            }
                        });

                        // Fetch the photo for review
                        if (isSimulatorMode) {
                            simulatePhoto();
                            updateStatus("Foto simulada gerada automaticamente (modo simulador)");
                            enablePhotoReviewButtons();
                        } else {
                            fetchLatestPhotoForReview();
                        }
                    } else {
                        updateStatus("Falha ao pausar para revisão: " + djiError.getDescription());
                    }
                }
            });
        }
    }

    private void tearDownListener() {
        if (waypointMissionOperator != null && listener != null) {
            waypointMissionOperator.removeListener(listener);
        }
    }

    private void fetchLatestPhotoForReview() {
        // Enable photo review
        isWaitingForReview = true;

        // If mediaManager is available, try to get the latest photo
        if (mediaManager != null) {
            // First refresh the file list
            refreshFileList();
        } else {
            // Without mediaManager, just simulate a photo
            updateStatus("MediaManager não disponível. Foto simulada.");
            simulatePhoto();
            enablePhotoReviewButtons();
        }
    }

    private void refreshFileList() {
        if (mediaManager != null && camera != null) {
            updateStatus("Atualizando lista de arquivos...");

            mediaManager.refreshFileListOfStorageLocation(storageLocation, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        updateStatus("Lista de arquivos atualizada");
                        getFileList();
                    } else {
                        updateStatus("Erro ao atualizar lista de arquivos: " + djiError.getDescription());
                        simulatePhoto();
                        enablePhotoReviewButtons();
                    }
                }
            });
        } else {
            updateStatus("MediaManager ou câmera não disponível");
            simulatePhoto();
            enablePhotoReviewButtons();
        }
    }

    private void getFileList() {
        if (mediaManager != null) {
            List<MediaFile> mediaFiles;

            // Get file list based on storage location
            if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
                mediaFiles = mediaManager.getSDCardFileListSnapshot();
            } else {
                mediaFiles = mediaManager.getInternalStorageFileListSnapshot();
            }

            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                // Get the first (most recent) media file
                MediaFile latestMedia = mediaFiles.get(0);
                fetchThumbnail(latestMedia);
            } else {
                updateStatus("Nenhuma foto encontrada");
                simulatePhoto();
                enablePhotoReviewButtons();
            }
        } else {
            updateStatus("MediaManager não disponível");
            simulatePhoto();
            enablePhotoReviewButtons();
        }
    }

    private void fetchThumbnail(MediaFile mediaFile) {
        if (mediaFile != null && scheduler != null) {
            FetchMediaTask task = new FetchMediaTask(mediaFile, FetchMediaTaskContent.THUMBNAIL, new FetchMediaTask.Callback() {
                @Override
                public void onUpdate(MediaFile file, FetchMediaTaskContent content, DJIError error) {
                    if (error == null) {
                        if (content == FetchMediaTaskContent.THUMBNAIL) {
                            if (file.getThumbnail() != null) {
                                final Bitmap thumbnail = file.getThumbnail();
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        displayPhoto(thumbnail);
                                        lastPhotoTaken = thumbnail;
                                        enablePhotoReviewButtons();
                                    }
                                });
                            } else {
                                updateStatus("Thumbnail não disponível");
                                simulatePhoto();
                                enablePhotoReviewButtons();
                            }
                        }
                    } else {
                        updateStatus("Erro ao obter thumbnail: " + error.getDescription());
                        simulatePhoto();
                        enablePhotoReviewButtons();
                    }
                }
            });

            scheduler.moveTaskToNext(task);
        } else {
            updateStatus("Scheduler ou arquivo de mídia inválido");
            simulatePhoto();
            enablePhotoReviewButtons();
        }
    }

    private void simulatePhoto() {
        // For development purposes, create a simulated photo
        post(new Runnable() {
            @Override
            public void run() {
                // Create a simulated image
                Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);

                // Create a more visually interesting simulated photo based on structure and photo indices
                int r = (currentInspectionIndex * 50) % 255;
                int g = (currentPhotoIndex * 40) % 255;
                int b = (currentInspectionIndex * currentPhotoIndex * 30) % 255;

                bitmap.eraseColor(Color.rgb(r, g, b));

                displayPhoto(bitmap);
                lastPhotoTaken = bitmap;
                isWaitingForReview = true;

                // If we're in simulator mode, enable the simulated photo button
                if (isSimulatorMode && btnSimulatePhoto != null) {
                    btnSimulatePhoto.setEnabled(true);
                }
            }
        });
    }

    private void displayPhoto(final Bitmap photo) {
        post(new Runnable() {
            @Override
            public void run() {
                if (photo != null) {
                    imagePreview.setImageBitmap(photo);
                    noImageText.setVisibility(View.GONE);
                } else {
                    imagePreview.setImageBitmap(null);
                    noImageText.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void enablePhotoReviewButtons() {
        post(new Runnable() {
            @Override
            public void run() {
                btnConfirmPhoto.setEnabled(true);
                btnAdjustPosition.setEnabled(true);
                btnTakePhoto.setEnabled(false);
                btnStartMission.setEnabled(false);
            }
        });
    }

    private void proceedToNextPhoto() {
        // Immediately resume the mission when photo is confirmed
        post(new Runnable() {
            @Override
            public void run() {
                btnConfirmPhoto.setEnabled(false);
                btnAdjustPosition.setEnabled(false);
                btnStartMission.setEnabled(false);
                isWaitingForReview = false;

                if (btnSimulatePhoto != null) {
                    btnSimulatePhoto.setEnabled(false);
                }

                updateStatus("Foto confirmada. Retomando missão...");

                // Resume the mission immediately
                resumeMission();
            }
        });
    }

    private void enterManualAdjustmentMode() {
        post(new Runnable() {
            @Override
            public void run() {
                // Disable mission control and enable manual flight
                isWaitingForReview = false;
                isInManualMode = true;

                btnConfirmPhoto.setEnabled(false);
                btnAdjustPosition.setEnabled(false);
                btnTakePhoto.setEnabled(true);
                btnStartMission.setEnabled(false);

                if (btnSimulatePhoto != null) {
                    btnSimulatePhoto.setEnabled(false);
                }

                updateStatus("Modo de ajuste manual ativado. Use o controle remoto para posicionar o drone, depois tire uma foto.");
            }
        });
    }

    private void takeManualPhoto() {
        if (camera != null) {
            camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                // Photo mode set, now take the photo
                                camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(final DJIError djiError) {
                                        post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (djiError == null) {
                                                    updateStatus("Foto tirada manualmente com sucesso");
                                                    // Exit manual mode
                                                    isInManualMode = false;
                                                    btnTakePhoto.setEnabled(false);

                                                    // Wait a moment for the drone to process the photo
                                                    new Handler().postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            // Show the photo for review
                                                            fetchLatestPhotoForReview();

                                                            // Only enable confirm button (will auto-resume on confirm)
                                                            post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    btnConfirmPhoto.setEnabled(true);
                                                                    btnStartMission.setEnabled(false);
                                                                    updateStatus("Foto manual tirada. Confirme para continuar a missão.");
                                                                }
                                                            });
                                                        }
                                                    }, 2000); // 2 seconds
                                                } else {
                                                    updateStatus("Falha ao tirar foto: " + djiError.getDescription());
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                updateStatus("Falha ao configurar modo de foto: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        } else {
            // Simulation for when camera is not available or in simulator mode
            simulatePhoto();
            updateStatus("Foto simulada tirada manualmente");
            isInManualMode = false;
            post(new Runnable() {
                @Override
                public void run() {
                    btnTakePhoto.setEnabled(false);
                    btnConfirmPhoto.setEnabled(true);
                    btnAdjustPosition.setEnabled(false); // Disable adjust since we already adjusted
                    btnStartMission.setEnabled(false); // Don't need this since confirm will resume

                    if (isSimulatorMode && btnSimulatePhoto != null) {
                        btnSimulatePhoto.setEnabled(false);
                    }

                    updateStatus("Foto manual simulada. Confirme para continuar a missão.");
                }
            });
        }
    }

    public void updateStatus(final String message) {
        post(new Runnable() {
            @Override
            public void run() {
                if (statusText != null) {
                    statusText.setText("Status: " + message);
                }
                Log.d(TAG, message);
            }
        });
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_structure_inspection_mission;
    }
}