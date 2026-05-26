package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureEngine;

/**
 * 抓取范围：按协议类型控制是否写入抓包列表（HTTP/HTTPS、WebSocket、TCP、UDP）。
 */
public class CaptureRangeFragment extends Fragment {

    private MaterialSwitch httpSwitch;
    private MaterialSwitch wsSwitch;
    private MaterialSwitch tcpSwitch;
    private MaterialSwitch udpSwitch;
    private boolean applying;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capture_range, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CaptureEngine engine = CaptureEngine.get();
        httpSwitch = view.findViewById(R.id.capture_range_http);
        wsSwitch = view.findViewById(R.id.capture_range_websocket);
        tcpSwitch = view.findViewById(R.id.capture_range_tcp);
        udpSwitch = view.findViewById(R.id.capture_range_udp);

        applying = true;
        httpSwitch.setChecked(engine.isCaptureHttpEnabled());
        wsSwitch.setChecked(engine.isCaptureWebSocketEnabled());
        tcpSwitch.setChecked(engine.isCaptureTcpEnabled());
        udpSwitch.setChecked(engine.isCaptureUdpEnabled());
        applying = false;

        httpSwitch.setOnCheckedChangeListener((btn, checked) -> onProtocolToggled(
                () -> CaptureEngine.get().setCaptureHttpEnabled(checked)));
        wsSwitch.setOnCheckedChangeListener((btn, checked) -> onProtocolToggled(
                () -> CaptureEngine.get().setCaptureWebSocketEnabled(checked)));
        tcpSwitch.setOnCheckedChangeListener((btn, checked) -> onProtocolToggled(
                () -> CaptureEngine.get().setCaptureTcpEnabled(checked)));
        udpSwitch.setOnCheckedChangeListener((btn, checked) -> onProtocolToggled(
                () -> CaptureEngine.get().setCaptureUdpEnabled(checked)));
    }

    private void onProtocolToggled(@NonNull Runnable apply) {
        if (applying) {
            return;
        }
        CaptureEngine engine = CaptureEngine.get();
        apply.run();
        if (!engine.isAnyProtocolCaptureEnabled()) {
            applying = true;
            syncSwitchesFromEngine();
            applying = false;
            engine.setCaptureHttpEnabled(true);
            syncSwitchesFromEngine();
            Toast.makeText(requireContext(), R.string.capture_range_at_least_one, Toast.LENGTH_SHORT).show();
        }
    }

    private void syncSwitchesFromEngine() {
        CaptureEngine engine = CaptureEngine.get();
        httpSwitch.setChecked(engine.isCaptureHttpEnabled());
        wsSwitch.setChecked(engine.isCaptureWebSocketEnabled());
        tcpSwitch.setChecked(engine.isCaptureTcpEnabled());
        udpSwitch.setChecked(engine.isCaptureUdpEnabled());
    }
}
