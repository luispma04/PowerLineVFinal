package com.dji.sdk.sample.demo.missionmanager;

import android.app.Service;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;

import dji.common.flightcontroller.FlightMode;
import dji.sdk.flightcontroller.FlightController;

/**
 * Class for basic manager view in mission manager
 */
public abstract class MissionBaseView extends RelativeLayout implements View.OnClickListener, PresentableView {

    protected FlightController flightController;

    protected Button simulatorBtn;
    protected Button maxAltitudeBtn;
    protected Button maxRadiusBtn;

    protected Button loadBtn;
    protected Button uploadBtn;
    protected Button startBtn;
    protected Button stopBtn;
    protected Button pauseBtn;
    protected Button resumeBtn;
    protected Button downloadBtn;

    protected TextView missionPushInfoTV;
    protected TextView FCPushInfoTV;
    protected ProgressBar progressBar;

    protected double homeLatitude = 181;
    protected double homeLongitude = 181;
    protected FlightMode flightState = null;

    public MissionBaseView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    private void showLongitudeLatitude() {
        ToastUtils.setResultToText(FCPushInfoTV,
                                   "Home point latitude: "
                                       + homeLatitude
                                       + "\n"
                                       + "Home point longitude: "
                                       + homeLongitude
                                       + "\n"
                                       + "Flight state: "
                                       + (flightState == null ? "" : flightState.name()));
    }



    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }
}
