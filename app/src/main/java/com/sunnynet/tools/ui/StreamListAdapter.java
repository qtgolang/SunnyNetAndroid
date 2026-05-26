package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureStreamEntry;
import com.sunnynet.tools.capture.StreamPayloadFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话详情内的流列表：支持关键字筛选；预览随「字符串 / 十六进制」变化；
 * **卡片序号**为整条会话内按到达时间的固定编号（#1…#N），**筛选不改变**已露出条目的该编号。
 */
public class StreamListAdapter extends RecyclerView.Adapter<StreamListAdapter.Holder> {

    private static final int PREVIEW_MAX_CHARS = 160;
    private static final Comparator<CaptureStreamEntry> CHRONO_ORDER = Comparator
            .comparingLong(CaptureStreamEntry::getTimestampMs)
            .thenComparingLong(CaptureStreamEntry::getId);

    public interface OnStreamClickListener {
        void onStreamClick(CaptureStreamEntry entry);
    }

    /** 会话内全部流（来自仓库），顺序与 {@link CaptureRecord#getStreamEntries()} 一致 */
    private final List<CaptureStreamEntry> sourceEntries = new ArrayList<>();
    /** 筛选后的可见列表 */
    private final List<CaptureStreamEntry> visibleEntries = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private OnStreamClickListener listener;
    /** 与流列表顶栏切换一致：true 为大写空格分隔 Hex */
    private boolean displayHex = true;
    private String filterKeywordNorm = "";

    /** 会话内全时序展示序号：**全部流**按到达时间分配的 #1…#N（含被筛掉的条目仍占位），筛选不改变已显示条目上的序号。 */
    private final Map<Long, Integer> chronologicalSeqByEntryId = new HashMap<>();
    /** 用于关键字筛选拼装「发送（共 N 字节）」等资源串；Detached 后为 null */
    private Context recyclerContextHost;

    public void setOnStreamClickListener(OnStreamClickListener listener) {
        this.listener = listener;
    }

    public void setDisplayHex(boolean displayHex) {
        if (this.displayHex == displayHex) {
            return;
        }
        this.displayHex = displayHex;
        notifyDataSetChanged();
    }

    public boolean isDisplayHex() {
        return displayHex;
    }

    /**
     * 关键词筛选（不区分大小写），匹配方向、时间、正文（原始 / UTF-8 / 空格十六进制）。
     */
    public void setFilterKeyword(String keyword) {
        String norm = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        if (norm.equals(this.filterKeywordNorm)) {
            return;
        }
        this.filterKeywordNorm = norm;
        rebuildVisibleAndNotify();
    }

    public void setEntries(List<CaptureStreamEntry> entries) {
        sourceEntries.clear();
        if (entries != null) {
            sourceEntries.addAll(entries);
        }
        rebuildVisibleAndNotify();
    }

    private void rebuildVisibleAndNotify() {
        visibleEntries.clear();
        boolean noNeedle = filterKeywordNorm.isEmpty();
        for (CaptureStreamEntry e : sourceEntries) {
            if (noNeedle || entryMatchesFilter(e)) {
                visibleEntries.add(e);
            }
        }
        rebuildChronologicalSeq();
        notifyDataSetChanged();
    }

    /** 在「会话全部条目」内按到达时间排序分配 #1…#N；筛选只影响列表是否显示，不改变编号。 */
    private void rebuildChronologicalSeq() {
        chronologicalSeqByEntryId.clear();
        if (sourceEntries.isEmpty()) {
            return;
        }
        List<CaptureStreamEntry> sorted = new ArrayList<>(sourceEntries);
        sorted.sort(CHRONO_ORDER);
        for (int i = 0; i < sorted.size(); i++) {
            chronologicalSeqByEntryId.put(sorted.get(i).getId(), i + 1);
        }
    }

    private boolean entryMatchesFilter(@NonNull CaptureStreamEntry e) {
        return buildSearchHaystack(e).contains(filterKeywordNorm);
    }

    /** 检索用全文：与当前展示模式无关，便于任意模式下都能筛到正文。 */
    @NonNull
    private String buildSearchHaystack(@NonNull CaptureStreamEntry e) {
        String raw = e.getBody() != null ? e.getBody() : "";
        StringBuilder sb = new StringBuilder(raw.length() + 96);
        if (recyclerContextHost != null) {
            sb.append(StreamEntryUi.directionWithByteCount(recyclerContextHost, e)).append('\n');
        } else {
            sb.append(StreamEntryUi.plainDirectionByteSuffix(e.getDirection(), raw)).append('\n');
        }
        sb.append(e.getDirection()).append('\n');
        sb.append(Integer.toString(StreamPayloadFormatter.logicalPayloadByteCount(raw))).append('\n');
        sb.append(timeFormat.format(new Date(e.getTimestampMs()))).append('\n');
        sb.append(Long.toString(e.getTimestampMs())).append('\n');
        sb.append(raw).append('\n');
        sb.append(StreamPayloadFormatter.toUtf8String(raw)).append('\n');
        sb.append(StreamPayloadFormatter.toSpacedHex(raw));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerContextHost = recyclerView.getContext();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerContextHost = null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stream_entry, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CaptureStreamEntry entry = visibleEntries.get(position);
        int seqNum = chronologicalSeqByEntryId.containsKey(entry.getId())
                ? chronologicalSeqByEntryId.get(entry.getId())
                : position + 1;
        holder.seq.setText(holder.itemView.getContext().getString(R.string.stream_card_seq_format, seqNum));
        holder.direction.setText(StreamEntryUi.directionWithByteCount(holder.itemView.getContext(), entry));
        holder.time.setText(timeFormat.format(new Date(entry.getTimestampMs())));
        String body = entry.getBody() != null ? entry.getBody() : "";
        holder.preview.setText(StreamPayloadFormatter.previewLine(body, displayHex, PREVIEW_MAX_CHARS));
        holder.preview.setTypeface(displayHex ? Typeface.MONOSPACE : Typeface.DEFAULT);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStreamClick(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return visibleEntries.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView seq;
        final TextView direction;
        final TextView time;
        final TextView preview;

        Holder(@NonNull View itemView) {
            super(itemView);
            seq = itemView.findViewById(R.id.stream_seq);
            direction = itemView.findViewById(R.id.stream_direction);
            time = itemView.findViewById(R.id.stream_time);
            preview = itemView.findViewById(R.id.stream_preview);
        }
    }
}
