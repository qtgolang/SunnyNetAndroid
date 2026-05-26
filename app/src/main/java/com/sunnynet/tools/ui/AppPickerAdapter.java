package com.sunnynet.tools.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.sunnynet.tools.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 应用多选列表：支持搜索过滤与勾选状态。
 */
public class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.ViewHolder> {

    private final List<InstalledAppInfo> allApps = new ArrayList<>();
    private final List<InstalledAppInfo> visibleApps = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private String searchQuery = "";
    private boolean showOnlySelected;

    public void setShowOnlySelected(boolean showOnlySelected) {
        this.showOnlySelected = showOnlySelected;
        applyFilter();
    }

    public boolean isShowOnlySelected() {
        return showOnlySelected;
    }

    public void clearAllSelections() {
        selectedPackages.clear();
        applyFilter();
    }

    public int getSelectedCount() {
        return selectedPackages.size();
    }

    public void setApps(@NonNull List<InstalledAppInfo> apps) {
        allApps.clear();
        allApps.addAll(apps);
        applyFilter();
    }

    public void setSelectedPackages(@NonNull Set<String> packages) {
        selectedPackages.clear();
        selectedPackages.addAll(packages);
        applyFilter();
    }

    @NonNull
    public Set<String> getSelectedPackages() {
        return new HashSet<>(selectedPackages);
    }

    public void setSearchQuery(@NonNull String query) {
        searchQuery = query.trim().toLowerCase(Locale.getDefault());
        applyFilter();
    }

    private void applyFilter() {
        visibleApps.clear();
        for (InstalledAppInfo app : allApps) {
            if (showOnlySelected && !selectedPackages.contains(app.packageName)) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                String label = app.label != null ? app.label.toString().toLowerCase(Locale.getDefault()) : "";
                if (!label.contains(searchQuery)
                        && !app.packageName.toLowerCase(Locale.getDefault()).contains(searchQuery)) {
                    continue;
                }
            }
            visibleApps.add(app);
        }
        notifyDataSetChanged();
    }

    public int getVisibleCount() {
        return visibleApps.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstalledAppInfo app = visibleApps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.pkg.setText(app.packageName);
        holder.checked.setChecked(selectedPackages.contains(app.packageName));
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            InstalledAppInfo item = visibleApps.get(pos);
            if (selectedPackages.contains(item.packageName)) {
                selectedPackages.remove(item.packageName);
            } else {
                selectedPackages.add(item.packageName);
            }
            applyFilter();
        });
    }

    @Override
    public int getItemCount() {
        return visibleApps.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView pkg;
        final MaterialCheckBox checked;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_icon);
            label = itemView.findViewById(R.id.app_label);
            pkg = itemView.findViewById(R.id.app_package);
            checked = itemView.findViewById(R.id.app_checked);
        }
    }
}
