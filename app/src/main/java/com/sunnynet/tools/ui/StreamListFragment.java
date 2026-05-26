package com.sunnynet.tools.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.capture.CaptureStreamEntry;
import com.sunnynet.tools.capture.StreamPayloadFormatter;

/**
 * WS/TCP/UDP 流 Tab：字符串 / Hex；弹窗为三栏检视（偏移｜Hex｜文本列/ANSI），列宽按弹窗根布局宽度折算；Hex 跨行拖选；短按单击列表内需未超 touch slop 且未点在拖柄上则清除选区，点在列表外按下时亦清除。
 */
public class StreamListFragment extends Fragment {

    private static final String ARG_RECORD_ID = "record_id";

    private long recordId;
    private StreamListAdapter adapter;
    private TextView emptyView;

    public static StreamListFragment newInstance(long recordId) {
        StreamListFragment fragment = new StreamListFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_RECORD_ID, recordId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        recordId = args != null ? args.getLong(ARG_RECORD_ID, -1) : -1;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stream_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = view.findViewById(R.id.stream_list);
        emptyView = view.findViewById(R.id.stream_empty);
        MaterialButtonToggleGroup modeToggle = view.findViewById(R.id.stream_display_mode_toggle);
        TextInputEditText filterInput = view.findViewById(R.id.stream_filter_input);

        adapter = new StreamListAdapter();
        adapter.setDisplayHex(true);
        modeToggle.check(R.id.stream_mode_hex);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            adapter.setDisplayHex(checkedId == R.id.stream_mode_hex);
        });

        adapter.setOnStreamClickListener(this::showStreamPayloadDialogForCurrentMode);
        filterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.setFilterKeyword(s != null ? s.toString() : "");
                updateEmptyOverlay(CaptureRepository.get().findById(recordId));
            }
        });

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        CaptureRecord record = CaptureRepository.get().findById(recordId);
        if (record == null) {
            adapter.setEntries(null);
            updateEmptyOverlay(null);
            return;
        }
        adapter.setEntries(record.getStreamEntries());
        updateEmptyOverlay(record);
    }

    /**
     * 无会话/无流与「有关键词但筛选结果为空」区分提示。
     */
    private void updateEmptyOverlay(@Nullable CaptureRecord record) {
        if (emptyView == null || adapter == null) {
            return;
        }
        if (record == null || record.getStreamCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.stream_list_empty);
            return;
        }
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.stream_list_filtered_empty);
            return;
        }
        emptyView.setVisibility(View.GONE);
    }

    /** 按顶栏所选模式弹出可滚动全文。 */
    private void showStreamPayloadDialogForCurrentMode(@NonNull CaptureStreamEntry entry) {
        showStreamPayloadDialog(entry, adapter.isDisplayHex());
    }

    @SuppressLint("StringFormatInvalid")
    private void showStreamPayloadDialog(@NonNull CaptureStreamEntry entry, boolean asHex) {
        String raw = entry.getBody() != null ? entry.getBody() : "";
        String display = StreamPayloadFormatter.toUtf8String(raw);
        if (display.isEmpty()) {
            display = getString(R.string.detail_empty);
        }

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stream_body, null, false);
        byte[] payloadBytes = StreamPayloadFormatter.resolvePayloadBytes(raw);
        final int streamScrollMaxPx = resolveStreamBodyScrollMaxHeightPx(requireContext());
        final int streamScrollMinPx = resolveStreamBodyScrollMinHeightPx(requireContext());
        final boolean hexWithPayload = asHex && payloadBytes.length > 0;
        final int streamScrollHeightPx = hexWithPayload
                ? Math.max(streamScrollMinPx, streamScrollMaxPx)
                : streamScrollMaxPx;
        applyStreamBodyScrollViewportHeight(root, streamScrollHeightPx);
        TextView content = root.findViewById(R.id.stream_body_content);
        View hscroll = root.findViewById(R.id.stream_body_hscroll);
        HexDumpRecyclerView streamHexRv = root.findViewById(R.id.stream_body_hex_recycler);

        /* 跨行选中后 Snackbar 若给 RecyclerView 加底 padding，在 NestedScrollView+弹窗下易触发整块重排版、三栏错位 */
        if (streamHexRv != null) {
            streamHexRv.setApplySnackRecyclerPadding(false);
        }

        int onSurfaceFallback = ContextCompat.getColor(requireContext(), R.color.sunny_on_surface);

        View hexProbeRow = null;
        final TextView colsMeasureForReflow;
        if (!asHex) {
            if (streamHexRv != null) {
                RecyclerView.Adapter<?> ada = streamHexRv.getAdapter();
                if (ada instanceof HexDumpLineAdapter) {
                    ((HexDumpLineAdapter) ada).clearRows();
                }
                streamHexRv.setVisibility(View.GONE);
            }
            content.setVisibility(View.VISIBLE);
            content.setTypeface(Typeface.DEFAULT);
            content.setBackgroundColor(Color.TRANSPARENT);
            int fg = MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurface,
                    onSurfaceFallback);
            content.setTextColor(fg);
            content.setText(display);
            colsMeasureForReflow = content;
        } else if (payloadBytes.length == 0) {
            if (streamHexRv != null) {
                RecyclerView.Adapter<?> ada = streamHexRv.getAdapter();
                if (ada instanceof HexDumpLineAdapter) {
                    ((HexDumpLineAdapter) ada).clearRows();
                }
                streamHexRv.setVisibility(View.GONE);
            }
            content.setVisibility(View.VISIBLE);
            content.setTypeface(Typeface.MONOSPACE);
            content.setBackgroundColor(Color.TRANSPARENT);
            content.setTextColor(MaterialColors.getColor(content,
                    com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));
            content.setText(getString(R.string.detail_empty));
            colsMeasureForReflow = content;
        } else {
            if (streamHexRv != null) {
                streamHexRv.setVisibility(View.VISIBLE);
            }
            content.setVisibility(View.GONE);
            content.setText("");
            /* 不可用 streamHexRv 作为父级：inflate 会因 RecyclerView 尚未 setLayoutManager 而在 generateLayoutParams 抛 IllegalStateException。 */
            hexProbeRow = LayoutInflater.from(requireContext()).inflate(
                    R.layout.item_hex_dump_line_dialog, (ViewGroup) root, false);
            HexDumpRowLayoutHelper.prepareTripletRowTypography(hexProbeRow);
            colsMeasureForReflow = hexProbeRow.findViewById(R.id.hex_line_bytes);
        }

        String emptyMarker = getString(R.string.detail_empty);
        String hexBase = StreamPayloadFormatter.toSpacedHex(raw);
        String utf8Base = StreamPayloadFormatter.toUtf8String(raw);
        final String hexForClip = hexBase.isEmpty() ? emptyMarker : hexBase;
        final String utf8ForClip = utf8Base.isEmpty() ? emptyMarker : utf8Base;
        final String asciiColumnClip = payloadBytes.length > 0
                ? HexDumpFormatter.toAsciiColumnPlain(payloadBytes) : emptyMarker;

        MaterialButton copyHexBtn = root.findViewById(R.id.stream_body_copy_hex);
        MaterialButton copyStrBtn = root.findViewById(R.id.stream_body_copy_string);
        MaterialButton copyAsciiColBtn = root.findViewById(R.id.stream_body_copy_ascii_column);
        View sepHexExtra = root.findViewById(R.id.stream_body_copy_sep_hex_extra);

        copyHexBtn.setContentDescription(getString(R.string.stream_body_copy_hex_desc));
        copyStrBtn.setContentDescription(getString(R.string.stream_body_copy_string_desc));
        if (copyAsciiColBtn != null) {
            copyAsciiColBtn.setContentDescription(getString(R.string.stream_body_copy_ascii_column_desc));
        }

        Context appCtx = requireContext();
        copyHexBtn.setOnClickListener(v -> ClipboardUiHelper.copyPlain(appCtx, hexForClip, "stream_hex"));
        copyStrBtn.setOnClickListener(v ->
                ClipboardUiHelper.copyPlain(appCtx, utf8ForClip, "stream_string"));

        boolean showHexExtraActions = asHex && payloadBytes.length > 0;
        int extraVis = showHexExtraActions ? View.VISIBLE : View.GONE;
        if (sepHexExtra != null) {
            sepHexExtra.setVisibility(extraVis);
        }
        if (copyAsciiColBtn != null) {
            copyAsciiColBtn.setVisibility(extraVis);
            copyAsciiColBtn.setOnClickListener(v ->
                    ClipboardUiHelper.copyPlain(appCtx, asciiColumnClip, "stream_ascii_column"));
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(root)
                .setCancelable(true)
                .create();
        TextView dialogTitle = root.findViewById(R.id.stream_body_dialog_title);
        if (dialogTitle != null) {
            dialogTitle.setText(getString(R.string.stream_body_title,
                    StreamEntryUi.directionWithByteCount(requireContext(), entry)));
        }
        View closeBtn = root.findViewById(R.id.stream_body_dialog_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dlg.dismiss());
        }
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(d -> {
            View reflowHost = streamHexRv != null ? streamHexRv : root.findViewById(R.id.stream_body_hscroll);
            HexDumpViewportHelper.clearHexReflow(reflowHost);
        });
        dlg.show();

        /* 标题/边距等：按下在 Hex 列表矩形外时收起跨行选区（列表内短按由 HexDumpRecyclerView 处理）。 */
        if (dlg.getWindow() != null) {
            dlg.getWindow().getDecorView().setOnTouchListener((v, ev) -> {
                if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                if (streamHexRv == null || !streamHexRv.isShown()) {
                    return false;
                }
                Rect b = new Rect();
                streamHexRv.getGlobalVisibleRect(b);
                if (!b.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    streamHexRv.clearCrossRowSelection();
                }
                return false;
            });
        }

        if (asHex && payloadBytes.length > 0 && streamHexRv != null && hexProbeRow != null) {
            Context ctx = requireContext();
            final View hexRowTemplate = hexProbeRow;
            HexDumpLineAdapter hexAdapter = new HexDumpLineAdapter(ctx, R.layout.item_hex_dump_line_dialog, streamHexRv);
            streamHexRv.setLayoutManager(new LinearLayoutManager(ctx));
            streamHexRv.setAdapter(hexAdapter);
            streamHexRv.setTag(R.id.tag_hex_dump_line_adapter, hexAdapter);
            HexDumpViewportHelper.attachHexReflow(streamHexRv, colsMeasureForReflow, dlg::isShowing, () -> {
                if (!dlg.isShowing()) {
                    return;
                }
                HexDumpViewportHelper.scheduleTripletLayoutWhenHostMeasured(streamHexRv, streamHexRv, root,
                        colsMeasureForReflow, dlg::isShowing, () -> {
                            if (!dlg.isShowing()) {
                                return;
                            }
                            int hostInner = HexDumpViewportHelper.resolveTripletHostInnerWidthPx(streamHexRv,
                                    root,
                                    colsMeasureForReflow);
                            int cols = HexDumpViewportHelper.computeBytesPerRowTripletBounded(hexRowTemplate,
                                    hostInner);
                            int[] ww = HexDumpViewportHelper.computeTripletWidthsFromInflatedRow(hexRowTemplate,
                                    cols);
                            hexAdapter.setPayloadAndLayout(payloadBytes, cols, ww[0], ww[1], ww[2]);
                            View scrollViewport = root.findViewById(R.id.stream_body_scroll);
                            HexDumpViewportHelper.scheduleClampHexRecyclerHeight(streamHexRv, streamHexRv,
                                    hexRowTemplate, cols, payloadBytes.length, ww[0], ww[1], ww[2],
                                    streamScrollMaxPx, streamScrollMinPx, scrollViewport, 0);
                        });
            });
        }
    }

    /** 流弹窗正文区最大高度：基准 {@link R.dimen#stream_body_scroll_height} + {@link R.dimen#stream_body_scroll_bonus_px}。 */
    private static int resolveStreamBodyScrollMaxHeightPx(@NonNull Context context) {
        android.content.res.Resources res = context.getResources();
        return res.getDimensionPixelSize(R.dimen.stream_body_scroll_height)
                + res.getDimensionPixelSize(R.dimen.stream_body_scroll_bonus_px);
    }

    /** 流弹窗 Hex 正文区高度下限（px）。 */
    private static int resolveStreamBodyScrollMinHeightPx(@NonNull Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.stream_body_scroll_min_px);
    }

    /** 打开弹窗时设置 {@code stream_body_scroll} 高度（字符串/Hex 模式共用）。 */
    private static void applyStreamBodyScrollViewportHeight(@NonNull View dialogRoot, int heightPx) {
        View scroll = dialogRoot.findViewById(R.id.stream_body_scroll);
        if (scroll == null) {
            return;
        }
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        if (lp != null) {
            lp.height = heightPx;
            scroll.setLayoutParams(lp);
        }
    }
}
