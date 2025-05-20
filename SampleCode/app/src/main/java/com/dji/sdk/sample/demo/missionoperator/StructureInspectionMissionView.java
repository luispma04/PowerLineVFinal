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
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionDetectionState;
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
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightAssistant;
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

public class StructureInspectionMissionView extends MissionBaseView implements PhotoGalleryAdapter.OnPhotoClickListener {

    private static final String TAG = "StructureInspection";

    // Request codes for file selection
    private static final int REQUEST_STRUCTURES_CSV = 1001;
    private static final int REQUEST_PHOTO_POSITIONS_CSV = 1002;

    // Mission parameters
    private static final double ONE_METER_OFFSET = 0.00000899322; // Approximately 1 meter in GPS coordinates
    private static final float DEFAULT_ALTITUDE = 30.0f; // Default altitude in meters
    private static final float DEFAULT_SPEED = 5.0f; // Default speed in m/s
    private static final float SAFE_DISTANCE = 5.0f; // Safe distance from structures in meters

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
    private int currentInspectionIndex = 0;
    private int currentPhotoIndex = 0;
    private boolean isWaitingForReview = false;
    private Bitmap lastPhotoTaken = null;
    private SettingsDefinitions.StorageLocation storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;

    // Flag for simulator mode
    private boolean isSimulatorMode = true;
    // Store initial home location for return to home functionality
    private double initialHomeLat;
    private double initialHomeLon;
    private float initialHomeAlt;
    private boolean isMissionPaused = false;

    // Track created waypoints
    private int totalWaypointCount = 0;
    private boolean missionPausedForPhotoReview = false;

    // Obstacle avoidance data
    private boolean obstacleAvoidanceEnabled = false;
    private float closestObstacleDistance = 0.0f;
    private StringBuilder obstacleInfoBuilder = new StringBuilder();

    // Flag para rastrear se a visualização de live stream está ativa
    private boolean isLiveStreamActive = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // For orientation locking
    private int originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean orientationLocked = false;

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
        Log.d(TAG, "Initializing StructureInspectionMissionView with simulatorMode: " + simulatorMode);
        this.isSimulatorMode = simulatorMode;
        init(context);
    }

    // Variable to track which child is being displayed in ViewFlipper
    private int currentViewFlipperChild = 0;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.d(TAG, "Configuration changed to: " + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait"));

        try {
            // CRITICAL: Save ALL states BEFORE modifying any views
            boolean wasLiveStreamActive = isLiveStreamActive;
            int mainViewFlipperState = 0;
            int galleryViewFlipperState = 0;
            int savedStructureId = currentStructureId;

            if (viewFlipper != null) {
                mainViewFlipperState = viewFlipper.getDisplayedChild();
            }

            if (galleryViewFlipper != null) {
                galleryViewFlipperState = galleryViewFlipper.getDisplayedChild();
            }

            // First handle live stream state
            if (liveStreamContainer != null && liveStreamView != null) {
                liveStreamView.cleanup();
                liveStreamContainer.removeAllViews();
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

            // IMPORTANT: Always refresh the photo storage manager here
            // This ensures gallery data is always current
            if (photoStorageManager != null) {
                photoStorageManager.refresh();
            }

            // Initialize gallery components but don't load data yet
            initPhotoGallery();

            // Update states and connection
            updateConnectionStatus();
            updateViewsBasedOnState();

            // IMPORTANT: First restore normal main view state (mission vs gallery)
            if (viewFlipper != null) {
                viewFlipper.setDisplayedChild(mainViewFlipperState);
            }

            // IMPORTANT: Now handle gallery state and populate data if needed
            if (mainViewFlipperState == 1 && galleryViewFlipper != null) {
                // We're in gallery view, so refresh gallery data completely
                refreshGallery();

                if (galleryViewFlipperState == 0) {
                    // We were in structures list
                    showStructuresList();
                } else if (galleryViewFlipperState == 1 && savedStructureId > 0) {
                    // We were viewing photos for a specific structure
                    showPhotosForStructure(savedStructureId);
                }
            }

            // LAST: Restore live stream if it was active
            if (wasLiveStreamActive) {
                new Handler().postDelayed(() -> {
                    showLiveStreamView();
                }, 300);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during configuration change", e);
            updateStatus("Erro durante rotação do dispositivo: " + e.getMessage());
        }
    }


    // Modified init method
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
        //btnTakePhoto = findViewById(R.id.btn_take_photo);
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
        //btnTakePhoto.setEnabled(false);
        btnStartMission.setEnabled(false);
        btnPause.setEnabled(false);
        btnStopMission.setEnabled(false);

        //livestream
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
                    // Create a simulated photo and show the review dialog
                    Log.d(TAG, "Manual photo review button clicked");
                    simulatePhoto();
                    // Add small delay to ensure bitmap is ready
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (lastPhotoTaken != null) {
                                showPhotoConfirmationDialog(lastPhotoTaken);
                            } else {
                                Log.e(TAG, "lastPhotoTaken is null!");
                                // Create emergency placeholder photo if null
                                Bitmap placeholder = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                                placeholder.eraseColor(Color.BLUE);
                                showPhotoConfirmationDialog(placeholder);
                            }
                        }
                    }, 200);
                }
            });
        }
        //btnTakePhoto.setOnClickListener(this);
        btnStartMission.setOnClickListener(this);
        btnStopMission.setOnClickListener(this);

        // Set toggle listener for pause button
        btnPause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Resume mission
                    resumeMission();
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
            // Remove any existing listeners to prevent duplicates
            btnLiveStream.setOnClickListener(null);

            // Add new listener with better debug info
            btnLiveStream.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Live stream button clicked - orientation: " +
                            (getResources().getConfiguration().orientation ==
                                    Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait"));
                    showLiveStreamView();
                }
            });

            // Ensure the button is visible and enabled
            btnLiveStream.setVisibility(View.VISIBLE);
            btnLiveStream.setEnabled(true);
        } else {
            Log.e(TAG, "Live stream button not found in current layout");
        }
    }

    private void showLiveStreamView() {
        Log.d(TAG, "showLiveStreamView called");

        if (liveStreamContainer == null) {
            Log.e(TAG, "Error: Live stream container is null");
            // Try to find it again if null
            liveStreamContainer = findViewById(R.id.live_stream_container);
            if (liveStreamContainer == null) {
                updateStatus("Erro: Container de livestream não encontrado");
                return;
            }
        }

        try {
            // Clean up existing view
            if (liveStreamView != null) {
                liveStreamView.cleanup();
            }
            liveStreamContainer.removeAllViews();

            // Create a new live stream view with the proper orientation context
            liveStreamView = new StructureLiveStreamView(getContext());

            // IMPORTANT: Set close listener BEFORE adding to container
            liveStreamView.setOnCloseListener(new StructureLiveStreamView.OnCloseListener() {
                @Override
                public void onClose() {
                    Log.d(TAG, "Close listener triggered");
                    hideLiveStreamView();
                }
            });

            // Add view to container
            liveStreamContainer.addView(liveStreamView);

            // Make container visible
            liveStreamContainer.setVisibility(View.VISIBLE);

            // Update state flag
            isLiveStreamActive = true;

            // Lock orientation AFTER UI is visible and working
            lockCurrentOrientation();

            Log.d(TAG, "Live stream view successfully shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing live stream view", e);
            updateStatus("Erro ao mostrar livestream: " + e.getMessage());

            // Reset state
            isLiveStreamActive = false;
            unlockOrientation();

            if (liveStreamContainer != null) {
                liveStreamContainer.setVisibility(View.GONE);
            }
        }
    }

    public void hideLiveStreamView() {
        Log.d(TAG, "hideLiveStreamView called");

        try {
            // Reset state flag
            isLiveStreamActive = false;

            // Unlock orientation
            unlockOrientation();

            // Hide and clean container
            if (liveStreamContainer != null) {
                liveStreamContainer.setVisibility(View.GONE);
                liveStreamContainer.removeAllViews();
            }

            // Clean up view
            if (liveStreamView != null) {
                liveStreamView.cleanup();
                liveStreamView = null;
            }

            Log.d(TAG, "Live stream view hidden successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error hiding live stream view", e);
        }
    }

    // Add methods to lock and unlock orientation
    private void lockCurrentOrientation() {
        try {
            if (getContext() instanceof Activity) {
                Activity activity = (Activity) getContext();

                // Save current orientation
                originalOrientation = activity.getRequestedOrientation();

                // Get current rotation
                int currentRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

                // Lock to current orientation
                switch (currentRotation) {
                    case Surface.ROTATION_0:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        break;
                    case Surface.ROTATION_90:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        break;
                    case Surface.ROTATION_180:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                        break;
                    case Surface.ROTATION_270:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                        break;
                    default:
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        break;
                }

                orientationLocked = true;
                Log.d(TAG, "Orientation locked");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error locking orientation", e);
        }
    }

    private void unlockOrientation() {
        try {
            if (orientationLocked && getContext() instanceof Activity) {
                Activity activity = (Activity) getContext();

                // Restore original orientation
                activity.setRequestedOrientation(originalOrientation);

                orientationLocked = false;
                Log.d(TAG, "Orientation unlocked");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unlocking orientation", e);
        }
    }

    private void initPhotoGallery() {
        Log.d(TAG, "Initializing photo gallery");

        // Set up recycler view for structures
        if (recyclerStructures != null) {
            // Check if RecyclerView already has a LayoutManager
            if (recyclerStructures.getLayoutManager() == null) {
                int spanCount = getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
                recyclerStructures.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
            }
        }

        // Set up recycler view for photos
        if (recyclerPhotos != null) {
            // Check if RecyclerView already has a LayoutManager
            if (recyclerPhotos.getLayoutManager() == null) {
                int spanCount = getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
                recyclerPhotos.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
            }
        }

        // Inicializar os adaptadores
        refreshGallery();
    }

    private void refreshGallery() {
        Log.d(TAG, "Refreshing gallery");

        try {
            // Refresh photo storage data
            if (photoStorageManager != null) {
                photoStorageManager.refresh();

                // Get structures with photos
                List<Integer> structureIds = photoStorageManager.getStructureIdsWithPhotos();
                Log.d(TAG, "Found " + structureIds.size() + " structures with photos");

                // Update structures list view
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
                        // Ensure adapter is set
                        if (recyclerStructures.getAdapter() != structureFolderAdapter) {
                            recyclerStructures.setAdapter(structureFolderAdapter);
                        }
                    }
                }

                // Update "no structures" text visibility
                if (noStructuresText != null) {
                    noStructuresText.setVisibility(structureIds.isEmpty() ? View.VISIBLE : View.GONE);
                }

                // If viewing a specific structure, update photos
                if (currentStructureId > 0 && galleryViewFlipper != null &&
                        galleryViewFlipper.getDisplayedChild() == 1) {
                    updatePhotosForCurrentStructure();
                }
            } else {
                Log.e(TAG, "photoStorageManager is null in refreshGallery");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing gallery", e);
        }
    }

    // Methods for switching between mission and gallery views
    private void showGalleryView() {
        Log.d(TAG, "Showing gallery view");

        if (viewFlipper != null) {
            // IMPORTANT: Always refresh gallery data before showing
            if (photoStorageManager != null) {
                photoStorageManager.refresh();
            }

            // Force refresh the gallery content
            refreshGallery();

            // Show gallery page
            viewFlipper.setDisplayedChild(1);
            currentViewFlipperChild = 1;

            // Make sure we're showing the structures list first
            if (galleryViewFlipper != null) {
                galleryViewFlipper.setDisplayedChild(0);
            }

            // Update gallery title
            if (galleryTitleText != null) {
                galleryTitleText.setText("Galeria de Fotos por Estrutura");
            }
        }
    }


    private void showMissionView() {
        Log.d(TAG, "Showing mission view");

        if (viewFlipper != null) {
            viewFlipper.setDisplayedChild(0);
            currentViewFlipperChild = 0;
        }
    }

    // Methods for structure-based photo browsing
    private void showStructuresList() {
        Log.d(TAG, "Showing structures list");

        if (galleryViewFlipper != null) {
            galleryViewFlipper.setDisplayedChild(0);
            currentStructureId = -1;

            // Update gallery title
            if (galleryTitleText != null) {
                galleryTitleText.setText("Galeria de Fotos por Estrutura");
            }

            // Atualize a lista de estruturas
            if (structureFolderAdapter != null && recyclerStructures != null) {
                List<Integer> structureIds = photoStorageManager.getStructureIdsWithPhotos();
                structureFolderAdapter.updateStructureList(structureIds);

                // Ensure correct adapter is set
                recyclerStructures.setAdapter(structureFolderAdapter);

                // Update "No structures" text visibility
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

            // Update structure title
            if (structureTitleText != null) {
                structureTitleText.setText("Estrutura " + structureId);
            }

            // Update photos for this structure
            updatePhotosForCurrentStructure();

            // Show photos view
            galleryViewFlipper.setDisplayedChild(1);

            // Update gallery title
            if (galleryTitleText != null) {
                galleryTitleText.setText("Fotos da Estrutura " + structureId);
            }
        }
    }

    private void updatePhotosForCurrentStructure() {
        Log.d(TAG, "Updating photos for structure: " + currentStructureId);

        if (currentStructureId <= 0) {
            Log.d(TAG, "Invalid structure ID, not updating photos");
            return;
        }

        try {
            // Get photos for this structure
            List<PhotoStorageManager.PhotoInfo> photos = photoStorageManager.getPhotosForStructure(currentStructureId);
            Log.d(TAG, "Found " + photos.size() + " photos for structure: " + currentStructureId);

            // Update no photos text visibility
            if (noPhotosText != null) {
                noPhotosText.setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
            }

            // Update photo adapter
            if (recyclerPhotos != null) {
                if (photoGalleryAdapter == null) {
                    photoGalleryAdapter = new PhotoGalleryAdapter(getContext(), photos, this);
                    recyclerPhotos.setAdapter(photoGalleryAdapter);
                } else {
                    photoGalleryAdapter.updatePhotoList(photos);
                    // IMPORTANT: Always ensure adapter is set to RecyclerView
                    if (recyclerPhotos.getAdapter() != photoGalleryAdapter) {
                        recyclerPhotos.setAdapter(photoGalleryAdapter);
                    }
                }
                // Force adapter to refresh
                photoGalleryAdapter.notifyDataSetChanged();
            } else {
                Log.e(TAG, "recyclerPhotos is null in updatePhotosForCurrentStructure");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating photos for structure", e);
        }
    }

    private void updateViewsBasedOnState() {
        // Update mission progress
        updateMissionProgress();

        // Update advanced mission information
        updateAdvancedMissionInfo();

        // Update the button states based on mission state
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

        // Update CSV info
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
        // Update progress bar and text views with current mission progress
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

        // Show current position in mission
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

        // Calculate overall progress percentage
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

        // Add mission parameters
        info.append("PARÂMETROS DA MISSÃO:\n");
        info.append("Velocidade: ").append(DEFAULT_SPEED).append(" m/s\n");
        info.append("Distância segura: ").append(SAFE_DISTANCE).append(" m\n\n");

        // Add mission statistics
        info.append("ESTATÍSTICAS:\n");
        info.append("Estruturas: ").append(inspectionPoints.size()).append("\n");
        info.append("Posições de foto por estrutura: ").append(photoPoints.size()).append("\n");
        info.append("Total de waypoints: ").append(totalWaypointCount).append("\n\n");

        // Add flight mode information
        info.append("ESTADO DO VOO:\n");
        if (flightController != null) {
            info.append("Modo de voo: ").append(flightState != null ? flightState.name() : "Desconhecido").append("\n");
        }

        // Add mission state
        if (waypointMissionOperator != null) {
            WaypointMissionState state = waypointMissionOperator.getCurrentState();
            info.append("Estado da missão: ").append(state != null ? state.getName() : "Não iniciada").append("\n\n");
        }

        // Add obstacle information
        info.append("DETECÇÃO DE OBSTÁCULOS:\n");
        info.append("Evitamento de obstáculos: ").append(obstacleAvoidanceEnabled ? "Ativado" : "Desativado").append("\n");
        if (obstacleAvoidanceEnabled) {
            info.append("Distância mais próxima: ").append(String.format("%.2f", closestObstacleDistance)).append("m\n");
            if (obstacleInfoBuilder.length() > 0) {
                info.append(obstacleInfoBuilder.toString()).append("\n");
            }
        }
        info.append("\n");

        // Add current photo info if in mission
        if (currentInspectionIndex >= 0 && currentPhotoIndex >= 0 && !inspectionPoints.isEmpty() && !photoPoints.isEmpty()) {
            info.append("POSIÇÃO ATUAL:\n");
            info.append("Estrutura: ").append(currentInspectionIndex + 1)
                    .append("/").append(inspectionPoints.size()).append("\n");
            info.append("Foto: ").append(currentPhotoIndex + 1)
                    .append("/").append(photoPoints.size()).append("\n");

            // Calculate progress
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
        }else if (id == R.id.btn_start_mission) {
            if (isMissionPaused || missionPausedForPhotoReview) {
                resumeMission();
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

        // Initialize FlightAssistant
        if (flightController != null) {
            flightAssistant = flightController.getFlightAssistant();
            if (flightAssistant != null) {
                // Enable obstacle avoidance features
                enableObstacleAvoidance(true);
                // Set up obstacle detection callback
                setUpObstacleDetectionCallback();
            } else {
                Log.e(TAG, "FlightAssistant is null");
                updateStatus("FlightAssistant não disponível");
            }
        }

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
                                droneLocationText.setText("Localização: Lat: " + latitude + ", Lon: " + longitude + ", Alt: " + altitude + "m");
                            }

                            // Update advanced mission info whenever flight state changes
                            updateAdvancedMissionInfo();
                        }
                    });
                }
            });
        }

        waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        setUpListener();
    }

    /**
     * Enable obstacle avoidance features
     * @param enable true to enable, false to disable
     */
    private void enableObstacleAvoidance(final boolean enable) {
        if (flightAssistant == null) {
            Log.e(TAG, "FlightAssistant is null, cannot enable obstacle avoidance");
            return;
        }

        // Enable collision avoidance
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

        // Enable upward vision obstacle avoidance
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

        // Enable horizontal obstacle avoidance during RTH
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

        // Enable landing protection
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

        // Update obstacle avoidance state for UI
        obstacleAvoidanceEnabled = enable;
        updateAdvancedMissionInfo();
    }

    /**
     * Set up obstacle detection callback to receive obstacle information
     */
    private void setUpObstacleDetectionCallback() {
        if (flightAssistant == null) return;

        flightAssistant.setVisionDetectionStateUpdatedCallback(new VisionDetectionState.Callback() {
            @Override
            public void onUpdate(@NonNull VisionDetectionState visionDetectionState) {
                // Get obstacle detection sectors
                ObstacleDetectionSector[] sectors = visionDetectionState.getDetectionSectors();

                // Reset obstacle information
                obstacleInfoBuilder = new StringBuilder();
                closestObstacleDistance = Float.MAX_VALUE;

                // Process each sector
                for (int i = 0; i < sectors.length; i++) {
                    ObstacleDetectionSector sector = sectors[i];
                    float distance = sector.getObstacleDistanceInMeters();

                    // Track closest obstacle
                    if (distance < closestObstacleDistance && distance > 0) {
                        closestObstacleDistance = distance;
                    }

                    // Add warning for close obstacles
                    if (distance < 10 && distance > 0) {
                        obstacleInfoBuilder.append("Setor ").append(i + 1)
                                .append(": ").append(String.format("%.2f", distance))
                                .append("m (Perigo: ").append(sector.getWarningLevel().name())
                                .append(")\n");
                    }
                }

                // Update system warning
                obstacleInfoBuilder.append("Alerta do sistema: ")
                        .append(visionDetectionState.getSystemWarning().name());

                // Update UI
                post(new Runnable() {
                    @Override
                    public void run() {
                        updateAdvancedMissionInfo();
                    }
                });
            }
        });
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

                // Update advanced mission info
                updateAdvancedMissionInfo();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        // Make sure we unlock orientation
        unlockOrientation();

        // Clean up live stream
        if (liveStreamView != null) {
            liveStreamView.cleanup();
            liveStreamView = null;
        }

        if (liveStreamContainer != null) {
            liveStreamContainer.removeAllViews();
        }

        // Clear handler callbacks
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
                    if (csvInfoText != null) {
                        csvInfoText.setText(inspectionPoints.size() + " estruturas carregadas");
                    }

                    if (!inspectionPoints.isEmpty() && !photoPoints.isEmpty() && btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                    }

                    updateStatus("Carregado " + inspectionPoints.size() + " estruturas");
                    updateAdvancedMissionInfo();
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

        // Reset current indices
        currentInspectionIndex = 0;
        currentPhotoIndex = 0;
        isMissionPaused = false;
        missionPausedForPhotoReview = false;

        // Enable obstacle avoidance before starting mission
        enableObstacleAvoidance(true);

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
                    if (btnStartMission != null) {
                        btnStartMission.setEnabled(true);
                    }
                } else {
                    updateStatus("Erro ao carregar missão: " + errorMsg);
                }

                // Update advanced mission info
                updateAdvancedMissionInfo();
            }
        });
    }

    private void uploadAndStartMission() {
        if (waypointMissionOperator != null &&
                (WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState()) ||
                        WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState()))) {

            // Make sure obstacle avoidance is enabled
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
                                                        btnPause.setChecked(false); // Set to pause icon
                                                    }
                                                    if (btnStopMission != null) {
                                                        btnStopMission.setEnabled(true);
                                                    }
                                                    if (btnStartMission != null) {
                                                        btnStartMission.setEnabled(false);
                                                    }
                                                    isMissionPaused = false;
                                                    missionPausedForPhotoReview = false;

                                                    // Update advanced mission info
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
                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(true); // Use same button to resume
                                    btnStartMission.setText("Continuar Missão");
                                }
                                isMissionPaused = true;

                                // Update advanced mission info
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

    private void resumeMission() {
        if (waypointMissionOperator != null) {
            // Make sure obstacle avoidance is enabled before resuming
            enableObstacleAvoidance(true);

            waypointMissionOperator.resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão retomada");
                                if (btnPause != null) {
                                    btnPause.setEnabled(true);
                                    btnPause.setChecked(false); // Set to pause icon
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(false);
                                    btnStartMission.setText("Iniciar Missão");
                                }
                                isMissionPaused = false;

                                // Reset photo review flags when resuming
                                if (missionPausedForPhotoReview) {
                                    missionPausedForPhotoReview = false;
                                    isWaitingForReview = false;
                                }

                                // Update advanced mission info
                                updateAdvancedMissionInfo();
                            } else {
                                updateStatus("Falha ao retomar missão: " + djiError.getDescription());
                            }
                        }
                    });
                }
            });
        }
    }

    private void stopMissionAndReturnHome() {
        if (waypointMissionOperator != null) {
            // First stop the current mission
            waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(final DJIError djiError) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (djiError == null) {
                                updateStatus("Missão encerrada, retornando para posição inicial...");

                                // Make sure obstacle avoidance is enabled for return home
                                enableObstacleAvoidance(true);

                                // Create a return to home mission
                                createReturnToHomeMission();

                                // Reset UI
                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                    btnPause.setChecked(false); // Set to pause icon
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

                                // Update advanced mission info
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

                    // Update mission progress
                    post(new Runnable() {
                        @Override
                        public void run() {
                            updateMissionProgress();
                            updateAdvancedMissionInfo();
                        }
                    });

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

                // Make sure obstacle avoidance is enabled
                enableObstacleAvoidance(true);
            }

            @Override
            public void onExecutionFinish(@Nullable final DJIError error) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            updateStatus("Missão concluída com sucesso");

                            // Reset UI
                            if (btnPause != null) {
                                btnPause.setEnabled(false);
                                btnPause.setChecked(false); // Set to pause icon
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

                            // Reset photo review states
                            isWaitingForReview = false;

                            // Update advanced mission info
                            updateAdvancedMissionInfo();
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
                                if (btnPause != null) {
                                    btnPause.setEnabled(false);
                                    btnPause.setChecked(true); // Set to resume icon
                                }
                                if (btnStartMission != null) {
                                    btnStartMission.setEnabled(false); // Will be enabled after photo review
                                    btnStartMission.setText("Continuar Missão");
                                }

                                // Update advanced mission info
                                updateAdvancedMissionInfo();
                            }
                        });

                        // Fetch the photo for review - with special handling for simulator mode
                        if (isSimulatorMode) {
                            // Force create a simulated photo and ensure it's run on the UI thread
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "Simulating photo in simulator mode");
                                    simulatePhoto();
                                    Log.d(TAG, "About to show photo dialog with simulated photo");
                                    // Add small delay to ensure bitmap is ready
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (lastPhotoTaken != null) {
                                                showPhotoConfirmationDialog(lastPhotoTaken);
                                            } else {
                                                Log.e(TAG, "lastPhotoTaken is null!");
                                                // Create emergency placeholder photo if null
                                                Bitmap placeholder = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                                                placeholder.eraseColor(Color.BLUE);
                                                showPhotoConfirmationDialog(placeholder);
                                            }
                                        }
                                    }, 200);
                                }
                            });
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
            showPhotoConfirmationDialog(lastPhotoTaken);
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
                        showPhotoConfirmationDialog(lastPhotoTaken);
                    }
                }
            });
        } else {
            updateStatus("MediaManager ou câmera não disponível");
            simulatePhoto();
            showPhotoConfirmationDialog(lastPhotoTaken);
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
                showPhotoConfirmationDialog(lastPhotoTaken);
            }
        } else {
            updateStatus("MediaManager não disponível");
            simulatePhoto();
            showPhotoConfirmationDialog(lastPhotoTaken);
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
                                        lastPhotoTaken = thumbnail;
                                        showPhotoConfirmationDialog(thumbnail);
                                    }
                                });
                            } else {
                                updateStatus("Thumbnail não disponível");
                                simulatePhoto();
                                showPhotoConfirmationDialog(lastPhotoTaken);
                            }
                        }
                    } else {
                        updateStatus("Erro ao obter thumbnail: " + error.getDescription());
                        simulatePhoto();
                        showPhotoConfirmationDialog(lastPhotoTaken);
                    }
                }
            });

            scheduler.moveTaskToNext(task);
        } else {
            updateStatus("Scheduler ou arquivo de mídia inválido");
            simulatePhoto();
            showPhotoConfirmationDialog(lastPhotoTaken);
        }
    }

    private void simulatePhoto() {
        // For development purposes, create a simulated photo
        Log.d(TAG, "Simulating photo in simulator mode");

        // Create a simulated image
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);

        // Create a more visually interesting simulated photo based on structure and photo indices
        int r = (currentInspectionIndex * 50) % 255;
        int g = (currentPhotoIndex * 40) % 255;
        int b = (currentInspectionIndex * currentPhotoIndex * 30) % 255;

        bitmap.eraseColor(Color.rgb(r, g, b));

        lastPhotoTaken = bitmap;
        isWaitingForReview = true;

        Log.d(TAG, "Simulated photo created with colors R:" + r + " G:" + g + " B:" + b);
    }

    // Method to take photos manually (used during mission and for manual photo button)
    // Method to take photos manually (always simulates for now)
    private void takePhoto() {
        // COMMENTED OUT FOR FUTURE USE - Real camera functionality
        /*
        if (camera != null && !isSimulatorMode) {
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
                                                    updateStatus("Foto tirada com sucesso");

                                                    // Wait a moment for the drone to process the photo
                                                    new Handler().postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            // Show the photo for review
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
        */
        // Always use simulation for now
        updateStatus("Tirando foto simulada...");
        simulatePhoto();
        post(new Runnable() {
            @Override
            public void run() {
                showPhotoConfirmationDialog(lastPhotoTaken);
            }
        });
        //}
    }

    // Method to show photo confirmation dialog with the new layout
    // Method to show photo confirmation dialog with orientation support
    private void showPhotoConfirmationDialog(Bitmap photo) {
        Log.d(TAG, "Showing photo confirmation dialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        // Check orientation and use appropriate layout
        int orientation = getResources().getConfiguration().orientation;
        View dialogView;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_photo_confirmation_land, null);
        } else {
            dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_photo_confirmation, null);
        }

        builder.setView(dialogView);

        // Set up the dialog components
        ImageView photoImageView = dialogView.findViewById(R.id.popup_image_preview);
        TextView photoDetailsText = dialogView.findViewById(R.id.text_photo_details);
        Button retakeButton = dialogView.findViewById(R.id.btn_popup_retake);
        Button acceptButton = dialogView.findViewById(R.id.btn_popup_accept);

        // Set photo and details
        photoImageView.setImageBitmap(photo);
        photoDetailsText.setText("Estrutura: S" + (currentInspectionIndex + 1) + " | Posição: P" + (currentPhotoIndex + 1));

        final AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // Prevent dismissing by tapping outside

        // Set up button click listeners
        retakeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                // Retake the photo
                takePhoto();
            }
        });

        acceptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();

                // Save the photo to storage
                savePhotoToStorage(photo);

                // Accept photo and proceed
                isWaitingForReview = false;
                updateStatus("Foto confirmada. Retomando missão...");

                // Resume the mission after a short delay
                if (btnStartMission != null) {
                    btnStartMission.setEnabled(true);
                }

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resumeMission();
                    }
                }, 500);
            }
        });

        dialog.show();
    }

    // Method to save photo to storage
    private void savePhotoToStorage(Bitmap photo) {
        if (photo != null && photoStorageManager != null) {
            // Save photo using PhotoStorageManager
            PhotoStorageManager.PhotoInfo savedPhoto =
                    photoStorageManager.savePhoto(photo, currentInspectionIndex + 1, currentPhotoIndex + 1);

            if (savedPhoto != null) {
                updateStatus("Foto salva em: " + savedPhoto.getFile().getAbsolutePath());

                // Update photo gallery if visible
                if (viewFlipper != null && viewFlipper.getDisplayedChild() == 1) {
                    refreshGallery();

                    // If we're viewing the current structure, update its photos
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

    // Implementation of PhotoGalleryAdapter.OnPhotoClickListener
    @Override
    public void onPhotoClick(PhotoStorageManager.PhotoInfo photoInfo) {
        // Show full screen photo view
        showFullscreenPhotoView(photoInfo);
    }

    @Override
    public void onDownloadClick(PhotoStorageManager.PhotoInfo photoInfo) {
        // Share photo with other apps
        sharePhoto(photoInfo);
    }

    @Override
    public void onDeleteClick(PhotoStorageManager.PhotoInfo photoInfo) {
        Log.d(TAG, "Delete photo clicked: " + photoInfo.getFilename());

        // Confirm before deletion
        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar Exclusão")
                .setMessage("Deseja excluir esta foto?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    // Delete the photo
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

    // Method to show fullscreen photo view
    private void showFullscreenPhotoView(PhotoStorageManager.PhotoInfo photoInfo) {
        if (photoInfo == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View fullscreenView = LayoutInflater.from(getContext()).inflate(R.layout.fullscreen_photo_view, null);
        builder.setView(fullscreenView);

        // Get components
        ImageView fullscreenImage = fullscreenView.findViewById(R.id.image_fullscreen_photo);
        TextView photoInfoText = fullscreenView.findViewById(R.id.text_fullscreen_photo_info);
        Button closeButton = fullscreenView.findViewById(R.id.btn_close_fullscreen);
        Button shareButton = fullscreenView.findViewById(R.id.btn_share_photo);
        Button deleteButton = fullscreenView.findViewById(R.id.btn_delete_fullscreen);

        // Set photo and info
        Bitmap photoBitmap = BitmapFactory.decodeFile(photoInfo.getFile().getAbsolutePath());
        fullscreenImage.setImageBitmap(photoBitmap);
        photoInfoText.setText(photoInfo.getStructureId() + " | " + photoInfo.getPhotoId() + " | " + photoInfo.getTimestamp());

        final AlertDialog dialog = builder.create();

        // Set button listeners
        closeButton.setOnClickListener(v -> dialog.dismiss());

        shareButton.setOnClickListener(v -> {
            dialog.dismiss();
            sharePhoto(photoInfo);
        });

        deleteButton.setOnClickListener(v -> {
            dialog.dismiss();
            // Show delete confirmation
            onDeleteClick(photoInfo);
        });

        dialog.show();
    }

    // Method to share photo with other apps - Versão ultra-compatível
    private void sharePhoto(PhotoStorageManager.PhotoInfo photoInfo) {
        if (photoInfo == null || !photoInfo.getFile().exists()) {
            updateStatus("Arquivo de foto não encontrado");
            return;
        }

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");

            // Use FileProvider para compatibilidade com Android 7.0+
            Uri photoUri;

            // Verificar versão do Android
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Para Android 7.0 (API 24) ou superior, use FileProvider
                String authority = getContext().getPackageName() + ".fileprovider";
                photoUri = androidx.core.content.FileProvider.getUriForFile(
                        getContext(),
                        authority,
                        photoInfo.getFile());

                // Conceder permissão de leitura temporária
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Para versões mais antigas, o método tradicional ainda funciona
                photoUri = Uri.fromFile(photoInfo.getFile());
            }

            shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Foto de Inspeção: " +
                    photoInfo.getStructureId() + " " + photoInfo.getPhotoId());

            getContext().startActivity(Intent.createChooser(shareIntent, "Compartilhar via"));
        } catch (Exception e) {
            // Registra e exibe o erro para depuração
            Log.e(TAG, "Erro ao compartilhar foto: " + e.getMessage(), e);
            updateStatus("Erro ao compartilhar foto: " + e.getMessage());

            // Fallback alternativo simplificado
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

    // Método para copiar arquivos usando streams (mais básico e compatível)
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