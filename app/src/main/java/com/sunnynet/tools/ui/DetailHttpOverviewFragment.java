package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.capture.HttpOverviewBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 详情「总览」Tab：键值表格展示摘要字段；交互与请求/响应信息表一致（见 {@link DetailKvTableAdapter}）。
 */
public class DetailHttpOverviewFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";

    public static DetailHttpOverviewFragment newInstance(long recordId) {
        DetailHttpOverviewFragment fragment = new DetailHttpOverviewFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_RECORD_ID, recordId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_overview_table, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = view.findViewById(R.id.overview_table_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));

        long recordId = getArguments() != null ? getArguments().getLong(ARG_RECORD_ID, -1) : -1;
        CaptureRecord record = CaptureRepository.get().findById(recordId);
        List<HttpOverviewBuilder.Row> rows = record != null
                ? HttpOverviewBuilder.build(requireContext(), record)
                : new ArrayList<>();
        list.setAdapter(new DetailKvTableAdapter(requireContext(), rows));
    }
}
