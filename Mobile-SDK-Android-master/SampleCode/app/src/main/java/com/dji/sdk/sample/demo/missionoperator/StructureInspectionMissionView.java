package com.dji.sdk.sample.demo.missionoperator;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private TextView csvInfoText;
    private TextView statusText;
    private TextView noImageText;
    private ImageView imagePreview;

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
        super(context);
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
        csvInfoText = findViewById(R.id.text_csv_info);
        statusText = findViewById(R.id.text_status);
        noImageText = findViewById(R.id.text_no_image);
        imagePreview = findViewById(R.id.image_preview);

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
        } else if (id == R.id.btn_start_mission) {
            if (waypointMissionOperator.getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
                resumeMission();
            } else if (mission != null) {
                uploadAndStartMission();
            } else {
                createInspectionMission();
            }
        } else if (id == R.id.btn_pause) {
            pauseMission();
        } else if (id == R.id.btn_stop_mission) {
            stopMission();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Get product instance and set up flight controller
        Aircraft aircraft = DJISampleApplication.getAircraftInstance();

        if (aircraft == null || !aircraft.isConnected()) {
            updateStatus("Aeronave não conectada");
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
                }
            });
        }

        waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        setUpListener();
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

        // Create mission for the first inspection point
        createWaypointMissionForInspectionPoint(currentInspectionIndex);
    }

    private void createWaypointMissionForInspectionPoint(int inspectionIndex) {
        if (inspectionIndex >= inspectionPoints.size()) {
            updateStatus("Missão de inspeção concluída!");
            return;
        }

        InspectionPoint point = inspectionPoints.get(inspectionIndex);

        // Create a mission builder
        WaypointMission.Builder builder = new WaypointMission.Builder();

        // Set mission parameters
        builder.autoFlightSpeed(DEFAULT_SPEED);
        builder.maxFlightSpeed(DEFAULT_SPEED * 2);
        builder.setExitMissionOnRCSignalLostEnabled(false);
        builder.finishedAction(WaypointMissionFinishedAction.NO_ACTION); // Don't go home between inspection points
        builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        builder.headingMode(WaypointMissionHeadingMode.AUTO);
        builder.setGimbalPitchRotationEnabled(true);

        // Calculate safe altitude for the structure
        float safeAltitude = point.groundAltitude + point.structureHeight + SAFE_DISTANCE;

        // First waypoint: Go to the inspection point at safe altitude
        Waypoint initialWaypoint = new Waypoint(
                point.latitude,
                point.longitude,
                safeAltitude
        );
        builder.addWaypoint(initialWaypoint);

        // Create the first photo waypoint
        createWaypointForPhotoPoint(builder, point, 0);

        // Build and load the mission
        mission = builder.build();

        DJIError error = waypointMissionOperator.loadMission(mission);
        final int missionIndex = inspectionIndex;
        final String errorMsg = (error != null) ? error.getDescription() : null;

        post(new Runnable() {
            @Override
            public void run() {
                if (errorMsg == null) {
                    updateStatus("Missão carregada para o ponto de inspeção " + (missionIndex + 1));
                    btnStartMission.setEnabled(true);
                } else {
                    updateStatus("Erro ao carregar missão: " + errorMsg);
                }
            }
        });
    }

    private void createWaypointForPhotoPoint(WaypointMission.Builder builder, InspectionPoint inspectionPoint, int photoIndex) {
        if (photoIndex >= photoPoints.size()) {
            return;
        }

        RelativePhotoPoint photoPoint = photoPoints.get(photoIndex);

        // Calculate absolute coordinates from relative offsets
        double photoLatitude = inspectionPoint.latitude + (photoPoint.offsetY * ONE_METER_OFFSET);
        double photoLongitude = inspectionPoint.longitude + (photoPoint.offsetX * ONE_METER_OFFSET);
        float photoAltitude = inspectionPoint.groundAltitude + inspectionPoint.structureHeight + photoPoint.offsetZ;

        // Create the waypoint
        Waypoint photoWaypoint = new Waypoint(photoLatitude, photoLongitude, photoAltitude);

        // Add gimbal pitch action
        photoWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, Math.round(photoPoint.gimbalPitch)));

        // Add photo action
        photoWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));

        // Add waypoint to the mission builder
        builder.addWaypoint(photoWaypoint);
    }

    private void uploadAndStartMission() {
        if (WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState()) ||
                WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState())) {

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
                    waypointMissionOperator.getCurrentState().getName());
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
                            } else {
                                updateStatus("Falha ao retomar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private void stopMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão encerrada");
                                btnPause.setEnabled(false);
                                btnStopMission.setEnabled(false);
                                btnStartMission.setEnabled(true);

                                // Reset UI
                                noImageText.setVisibility(View.VISIBLE);
                                imagePreview.setImageBitmap(null);
                                btnConfirmPhoto.setEnabled(false);
                                btnAdjustPosition.setEnabled(false);
                            } else {
                                updateStatus("Falha ao encerrar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
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

                    // Update status with current progress
                    updateStatus("Waypoint: " + currentWaypointIndex +
                            " | Estrutura: " + (currentInspectionIndex+1) + "/" + inspectionPoints.size() +
                            " | Foto: " + (currentPhotoIndex+1) + "/" + photoPoints.size());
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
                            updateStatus("Foto tirada. Aguardando confirmação...");

                            // Fetch the latest photo for review
                            fetchLatestPhotoForReview();
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
                bitmap.eraseColor(0xFF3498DB); // Blue color
                displayPhoto(bitmap);
                lastPhotoTaken = bitmap;
                enablePhotoReviewButtons();
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
            }
        });
    }

    private void proceedToNextPhoto() {
        post(new Runnable() {
            @Override
            public void run() {
                isWaitingForReview = false;
                btnConfirmPhoto.setEnabled(false);
                btnAdjustPosition.setEnabled(false);
                updateStatus("Avançando para próxima foto...");
            }
        });

        // Move to the next photo position
        currentPhotoIndex++;

        if (currentPhotoIndex >= photoPoints.size()) {
            // All photos for this inspection point are done
            currentPhotoIndex = 0;
            currentInspectionIndex++;

            // Move to the next inspection point
            if (currentInspectionIndex < inspectionPoints.size()) {
                // Create a new mission for the next inspection point
                createWaypointMissionForInspectionPoint(currentInspectionIndex);
            } else {
                // All inspection points are complete
                updateStatus("Missão de inspeção concluída!");
                post(new Runnable() {
                    @Override
                    public void run() {
                        btnStartMission.setEnabled(false);
                        btnStopMission.setEnabled(false);
                        btnPause.setEnabled(false);
                    }
                });
            }
        } else {
            // Create mission for the next photo at the same inspection point
            createMissionForNextPhoto();
        }
    }

    private void createMissionForNextPhoto() {
        if (currentInspectionIndex >= inspectionPoints.size() || currentPhotoIndex >= photoPoints.size()) {
            return;
        }

        InspectionPoint inspectionPoint = inspectionPoints.get(currentInspectionIndex);

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

        // Create the waypoint for the next photo
        createWaypointForPhotoPoint(builder, inspectionPoint, currentPhotoIndex);

        // Build and load the mission
        mission = builder.build();

        DJIError error = waypointMissionOperator.loadMission(mission);
        final int photoIndex = currentPhotoIndex;
        final int inspectionIndex = currentInspectionIndex;
        final String errorMsg = (error != null) ? error.getDescription() : null;

        post(new Runnable() {
            @Override
            public void run() {
                if (errorMsg == null) {
                    updateStatus("Missão carregada para foto " + (photoIndex + 1) +
                            " no ponto de inspeção " + (inspectionIndex + 1));
                    uploadAndStartMission();
                } else {
                    updateStatus("Falha ao carregar missão: " + errorMsg);
                }
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
                                                    // Exit manual mode and proceed to next photo
                                                    isInManualMode = false;
                                                    btnTakePhoto.setEnabled(false);

                                                    // Wait a moment for the drone to process the photo
                                                    new Handler().postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            fetchLatestPhotoForReview();
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
            // Simulation for when camera is not available
            simulatePhoto();
            updateStatus("Foto simulada tirada manualmente");
            isInManualMode = false;
            post(new Runnable() {
                @Override
                public void run() {
                    btnTakePhoto.setEnabled(false);
                    btnConfirmPhoto.setEnabled(true);
                }
            });
        }
    }

    private void updateStatus(final String message) {
        post(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Status: " + message);
                Log.d(TAG, message);
            }
        });
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_structure_inspection_mission;
    }
}