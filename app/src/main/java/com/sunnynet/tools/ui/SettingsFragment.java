package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureEngine;
import com.sunnynet.tools.net.LocalNetworkHelper;

/**
 * 端口设置页：展示内网访问地址，并可修改监听端口（抓包中修改将自动重启服务）。
 */
public class SettingsFragment extends Fragment {

    private TextView lanEndpointView;
    private MaterialButton lanCopyButton;
    private TextInputEditText portInput;
    private MaterialButton portSaveButton;
    private boolean applyingPort;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CaptureEngine engine = CaptureEngine.get();
        lanEndpointView = view.findViewById(R.id.settings_lan_endpoint);
        lanCopyButton = view.findViewById(R.id.settings_lan_copy);
        portInput = view.findViewById(R.id.settings_port);
        portSaveButton = view.findViewById(R.id.settings_port_save);

        portInput.setText(String.valueOf(engine.getListenPort()));
        refreshLanAccessDisplay();

        lanCopyButton.setOnClickListener(v -> copyLanEndpoint());

        portSaveButton.setOnClickListener(v -> {
            applyPortIfChanged();
            portInput.clearFocus();
        });

        portInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyPortIfChanged();
                v.clearFocus();
                return true;
            }
            return false;
        });

        portInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshLanAccessDisplay();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!applyingPort && portInput != null) {
            portInput.setText(String.valueOf(CaptureEngine.get().getListenPort()));
        }
        refreshLanAccessDisplay();
    }

    /** 根据当前输入或已保存端口刷新内网访问地址展示。 */
    private void refreshLanAccessDisplay() {
        if (lanEndpointView == null || lanCopyButton == null) {
            return;
        }
        int port = resolveDisplayPort();
        String lanIp = LocalNetworkHelper.getPrimaryLanIpv4();
        String endpoint = LocalNetworkHelper.formatLanAccessEndpoint(lanIp, port);
        if (endpoint != null) {
            lanEndpointView.setText(endpoint);
            lanCopyButton.setEnabled(true);
        } else {
            lanEndpointView.setText(R.string.port_settings_lan_unavailable);
            lanCopyButton.setEnabled(false);
        }
    }

    private int resolveDisplayPort() {
        if (portInput != null && portInput.getText() != null) {
            String raw = portInput.getText().toString().trim();
            if (!raw.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(raw);
                    if (CaptureEngine.isValidListenPort(parsed)) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return CaptureEngine.get().getListenPort();
    }

    private void copyLanEndpoint() {
        int port = resolveDisplayPort();
        String lanIp = LocalNetworkHelper.getPrimaryLanIpv4();
        String endpoint = LocalNetworkHelper.formatLanAccessEndpoint(lanIp, port);
        if (endpoint != null) {
            ClipboardUiHelper.copyPlain(requireContext(), endpoint, "lan-proxy-endpoint");
        }
    }

    private void applyPortIfChanged() {
        if (applyingPort || portInput == null) {
            return;
        }
        String raw = portInput.getText() != null ? portInput.getText().toString().trim() : "";
        int port;
        try {
            port = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            portInput.setText(String.valueOf(CaptureEngine.get().getListenPort()));
            Toast.makeText(requireContext(), R.string.settings_port_invalid, Toast.LENGTH_SHORT).show();
            refreshLanAccessDisplay();
            return;
        }
        if (!CaptureEngine.isValidListenPort(port)) {
            portInput.setText(String.valueOf(CaptureEngine.get().getListenPort()));
            Toast.makeText(requireContext(), R.string.settings_port_invalid, Toast.LENGTH_SHORT).show();
            refreshLanAccessDisplay();
            return;
        }
        if (port == CaptureEngine.get().getListenPort()) {
            return;
        }

        applyingPort = true;
        setPortControlsEnabled(false);
        CaptureEngine.get().applyListenPortAsync(port, result -> {
            if (!isAdded()) {
                return;
            }
            applyingPort = false;
            setPortControlsEnabled(true);
            portInput.setText(String.valueOf(CaptureEngine.get().getListenPort()));
            refreshLanAccessDisplay();
            switch (result) {
                case APPLIED_IDLE:
                    Toast.makeText(requireContext(), R.string.settings_port_saved, Toast.LENGTH_SHORT).show();
                    break;
                case RESTARTED:
                    Toast.makeText(requireContext(), R.string.settings_port_restarted, Toast.LENGTH_SHORT).show();
                    break;
                case RESTART_FAILED:
                    Toast.makeText(requireContext(), R.string.settings_port_restart_failed, Toast.LENGTH_LONG).show();
                    break;
                case INVALID:
                    Toast.makeText(requireContext(), R.string.settings_port_invalid, Toast.LENGTH_SHORT).show();
                    break;
                case UNCHANGED:
                default:
                    break;
            }
        });
    }

    private void setPortControlsEnabled(boolean enabled) {
        if (portInput != null) {
            portInput.setEnabled(enabled);
        }
        if (portSaveButton != null) {
            portSaveButton.setEnabled(enabled);
        }
        if (lanCopyButton != null) {
            lanCopyButton.setEnabled(enabled && LocalNetworkHelper.getPrimaryLanIpv4() != null);
        }
    }
}
