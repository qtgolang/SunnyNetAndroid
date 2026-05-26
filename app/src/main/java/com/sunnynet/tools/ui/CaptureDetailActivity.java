package com.sunnynet.tools.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.capture.HttpDetailSections;

/**
 * 抓包详情：HTTP 为总览 / 请求（分段 + 正文模式） / 响应（同上） / 时间；WS 为总览/流列表/请求/响应；TCP/UDP 为总览/流列表。
 */
public class CaptureDetailActivity extends AppCompatActivity {

    private static final String EXTRA_RECORD_ID = "record_id";

    public static void start(Context context, long recordId) {
        Intent intent = new Intent(context, CaptureDetailActivity.class);
        intent.putExtra(EXTRA_RECORD_ID, recordId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_detail);

        MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        long recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, -1);
        CaptureRecord record = CaptureRepository.get().findById(recordId);
        TabLayout tabs = findViewById(R.id.detail_tabs);
        androidx.viewpager2.widget.ViewPager2 pager = findViewById(R.id.detail_pager);

        if (record == null) {
            pager.setAdapter(new SinglePageAdapter(this, getString(R.string.capture_detail_missing)));
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(record.getProtocol());
        }

        if (record.isStreamSession()) {
            pager.setAdapter(new StreamDetailPagerAdapter(this, record));
            if (CaptureRecord.TYPE_WEBSOCKET.equals(record.getProtocol())) {
                new TabLayoutMediator(tabs, pager, (tab, position) -> {
                    int[] titles = new int[]{
                            R.string.detail_tab_overview,
                            R.string.detail_tab_streams,
                            R.string.detail_tab_request,
                            R.string.detail_tab_response
                    };
                    tab.setText(titles[position]);
                }).attach();
            } else {
                new TabLayoutMediator(tabs, pager, (tab, position) -> {
                    int[] titles = new int[]{
                            R.string.detail_tab_overview,
                            R.string.detail_tab_streams
                    };
                    tab.setText(titles[position]);
                }).attach();
            }
        } else {
            pager.setAdapter(new HttpDetailPagerAdapter(this, record));
            new TabLayoutMediator(tabs, pager, (tab, position) -> {
                int[] titles = new int[]{
                        R.string.detail_tab_overview,
                        R.string.detail_tab_request,
                        R.string.detail_tab_response,
                        R.string.detail_tab_time,
                };
                tab.setText(titles[position]);
            }).attach();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardDismissHelper.consumeOutsideTapHideIme(getWindow(), ev);
        return super.dispatchTouchEvent(ev);
    }

    private static class StreamDetailPagerAdapter extends FragmentStateAdapter {

        private final FragmentActivity host;
        private final long recordId;
        private final CharSequence overview;
        private final CaptureRecord record;
        private final boolean websocket;

        StreamDetailPagerAdapter(@NonNull FragmentActivity activity, @NonNull CaptureRecord record) {
            super(activity);
            host = activity;
            this.record = record;
            recordId = record.getId();
            overview = ConnectionStatusUiHelper.buildStreamOverview(activity, record);
            websocket = CaptureRecord.TYPE_WEBSOCKET.equals(record.getProtocol());
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return DetailTextFragment.newInstance(overview);
            }
            if (position == 1) {
                return StreamListFragment.newInstance(recordId);
            }
            if (position == 2) {
                String rawRequest = HttpDetailSections.buildRawRequestMessage(record);
                return DetailTextFragment.newInstance(rawRequest.isEmpty()
                        ? host.getString(R.string.detail_empty) : rawRequest);
            }
            String rawResponse = HttpDetailSections.buildRawResponseMessage(record);
            return DetailTextFragment.newInstance(rawResponse.isEmpty()
                    ? host.getString(R.string.detail_empty) : rawResponse);
        }

        @Override
        public int getItemCount() {
            return websocket ? 4 : 2;
        }
    }

    private static class HttpDetailPagerAdapter extends FragmentStateAdapter {

        private final long recordId;

        HttpDetailPagerAdapter(@NonNull FragmentActivity activity, @NonNull CaptureRecord record) {
            super(activity);
            recordId = record.getId();
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return DetailHttpOverviewFragment.newInstance(recordId);
            }
            if (position == 1) {
                return DetailHttpRequestFragment.newInstance(recordId);
            }
            if (position == 2) {
                return DetailHttpResponseFragment.newInstance(recordId);
            }
            return DetailHttpTimeFragment.newInstance(recordId);
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }

    private static class SinglePageAdapter extends FragmentStateAdapter {
        private final String text;

        SinglePageAdapter(@NonNull FragmentActivity activity, String text) {
            super(activity);
            this.text = text;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return DetailTextFragment.newInstance(text);
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }
}
