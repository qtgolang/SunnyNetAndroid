package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 抓包目标：选择要抓取流量的应用包名（原设置页「抓包目标」独立为侧栏入口）。
 */
public class CaptureTargetFragment extends Fragment {

    private TextView summaryView;
    private MaterialButton selectAppsButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capture_target, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        summaryView = view.findViewById(R.id.capture_target_summary);
        selectAppsButton = view.findViewById(R.id.capture_target_select_apps);

        selectAppsButton.setOnClickListener(v -> openAppPicker());
        getParentFragmentManager().setFragmentResultListener(
                AppPickerDialogFragment.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    ArrayList<String> selected = result.getStringArrayList(
                            AppPickerDialogFragment.RESULT_PACKAGES);
                    CaptureEngine.get().setTargetPackages(selected);
                    updateSummary();
                });

        updateSummary();
        updateSelectEnabled();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummary();
        updateSelectEnabled();
    }

    private void updateSelectEnabled() {
        selectAppsButton.setEnabled(!CaptureEngine.get().isRunning());
    }

    private void updateSummary() {
        CaptureEngine engine = CaptureEngine.get();
        if (engine.isCaptureAllApps()) {
            summaryView.setText(R.string.settings_target_apps_all);
            return;
        }
        int count = engine.getTargetPackages().size();
        summaryView.setText(getString(R.string.settings_target_apps_count, count));
    }

    private void openAppPicker() {
        if (CaptureEngine.get().isRunning()) {
            Toast.makeText(requireContext(), R.string.settings_target_apps_running, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> current = CaptureEngine.get().getTargetPackages();
        AppPickerDialogFragment.newInstance(new HashSet<>(current))
                .show(getParentFragmentManager(), AppPickerDialogFragment.TAG);
    }
}
