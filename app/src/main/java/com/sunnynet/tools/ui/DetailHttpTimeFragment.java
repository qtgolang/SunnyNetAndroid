package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * HTTP 详情「时间」Tab：记录请求时刻、响应时刻（若尚未到达则占位）以及往返耗时。
 */
public class DetailHttpTimeFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";

    @NonNull
    public static DetailHttpTimeFragment newInstance(long recordId) {
        DetailHttpTimeFragment f = new DetailHttpTimeFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_RECORD_ID, recordId);
        f.setArguments(args);
        return f;
    }

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_http_time, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long recordId = getArguments() != null ? getArguments().getLong(ARG_RECORD_ID, -1) : -1;
        CaptureRecord record = CaptureRepository.get().findById(recordId);
        TextView reqAt = view.findViewById(R.id.detail_time_request_at);
        TextView respAt = view.findViewById(R.id.detail_time_response_at);
        TextView dur = view.findViewById(R.id.detail_time_duration);
        String pending = getString(R.string.detail_time_pending_response);
        if (record == null) {
            String na = getString(R.string.detail_empty);
            reqAt.setText(getString(R.string.detail_time_row_request, na));
            respAt.setText(getString(R.string.detail_time_row_response, na));
            dur.setText(getString(R.string.detail_time_row_duration, na));
            return;
        }
        long t0 = record.getTimestampMs();
        long t1 = record.getResponseTimestampMs();
        reqAt.setText(getString(R.string.detail_time_row_request, formatMillis(t0)));
        if (t1 <= 0) {
            respAt.setText(getString(R.string.detail_time_row_response, pending));
            dur.setText(getString(R.string.detail_time_row_duration, pending));
        } else {
            respAt.setText(getString(R.string.detail_time_row_response, formatMillis(t1)));
            long delta = Math.max(0L, t1 - t0);
            dur.setText(getString(R.string.detail_time_row_duration, formatDurationMs(delta)));
        }
    }

    @NonNull
    private static String formatMillis(long ms) {
        if (ms <= 0) {
            return "—";
        }
        synchronized (FORMAT) {
            return FORMAT.format(new Date(ms));
        }
    }

    @NonNull
    private static String formatDurationMs(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        return String.format(Locale.getDefault(), "%.3f s", ms / 1000.0);
    }
}
