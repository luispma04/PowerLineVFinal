package com.dji.sdk.sample.internal.controller;

import android.animation.AnimatorInflater;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionoperator.StructureInspectionMissionView;
import com.dji.sdk.sample.internal.model.ViewWrapper;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.DemoListView;
import com.dji.sdk.sample.internal.view.PresentableView;
import com.squareup.otto.Subscribe;

import java.util.Stack;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity implements StructureInspectionMissionView.FilePickerCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private FrameLayout contentFrameLayout;
    private ObjectAnimator pushInAnimator;
    private ObjectAnimator pushOutAnimator;
    private ObjectAnimator popInAnimator;
    private LayoutTransition popOutTransition;
    private ProgressBar progressBar;
    private Stack<ViewWrapper> stack;
    private TextView titleTextView;
    private SearchView searchView;
    private MenuItem searchViewItem;
    private MenuItem hintItem;

    // Track SDK registration status
    private boolean isRegistrationInProgress = false;
    private StructureInspectionMissionView structureView;

    // Flag to easily determine if we're running in simulator mode
    private boolean isSimulatorMode = true; // Set to true by default for testing

    //region Life-cycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DJISampleApplication.getEventBus().register(this);
        setContentView(R.layout.activity_main);
        setupActionBar();
        contentFrameLayout = (FrameLayout) findViewById(R.id.framelayout_content);

        // Get the progress bar
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);

        // Setup animations and stack
        setupInAnimations();
        stack = new Stack<ViewWrapper>();

        // Create the Structure Inspection Mission View
        structureView = new StructureInspectionMissionView(this, isSimulatorMode);

        // Remove any existing views in the content frame
        if (contentFrameLayout.getChildCount() > 0) {
            contentFrameLayout.removeAllViews();
        }

        // Add the structure view to the content frame
        contentFrameLayout.addView(structureView);

        // Push the view to the stack with the appropriate title
        stack.push(new ViewWrapper(structureView, R.string.component_listview_structure_inspection_mission));

        // Refresh the title
        refreshTitle();
        refreshOptionsMenu();

        // Initialize DJI SDK if not already done
        startSDKRegistration();
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress) {
            return;
        }

        isRegistrationInProgress = true;

        // Show progress bar for registration
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // Initialize and register the SDK
        DJISDKManager.getInstance().registerApp(this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError djiError) {
                isRegistrationInProgress = false;
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                    // SDK registered successfully
                    DJISDKManager.getInstance().startConnectionToProduct();
                    ToastUtils.setResultToToast("SDK registered successfully");
                    // Update connection status in StructureInspectionMissionView
                    if (structureView != null) {
                        structureView.updateStatus("SDK registered successfully");
                        structureView.updateConnectionStatus();
                    }
                } else {
                    ToastUtils.setResultToToast("SDK registration failed: " + djiError.getDescription());
                    // Update connection status in StructureInspectionMissionView
                    if (structureView != null) {
                        structureView.updateStatus("SDK registration failed: " + djiError.getDescription());
                        structureView.updateConnectionStatus();
                    }
                }
            }

            @Override
            public void onProductDisconnect() {
                ToastUtils.setResultToToast("Aircraft disconnected");
                // Update connection status in StructureInspectionMissionView
                if (structureView != null) {
                    structureView.updateStatus("Aircraft disconnected");
                    structureView.updateConnectionStatus();
                }
            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                ToastUtils.setResultToToast("Aircraft connected");
                // Update connection status in StructureInspectionMissionView
                if (structureView != null) {
                    structureView.onProductConnected();
                    structureView.updateStatus("Aircraft connected");
                    structureView.updateConnectionStatus();
                }
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {
                ToastUtils.setResultToToast("Aircraft changed");
                // Update connection status in StructureInspectionMissionView
                if (structureView != null) {
                    structureView.updateStatus("Aircraft changed");
                    structureView.updateConnectionStatus();
                }
            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
                // Component changed
                if (newComponent != null) {
                    newComponent.setComponentListener(new BaseComponent.ComponentListener() {
                        @Override
                        public void onConnectivityChange(boolean isConnected) {
                            String componentName = componentKey.name();
                            ToastUtils.setResultToToast(componentName + (isConnected ? " connected" : " disconnected"));
                            // Update connection status in StructureInspectionMissionView
                            if (structureView != null) {
                                structureView.updateStatus(componentName + (isConnected ? " connected" : " disconnected"));
                                structureView.updateConnectionStatus();
                            }
                        }
                    });
                }
            }

            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                // Update initialization progress in StructureInspectionMissionView
                if (structureView != null) {
                    structureView.updateStatus("SDK Initialization: " + djisdkInitEvent.toString() + " " + i);
                }
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                // Update database download progress in StructureInspectionMissionView
                if (structureView != null) {
                    structureView.updateStatus("Database Download Progress: " + current + "/" + total);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        DJISampleApplication.getEventBus().unregister(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the options menu from XML
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchViewItem = menu.findItem(R.id.action_search);
        hintItem = menu.findItem(R.id.action_hint);
        searchView = (SearchView) searchViewItem.getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(""));
                return false;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(query));
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                DJISampleApplication.getEventBus().post(new SearchQueryEvent(newText));
                return false;
            }
        });

        // Hint click
        hintItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showHint();
                return false;
            }
        });

        // Update menu visibility
        refreshOptionsMenu();
        return true;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    @Override
    public void onBackPressed() {
        // If this is the only view (our Structure Inspection view), just exit the app
        if (stack.size() <= 1) {
            finish();
        } else {
            // Otherwise, pop the view as normal
            popView();
        }
    }

    // ADDED - Configuration change handling
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Pass the configuration change to the current view if it's a StructureInspectionMissionView
        if (stack.size() > 0 && stack.peek().getView() instanceof StructureInspectionMissionView) {
            ((StructureInspectionMissionView) stack.peek().getView()).onConfigurationChanged(newConfig);
        }
    }

    //endregion

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (null != actionBar) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.actionbar_custom);

            titleTextView = (TextView) (actionBar.getCustomView().findViewById(R.id.title_tv));
        }
    }

    private void setupInAnimations() {
        pushInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_in_right);
        pushOutAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_out);
        popInAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.fade_in);
        ObjectAnimator popOutAnimator =
                (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.slide_out_right);

        pushOutAnimator.setStartDelay(100);

        popOutTransition = new LayoutTransition();
        popOutTransition.setAnimator(LayoutTransition.DISAPPEARING, popOutAnimator);
        popOutTransition.setDuration(popOutAnimator.getDuration());
    }

    private void pushView(ViewWrapper wrapper) {
        if (stack.size() <= 0) {
            return;
        }

        contentFrameLayout.setLayoutTransition(null);

        View showView = wrapper.getView();

        View preView = stack.peek().getView();

        stack.push(wrapper);

        if (showView.getParent() != null) {
            ((ViewGroup) showView.getParent()).removeView(showView);
        }
        contentFrameLayout.addView(showView);

        pushOutAnimator.setTarget(preView);
        pushOutAnimator.start();

        pushInAnimator.setTarget(showView);
        pushInAnimator.setFloatValues(contentFrameLayout.getWidth(), 0);
        pushInAnimator.start();

        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshTitle() {
        // If this is the only view in stack, it's our Structure Inspection view
        if (stack.size() == 1) {
            titleTextView.setText(R.string.component_listview_structure_inspection_mission);
        } else if (stack.size() > 1) {
            ViewWrapper wrapper = stack.peek();
            titleTextView.setText(wrapper.getTitleId());
        }
    }

    private void popView() {
        if (stack.size() <= 1) {
            finish();
            return;
        }

        ViewWrapper removeWrapper = stack.pop();

        View showView = stack.peek().getView();
        View removeView = removeWrapper.getView();

        contentFrameLayout.setLayoutTransition(popOutTransition);
        contentFrameLayout.removeView(removeView);

        popInAnimator.setTarget(showView);
        popInAnimator.start();

        refreshTitle();
        refreshOptionsMenu();
    }

    private void refreshOptionsMenu() {
        // Always hide search since we're not showing the demo list
        if (searchViewItem != null) {
            searchViewItem.setVisible(false);
            searchViewItem.collapseActionView();
        }

        // Show hint if the current view is a PresentableView (Structure Inspection is one)
        if (stack.size() >= 1 && stack.peek().getView() instanceof PresentableView) {
            if (hintItem != null) {
                hintItem.setVisible(true);
            }
        } else {
            if (hintItem != null) {
                hintItem.setVisible(false);
            }
        }
    }

    private void showHint() {
        if (stack.size() != 0 && stack.peek().getView() instanceof PresentableView) {
            ToastUtils.setResultToToast(((PresentableView) stack.peek().getView()).getHint());
        }
    }

    // Implementação da interface FilePickerCallback
    @Override
    public void openFilePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            // Adicionar filtro para mostrar apenas arquivos CSV
            String[] mimeTypes = {"text/csv", "text/comma-separated-values", "application/csv"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            startActivityForResult(Intent.createChooser(intent, "Selecionar arquivo CSV"), requestCode);
        } catch (Exception e) {
            Toast.makeText(this, "Por favor, instale um gerenciador de arquivos", Toast.LENGTH_SHORT).show();
        }
    }

    // Lidar com o resultado da seleção de arquivo
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == 1001 || requestCode == 1002) {
                // Encontrar a view de inspeção atual
                if (stack.size() > 0) {
                    View currentView = stack.peek().getView();
                    if (currentView instanceof StructureInspectionMissionView) {
                        ((StructureInspectionMissionView) currentView).onFileSelected(requestCode, data.getData());
                    }
                }
            }
        }
    }

    //region Event-Bus
    @Subscribe
    public void onReceiveStartFullScreenRequest(RequestStartFullScreenEvent event) {
        getSupportActionBar().hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Subscribe
    public void onReceiveEndFullScreenRequest(RequestEndFullScreenEvent event) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getSupportActionBar().show();
    }

    @Subscribe
    public void onPushView(final ViewWrapper wrapper) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pushView(wrapper);
            }
        });
    }

    @Subscribe
    public void onConnectivityChange(ConnectivityChangeEvent event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshTitle();
                // Update the connection status in the StructureInspectionMissionView
                if (structureView != null) {
                    structureView.updateConnectionStatus();
                }
            }
        });
    }

    public static class SearchQueryEvent {
        private final String query;

        public SearchQueryEvent(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }
    }

    public static class RequestStartFullScreenEvent {
    }

    public static class RequestEndFullScreenEvent {
    }

    public static class ConnectivityChangeEvent {
    }
    //endregion
}