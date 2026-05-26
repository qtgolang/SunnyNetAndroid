package com.sunnynet.tools.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.sunnynet.tools.MainActivity;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureEngine;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.service.CaptureNotificationHelper;

/**
 * 抓包工作台：控制区、筛选区、列表区；顶栏右侧仅展示 VPN 与条数概要。
 */
public class CaptureFragment extends Fragment implements
        CaptureRepository.Listener,
        CaptureEngine.StatusListener {

    private static final long POLL_INTERVAL_MS = 1000L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || getView() == null || !CaptureEngine.get().isRunning()) {
                return;
            }
            applyListFilter();
            updateStatusBarOnly();
            uiHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private MaterialButton toggleCaptureButton;
    private TextView statusSubText;
    private TextView emptyListHint;
    private RecyclerView captureList;
    private CaptureListAdapter adapter;
    private String protocolFilter = null;
    private String searchKeyword = "";
    private boolean suppressStoppedToastForVpnLoss;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toggleCaptureButton = view.findViewById(R.id.btn_toggle_capture);
        bindToolbarStatusViews();
        emptyListHint = view.findViewById(R.id.empty_list_hint);
        captureList = view.findViewById(R.id.capture_list);
        MaterialAutoCompleteTextView protocolDropdown = view.findViewById(R.id.protocol_filter_dropdown);
        TextInputEditText searchInput = view.findViewById(R.id.search_input);

        adapter = new CaptureListAdapter();
        adapter.setAppContext(requireContext());
        adapter.setOnItemClickListener(record ->
                CaptureDetailActivity.start(requireContext(), record.getId()));
        captureList.setLayoutManager(new LinearLayoutManager(requireContext()));
        captureList.setItemAnimator(null);
        captureList.setAdapter(adapter);
        refreshListFromRepository();

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> CaptureRepository.get().clear());
        toggleCaptureButton.setOnClickListener(v -> onToggleCaptureClicked());

        setupProtocolDropdown(protocolDropdown);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchKeyword = s != null ? s.toString().trim() : "";
                applyListFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (!granted) {
                        Toast.makeText(requireContext(),
                                R.string.notification_permission_need, Toast.LENGTH_LONG).show();
                    }
                    startCaptureEngine();
                });

        CaptureRepository.get().addListener(this);
        CaptureEngine.get().addListener(this);
        updateUiState();
    }

    @Override
    public void onDestroyView() {
        stopPollLoop();
        CaptureRepository.get().removeListener(this);
        CaptureEngine.get().removeListener(this);
        View statusPanel = requireActivity().findViewById(R.id.toolbar_capture_status);
        if (statusPanel != null) {
            statusPanel.setVisibility(View.GONE);
        }
        super.onDestroyView();
    }

    /** 状态控件在 MainActivity 顶栏，仅工作台可见。 */
    private void bindToolbarStatusViews() {
        statusSubText = requireActivity().findViewById(R.id.status_subtext);
        View statusPanel = requireActivity().findViewById(R.id.toolbar_capture_status);
        if (statusPanel != null) {
            statusPanel.setVisibility(View.VISIBLE);
        }
    }

    private void onToggleCaptureClicked() {
        CaptureEngine engine = CaptureEngine.get();
        if (engine.isRunning()) {
            toggleCaptureButton.setEnabled(false);
            new Thread(() -> {
                engine.stop();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        toggleCaptureButton.setEnabled(true);
                        updateUiState();
                    });
                }
            }, "capture-stop-ui").start();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startCaptureEngine();
    }

    private void startCaptureEngine() {
        if (!CaptureEngine.get().start()) {
            Toast.makeText(requireContext(), R.string.capture_start_failed, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.capture_vpn_hint, Toast.LENGTH_LONG).show();
        }
    }

    private void startPollLoop() {
        uiHandler.removeCallbacks(pollRunnable);
        uiHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPollLoop() {
        uiHandler.removeCallbacks(pollRunnable);
    }

    private void setupProtocolDropdown(@NonNull MaterialAutoCompleteTextView dropdown) {
        String[] labels = getResources().getStringArray(R.array.protocol_filter_labels);
        ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels);
        dropdown.setAdapter(dropdownAdapter);
        dropdown.setText(labels[0], false);
        protocolFilter = null;
        dropdown.setOnItemClickListener((parent, itemView, position, id) -> {
            protocolFilter = mapProtocolLabel(labels[position]);
            applyListFilter();
        });
    }

    @Nullable
    private String mapProtocolLabel(@NonNull String label) {
        if (getString(R.string.filter_all).equals(label)) {
            return null;
        }
        if (getString(R.string.filter_http_slash).equals(label)) {
            return CaptureRecord.TYPE_HTTP;
        }
        if (getString(R.string.filter_ws_slash).equals(label)) {
            return CaptureRecord.TYPE_WEBSOCKET;
        }
        if (getString(R.string.filter_get).equals(label)) {
            return CaptureListAdapter.HTTP_METHOD_FILTER_PREFIX + "GET";
        }
        if (getString(R.string.filter_post).equals(label)) {
            return CaptureListAdapter.HTTP_METHOD_FILTER_PREFIX + "POST";
        }
        if ("TCP".equalsIgnoreCase(label)) {
            return CaptureRecord.TYPE_TCP;
        }
        if ("UDP".equalsIgnoreCase(label)) {
            return CaptureRecord.TYPE_UDP;
        }
        return null;
    }

    private void applyListFilter() {
        boolean stickToBottom = adapter.getItemCount() == 0
                || (captureList.getVisibility() == View.VISIBLE && !captureList.canScrollVertically(1));
        adapter.setFilter(protocolFilter, searchKeyword);
        adapter.setItems(CaptureRepository.get().snapshot());
        updateEmptyState();
        final int count = adapter.getItemCount();
        if (stickToBottom && count > 0 && captureList.getVisibility() == View.VISIBLE) {
            captureList.post(() -> {
                if (!isAdded() || captureList == null) {
                    return;
                }
                int n = adapter.getItemCount();
                if (n > 0 && captureList.getVisibility() == View.VISIBLE) {
                    captureList.scrollToPosition(n - 1);
                }
            });
        }
    }

    private void updateEmptyState() {
        if (emptyListHint == null || captureList == null) {
            return;
        }
        boolean empty = adapter.getItemCount() == 0;
        emptyListHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        captureList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void refreshListFromRepository() {
        applyListFilter();
    }

    private void updateUiState() {
        if (!isAdded() || getView() == null) {
            return;
        }
        CaptureEngine engine = CaptureEngine.get();
        boolean running = engine.isRunning();
        toggleCaptureButton.setText(running ? R.string.capture_stop : R.string.capture_start);
        toggleCaptureButton.setIconResource(running ? R.drawable.ic_stop : R.drawable.ic_play);
        toggleCaptureButton.setEnabled(true);
        updateStatusBarOnly();
    }

    private void updateStatusBarOnly() {
        if (!isAdded() || getView() == null) {
            return;
        }
        int count = CaptureRepository.get().recordCount();
        if (statusSubText != null) {
            statusSubText.setText(getString(R.string.status_bar_record_count, count));
        }
    }

    @Override
    public void onRecordAdded(CaptureRecord record) {
    }

    @Override
    public void onRecordUpdated(CaptureRecord record) {
    }

    @Override
    public void onRecordsBatchChanged() {
    }

    @Override
    public void onRecordsRefreshRequested() {
        if (!isAdded() || getView() == null) {
            return;
        }
        applyListFilter();
        updateUiState();
    }

    @Override
    public void onRecordsCleared() {
        adapter.clear();
        updateEmptyState();
        updateUiState();
    }

    @Override
    public void onEngineStarted() {
        updateUiState();
    }

    @Override
    public void onVpnSuccess() {
        updateUiState();
        startPollLoop();
        Toast.makeText(requireContext(), R.string.capture_vpn_ok, Toast.LENGTH_SHORT).show();
        Toast.makeText(requireContext(), R.string.notification_stop_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onVpnFailed(String message) {
        stopPollLoop();
        updateUiState();
        if (CaptureEngine.isListenPortInUseError(message)) {
            showPortInUseDialog();
        } else if (CaptureEngine.isListenPortPermissionDeniedError(message)) {
            showPortPermissionDeniedDialog();
        } else {
            Toast.makeText(requireContext(), getString(R.string.capture_vpn_fail, message), Toast.LENGTH_LONG).show();
        }
    }

    /** 监听端口被占用：对话框说明原因，可一键跳转设置页。 */
    private void showPortInUseDialog() {
        int port = CaptureEngine.get().getListenPort();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.capture_port_in_use_title)
                .setMessage(getString(R.string.capture_port_in_use_message, port))
                .setPositiveButton(R.string.capture_port_in_use_go_settings, (dialog, which) -> {
                    if (requireActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).navigateToSettings();
                    }
                })
                .setNegativeButton(R.string.capture_port_in_use_dismiss, null)
                .show();
    }

    /** 监听端口无权限绑定：说明常见原因，引导去设置改端口。 */
    private void showPortPermissionDeniedDialog() {
        int port = CaptureEngine.get().getListenPort();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.capture_port_bind_denied_title)
                .setMessage(getString(R.string.capture_port_bind_denied_message, port))
                .setPositiveButton(R.string.capture_port_in_use_go_settings, (dialog, which) -> {
                    if (requireActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).navigateToSettings();
                    }
                })
                .setNegativeButton(R.string.capture_port_in_use_dismiss, null)
                .show();
    }

    @Override
    public void onVpnTransportLost() {
        suppressStoppedToastForVpnLoss = true;
        if (isAdded()) {
            Toast.makeText(requireContext(), R.string.capture_stopped_vpn_takeover, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onEngineStopped() {
        stopPollLoop();
        applyListFilter();
        updateUiState();
        if (suppressStoppedToastForVpnLoss) {
            suppressStoppedToastForVpnLoss = false;
        } else {
            Toast.makeText(requireContext(), R.string.capture_stopped, Toast.LENGTH_SHORT).show();
        }
    }
}
