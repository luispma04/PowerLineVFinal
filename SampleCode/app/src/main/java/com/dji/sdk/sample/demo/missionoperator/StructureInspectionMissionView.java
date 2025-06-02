package com.dji.sdk.sample.demo.missionoperator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
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
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionmanager.MissionBaseView;
import com.dji.sdk.sample.demo.missionoperator.adapter.PhotoGalleryAdapter;
import com.dji.sdk.sample.demo.missionoperator.adapter.StructureFolderAdapter;
import com.dji.sdk.sample.demo.missionoperator.util.PhotoStorageManager;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ToastUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionDetectionState;
import dji.common.gimbal.CapabilityKey;
import dji.common.gimbal.GimbalMode;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
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
import dji.common.util.DJIParamCapability;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;

public class StructureInspectionMissionView extends MissionBaseView implements PhotoGalleryAdapter.OnPhotoClickListener {

    private static final String TAG = "StructureInspection";

    // Request codes for file selection
    private static final int REQUEST_STRUCTURES_CSV = 1001;
    private static final int REQUEST_PHOTO_POSITIONS_CSV = 1002;

    // Mission parameters
    private static final double ONE_METER_OFFSET = 0.00000899322; // Approximately 1 meter in GPS coordinates
    private static final float DEFAULT_ALTITUDE = 0.0f; // Minimum base altitude in meters
    private static final float DEFAULT_SPEED = 5.0f; // Default speed in m/s
    private static final float SAFE_DISTANCE = 2.5f; // Safe distance from structures in meters
    private static final float SAFETY_ALTITUDE = 25.0f; // Safety altitude for traveling between structures

    // UI components - Main view
    private Button btnLoadStructures;
    private Button btnReviewPhoto;
    private Button btnLoadPhotoPositions;
    private Button btnTakePhoto;
    private Button btnStartMission;
    private ToggleButton btnPause;
    private Button btnStopMission;
    private TextView csvInfoText;
    private TextView statusText;
    private ProgressBar progressMission;
    private TextView currentStructureText;
    private TextView currentPhotoText;
    private TextView advancedMissionInfoText;

    // Connection status components
    private TextView connectionStatusText;
    private TextView modelTextView;
    private TextView batteryText;
    private TextView droneLocationText;

    // Gallery components - Main gallery view
    private ViewFlipper viewFlipper;
    private Button btnToggleGallery;
    private Button btnBackToMission;
    private RecyclerView recyclerPhotos;
    private TextView noPhotosText;
    private Button btnLiveStream;
    private FrameLayout liveStreamContainer;
    private StructureLiveStreamView liveStreamView;

    // Gallery structure components
    private ViewFlipper galleryViewFlipper;
    private RecyclerView recyclerStructures;
    private TextView noStructuresText;
    private Button btnBackToStructures;
    private TextView structureTitleText;
    private TextView galleryTitleText;

    // Adaptadores separados para fotos e estruturas
    private PhotoGalleryAdapter photoGalleryAdapter;
    private StructureFolderAdapter structureFolderAdapter;

    private int currentStructureId = -1;

    // Mission components
    private WaypointMissionOperator waypointMissionOperator;
    private FlightController flightController;
    private FlightAssistant flightAssistant;
    private Camera camera;
    private WaypointMission mission;
    private WaypointMissionOperatorListener listener;
    private MediaManager mediaManager;
    private FetchMediaTaskScheduler scheduler;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Photo storage and gallery components
    private PhotoStorageManager photoStorageManager;

    // Mission data
    private List<InspectionPoint> inspectionPoints;
    private List<RelativePhotoPoint> photoPoints;
    private List<Integer> photoWaypointIndices; // Track photo waypoint indices
    private int currentInspectionIndex = 0;
    private int currentPhotoIndex = 0;
    private boolean isWaitingForReview = false;
    private Bitmap lastPhotoTaken = null;
    private SettingsDefinitions.StorageLocation storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;

    // Mission state variables
    private boolean isSimulatorMode = false;
    private double initialHomeLat;
    private double initialHomeLon;
    private float initialHomeAlt;
    private boolean isMissionPaused = false;
    private int totalWaypointCount = 0;
    private boolean missionPausedForPhotoReview = false;

    // Photo review timeout and state
    private Handler photoTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable photoTimeoutRunnable;
    private int lastPhotoWaypointProcessed = -1;
    private boolean forceNextPhotoReview = false;

    // Flight state variables
    private double homeLatitude;
    private double homeLongitude;
    private FlightMode flightState; // FIXED: Use correct FlightMode import

    // Obstacle avoidance data
    private boolean obstacleAvoidanceEnabled = false;
    private float closestObstacleDistance = 0.0f;
    private StringBuilder obstacleInfoBuilder = new StringBuilder();

    // Live stream state
    private boolean isLiveStreamActive = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Orientation management
    private int originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean orientationLocked = false;

    // ENHANCED PHOTO FETCHING VARIABLES
    private int photoFetchRetryCount = 0;
    private static final int MAX_PHOTO_FETCH_RETRIES = 5;
    private static final long PHOTO_FETCH_RETRY_DELAY = 2000; // 2 seconds
    private boolean isPhotoFetchInProgress = false;
    private long lastPhotoTakenTime = 0;

    // Data classes for storing mission information
    private class InspectionPoint {
        double latitude;
        double longitude;
        float groundAltitude; // Elevation difference from base level (in meters)
        float structureHeight; // Height of the structure (in meters)

        InspectionPoint(double lat, double lon, float elevationDiff, float height) {
            latitude = lat;
            longitude = lon;
            groundAltitude = elevationDiff;
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
        this(context, false); // Default to real drone mode for testing
    }

    public StructureInspectionMissionView(Context context, boolean simulatorMode) {
        super(context);
        Log.d(TAG, "Initializing StructureInspectionMissionView with simulatorMode: " + simulatorMode);
        this.isSimulatorMode = simulatorMode;

        // Log mode for debugging
        if (simulatorMode) {
            Log.i(TAG, "Running in SIMULATOR mode - photos will be simulated");
        } else {
            Log.i(TAG, "Running in REAL DRONE mode - will attempt to use real camera");
        }

        init(context);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.d(TAG, "Configuration changed to: " + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait"));

        try {
            // SAVE CURRENT STATE
            int mainViewFlipperState = 0;
            int galleryViewFlipperState = 0;
            int savedStructureId = currentStructureId;

            boolean liveStreamWasVisible = false;
            if (liveStreamContainer != null) {
                liveStreamWasVisible = liveStreamContainer.getVisibility() == View.VISIBLE &&
                        liveStreamContainer.getChildCount() > 0;
                Log.d(TAG, "Live stream container was " + (liveStreamWasVisible ? "visible" : "not visible"));
            }

            if (viewFlipper != null) {
                mainViewFlipperState = viewFlipper.getDisplayedChild();
            }

            if (galleryViewFlipper != null) {
                galleryViewFlipperState = galleryViewFlipper.getDisplayedChild();
            }

            // Clean up live stream view if it exists
            if (liveStreamContainer != null) {
                liveStreamContainer.removeAllViews();
            }
            if (liveStreamView != null) {
                liveStreamView = null;
            }

            // Re-initialize layout
            removeAllViews();
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                inflate(getContext(), R.layout.view_structure_inspection_mission_land, this);
            } else {
                inflate(getContext(), R.layout.view_structure_inspection_mission, this);
            }

            // Re-initialize views
            findViews();
            setupListeners();
            initPhotoGallery();

            // Update UI states
            updateConnectionStatus();
            updateViewsBasedOnState();

            // Restore view states
            if (viewFlipper != null) {
                viewFlipper.setDisplayedChild(mainViewFlipperState);
            }

            if (mainViewFlipperState == 1 && galleryViewFlipper != null) {
                galleryViewFlipper.setDisplayedChild(galleryViewFlipperState);

                if (galleryViewFlipperState == 1 && savedStructureId > 0) {
                    showPhotosForStructure(savedStructureId);
                }
            }

            // Restore live stream if it was visible
            if (liveStreamWasVisible && liveStreamContainer != null) {
                mainHandler.postDelayed(() -> {
                    try {
                        liveStreamView = new StructureLiveStreamView(getContext());
                        liveStreamView.setOnCloseListener(() -> hideLiveStreamView());
                        liveStreamContainer.addView(liveStreamView);
                        liveStreamContainer.setVisibility(View.VISIBLE);
                        isLiveStreamActive = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error recreating live stream", e);
                    }
                }, 300);
            } else {
                isLiveStreamActive = false;
                if (liveStreamContainer != null) {
                    liveStreamContainer.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during configuration change", e);
            isLiveStreamActive = false;
            if (liveStreamContainer != null) {
                liveStreamContainer.setVisibility(View.GONE);
            }
        }
    }

    private void init(Context context) {
        // Initialize UI components based on current orientation
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            inflate(context, R.layout.view_structure_inspection_mission_land, this);
        } else {
            inflate(context, R.layout.view_structure_inspection_mission, this);
        }

        // Initialize the PhotoStorageManager before using it
        photoStorageManager = new PhotoStorageManager(context);

        // Find views and set up listeners
        findViews();
        setupListeners();

        // Initialize mission data
        inspectionPoints = new ArrayList<>();
        photoPoints = new ArrayList<>();

        // Initialize connection status
        updateConnectionStatus();

        // Initialize photo gallery
        initPhotoGallery();
    }

    private void findViews() {
        // Main mission controls
        btnLoadStructures = findViewById(R.id.btn_load_structures);
        btnLoadPhotoPositions = findViewById(R.id.btn_load_photo_positions);
        btnStartMission = findViewById(R.id.btn_start_mission);
        btnPause = findViewById(R.id.btn_pause);
        btnStopMission = findViewById(R.id.btn_stop_mission);
        csvInfoText = findViewById(R.id.text_csv_info);
        statusText = findViewById(R.id.text_status);
        progressMission = findViewById(R.id.progress_mission);
        currentStructureText = findViewById(R.id.text_current_structure);
        currentPhotoText = findViewById(R.id.text_current_photo);
        btnReviewPhoto = findViewById(R.id.btn_review_photo);
        advancedMissionInfoText = findViewById(R.id.text_advanced_mission_info);

        // Gallery components
        viewFlipper = findViewById(R.id.view_flipper);
        btnToggleGallery = findViewById(R.id.btn_toggle_gallery);
        btnBackToMission = findViewById(R.id.btn_back_to_mission);
        recyclerPhotos = findViewById(R.id.recycler_photos);
        noPhotosText = findViewById(R.id.text_no_photos);

        // Gallery structure components
        galleryViewFlipper = findViewById(R.id.gallery_view_flipper);
        recyclerStructures = findViewById(R.id.recycler_structures);
        noStructuresText = findViewById(R.id.text_no_structures);
        btnBackToStructures = findViewById(R.id.btn_back_to_structures);
        structureTitleText = findViewById(R.id.text_structure_title);
        galleryTitleText = findViewById(R.id.text_gallery_title);

        // Connection status components
        connectionStatusText = findViewById(R.id.text_connection_status);
        modelTextView = findViewById(R.id.text_product_model);
        batteryText = findViewById(R.id.text_battery_info);
        droneLocationText = findViewById(R.id.text_drone_location);

        // Initialize button states
        btnStartMission.setEnabled(false);
        btnPause.setEnabled(false);
        btnStopMission.setEnabled(false);

        // Live stream
        btnLiveStream = findViewById(R.id.btn_live_stream);
        liveStreamContainer = findViewById(R.id.live_stream_container);
    }

    private void setupListeners() {
        // Set click listeners for mission controls
        btnLoadStructures.setOnClickListener(this);
        btnLoadPhotoPositions.setOnClickListener(this);
        if (btnReviewPhoto != null) {
            btnReviewPhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "🔧 Manual photo review button clicked");
                    forcePhotoReview();
                }
            });
        }
        btnStartMission.setOnClickListener(this);
        btnStopMission.setOnClickListener(this);

        // Set toggle listener for pause button
        btnPause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Resume mission
                    resumeMissionAutomatically();
                } else {
                    // Pause mission
                    pauseMission();
                }
            }
        });

        // Set up gallery toggle buttons
        if (btnToggleGallery != null) {
            btnToggleGallery.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showGalleryView();
                }
            });
        }

        if (btnBackToMission != null) {
            btnBackToMission.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMissionView();
                }
            });
        }

        if (btnBackToStructures != null) {
            btnBackToStructures.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showStructuresList();
                }
            });
        }

        if (btnLiveStream != null) {
            btnLiveStream.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLiveStreamView();
                }
            });
        }
    }

    private void showLiveStreamView() {
        Log.d(TAG, "showLiveStreamView called");

        try {
            if (liveStreamContainer == null) {
                Log.e(TAG, "Live stream container not found");
                return;
            }

            if (liveStreamView != null) {
                try {
                    liveStreamView.cleanup();
                } catch (Exception e) {
                    Log.e(TAG, "Error cleaning up existing live stream view", e);
                }
            }
            liveStreamContainer.removeAllViews();

            try {
                liveStreamView = new StructureLiveStreamView(getContext());
                Log.d(TAG, "Created new live stream view: " + liveStreamView);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create live stream view", e);
                return;
            }

            liveStreamView.setOnCloseListener(new StructureLiveStreamView.OnCloseListener() {
                @Override
                public void onClose() {
                    Log.d(TAG, "Close listener triggered");
                    hideLiveStreamView();
                }
            });

            liveStreamContainer.addView(liveStreamView);
            liveStreamContainer.setVisibility(View.VISIBLE);
            isLiveStreamActive = true;
            Log.d(TAG, "Live stream view shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error showing live stream", e);
            isLiveStreamActive = false;
        }
    }

    public void hideLiveStreamView() {
        Log.d(TAG, "hideLiveStreamView called");

        try {
            isLiveStreamActive = false;

            if (liveStreamContainer != null) {
                liveStreamContainer.setVisibility(View.GONE);
                liveStreamContainer.removeAllViews();
            }

            if (liveStreamView != null) {
                try {
                    liveStreamView.cleanup();
                } catch (Exception e) {
                    Log.e(TAG, "Error during live stream cleanup", e);
                }
                liveStreamView = null;
            }

            Log.d(TAG, "Live stream view hidden");
        } catch (Exception e) {
            Log.e(TAG, "Error hiding live stream view", e);
        }
    }

    private void initPhotoGallery() {
        Log.d(TAG, "Initializing photo gallery");

        if (recyclerStructures != null) {
            if (recyclerStructures.getLayoutManager() == null) {
                int spanCount = getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
                recyclerStructures.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
            }
        }

        if (recyclerPhotos != null) {
            if (recyclerPhotos.getLayoutManager() == null) {
                int spanCount = getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
                recyclerPhotos.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
            }
        }

        refreshGallery();
    }

    private void refreshGallery() {
        Log.d(TAG, "Refreshing gallery");

        photoStorageManager.refresh();

        List<Integer> structureIds = photoStorageManager.getStructureIdsWithPhotos();
        Log.d(TAG, "Found " + structureIds.size() + " structures with photos");

        if (recyclerStructures != null) {
            if (structureFolderAdapter == null) {
                structureFolderAdapter = new StructureFolderAdapter(
                        getContext(),
                        structureIds,
                        photoStorageManager,
                        new StructureFolderAdapter.OnStructureClickListener() {
                            @Override
                            public void onStructureClick(int structureId) {
                                Log.d(TAG, "Structure clicked: " + structureId);
                                showPhotosForStructure(structureId);
                            }
                        }
                );
                recyclerStructures.setAdapter(structureFolderAdapter);
            } else {
                structureFolderAdapter.updateStructureList(structureIds);
            }
        }

        if (noStructuresText != null) {
            noStructuresText.setVisibility(structureIds.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (currentStructureId > 0 && galleryViewFlipper != null && galleryViewFlipper.getDisplayedChild() == 1) {
            updatePhotosForCurrentStructure();
        }
    }

    private void showGalleryView() {
        Log.d(TAG, "Showing gallery view");

        if (viewFlipper != null) {
            refreshGallery();
            viewFlipper.setDisplayedChild(1);

            if (galleryViewFlipper != null) {
                galleryViewFlipper.setDisplayedChild(0);
            }

            if (galleryTitleText != null) {
                galleryTitleText.setText("Galeria de Fotos por Estrutura");
            }
        }
    }

    private void showMissionView() {
        Log.d(TAG, "Showing mission view");

        if (viewFlipper != null) {
            viewFlipper.setDisplayedChild(0);
        }
    }

    private void showStructuresList() {
        Log.d(TAG, "Showing structures list");

        if (galleryViewFlipper != null) {
            galleryViewFlipper.setDisplayedChild(0);
            currentStructureId = -1;

            if (galleryTitleText != null) {
                galleryTitleText.setText("Galeria de Fotos por Estrutura");
            }

            if (structureFolderAdapter != null && recyclerStructures != null) {
                List<Integer> structureIds = photoStorageManager.getStructureIdsWithPhotos();
                structureFolderAdapter.updateStructureList(structureIds);
                recyclerStructures.setAdapter(structureFolderAdapter);

                if (noStructuresText != null) {
                    noStructuresText.setVisibility(structureIds.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    private void showPhotosForStructure(int structureId) {
        Log.d(TAG, "Showing photos for structure: " + structureId);

        if (galleryViewFlipper != null) {
            currentStructureId = structureId;

            if (structureTitleText != null) {
                structureTitleText.setText("Estrutura " + structureId);
            }

            updatePhotosForCurrentStructure();
            galleryViewFlipper.setDisplayedChild(1);

            if (galleryTitleText != null) {
                galleryTitleText.setText("Fotos da Estrutura " + structureId);
            }
        }
    }

    private void updatePhotosForCurrentStructure() {
        Log.d(TAG, "Updating photos for structure: " + currentStructureId);

        if (currentStructureId <= 0) {
            return;
        }

        List<PhotoStorageManager.PhotoInfo> photos = photoStorageManager.getPhotosForStructure(currentStructureId);
        Log.d(TAG, "Found " + photos.size() + " photos for structure: " + currentStructureId);

        if (noPhotosText != null) {
            noPhotosText.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (recyclerPhotos != null) {
            if (photoGalleryAdapter == null) {
                photoGalleryAdapter = new PhotoGalleryAdapter(getContext(), photos, this);
                recyclerPhotos.setAdapter(photoGalleryAdapter);
            } else {
                photoGalleryAdapter.updatePhotoList(photos);
                recyclerPhotos.setAdapter(photoGalleryAdapter);
            }
        }
    }

    private void updateViewsBasedOnState() {
        updateMissionProgress();
        updateAdvancedMissionInfo();

        if (btnStartMission != null) {
            btnStartMission.setEnabled((!inspectionPoints.isEmpty() && !photoPoints.isEmpty()) ||
                    isMissionPaused || missionPausedForPhotoReview);
        }

        if (btnPause != null) {
            btnPause.setEnabled(waypointMissionOperator != null &&
                    waypointMissionOperator.getCurrentState() == WaypointMissionState.EXECUTING &&
                    !isMissionPaused && !missionPausedForPhotoReview);
        }

        if (btnStopMission != null) {
            btnStopMission.setEnabled(waypointMissionOperator != null &&
                    (waypointMissionOperator.getCurrentState() == WaypointMissionState.EXECUTING ||
                            waypointMissionOperator.getCurrentState() == WaypointMissionState.EXECUTION_PAUSED));
        }

        if (csvInfoText != null && (!inspectionPoints.isEmpty() || !photoPoints.isEmpty())) {
            StringBuilder info = new StringBuilder();
            if (!inspectionPoints.isEmpty()) {
                info.append(inspectionPoints.size()).append(" estruturas carregadas");
            }
            if (!photoPoints.isEmpty()) {
                if (info.length() > 0) {
                    info.append(", ");
                }
                info.append(photoPoints.size()).append(" posições de foto");
            }
            csvInfoText.setText(info.toString());
        }
    }

    private void updateMissionProgress() {
        if (inspectionPoints.isEmpty() || photoPoints.isEmpty()) {
            if (progressMission != null) {
                progressMission.setProgress(0);
            }
            if (currentStructureText != null) {
                currentStructureText.setText("Estrutura: 0/0");
            }
            if (currentPhotoText != null) {
                currentPhotoText.setText("Foto: 0/0");
            }
            return;
        }

        if (currentStructureText != null) {
            currentStructureText.setText(String.format("Estrutura: %d/%d",
                    Math.min(currentInspectionIndex + 1, inspectionPoints.size()),
                    inspectionPoints.size()));
        }

        if (currentPhotoText != null) {
            currentPhotoText.setText(String.format("Foto: %d/%d",
                    Math.min(currentPhotoIndex + 1, photoPoints.size()),
                    photoPoints.size()));
        }

        int totalPhotos = inspectionPoints.size() * photoPoints.size();
        int completedPhotos = currentInspectionIndex * photoPoints.size() + currentPhotoIndex;
        int progressPercent = (totalPhotos > 0) ? (completedPhotos * 100 / totalPhotos) : 0;

        if (progressMission != null) {
            progressMission.setProgress(progressPercent);
        }
    }

    private void updateAdvancedMissionInfo() {
        if (advancedMissionInfoText == null) return;

        StringBuilder info = new StringBuilder();

        info.append("PARÂMETROS DA MISSÃO:\n");
        info.append("Velocidade: ").append(DEFAULT_SPEED).append(" m/s\n");
        info.append("Altitude base: ").append(DEFAULT_ALTITUDE).append(" m\n");
        info.append("Altitude de segurança: ").append(SAFETY_ALTITUDE).append(" m\n");
        info.append("Distância segura: ").append(SAFE_DISTANCE).append(" m\n\n");

        info.append("ESTATÍSTICAS:\n");
        info.append("Estruturas: ").append(inspectionPoints.size()).append("\n");
        info.append("Posições de foto por estrutura: ").append(photoPoints.size()).append("\n");
        info.append("Total de waypoints: ").append(totalWaypointCount).append("\n");

        int safetyWaypoints = Math.max(0, (inspectionPoints.size() - 1) * 2);
        info.append("Waypoints de segurança: ").append(safetyWaypoints).append("\n\n");

        if (!inspectionPoints.isEmpty()) {
            info.append("ALTITUDES DE VOO:\n");
            float minElevation = Float.MAX_VALUE;
            float maxElevation = Float.MIN_VALUE;
            float minSafetyAlt = Float.MAX_VALUE;
            float maxSafetyAlt = Float.MIN_VALUE;

            for (InspectionPoint point : inspectionPoints) {
                float totalElevation = point.groundAltitude + point.structureHeight;
                minElevation = Math.min(minElevation, totalElevation);
                maxElevation = Math.max(maxElevation, totalElevation);

                float safetyAlt = SAFETY_ALTITUDE + point.groundAltitude;
                minSafetyAlt = Math.min(minSafetyAlt, safetyAlt);
                maxSafetyAlt = Math.max(maxSafetyAlt, safetyAlt);
            }

            info.append("Altitude de inspeção mín: ").append(String.format("%.1f", DEFAULT_ALTITUDE + minElevation + SAFE_DISTANCE)).append("m\n");
            info.append("Altitude de inspeção máx: ").append(String.format("%.1f", DEFAULT_ALTITUDE + maxElevation + SAFE_DISTANCE)).append("m\n");
            info.append("Altitude de segurança mín: ").append(String.format("%.1f", minSafetyAlt)).append("m\n");
            info.append("Altitude de segurança máx: ").append(String.format("%.1f", maxSafetyAlt)).append("m\n\n");
        }

        info.append("ESTADO DO VOO:\n");
        if (flightController != null) {
            // FIXED: Proper null check and toString handling for DJI SDK enums
            String flightModeString = "Desconhecido";
            if (flightState != null) {
                flightModeString = flightState.toString();
            }
            info.append("Modo de voo: ").append(flightModeString).append("\n");
        }

        if (waypointMissionOperator != null) {
            WaypointMissionState state = waypointMissionOperator.getCurrentState();
            String stateString = "Não iniciada";
            if (state != null) {
                stateString = state.toString();
            }
            info.append("Estado da missão: ").append(stateString).append("\n\n");
        }

        info.append("DETECÇÃO DE OBSTÁCULOS:\n");
        info.append("Evitamento de obstáculos: ").append(obstacleAvoidanceEnabled ? "Ativado" : "Desativado").append("\n");
        if (obstacleAvoidanceEnabled) {
            info.append("Distância mais próxima: ").append(String.format("%.2f", closestObstacleDistance)).append("m\n");
            if (obstacleInfoBuilder.length() > 0) {
                info.append(obstacleInfoBuilder.toString()).append("\n");
            }
        }
        info.append("\n");

        if (currentInspectionIndex >= 0 && currentPhotoIndex >= 0 && !inspectionPoints.isEmpty() && !photoPoints.isEmpty()) {
            info.append("POSIÇÃO ATUAL:\n");
            info.append("Estrutura: ").append(currentInspectionIndex + 1)
                    .append("/").append(inspectionPoints.size()).append("\n");
            info.append("Foto: ").append(currentPhotoIndex + 1)
                    .append("/").append(photoPoints.size()).append("\n");

            if (currentInspectionIndex < inspectionPoints.size()) {
                InspectionPoint currentPoint = inspectionPoints.get(currentInspectionIndex);
                float currentAltitude = DEFAULT_ALTITUDE + currentPoint.groundAltitude + currentPoint.structureHeight + SAFE_DISTANCE;
                float currentSafetyAlt = SAFETY_ALTITUDE + currentPoint.groundAltitude;
                info.append("Altitude de inspeção: ").append(String.format("%.1f", currentAltitude)).append("m\n");
                info.append("Altitude de segurança: ").append(String.format("%.1f", currentSafetyAlt)).append("m\n");
                info.append("Elevação terreno: ").append(String.format("%.1f", currentPoint.groundAltitude)).append("m\n");
                info.append("Altura estrutura: ").append(String.format("%.1f", currentPoint.structureHeight)).append("m\n");
            }

            int totalPhotos = inspectionPoints.size() * photoPoints.size();
            int completedPhotos = currentInspectionIndex * photoPoints.size() + currentPhotoIndex;
            int progressPercent = (totalPhotos > 0) ? (completedPhotos * 100 / totalPhotos) : 0;
            info.append("Progresso total: ").append(progressPercent).append("%");
        }

        advancedMissionInfoText.setText(info.toString());
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.btn_load_structures) {
            openFilePicker(REQUEST_STRUCTURES_CSV);
        } else if (id == R.id.btn_load_photo_positions) {
            openFilePicker(REQUEST_PHOTO_POSITIONS_CSV);
        } else if (id == R.id.btn_start_mission) {
            // Check if we're in a paused state that needs resume
            if (isMissionPaused || missionPausedForPhotoReview) {
                Log.d(TAG, "🔄 Manual resume triggered (fallback)");
                resumeMissionAutomatically();
            } else if (mission != null) {
                uploadAndStartMission();
            } else {
                createInspectionMission();
            }
        } else if (id == R.id.btn_stop_mission) {
            stopMissionAndReturnHome();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        updateConnectionStatus();
        initializeProductAndSDK();
    }

    private void initializeProductAndSDK() {
        Aircraft aircraft = DJISampleApplication.getAircraftInstance();

        if (aircraft == null || !aircraft.isConnected()) {
            updateStatus("Aeronave não conectada");
            updateConnectionStatus();
            return;
        }

        flightController = aircraft.getFlightController();
        camera = aircraft.getCamera();

        if (camera != null) {
            Log.i(TAG, "Camera initialized successfully - real camera mode available");

            // ENHANCED: Set up media manager immediately
            setupMediaManagerForPhotoFetching();
        } else {
            Log.w(TAG, "Camera not available - will use simulator mode");
        }

        // CONFIGURAR GIMBAL PARA CONTROLE INDEPENDENTE
        if (aircraft != null) {
            Gimbal gimbal = null;
            if (aircraft.getGimbals() != null && !aircraft.getGimbals().isEmpty()) {
                gimbal = aircraft.getGimbals().get(0);
            } else if (aircraft.getGimbal() != null) {
                gimbal = aircraft.getGimbal();
            }

            if (gimbal != null) {
                setupGimbalForFreeMode(gimbal);
            } else {
                Log.e(TAG, "Gimbal not available");
                updateStatus("Gimbal não disponível");
            }
        }

        if (flightController != null) {
            flightAssistant = flightController.getFlightAssistant();
            if (flightAssistant != null) {
                enableObstacleAvoidance(true);
                setUpObstacleDetectionCallback();
            } else {
                Log.e(TAG, "FlightAssistant is null");
                updateStatus("FlightAssistant não disponível");
            }
        }

        if (flightController != null) {
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    homeLatitude = flightControllerState.getHomeLocation().getLatitude();
                    homeLongitude = flightControllerState.getHomeLocation().getLongitude();
                    flightState = flightControllerState.getFlightMode();

                    if (initialHomeLat == 0 && initialHomeLon == 0) {
                        initialHomeLat = homeLatitude;
                        initialHomeLon = homeLongitude;
                        initialHomeAlt = flightControllerState.getAircraftLocation().getAltitude();
                    }

                    final double latitude = flightControllerState.getAircraftLocation().getLatitude();
                    final double longitude = flightControllerState.getAircraftLocation().getLongitude();
                    final float altitude = flightControllerState.getAircraftLocation().getAltitude();

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (droneLocationText != null) {
                                droneLocationText.setText("Localização: Lat: " + latitude + ", Lon: " + longitude + ", Alt: " + altitude + "m");
                            }

                            updateAdvancedMissionInfo();
                        }
                    });
                }
            });
        }

        waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        setUpListener();
    }

    // ENHANCED: Setup media manager properly for photo fetching
    private void setupMediaManagerForPhotoFetching() {
        if (camera == null) {
            Log.e(TAG, "Camera is null, cannot setup media manager");
            return;
        }

        mediaManager = camera.getMediaManager();
        if (mediaManager != null) {
            scheduler = mediaManager.getScheduler();
            Log.d(TAG, "✅ MediaManager and scheduler initialized successfully");

            // Get and set storage location
            camera.getStorageLocation(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.StorageLocation>() {
                @Override
                public void onSuccess(SettingsDefinitions.StorageLocation value) {
                    storageLocation = value;
                    Log.i(TAG, "📍 Storage location set to: " + value.toString());
                    updateStatus("Storage configurado: " + value.toString());
                }

                @Override
                public void onFailure(DJIError djiError) {
                    Log.e(TAG, "❌ Failed to get storage location: " + djiError.getDescription());
                    // Try setting to internal storage as fallback
                    storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
                    updateStatus("Storage padrão configurado (erro ao detectar: " + djiError.getDescription() + ")");
                }
            });

            // Resume scheduler if needed
            if (scheduler != null && scheduler.getState() == FetchMediaTaskScheduler.FetchMediaTaskSchedulerState.SUSPENDED) {
                scheduler.resume(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            Log.d(TAG, "✅ Media scheduler resumed successfully");
                        } else {
                            Log.e(TAG, "❌ Failed to resume media scheduler: " + djiError.getDescription());
                        }
                    }
                });
            }
        } else {
            Log.e(TAG, "❌ MediaManager is null - photo fetching will not work properly");
        }
    }

    // GIMBAL SETUP METHODS
    private void setupGimbalForFreeMode(Gimbal gimbal) {
        Log.d(TAG, "Setting up gimbal for independent control");

        debugGimbalModes();
        checkGimbalCapabilities(gimbal);

        gimbal.setMode(GimbalMode.YAW_FOLLOW, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "✅ Gimbal set to YAW_FOLLOW mode successfully");
                    updateStatus("Gimbal configurado para controle independente");
                } else {
                    Log.e(TAG, "❌ Failed to set gimbal mode: " + djiError.getDescription());
                    updateStatus("Erro ao configurar gimbal: " + djiError.getDescription());

                    tryFreeMode(gimbal);
                }
            }
        });
    }

    private void tryFreeMode(Gimbal gimbal) {
        try {
            gimbal.setMode(GimbalMode.FREE, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        Log.d(TAG, "✅ Gimbal set to FREE mode successfully");
                        updateStatus("Gimbal configurado para modo livre");
                    } else {
                        Log.e(TAG, "❌ Failed to set FREE mode: " + djiError.getDescription());
                        updateStatus("Usando modo padrão do gimbal");
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "FREE mode not available: " + e.getMessage());
            updateStatus("Modo livre não disponível - usando configuração padrão");
        }
    }

    private void debugGimbalModes() {
        Log.d(TAG, "=== DEBUG: Available Gimbal Modes ===");
        try {
            for (GimbalMode mode : GimbalMode.values()) {
                Log.d(TAG, "Available Gimbal Mode: " + mode.toString()); // Use toString() for DJI SDK enums
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting gimbal modes: " + e.getMessage());
        }
    }

    private void checkGimbalCapabilities(Gimbal gimbal) {
        if (gimbal == null) return;

        Log.d(TAG, "=== DEBUG: Gimbal Capabilities ===");

        try {
            boolean pitchSupported = isFeatureSupported(gimbal, CapabilityKey.ADJUST_PITCH);
            Log.d(TAG, "Pitch control supported: " + pitchSupported);

            boolean yawSupported = isFeatureSupported(gimbal, CapabilityKey.ADJUST_YAW);
            Log.d(TAG, "Yaw control supported: " + yawSupported);

            boolean rollSupported = isFeatureSupported(gimbal, CapabilityKey.ADJUST_ROLL);
            Log.d(TAG, "Roll control supported: " + rollSupported);

            updateStatus("Gimbal - Pitch: " + pitchSupported + ", Yaw: " + yawSupported + ", Roll: " + rollSupported);
        } catch (Exception e) {
            Log.e(TAG, "Error checking gimbal capabilities: " + e.getMessage());
            updateStatus("Erro ao verificar capacidades do gimbal");
        }
    }

    private boolean isFeatureSupported(Gimbal gimbal, CapabilityKey key) {
        if (gimbal == null) {
            return false;
        }

        DJIParamCapability capability = null;
        if (gimbal.getCapabilities() != null) {
            capability = gimbal.getCapabilities().get(key);
        }

        if (capability != null) {
            return capability.isSupported();
        }
        return false;
    }

    private void enableObstacleAvoidance(final boolean enable) {
        if (flightAssistant == null) {
            Log.e(TAG, "FlightAssistant is null, cannot enable obstacle avoidance");
            return;
        }

        flightAssistant.setCollisionAvoidanceEnabled(enable, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "Collision avoidance " + (enable ? "enabled" : "disabled"));
                } else {
                    Log.e(TAG, "Failed to set collision avoidance: " + djiError.getDescription());
                }
            }
        });

        flightAssistant.setUpwardVisionObstacleAvoidanceEnabled(enable, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "Upward vision obstacle avoidance " + (enable ? "enabled" : "disabled"));
                } else {
                    Log.e(TAG, "Failed to set upward vision obstacle avoidance: " + djiError.getDescription());
                }
            }
        });

        flightAssistant.setRTHObstacleAvoidanceEnabled(enable, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "RTH obstacle avoidance " + (enable ? "enabled" : "disabled"));
                } else {
                    Log.e(TAG, "Failed to set RTH obstacle avoidance: " + djiError.getDescription());
                }
            }
        });

        flightAssistant.setLandingProtectionEnabled(enable, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "Landing protection " + (enable ? "enabled" : "disabled"));
                } else {
                    Log.e(TAG, "Failed to set landing protection: " + djiError.getDescription());
                }
            }
        });

        obstacleAvoidanceEnabled = enable;
        updateAdvancedMissionInfo();
    }

    private void setUpObstacleDetectionCallback() {
        if (flightAssistant == null) return;

        flightAssistant.setVisionDetectionStateUpdatedCallback(new VisionDetectionState.Callback() {
            @Override
            public void onUpdate(@NonNull VisionDetectionState visionDetectionState) {
                ObstacleDetectionSector[] sectors = visionDetectionState.getDetectionSectors();

                obstacleInfoBuilder = new StringBuilder();
                closestObstacleDistance = Float.MAX_VALUE;

                for (int i = 0; i < sectors.length; i++) {
                    ObstacleDetectionSector sector = sectors[i];
                    float distance = sector.getObstacleDistanceInMeters();

                    if (distance < closestObstacleDistance && distance > 0) {
                        closestObstacleDistance = distance;
                    }

                    if (distance < 10 && distance > 0) {
                        obstacleInfoBuilder.append("Setor ").append(i + 1)
                                .append(": ").append(String.format("%.2f", distance))
                                .append("m (Perigo: ").append(sector.getWarningLevel().toString()) // Use toString() for DJI SDK enums
                                .append(")\n");
                    }
                }

                obstacleInfoBuilder.append("Alerta do sistema: ")
                        .append(visionDetectionState.getSystemWarning().toString()); // Use toString() for DJI SDK enums

                post(new Runnable() {
                    @Override
                    public void run() {
                        updateAdvancedMissionInfo();
                    }
                });
            }
        });
    }

    public void onProductConnected() {
        post(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
                initializeProductAndSDK();
            }
        });
    }

    public void updateConnectionStatus() {
        post(new Runnable() {
            @Override
            public void run() {
                BaseProduct product = DJISampleApplication.getProductInstance();

                if (connectionStatusText != null) {
                    if (product != null && product.isConnected()) {
                        connectionStatusText.setText("Conectado");
                        connectionStatusText.setTextColor(Color.parseColor("#4CAF50"));
                    } else {
                        connectionStatusText.setText("Desconectado");
                        connectionStatusText.setTextColor(Color.parseColor("#F44336"));
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

                updateAdvancedMissionInfo();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        // Cancel photo timeout
        if (photoTimeoutRunnable != null) {
            photoTimeoutHandler.removeCallbacks(photoTimeoutRunnable);
            photoTimeoutRunnable = null;
        }

        // Reset state
        lastPhotoWaypointProcessed = -1;
        forceNextPhotoReview = false;

        isLiveStreamActive = false;

        if (liveStreamView != null) {
            try {
                liveStreamView.cleanup();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up live stream view", e);
            }
            liveStreamView = null;
        }

        if (liveStreamContainer != null) {
            liveStreamContainer.removeAllViews();
        }

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        tearDownListener();

        if (flightAssistant != null) {
            flightAssistant.setVisionDetectionStateUpdatedCallback(null);
        }

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
            String fileName = getFileNameFromUri(fileUri);
            if (!fileName.toLowerCase().endsWith(".csv")) {
                updateStatus("Por favor, selecione apenas arquivos CSV");
                return;
            }

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
                    float elevationDiff = Float.parseFloat(values[2]);
                    float height = Float.parseFloat(values[3]);

                    inspectionPoints.add(new InspectionPoint(lat, lon, elevationDiff, height));
                }
            }

            reader.close();

            post(new Runnable() {
                @Override
                public void run() {
                    if (csvInfoText != null) {
                        csvInfoText.setText(inspectionPoints.size() + " estruturas carregadas");
                    }

                    if (!inspectionPoints.isEmpty() && !photoPoints.isEmpty() && btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                    }

                    updateStatus("Carregado " + inspectionPoints.size() + " estruturas (formato: lat,lon,elevation_diff,height)");
                    updateAdvancedMissionInfo();
                }
            });

        } catch (IOException e) {
            final String errorMsg = e.getMessage();
            updateStatus("Erro ao carregar arquivo de estruturas: " + errorMsg);
            Log.e(TAG, "Error reading structures CSV file", e);
        } catch (NumberFormatException e) {
            updateStatus("Erro no formato CSV: verifique se os dados são numéricos (lat,lon,elevation_diff,height)");
            Log.e(TAG, "Error parsing CSV numbers", e);
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
                    if (csvInfoText != null) {
                        String currentText = csvInfoText.getText().toString();
                        if (currentText.contains("estruturas carregadas")) {
                            csvInfoText.setText(currentText + ", " + photoPoints.size() + " posições de foto");
                        } else {
                            csvInfoText.setText(photoPoints.size() + " posições de foto carregadas");
                        }
                    }

                    if (!inspectionPoints.isEmpty() && !photoPoints.isEmpty() && btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                    }

                    updateStatus("Carregado " + photoPoints.size() + " posições de foto");
                    updateAdvancedMissionInfo();
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

        currentInspectionIndex = 0;
        currentPhotoIndex = 0;
        isMissionPaused = false;
        missionPausedForPhotoReview = false;

        enableObstacleAvoidance(true);
        createCompleteMission();
    }

    // COMPLETE MISSION CREATION WITH SAFETY WAYPOINTS AND CAMERA CONTROL
    private void createCompleteMission() {
        WaypointMission.Builder builder = new WaypointMission.Builder();

        builder.autoFlightSpeed(DEFAULT_SPEED);
        builder.maxFlightSpeed(DEFAULT_SPEED * 2);
        builder.setExitMissionOnRCSignalLostEnabled(false);
        builder.finishedAction(WaypointMissionFinishedAction.GO_HOME);
        builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        builder.headingMode(WaypointMissionHeadingMode.USING_WAYPOINT_HEADING);
        builder.setGimbalPitchRotationEnabled(true);

        totalWaypointCount = 0;
        photoWaypointIndices = new ArrayList<>();

        InspectionPoint point1 = inspectionPoints.get(0);

        Waypoint firstsafewaypoint = new Waypoint(
                initialHomeLat,
                initialHomeLon,
                SAFETY_ALTITUDE
        );
        firstsafewaypoint.heading = 0; // North (forward)
        builder.addWaypoint(firstsafewaypoint);
        totalWaypointCount++;

        Waypoint firstwaypoint = new Waypoint(
                point1.latitude,
                point1.longitude,
                SAFETY_ALTITUDE
        );
        firstwaypoint.heading = 0; // North (forward)
        builder.addWaypoint(firstwaypoint);
        totalWaypointCount++;

        for (int i = 0; i < inspectionPoints.size(); i++) {
            InspectionPoint point = inspectionPoints.get(i);

            float safeAltitude = DEFAULT_ALTITUDE + point.groundAltitude + point.structureHeight + SAFE_DISTANCE;

            // First waypoint for this structure: Go to the inspection point at safe altitude
            Waypoint initialWaypoint = new Waypoint(
                    point.latitude,
                    point.longitude,
                    safeAltitude
            );
            initialWaypoint.heading = 0; // North (forward)
            builder.addWaypoint(initialWaypoint);
            totalWaypointCount++;

            double lastPhotoLat = point.latitude;
            double lastPhotoLon = point.longitude;

            // Add all photo waypoints for this inspection point
            for (int j = 0; j < photoPoints.size(); j++) {
                RelativePhotoPoint photoPoint = photoPoints.get(j);

                double photoLatitude = point.latitude + (photoPoint.offsetY * ONE_METER_OFFSET);
                double photoLongitude = point.longitude + (photoPoint.offsetX * ONE_METER_OFFSET);
                float photoAltitude = DEFAULT_ALTITUDE + point.groundAltitude + point.structureHeight + photoPoint.offsetZ;

                Waypoint photoWaypoint = new Waypoint(photoLatitude, photoLongitude, photoAltitude);

                // CAMERA CONTROL: Point drone toward structure center
                float headingToStructure = calculateHeadingToStructure(photoPoint.offsetX, photoPoint.offsetY);
                photoWaypoint.heading = (int) headingToStructure;

                // FIXED: Cast float to int for gimbal pitch
                int gimbalPitchValue = Math.round(photoPoint.gimbalPitch);
                photoWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, gimbalPitchValue));
                photoWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));

                // TRACK THIS AS A PHOTO WAYPOINT
                photoWaypointIndices.add(totalWaypointCount);

                builder.addWaypoint(photoWaypoint);
                totalWaypointCount++;

                lastPhotoLat = photoLatitude;
                lastPhotoLon = photoLongitude;
            }

            // Add safety waypoints ONLY if this is not the last structure
            if (i < inspectionPoints.size() - 1) {
                // Safety Waypoint 1: Fly up to safety altitude at last photo position
                Waypoint safetyWaypoint1 = new Waypoint(
                        lastPhotoLat,
                        lastPhotoLon,
                        SAFETY_ALTITUDE + point.groundAltitude
                );
                safetyWaypoint1.heading = 0;
                builder.addWaypoint(safetyWaypoint1);
                totalWaypointCount++;

                InspectionPoint nextPoint = inspectionPoints.get(i + 1);

                // Safety Waypoint 2: Fly to next structure coordinates at safety altitude
                Waypoint safetyWaypoint2 = new Waypoint(
                        nextPoint.latitude,
                        nextPoint.longitude,
                        SAFETY_ALTITUDE + nextPoint.groundAltitude
                );
                safetyWaypoint2.heading = 0;
                builder.addWaypoint(safetyWaypoint2);
                totalWaypointCount++;
            }
        }

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
                    updateStatus("Missão completa carregada com " + totalWaypointCount + " waypoints (" + photoWaypointIndices.size() + " fotos)");
                    if (btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                    }
                } else {
                    updateStatus("Erro ao carregar missão: " + errorMsg);
                }

                updateAdvancedMissionInfo();
            }
        });
    }

    private float calculateHeadingToStructure(float offsetX, float offsetY) {
        double angleRadians = Math.atan2(-offsetX, -offsetY);
        float angleDegrees = (float) Math.toDegrees(angleRadians);
        float headingDegrees = 90 - angleDegrees;

        while (headingDegrees < 0) headingDegrees += 360;
        while (headingDegrees >= 360) headingDegrees -= 360;

        return headingDegrees;
    }

    private void uploadAndStartMission() {
        if (waypointMissionOperator != null &&
                (WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState()) ||
                        WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState()))) {

            enableObstacleAvoidance(true);

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
                                                    if (btnPause != null) {
                                                        btnPause.setEnabled(true);
                                                        btnPause.setChecked(false);
                                                    }
                                                    if (btnStopMission != null) {
                                                        btnStopMission.setEnabled(true);
                                                    }
                                                    if (btnStartMission != null) {
                                                        btnStartMission.setEnabled(false);
                                                    }
                                                    isMissionPaused = false;
                                                    missionPausedForPhotoReview = false;

                                                    updateAdvancedMissionInfo();
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
            String currentStateString = "Operator not initialized";
            if (waypointMissionOperator != null) {
                WaypointMissionState currentState = waypointMissionOperator.getCurrentState();
                currentStateString = currentState != null ? currentState.toString() : "Unknown state";
            }
            updateStatus("Missão não está pronta para envio. Estado atual: " + currentStateString);
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
                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(true);
                                    btnStartMission.setText("Continuar Missão");
                                }
                                isMissionPaused = true;

                                updateAdvancedMissionInfo();
                            } else {
                                updateStatus("Falha ao pausar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    // AUTOMATIC MISSION RESUME
    private void resumeMissionAutomatically() {
        if (waypointMissionOperator != null) {
            enableObstacleAvoidance(true);

            waypointMissionOperator.resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                Log.d(TAG, "✅ Mission resumed automatically after photo acceptance");
                                updateStatus("Missão retomada automaticamente");

                                if (btnPause != null) {
                                    btnPause.setEnabled(true);
                                    btnPause.setChecked(false);
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(false);
                                    btnStartMission.setText("Iniciar Missão");
                                }
                                if (btnStopMission != null) {
                                    btnStopMission.setEnabled(true);
                                }

                                isMissionPaused = false;

                                if (missionPausedForPhotoReview) {
                                    missionPausedForPhotoReview = false;
                                    isWaitingForReview = false;
                                }

                                updateAdvancedMissionInfo();
                            } else {
                                Log.e(TAG, "❌ Failed to resume mission automatically: " + djiError.getDescription());
                                updateStatus("Falha ao retomar missão: " + djiError.getDescription());

                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(true);
                                    btnStartMission.setText("Continuar Missão");
                                }
                            }
                        }
                    });
                }
            });
        } else {
            Log.e(TAG, "❌ Cannot resume: WaypointMissionOperator is null");
            updateStatus("Erro: Operador de missão não disponível");

            post(new Runnable() {
                @Override
                public void run() {
                    if (btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                        btnStartMission.setText("Continuar Missão");
                    }
                }
            });
        }
    }

    private void stopMissionAndReturnHome() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão encerrada, retornando para posição inicial...");

                                enableObstacleAvoidance(true);
                                createReturnToHomeMission();

                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                    btnPause.setChecked(false);
                                }
                                if (btnStopMission != null) {
                                    btnStopMission.setEnabled(false);
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(true);
                                    btnStartMission.setText("Iniciar Missão");
                                }
                                isMissionPaused = false;
                                missionPausedForPhotoReview = false;

                                updateAdvancedMissionInfo();
                            } else {
                                updateStatus("Falha ao encerrar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private void createReturnToHomeMission() {
        if (flightController != null) {
            updateStatus("Configurando retorno para casa...");

            // 1. PRIMEIRO: Configurar altitude RTH
            flightController.setGoHomeHeightInMeters((int) SAFETY_ALTITUDE, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        Log.d(TAG, "RTH altitude set to: " + SAFETY_ALTITUDE + "m");
                        updateStatus("Altitude RTH configurada: " + SAFETY_ALTITUDE + "m");

                        // 2. DEPOIS: Iniciar Go Home com altitude configurada
                        flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (djiError == null) {
                                            updateStatus("Retornando para casa com altitude: " + SAFETY_ALTITUDE + "m");
                                            Log.d(TAG, "Go Home started successfully with custom altitude");

                                            // Atualizar estados dos botões
                                            if (btnPause != null) {
                                                btnPause.setEnabled(false);
                                                btnPause.setChecked(false);
                                            }
                                            if (btnStopMission != null) {
                                                btnStopMission.setEnabled(false);
                                            }
                                            if (btnStartMission != null) {
                                                btnStartMission.setEnabled(true);
                                                btnStartMission.setText("Iniciar Missão");
                                            }

                                            // Reset das variáveis de estado
                                            isMissionPaused = false;
                                            missionPausedForPhotoReview = false;
                                            isWaitingForReview = false;

                                            updateAdvancedMissionInfo();
                                        } else {
                                            updateStatus("Falha ao iniciar Go Home: " + djiError.getDescription());
                                            Log.e(TAG, "Failed to start Go Home: " + djiError.getDescription());

                                            // Reabilitar controles em caso de erro
                                            if (btnStartMission != null) {
                                                btnStartMission.setEnabled(true);
                                                btnStartMission.setText("Iniciar Missão");
                                            }
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to set RTH altitude: " + djiError.getDescription());
                        updateStatus("Falha ao configurar altitude RTH: " + djiError.getDescription());

                        // 3. FALLBACK: Go Home com altitude padrão se falhar configuração
                        flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (djiError == null) {
                                            updateStatus("Retornando para casa (altitude padrão do sistema)");
                                            Log.w(TAG, "Go Home started with default system altitude");

                                            // Atualizar estados dos botões
                                            if (btnPause != null) {
                                                btnPause.setEnabled(false);
                                                btnPause.setChecked(false);
                                            }
                                            if (btnStopMission != null) {
                                                btnStopMission.setEnabled(false);
                                            }
                                            if (btnStartMission != null) {
                                                btnStartMission.setEnabled(true);
                                                btnStartMission.setText("Iniciar Missão");
                                            }

                                            // Reset das variáveis de estado
                                            isMissionPaused = false;
                                            missionPausedForPhotoReview = false;
                                            isWaitingForReview = false;

                                            updateAdvancedMissionInfo();
                                        } else {
                                            updateStatus("Falha crítica ao iniciar Go Home: " + djiError.getDescription());
                                            Log.e(TAG, "Critical failure - Go Home failed: " + djiError.getDescription());

                                            // Reabilitar controles em caso de erro crítico
                                            if (btnStartMission != null) {
                                                btnStartMission.setEnabled(true);
                                                btnStartMission.setText("Iniciar Missão");
                                            }
                                            if (btnStopMission != null) {
                                                btnStopMission.setEnabled(false);
                                            }
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            });
        } else {
            // 4. ERRO: FlightController não disponível
            Log.e(TAG, "FlightController is null - cannot initiate return to home");
            updateStatus("Erro: Controlador de voo não disponível");

            post(new Runnable() {
                @Override
                public void run() {
                    // Reabilitar controles
                    if (btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                        btnStartMission.setText("Iniciar Missão");
                    }
                    if (btnStopMission != null) {
                        btnStopMission.setEnabled(false);
                    }
                    if (btnPause != null) {
                        btnPause.setEnabled(false);
                        btnPause.setChecked(false);
                    }

                    // Reset das variáveis de estado
                    isMissionPaused = false;
                    missionPausedForPhotoReview = false;
                    isWaitingForReview = false;

                    updateAdvancedMissionInfo();
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

            // ROBUST PHOTO DETECTION WITH TIMEOUT
            @Override
            public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent event) {
                if (event.getProgress() != null) {
                    final int currentWaypointIndex = event.getProgress().targetWaypointIndex;

                    boolean isPhotoWaypoint = photoWaypointIndices != null && photoWaypointIndices.contains(currentWaypointIndex);

                    if (isPhotoWaypoint && photoPoints.size() > 0) {
                        int photoWaypointPosition = photoWaypointIndices.indexOf(currentWaypointIndex);
                        int photosPerInspection = photoPoints.size();

                        currentInspectionIndex = photoWaypointPosition / photosPerInspection;
                        currentPhotoIndex = photoWaypointPosition % photosPerInspection;
                    }

                    post(new Runnable() {
                        @Override
                        public void run() {
                            updateMissionProgress();
                            updateAdvancedMissionInfo();
                        }
                    });

                    WaypointMissionState currentState = event.getCurrentState();
                    boolean waypointReached = event.getProgress().isWaypointReached;

                    Log.d(TAG, "=== WAYPOINT UPDATE DEBUG ===");
                    Log.d(TAG, "Waypoint: " + currentWaypointIndex + "/" + totalWaypointCount);
                    Log.d(TAG, "Is photo waypoint: " + isPhotoWaypoint);
                    Log.d(TAG, "Waypoint reached: " + waypointReached);
                    Log.d(TAG, "Mission state: " + (currentState != null ? currentState.toString() : "null")); // Use toString() for DJI SDK enums
                    Log.d(TAG, "Mission paused for review: " + missionPausedForPhotoReview);
                    Log.d(TAG, "Waiting for review: " + isWaitingForReview);
                    Log.d(TAG, "Last processed: " + lastPhotoWaypointProcessed);

                    updateStatus("Waypoint: " + currentWaypointIndex + "/" + totalWaypointCount +
                            " | Estrutura: " + (currentInspectionIndex + 1) + "/" + inspectionPoints.size() +
                            " | Foto: " + (currentPhotoIndex + 1) + "/" + photoPoints.size() +
                            " | Foto?: " + (isPhotoWaypoint ? "SIM" : "NÃO") +
                            " | Reached: " + waypointReached);

                    // PHOTO REVIEW LOGIC - ROBUST CONDITIONS
                    if (isPhotoWaypoint && currentWaypointIndex != lastPhotoWaypointProcessed) {

                        boolean primaryConditions = waypointReached &&
                                currentState == WaypointMissionState.EXECUTING &&
                                !missionPausedForPhotoReview &&
                                !isWaitingForReview;

                        boolean secondaryConditions = (currentState == WaypointMissionState.EXECUTING ||
                                currentState == WaypointMissionState.EXECUTION_PAUSED) &&
                                !missionPausedForPhotoReview &&
                                !isWaitingForReview;

                        boolean forceCondition = forceNextPhotoReview;

                        Log.d(TAG, "Primary conditions: " + primaryConditions);
                        Log.d(TAG, "Secondary conditions: " + secondaryConditions);
                        Log.d(TAG, "Force condition: " + forceCondition);

                        if (primaryConditions || forceCondition) {
                            Log.d(TAG, "✅ TRIGGERING photo review (primary/force) for waypoint: " + currentWaypointIndex);
                            lastPhotoWaypointProcessed = currentWaypointIndex;
                            forceNextPhotoReview = false;
                            triggerPhotoReview();

                        } else if (secondaryConditions) {
                            Log.d(TAG, "⏰ Setting up TIMEOUT for photo review at waypoint: " + currentWaypointIndex);
                            setupPhotoReviewTimeout(currentWaypointIndex);
                        }
                    }
                }
            }

            @Override
            public void onExecutionStart() {
                updateStatus("Execução da missão iniciada");
                enableObstacleAvoidance(true);
            }

            @Override
            public void onExecutionFinish(@Nullable final DJIError error) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            updateStatus("Missão concluída com sucesso");

                            if (btnPause != null) {
                                btnPause.setEnabled(false);
                                btnPause.setChecked(false);
                            }
                            if (btnStopMission != null) {
                                btnStopMission.setEnabled(false);
                            }
                            if (btnStartMission != null) {
                                btnStartMission.setEnabled(true);
                                btnStartMission.setText("Iniciar Missão");
                            }
                            isMissionPaused = false;
                            missionPausedForPhotoReview = false;
                            isWaitingForReview = false;

                            updateAdvancedMissionInfo();
                        } else {
                            updateStatus("Falha na execução da missão: " + error.getDescription());
                        }

                        missionPausedForPhotoReview = false;
                        isWaitingForReview = false;
                    }
                });
            }
        };

        if (waypointMissionOperator != null) {
            waypointMissionOperator.addListener(listener);
        }
    }

    // PHOTO REVIEW TIMEOUT SYSTEM
    private void setupPhotoReviewTimeout(final int waypointIndex) {
        if (photoTimeoutRunnable != null) {
            photoTimeoutHandler.removeCallbacks(photoTimeoutRunnable);
        }

        photoTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "⚠️ TIMEOUT triggered for waypoint: " + waypointIndex);

                if (waypointIndex != lastPhotoWaypointProcessed &&
                        !missionPausedForPhotoReview &&
                        !isWaitingForReview) {

                    Log.d(TAG, "🚨 FORCING photo review due to timeout");
                    lastPhotoWaypointProcessed = waypointIndex;
                    triggerPhotoReview();
                }
            }
        };

        photoTimeoutHandler.postDelayed(photoTimeoutRunnable, 3000);
    }

    private void triggerPhotoReview() {
        if (photoTimeoutRunnable != null) {
            photoTimeoutHandler.removeCallbacks(photoTimeoutRunnable);
            photoTimeoutRunnable = null;
        }

        Log.d(TAG, "📸 Starting photo review process...");
        pauseMissionForPhotoReview();
    }

    // AUTOMATIC PHOTO TAKING AND REVIEW
    private void pauseMissionForPhotoReview() {
        if (waypointMissionOperator != null) {
            if (photoTimeoutRunnable != null) {
                photoTimeoutHandler.removeCallbacks(photoTimeoutRunnable);
                photoTimeoutRunnable = null;
            }

            waypointMissionOperator.pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        missionPausedForPhotoReview = true;
                        isMissionPaused = true;
                        Log.d(TAG, "✅ Mission paused successfully for photo review");
                        updateStatus("Missão pausada para revisão de foto");

                        post(new Runnable() {
                            @Override
                            public void run() {
                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                    btnPause.setChecked(true);
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(false);
                                    btnStartMission.setText("Aguardando confirmação...");
                                }
                                if (btnStopMission != null) {
                                    btnStopMission.setEnabled(true);
                                }

                                updateAdvancedMissionInfo();
                            }
                        });

                        // ENHANCED: Wait for photo to be taken and processed by DJI, then fetch it
                        fetchLatestDronePhotoWithRetry();
                    } else {
                        Log.e(TAG, "❌ Failed to pause mission: " + djiError.getDescription());
                        updateStatus("Falha ao pausar para revisão: " + djiError.getDescription());

                        missionPausedForPhotoReview = false;
                        isMissionPaused = false;
                    }
                }
            });
        } else {
            Log.e(TAG, "❌ WaypointMissionOperator is null!");
            updateStatus("Erro: Operador de missão não disponível");
        }
    }

    // ENHANCED: Robust photo fetching with retry mechanism
    private void fetchLatestDronePhotoWithRetry() {
        Log.d(TAG, "🔄 Starting enhanced photo fetch with retry mechanism");

        // Reset retry counter and set fetch in progress
        photoFetchRetryCount = 0;
        isPhotoFetchInProgress = true;
        lastPhotoTakenTime = System.currentTimeMillis();

        updateStatus("Aguardando processamento da foto...");

        // Wait a bit for the DJI camera to process and save the photo
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                attemptPhotoFetch();
            }
        }, 3000); // Initial wait of 3 seconds
    }

    // ENHANCED: Single photo fetch attempt with comprehensive error handling
    private void attemptPhotoFetch() {
        if (!isPhotoFetchInProgress) {
            Log.d(TAG, "Photo fetch cancelled - no longer in progress");
            return;
        }

        Log.d(TAG, "📸 Attempt " + (photoFetchRetryCount + 1) + "/" + MAX_PHOTO_FETCH_RETRIES + " to fetch photo");
        updateStatus("Tentativa " + (photoFetchRetryCount + 1) + " de buscar foto...");

        // Check if we have proper setup
        if (!validatePhotoFetchSetup()) {
            handlePhotoFetchFailure("Setup de busca de foto inválido");
            return;
        }

        // Start the fetch process
        refreshFileListForPhotoFetch();
    }

    // ENHANCED: Validate that we have everything needed for photo fetching
    private boolean validatePhotoFetchSetup() {
        if (camera == null) {
            Log.e(TAG, "❌ Camera is null");
            return false;
        }

        if (mediaManager == null) {
            Log.e(TAG, "❌ MediaManager is null");
            setupMediaManagerForPhotoFetching(); // Try to re-initialize
            return mediaManager != null;
        }

        if (scheduler == null) {
            Log.e(TAG, "❌ Scheduler is null");
            return false;
        }

        return true;
    }

    // ENHANCED: Refresh file list with comprehensive error handling
    private void refreshFileListForPhotoFetch() {
        Log.d(TAG, "🔄 Refreshing file list for storage: " + storageLocation);

        mediaManager.refreshFileListOfStorageLocation(storageLocation, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Log.d(TAG, "✅ File list refresh successful");
                    // Small delay to ensure file list is fully updated
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            getLatestPhotoFromFileList();
                        }
                    }, 1000);
                } else {
                    Log.e(TAG, "❌ File list refresh failed: " + djiError.getDescription());

                    // Try alternative storage location
                    if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) {
                        Log.d(TAG, "🔄 Trying SD card as alternative storage");
                        storageLocation = SettingsDefinitions.StorageLocation.SDCARD;
                        refreshFileListForPhotoFetch();
                    } else {
                        Log.d(TAG, "🔄 Trying internal storage as alternative");
                        storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
                        refreshFileListForPhotoFetch();
                    }
                }
            }
        });
    }

    // ENHANCED: Get latest photo from file list with better filtering
    private void getLatestPhotoFromFileList() {
        if (!isPhotoFetchInProgress) {
            Log.d(TAG, "Photo fetch cancelled during file list processing");
            return;
        }

        Log.d(TAG, "📁 Getting latest photo from file list");

        List<MediaFile> mediaFiles;
        if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
            mediaFiles = mediaManager.getSDCardFileListSnapshot();
        } else {
            mediaFiles = mediaManager.getInternalStorageFileListSnapshot();
        }

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            Log.d(TAG, "📋 Found " + mediaFiles.size() + " media files");

            // Find the most recent photo that was taken after we started the photo process
            MediaFile latestPhoto = findMostRecentPhoto(mediaFiles);

            if (latestPhoto != null) {
                Log.d(TAG, "📸 Latest photo found: " + latestPhoto.getFileName() +
                        ", Size: " + latestPhoto.getFileSize() + " bytes");

                fetchPhotoContent(latestPhoto);
            } else {
                Log.w(TAG, "⚠️ No suitable recent photo found");
                handlePhotoFetchRetry("Nenhuma foto recente encontrada");
            }
        } else {
            Log.w(TAG, "⚠️ Media file list is empty");
            handlePhotoFetchRetry("Lista de arquivos vazia");
        }
    }

    // ENHANCED: Find most recent photo with better filtering
    private MediaFile findMostRecentPhoto(List<MediaFile> mediaFiles) {
        MediaFile mostRecent = null;
        long mostRecentTime = lastPhotoTakenTime - 1000; // Allow 1 seconds before our photo process started

        for (MediaFile mediaFile : mediaFiles) {
            // Only consider photo files
            if (mediaFile.getMediaType() == MediaFile.MediaType.JPEG ||
                    mediaFile.getMediaType() == MediaFile.MediaType.RAW_DNG) {

                long fileTime = mediaFile.getTimeCreated();

                Log.d(TAG, "🔍 Checking photo: " + mediaFile.getFileName() +
                        ", Created: " + new java.util.Date(fileTime) +
                        ", Size: " + mediaFile.getFileSize());

                if (fileTime > mostRecentTime) {
                    mostRecent = mediaFile;
                    mostRecentTime = fileTime;
                }
            }
        }

        if (mostRecent != null) {
            Log.d(TAG, "✅ Selected most recent photo: " + mostRecent.getFileName() +
                    ", Created: " + new java.util.Date(mostRecentTime));
        }

        return mostRecent;
    }

    // ENHANCED: Fetch photo content with multiple fallback strategies
    private void fetchPhotoContent(MediaFile mediaFile) {
        if (!isPhotoFetchInProgress) {
            Log.d(TAG, "Photo fetch cancelled during content fetch");
            return;
        }

        Log.d(TAG, "📥 Fetching photo content for: " + mediaFile.getFileName());
        updateStatus("Carregando foto: " + mediaFile.getFileName());

        // Try thumbnail first (faster)
        FetchMediaTask thumbnailTask = new FetchMediaTask(mediaFile, FetchMediaTaskContent.THUMBNAIL,
                new FetchMediaTask.Callback() {
                    @Override
                    public void onUpdate(MediaFile file, FetchMediaTaskContent content, DJIError error) {
                        if (!isPhotoFetchInProgress) {
                            Log.d(TAG, "Photo fetch cancelled during thumbnail fetch");
                            return;
                        }

                        if (error == null && content == FetchMediaTaskContent.THUMBNAIL) {
                            Bitmap thumbnail = file.getThumbnail();
                            if (thumbnail != null) {
                                Log.d(TAG, "✅ Thumbnail fetched successfully");
                                handlePhotoFetchSuccess(thumbnail);
                            } else {
                                Log.w(TAG, "⚠️ Thumbnail is null, trying preview");
                                fetchPhotoPreview(mediaFile);
                            }
                        } else {
                            Log.e(TAG, "❌ Thumbnail fetch failed: " + (error != null ? error.getDescription() : "Unknown"));
                            fetchPhotoPreview(mediaFile);
                        }
                    }
                });

        scheduler.moveTaskToNext(thumbnailTask);
    }

    // ENHANCED: Fetch preview as fallback
    private void fetchPhotoPreview(MediaFile mediaFile) {
        if (!isPhotoFetchInProgress) {
            Log.d(TAG, "Photo fetch cancelled during preview fetch");
            return;
        }

        Log.d(TAG, "🖼️ Fetching photo preview as fallback");
        updateStatus("Carregando preview da foto...");

        FetchMediaTask previewTask = new FetchMediaTask(mediaFile, FetchMediaTaskContent.PREVIEW,
                new FetchMediaTask.Callback() {
                    @Override
                    public void onUpdate(MediaFile file, FetchMediaTaskContent content, DJIError error) {
                        if (!isPhotoFetchInProgress) {
                            Log.d(TAG, "Photo fetch cancelled during preview fetch");
                            return;
                        }

                        if (error == null && content == FetchMediaTaskContent.PREVIEW) {
                            Bitmap preview = file.getPreview();
                            if (preview != null) {
                                Log.d(TAG, "✅ Preview fetched successfully");
                                handlePhotoFetchSuccess(preview);
                            } else {
                                Log.w(TAG, "⚠️ Preview is also null");
                                handlePhotoFetchRetry("Preview da foto não disponível");
                            }
                        } else {
                            Log.e(TAG, "❌ Preview fetch failed: " + (error != null ? error.getDescription() : "Unknown"));
                            handlePhotoFetchRetry("Erro ao carregar preview: " + (error != null ? error.getDescription() : "Desconhecido"));
                        }
                    }
                });

        scheduler.moveTaskToNext(previewTask);
    }

    // ENHANCED: Handle successful photo fetch
    private void handlePhotoFetchSuccess(Bitmap photo) {
        isPhotoFetchInProgress = false;
        photoFetchRetryCount = 0;

        Log.d(TAG, "🎉 Photo fetch SUCCESS! Showing confirmation dialog");

        post(new Runnable() {
            @Override
            public void run() {
                lastPhotoTaken = photo;
                updateStatus("Foto carregada com sucesso!");
                showPhotoConfirmationDialog(photo);
            }
        });
    }

    // ENHANCED: Handle photo fetch retry logic
    private void handlePhotoFetchRetry(String reason) {
        photoFetchRetryCount++;

        Log.w(TAG, "⚠️ Photo fetch attempt " + photoFetchRetryCount + " failed: " + reason);

        if (photoFetchRetryCount < MAX_PHOTO_FETCH_RETRIES) {
            Log.d(TAG, "🔄 Retrying photo fetch in " + (PHOTO_FETCH_RETRY_DELAY / 1000) + " seconds...");
            updateStatus("Tentativa " + photoFetchRetryCount + " falhou. Tentando novamente...");

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    attemptPhotoFetch();
                }
            }, PHOTO_FETCH_RETRY_DELAY);
        } else {
            handlePhotoFetchFailure("Máximo de tentativas atingido: " + reason);
        }
    }

    // ENHANCED: Handle complete photo fetch failure
    private void handlePhotoFetchFailure(String reason) {
        isPhotoFetchInProgress = false;
        photoFetchRetryCount = 0;

        Log.e(TAG, "❌ Photo fetch FAILED completely: " + reason);

        post(new Runnable() {
            @Override
            public void run() {
                updateStatus("Falha ao carregar foto: " + reason);
                showPhotoErrorDialog();
            }
        });
    }

    // MODIFIED: Show error dialog when real photo cannot be loaded
    private void showPhotoErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Erro ao Carregar Foto")
                .setMessage("Não foi possível carregar a foto do drone após " + MAX_PHOTO_FETCH_RETRIES +
                        " tentativas. Deseja tentar novamente ou continuar sem foto?")
                .setPositiveButton("Tentar Novamente", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                        updateStatus("Tentando buscar foto novamente...");
                        fetchLatestDronePhotoWithRetry();
                    }
                })
                .setNegativeButton("Continuar Missão", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                        updateStatus("Continuando missão sem confirmação de foto...");
                        // Continue mission without photo confirmation
                        resumeMissionAutomatically();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // ENHANCED: Photo confirmation dialog with real drone photo
    private void showPhotoConfirmationDialog(Bitmap photo) {
        Log.d(TAG, "📱 Showing photo confirmation dialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        int orientation = getResources().getConfiguration().orientation;
        View dialogView;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_photo_confirmation_land, null);
        } else {
            dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_photo_confirmation, null);
        }

        builder.setView(dialogView);

        ImageView photoImageView = dialogView.findViewById(R.id.popup_image_preview);
        TextView photoDetailsText = dialogView.findViewById(R.id.text_photo_details);
        Button retakeButton = dialogView.findViewById(R.id.btn_popup_retake);
        Button acceptButton = dialogView.findViewById(R.id.btn_popup_accept);

        photoImageView.setImageBitmap(photo);
        photoDetailsText.setText("Estrutura: S" + (currentInspectionIndex + 1) + " | Posição: P" + (currentPhotoIndex + 1));

        final AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        retakeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                updateStatus("Tirando nova foto...");
                // Re-fetch the latest photo (DJI might have taken another one)
                fetchLatestDronePhotoWithRetry();
            }
        });

        acceptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();

                savePhotoToStorage(photo);

                isWaitingForReview = false;
                updateStatus("Foto aceita. Retomando missão automaticamente...");

                // AUTOMATIC RESUME
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resumeMissionAutomatically();
                    }
                }, 500);
            }
        });

        dialog.show();
    }

    // FORCE PHOTO REVIEW FOR MANUAL BUTTON - ENHANCED
    public void forcePhotoReview() {
        Log.d(TAG, "🔧 MANUAL photo review triggered");

        // Set force flag
        forceNextPhotoReview = true;

        // If no mission is running, just fetch the latest photo
        if (waypointMissionOperator == null ||
                waypointMissionOperator.getCurrentState() != WaypointMissionState.EXECUTING) {

            Log.d(TAG, "🔧 No active mission - fetching latest photo directly");
            updateStatus("Buscando última foto tirada...");
            fetchLatestDronePhotoWithRetry();
        } else {
            // Mission is running - trigger the normal photo review process
            triggerPhotoReview();
        }
    }

    private void savePhotoToStorage(Bitmap photo) {
        if (photo != null && photoStorageManager != null) {
            PhotoStorageManager.PhotoInfo savedPhoto =
                    photoStorageManager.savePhoto(photo, currentInspectionIndex + 1, currentPhotoIndex + 1);

            if (savedPhoto != null) {
                updateStatus("Foto salva em: " + savedPhoto.getFile().getAbsolutePath());

                if (viewFlipper != null && viewFlipper.getDisplayedChild() == 1) {
                    refreshGallery();

                    if (galleryViewFlipper != null && galleryViewFlipper.getDisplayedChild() == 1 &&
                            currentStructureId == currentInspectionIndex + 1) {
                        updatePhotosForCurrentStructure();
                    }
                }
            } else {
                updateStatus("Erro ao salvar foto");
            }
        }
    }

    private void tearDownListener() {
        if (waypointMissionOperator != null && listener != null) {
            waypointMissionOperator.removeListener(listener);
        }
    }

    // PHOTO GALLERY INTERFACE IMPLEMENTATION
    @Override
    public void onPhotoClick(PhotoStorageManager.PhotoInfo photoInfo) {
        showFullscreenPhotoView(photoInfo);
    }

    @Override
    public void onDownloadClick(PhotoStorageManager.PhotoInfo photoInfo) {
        sharePhoto(photoInfo);
    }

    @Override
    public void onDeleteClick(PhotoStorageManager.PhotoInfo photoInfo) {
        Log.d(TAG, "Delete photo clicked: " + photoInfo.getFilename());

        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar Exclusão")
                .setMessage("Deseja excluir esta foto?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    if (photoStorageManager.deletePhoto(photoInfo)) {
                        updateStatus("Foto excluída com sucesso");
                        refreshGallery();
                    } else {
                        updateStatus("Erro ao excluir foto");
                    }
                })
                .setNegativeButton("Não", null)
                .show();
    }

    private void showFullscreenPhotoView(PhotoStorageManager.PhotoInfo photoInfo) {
        if (photoInfo == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View fullscreenView = LayoutInflater.from(getContext()).inflate(R.layout.fullscreen_photo_view, null);
        builder.setView(fullscreenView);

        ImageView fullscreenImage = fullscreenView.findViewById(R.id.image_fullscreen_photo);
        TextView photoInfoText = fullscreenView.findViewById(R.id.text_fullscreen_photo_info);
        Button closeButton = fullscreenView.findViewById(R.id.btn_close_fullscreen);
        Button shareButton = fullscreenView.findViewById(R.id.btn_share_photo);
        Button deleteButton = fullscreenView.findViewById(R.id.btn_delete_fullscreen);

        Bitmap photoBitmap = BitmapFactory.decodeFile(photoInfo.getFile().getAbsolutePath());
        fullscreenImage.setImageBitmap(photoBitmap);
        photoInfoText.setText(photoInfo.getStructureId() + " | " + photoInfo.getPhotoId() + " | " + photoInfo.getTimestamp());

        final AlertDialog dialog = builder.create();

        closeButton.setOnClickListener(v -> dialog.dismiss());

        shareButton.setOnClickListener(v -> {
            dialog.dismiss();
            sharePhoto(photoInfo);
        });

        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            onDeleteClick(photoInfo);
        });

        dialog.show();
    }

    private void sharePhoto(PhotoStorageManager.PhotoInfo photoInfo) {
        if (photoInfo == null || !photoInfo.getFile().exists()) {
            updateStatus("Arquivo de foto não encontrado");
            return;
        }

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");

            Uri photoUri;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                String authority = getContext().getPackageName() + ".fileprovider";
                photoUri = androidx.core.content.FileProvider.getUriForFile(
                        getContext(),
                        authority,
                        photoInfo.getFile());

                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                photoUri = Uri.fromFile(photoInfo.getFile());
            }

            shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Foto de Inspeção: " +
                    photoInfo.getStructureId() + " " + photoInfo.getPhotoId());

            getContext().startActivity(Intent.createChooser(shareIntent, "Compartilhar via"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar foto: " + e.getMessage(), e);
            updateStatus("Erro ao compartilhar foto: " + e.getMessage());

            File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            File destFile = new File(downloadDir, photoInfo.getFilename());
            boolean success = false;

            try {
                success = copyFileUsingStream(photoInfo.getFile(), destFile);
                if (success) {
                    updateStatus("Foto salva em Downloads: " + destFile.getName());
                } else {
                    updateStatus("Não foi possível salvar a foto em Downloads");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Erro no método alternativo: " + ex.getMessage(), ex);
                updateStatus("Não foi possível compartilhar ou salvar a foto");
            }
        }
    }

    private boolean copyFileUsingStream(File source, File dest) {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(source));
            os = new BufferedOutputStream(new FileOutputStream(dest));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar arquivo: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {
                // Ignorar
            }
            try {
                if (os != null) os.close();
            } catch (Exception e) {
                // Ignorar
            }
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