package com.sunnynet.tools.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRule;
import com.sunnynet.tools.capture.CaptureRuleStore;

/**
 * 某一类规则的列表页：增删改、启用开关。
 */
public class RuleListFragment extends Fragment implements RuleListAdapter.Listener {

    private static final String ARG_TYPE = "rule_type";

    private CaptureRule.Type ruleType;
    private TextView descView;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private RuleListAdapter adapter;

    @NonNull
    public static RuleListFragment newInstance(@NonNull CaptureRule.Type type) {
        RuleListFragment fragment = new RuleListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        fragment.setArguments(args);
        return fragment;
    }

    @StringRes
    public int getTitleRes() {
        return titleResForType(resolveRuleType());
    }

    @StringRes
    public static int titleResForType(@NonNull CaptureRule.Type type) {
        switch (type) {
            case REPLACE:
                return R.string.rules_type_replace_title;
            case REWRITE:
                return R.string.rules_type_rewrite_title;
            case INTERCEPT:
                return R.string.rules_type_intercept_title;
            case HOSTS:
                return R.string.rules_type_hosts_title;
            case BLOCK:
            default:
                return R.string.rules_type_block_title;
        }
    }

    @NonNull
    private CaptureRule.Type resolveRuleType() {
        Bundle args = getArguments();
        if (args != null) {
            return CaptureRule.parseType(args.getString(ARG_TYPE), CaptureRule.Type.BLOCK);
        }
        return ruleType != null ? ruleType : CaptureRule.Type.BLOCK;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rule_list, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ruleType = resolveRuleType();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ruleType = resolveRuleType();
        descView = view.findViewById(R.id.rule_list_desc);
        emptyView = view.findViewById(R.id.rule_list_empty);
        recyclerView = view.findViewById(R.id.rule_list_recycler);
        FloatingActionButton addFab = view.findViewById(R.id.rule_list_add);

        descView.setText(descResForType(ruleType));
        adapter = new RuleListAdapter(ruleType);
        adapter.setListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        addFab.setOnClickListener(v -> showEditDialog(null));
        reloadRules();
    }

    private void reloadRules() {
        adapter.setItems(CaptureRuleStore.get(requireContext()).getRulesByType(ruleType));
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRuleEnabledChanged(@NonNull CaptureRule rule, boolean enabled) {
        CaptureRuleStore.get(requireContext()).setRuleEnabled(rule.getId(), enabled);
    }

    @Override
    public void onRuleClick(@NonNull CaptureRule rule) {
        showEditDialog(rule);
    }

    @Override
    public void onRuleLongClick(@NonNull CaptureRule rule) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rules_delete_title)
                .setMessage(R.string.rules_delete_message)
                .setPositiveButton(R.string.rules_delete_confirm, (d, w) -> {
                    CaptureRuleStore.get(requireContext()).delete(rule.getId());
                    reloadRules();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditDialog(@Nullable CaptureRule existing) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_rule_edit, null, false);
        RuleEditFormHelper form = RuleEditFormHelper.bind(requireContext(), dialogView, ruleType, existing);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.rules_add : R.string.rules_edit)
                .setView(dialogView)
                .setPositiveButton(R.string.rules_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                KeyboardDismissHelper.installOutsideTapHideIme(dialog.getWindow());
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String error = form.validate();
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        CaptureRule rule = existing != null ? existing : new CaptureRule();
                        form.readInto(rule);
                        if (ruleType == CaptureRule.Type.INTERCEPT) {
                            Toast.makeText(requireContext(), R.string.rules_intercept_pending, Toast.LENGTH_SHORT).show();
                        }
                        CaptureRuleStore.get(requireContext()).upsert(rule);
                        reloadRules();
                        dialog.dismiss();
                    });
        });
        dialog.show();
    }

    @StringRes
    private int descResForType(@NonNull CaptureRule.Type type) {
        switch (type) {
            case REPLACE:
                return R.string.rules_type_replace_list_desc;
            case REWRITE:
                return R.string.rules_type_rewrite_list_desc;
            case INTERCEPT:
                return R.string.rules_type_intercept_list_desc;
            case HOSTS:
                return R.string.rules_type_hosts_list_desc;
            case BLOCK:
            default:
                return R.string.rules_type_block_list_desc;
        }
    }
}
