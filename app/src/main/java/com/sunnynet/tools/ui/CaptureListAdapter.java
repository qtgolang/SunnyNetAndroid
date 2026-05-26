package com.sunnynet.tools.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.HttpDetailSections;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 抓包主列表适配器：支持协议类型与关键词筛选；每条卡片 **# 序号** 按**捕获时间正序**（最早 #1）；主列表从上到下为旧→新（**最新在底部**），筛选不改变编号。
 */
public class CaptureListAdapter extends RecyclerView.Adapter<CaptureListAdapter.ViewHolder> {

    /** {@link #setFilter} 协议筛 HTTP 方法前缀，后缀为大写 Method（GET/POST…）。 */
    public static final String HTTP_METHOD_FILTER_PREFIX = "HTTP_METHOD:";

    private static final Comparator<CaptureRecord> CAPTURE_CHRONO_ORDER = Comparator
            .comparingLong(CaptureRecord::getTimestampMs)
            .thenComparingLong(CaptureRecord::getId);

    public interface OnItemClickListener {
        void onItemClick(CaptureRecord record);
    }

    private final List<CaptureRecord> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private OnItemClickListener listener;
    @Nullable
    private String protocolFilter;
    private String searchKeyword = "";
    @Nullable
    private android.content.Context appContext;

    /** 未筛选全量内按「时间升序 + id」分配 #1…#N；筛选仅隐藏行，不改编号 */
    private final Map<Long, Integer> listSeqByRecordId = new HashMap<>();

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** 用于搜索时匹配应用名；传 ApplicationContext 即可。 */
    public void setAppContext(@Nullable android.content.Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    /** 设置筛选条件后需调用 {@link #setItems(List)} 刷新 */
    public void setFilter(@Nullable String protocolFilter, String searchKeyword) {
        this.protocolFilter = protocolFilter;
        this.searchKeyword = searchKeyword != null ? searchKeyword : "";
    }

    /** 传入完整快照时先据此建立序号映射，再按筛选条件填充列表。 */
    public void setItems(List<CaptureRecord> records) {
        rebuildListSeq(records);
        items.clear();
        if (records != null) {
            for (CaptureRecord record : records) {
                if (matchesFilter(record)) {
                    items.add(record);
                }
            }
        }
        notifyDataSetChanged();
    }

    /** 在快照副本上按时间升序编号，与仓库「新在前」展示顺序解耦。 */
    private void rebuildListSeq(@Nullable List<CaptureRecord> snapshot) {
        listSeqByRecordId.clear();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        List<CaptureRecord> sorted = new ArrayList<>(snapshot);
        sorted.sort(CAPTURE_CHRONO_ORDER);
        for (int i = 0; i < sorted.size(); i++) {
            listSeqByRecordId.put(sorted.get(i).getId(), i + 1);
        }
    }

    public void addItem(CaptureRecord record) {
        if (!matchesFilter(record)) {
            return;
        }
        items.add(record);
        notifyItemInserted(items.size() - 1);
    }

    public void updateItem(CaptureRecord record) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == record.getId()) {
                if (matchesFilter(record)) {
                    items.set(i, record);
                    notifyItemChanged(i);
                } else {
                    items.remove(i);
                    notifyItemRemoved(i);
                }
                return;
            }
        }
        if (matchesFilter(record)) {
            addItem(record);
        }
    }

    public void clear() {
        items.clear();
        listSeqByRecordId.clear();
        notifyDataSetChanged();
    }

    private boolean matchesFilter(CaptureRecord record) {
        if (protocolFilter != null) {
            if (protocolFilter.startsWith(HTTP_METHOD_FILTER_PREFIX)) {
                if (!CaptureRecord.TYPE_HTTP.equals(record.getProtocol())) {
                    return false;
                }
                String want = protocolFilter.substring(HTTP_METHOD_FILTER_PREFIX.length());
                String method = HttpDetailSections.parseMethodAndUrl(record)[0];
                if (method == null || !want.equalsIgnoreCase(method.trim())) {
                    return false;
                }
            } else if (!protocolFilter.equals(record.getProtocol())) {
                return false;
            }
        }
        if (!searchKeyword.isEmpty()) {
            String appText = appContext != null
                    ? AppIconHelper.searchableText(appContext, record)
                    : fallbackPackageSearchText(record);
            String hay = (record.buildSearchText() + "\n" + appText)
                    .toLowerCase(Locale.ROOT);
            if (!hay.contains(searchKeyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static String fallbackPackageSearchText(@NonNull CaptureRecord record) {
        if (record.getPackageName() == null || record.getPackageName().isEmpty()) {
            return record.isProxyRequestDisplay() ? "代理请求" : "系统进程";
        }
        return record.getPackageName();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_capture, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CaptureRecord record = items.get(position);
        int seqNum = listSeqByRecordId.getOrDefault(record.getId(), position + 1);
        holder.seq.setText(holder.itemView.getContext().getString(R.string.stream_card_seq_format, seqNum));
        holder.protocol.setText(record.getProtocol());
        ProtocolBadgeHelper.apply(holder.protocol, record.getProtocol());
        AppIconHelper.bindListItem(holder.appIcon, holder.appName, holder.appPackage, record);
        CaptureCardUiHelper.bindTitle(holder.title, record);
        ConnectionStatusUiHelper.applyListItem(holder.summary, holder.accent, record);
        String primaryCt = HttpDetailSections.extractResponsePrimaryContentType(record);
        if (primaryCt.isEmpty()) {
            holder.responseContentType.setVisibility(View.GONE);
            holder.responseContentType.setText("");
        } else {
            holder.responseContentType.setText(primaryCt);
            holder.responseContentType.setVisibility(View.VISIBLE);
        }
        holder.time.setText(timeFormat.format(new Date(record.getTimestampMs())));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(record);
            }
        });
        CaptureListResponseThumbBinder.bind(holder.thumbFrame, holder.responseThumb, record);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        CaptureListResponseThumbBinder.release(holder.thumbFrame, holder.responseThumb);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View accent;
        final TextView seq;
        final ImageView appIcon;
        final TextView appName;
        final TextView appPackage;
        final TextView protocol;
        final TextView title;
        final TextView summary;
        /** HTTP 响应 {@code Content-Type} 主类型（分号前），无则 {@link View#GONE}。 */
        final TextView responseContentType;
        final TextView time;
        final View thumbFrame;
        final ImageView responseThumb;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            accent = itemView.findViewById(R.id.capture_accent);
            seq = itemView.findViewById(R.id.capture_seq);
            appIcon = itemView.findViewById(R.id.capture_app_icon);
            appName = itemView.findViewById(R.id.capture_app_name);
            appPackage = itemView.findViewById(R.id.capture_app_package);
            protocol = itemView.findViewById(R.id.capture_protocol);
            title = itemView.findViewById(R.id.capture_title);
            summary = itemView.findViewById(R.id.capture_summary);
            responseContentType = itemView.findViewById(R.id.capture_response_content_type);
            time = itemView.findViewById(R.id.capture_time);
            thumbFrame = itemView.findViewById(R.id.capture_list_thumb_frame);
            responseThumb = itemView.findViewById(R.id.capture_list_response_thumb);
        }
    }
}
