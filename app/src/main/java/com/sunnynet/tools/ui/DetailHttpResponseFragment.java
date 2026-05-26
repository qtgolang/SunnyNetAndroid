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
import com.sunnynet.tools.capture.HttpDetailSections;
import com.sunnynet.tools.capture.HttpDetailTableRows;
import com.sunnynet.tools.capture.HttpOverviewBuilder;

import java.util.Collections;
import java.util.List;

/**
 * HTTP 详情「响应」Tab：响应信息表格、可折叠的协议头表格、主体；主体内嵌字符串 / 十六进制切换。
 */
public class DetailHttpResponseFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";

    @NonNull
    public static DetailHttpResponseFragment newInstance(long recordId) {
        DetailHttpResponseFragment f = new DetailHttpResponseFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_RECORD_ID, recordId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_http_response, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long recordId = getArguments() != null ? getArguments().getLong(ARG_RECORD_ID, -1) : -1;
        CaptureRecord record = CaptureRepository.get().findById(recordId);
        String empty = getString(R.string.detail_empty);
        RecyclerView infoRv = view.findViewById(R.id.detail_response_info_table);
        RecyclerView headersRv = view.findViewById(R.id.detail_response_headers_table);
        if (record == null) {
            bindEmpty(view, empty, infoRv, headersRv);
            return;
        }
        bindKvTable(infoRv, HttpDetailTableRows.buildResponseSummary(requireContext(), record));
        bindKvTable(headersRv, HttpDetailTableRows.buildHeaderRows(requireContext(),
                HttpDetailSections.buildResponseHeadersOnly(record)));
        DetailProtocolHeadersSectionBinder.bindDefaultCollapsed(
                view.findViewById(R.id.detail_response_headers_header),
                view.findViewById(R.id.detail_response_headers_expand_icon),
                headersRv);
        String body = HttpDetailSections.buildResponseBodyFromRecord(record);
        HttpBodyDisplayHelper.bind(requireContext(), view.findViewById(R.id.detail_response_body_block), body);
    }

    private void bindKvTable(@NonNull RecyclerView rv,
                             @NonNull List<HttpOverviewBuilder.Row> rows) {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new DetailKvTableAdapter(requireContext(), rows));
    }

    private void bindEmpty(@NonNull View view, @NonNull String empty,
                           @NonNull RecyclerView infoRv, @NonNull RecyclerView headersRv) {
        List<HttpOverviewBuilder.Row> placeholder =
                Collections.singletonList(new HttpOverviewBuilder.Row(empty, empty));
        bindKvTable(infoRv, placeholder);
        bindKvTable(headersRv, placeholder);
        DetailProtocolHeadersSectionBinder.bindDefaultCollapsed(
                view.findViewById(R.id.detail_response_headers_header),
                view.findViewById(R.id.detail_response_headers_expand_icon),
                headersRv);
        HttpBodyDisplayHelper.bind(requireContext(), view.findViewById(R.id.detail_response_body_block), "");
    }
}
