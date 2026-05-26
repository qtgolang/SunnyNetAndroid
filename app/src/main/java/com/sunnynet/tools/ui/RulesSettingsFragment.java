package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.sunnynet.tools.MainActivity;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRule;
import com.sunnynet.tools.capture.CaptureRuleStore;

import java.util.EnumMap;
import java.util.Map;

/**
 * 规则设置总览：总开关 + 五类规则入口（替换/重写/拦截/屏蔽/Hosts）。
 */
public class RulesSettingsFragment extends Fragment {

    private MaterialSwitch masterSwitch;
    private boolean applying;
    private final Map<CaptureRule.Type, View> categoryRows = new EnumMap<>(CaptureRule.Type.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rules_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CaptureRuleStore store = CaptureRuleStore.get(requireContext());
        masterSwitch = view.findViewById(R.id.rules_master_switch);

        applying = true;
        masterSwitch.setChecked(store.isRulesEnabled());
        applying = false;
        masterSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!applying) {
                store.setRulesEnabled(checked);
            }
        });

        LinearLayout container = view.findViewById(R.id.rules_categories_container);
        addCategoryRow(container, CaptureRule.Type.REPLACE,
                R.string.rules_type_replace_title, R.string.rules_type_replace_desc);
        addCategoryRow(container, CaptureRule.Type.REWRITE,
                R.string.rules_type_rewrite_title, R.string.rules_type_rewrite_desc);
        addCategoryRow(container, CaptureRule.Type.INTERCEPT,
                R.string.rules_type_intercept_title, R.string.rules_type_intercept_desc);
        addCategoryRow(container, CaptureRule.Type.BLOCK,
                R.string.rules_type_block_title, R.string.rules_type_block_desc);
        addCategoryRow(container, CaptureRule.Type.HOSTS,
                R.string.rules_type_hosts_title, R.string.rules_type_hosts_desc);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCategoryCounts();
    }

    private void addCategoryRow(@NonNull LinearLayout container, @NonNull CaptureRule.Type type,
                                @StringRes int titleRes, @StringRes int subtitleRes) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_rules_category_row, container, false);
        TextView title = row.findViewById(R.id.rules_category_title);
        TextView subtitle = row.findViewById(R.id.rules_category_subtitle);
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
        row.setTag(type);
        row.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (tag instanceof CaptureRule.Type && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToRuleList((CaptureRule.Type) tag);
            }
        });
        categoryRows.put(type, row);
        container.addView(row);
    }

    private void refreshCategoryCounts() {
        if (!isAdded()) {
            return;
        }
        CaptureRuleStore store = CaptureRuleStore.get(requireContext());
        for (Map.Entry<CaptureRule.Type, View> entry : categoryRows.entrySet()) {
            TextView countView = entry.getValue().findViewById(R.id.rules_category_count);
            countView.setText(getString(R.string.rules_count_format,
                    store.countByType(entry.getKey())));
        }
    }
}
