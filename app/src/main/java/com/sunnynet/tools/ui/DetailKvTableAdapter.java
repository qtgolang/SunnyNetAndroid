package com.sunnynet.tools.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.HttpOverviewBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 详情键值表格：总览、请求/响应信息与协议头等处复用。
 * 单击复制字段值（优先 {@link HttpOverviewBuilder.Row#copyText}，否则右列展示值）；
 * 长按弹出菜单：复制字段名、复制字段值、复制「名: 值」。
 */
public final class DetailKvTableAdapter extends RecyclerView.Adapter<DetailKvTableAdapter.Holder> {

    private final Context context;
    private final List<HttpOverviewBuilder.Row> rows;

    public DetailKvTableAdapter(@NonNull Context context,
                                @NonNull List<HttpOverviewBuilder.Row> rows) {
        this.context = context.getApplicationContext();
        this.rows = new ArrayList<>(rows);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_overview_row, parent, false);
        return new Holder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HttpOverviewBuilder.Row row = rows.get(position);
        holder.label.setText(row.label);
        holder.value.setText(row.value);

        holder.value.setTextIsSelectable(false);
        bindKeyValueRow(holder, row);
    }

    private void bindKeyValueRow(@NonNull Holder holder, @NonNull HttpOverviewBuilder.Row row) {
        String fieldValue = effectiveFieldValue(row);
        boolean canCopyValue = !fieldValue.isEmpty();
        holder.itemView.setLongClickable(true);
        holder.itemView.setClickable(canCopyValue);
        holder.itemView.setFocusable(canCopyValue);
        if (canCopyValue) {
            holder.itemView.setOnClickListener(v -> copyPlain(fieldValue));
        } else {
            holder.itemView.setOnClickListener(null);
        }
        String label = row.label != null ? row.label : "";
        holder.itemView.setOnLongClickListener(v -> {
            showKvCopyMenu(holder.itemView, label, fieldValue);
            return true;
        });
    }

    @NonNull
    private static String effectiveFieldValue(@NonNull HttpOverviewBuilder.Row row) {
        if (row.copyText != null && !row.copyText.isEmpty()) {
            return row.copyText;
        }
        return row.value != null ? row.value : "";
    }

    /** 「名: 值」；标签为空时仅返回值。 */
    @NonNull
    private static String labelValueLine(@NonNull String label, @NonNull String value) {
        String n = label.trim();
        if (n.isEmpty()) {
            return value;
        }
        return n + ": " + value;
    }

    private void showKvCopyMenu(@NonNull View anchor, @NonNull String label,
                                @NonNull String value) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.inflate(R.menu.menu_detail_kv_header_row);
        MenuItem nameItem = menu.getMenu().findItem(R.id.menu_copy_header_name);
        nameItem.setEnabled(!label.trim().isEmpty());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_copy_header_name) {
                copyPlain(label);
                return true;
            }
            if (id == R.id.menu_copy_header_value) {
                if (!value.isEmpty()) {
                    copyPlain(value);
                }
                return true;
            }
            if (id == R.id.menu_copy_header_name_value) {
                copyPlain(labelValueLine(label, value));
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void copyPlain(@NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("detail_kv", text));
        Toast.makeText(context, R.string.detail_overview_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView value;

        Holder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.overview_row_label);
            value = itemView.findViewById(R.id.overview_row_value);
        }
    }
}
