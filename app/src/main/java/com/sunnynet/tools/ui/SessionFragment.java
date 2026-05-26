package com.sunnynet.tools.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sunnynet.tools.R;
import com.sunnynet.tools.session.SessionBundleManager;

/**
 * 会话文件：导入/导出（目标格式 .syn3 / .sy4，与桌面 SunnyNetV5 对齐）。
 */
public class SessionFragment extends Fragment {

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportPicked);

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"),
                    this::onExportTarget);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btn_import_session).setOnClickListener(v ->
                importLauncher.launch(new String[]{"*/*"}));
        view.findViewById(R.id.btn_export_session).setOnClickListener(v ->
                exportLauncher.launch("capture.syn3"));
    }

    private void onImportPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        SessionBundleManager.Result result = SessionBundleManager.importFromUri(requireContext(), uri);
        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
        if (result.success) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CaptureFragment())
                    .commit();
        }
    }

    private void onExportTarget(Uri uri) {
        if (uri == null) {
            return;
        }
        SessionBundleManager.Result result = SessionBundleManager.exportToUri(requireContext(), uri);
        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
    }
}
