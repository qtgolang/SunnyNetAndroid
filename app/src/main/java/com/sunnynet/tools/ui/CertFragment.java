package com.sunnynet.tools.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 证书页：导出 SunnyNet 根证书供用户安装（HTTPS 解密前置步骤）。
 */
public class CertFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cert, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btn_export_cert).setOnClickListener(v -> exportRootCert());
    }

    private void exportRootCert() {
        String pem = CaptureEngine.get().getSunny().ExportCert();
        if (pem == null || pem.isEmpty()) {
            Toast.makeText(requireContext(), R.string.cert_export_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = requireContext().getCacheDir();
            File file = new File(dir, "sunnynet-root.crt");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(pem);
            }
            android.net.Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/x-x509-ca-cert");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.cert_export)));
        } catch (IOException e) {
            Toast.makeText(requireContext(), R.string.cert_export_fail, Toast.LENGTH_SHORT).show();
        }
    }
}
