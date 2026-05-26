package com.sunnynet.tools.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sunnynet.tools.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 已安装应用多选对话框；不选任何项表示抓取全部应用（不含本应用）。
 */
public class AppPickerDialogFragment extends DialogFragment {

    public static final String TAG = "AppPickerDialog";
    public static final String REQUEST_KEY = "app_picker_result";
    public static final String RESULT_PACKAGES = "selected_packages";
    private static final String ARG_SELECTED = "initial_selected";

    private AppPickerAdapter adapter;
    private ProgressBar loadingView;
    private TextView emptyView;
    private RecyclerView listView;
    private MaterialCheckBox showSelectedOnlyCheck;
    private MaterialButton clearAllButton;

    public static AppPickerDialogFragment newInstance(@NonNull Set<String> selectedPackages) {
        AppPickerDialogFragment fragment = new AppPickerDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_SELECTED, new ArrayList<>(selectedPackages));
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View content = getLayoutInflater().inflate(R.layout.dialog_app_picker, null);
        loadingView = content.findViewById(R.id.app_picker_loading);
        emptyView = content.findViewById(R.id.app_picker_empty);
        listView = content.findViewById(R.id.app_picker_list);
        showSelectedOnlyCheck = content.findViewById(R.id.app_picker_show_selected);
        clearAllButton = content.findViewById(R.id.app_picker_clear_all);
        TextInputEditText searchInput = content.findViewById(R.id.app_picker_search);

        adapter = new AppPickerAdapter();
        listView.setAdapter(adapter);
        Set<String> initial = new HashSet<>();
        Bundle args = getArguments();
        if (args != null) {
            ArrayList<String> selected = args.getStringArrayList(ARG_SELECTED);
            if (selected != null) {
                initial.addAll(selected);
            }
        }
        adapter.setSelectedPackages(initial);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setSearchQuery(s != null ? s.toString() : "");
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        showSelectedOnlyCheck.setOnCheckedChangeListener((btn, checked) -> {
            adapter.setShowOnlySelected(checked);
            updateEmptyState();
        });

        clearAllButton.setOnClickListener(v -> {
            adapter.clearAllSelections();
            updateEmptyState();
        });

        loadAppsAsync();

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.capture_packages_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Bundle result = new Bundle();
                    result.putStringArrayList(RESULT_PACKAGES,
                            new ArrayList<>(adapter.getSelectedPackages()));
                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
            KeyboardDismissHelper.installOutsideTapHideIme(window);
        }
    }

    private void loadAppsAsync() {
        showLoading(true);
        new Thread(() -> {
            List<InstalledAppInfo> apps = InstalledAppLoader.load(requireContext());
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                adapter.setApps(apps);
                showLoading(false);
                updateEmptyState();
            });
        }, "load-installed-apps").start();
    }

    private void showLoading(boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        listView.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading) {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void updateEmptyState() {
        if (loadingView.getVisibility() == View.VISIBLE) {
            return;
        }
        boolean empty = adapter.getVisibleCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            if (adapter.isShowOnlySelected() && adapter.getSelectedCount() == 0) {
                emptyView.setText(R.string.app_picker_empty_selected);
            } else {
                emptyView.setText(R.string.app_picker_empty);
            }
        }
    }
}
