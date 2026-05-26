package com.sunnynet.tools.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRule;
import com.sunnynet.tools.capture.CaptureRuleConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按规则类型绑定/校验/读取添加规则表单，对齐 SunnyNetV5 各弹窗字段。
 */
public final class RuleEditFormHelper {

    private final Context context;
    private final CaptureRule.Type ruleType;
    private final View root;

    private TextView subtitleView;
    private View sectionScopes;
    private LinearLayout scopesContainer;
    private View sectionRewriteMatch;
    private MaterialAutoCompleteTextView methodInput;
    private TextInputEditText matchUrlInput;
    private View sectionMatchIn;
    private LinearLayout matchInContainer;
    private View sectionMatch;
    private MaterialAutoCompleteTextView matchTypeInput;
    private TextInputEditText matchDataInput;
    private View sectionReplace;
    private MaterialAutoCompleteTextView oldTypeInput;
    private MaterialAutoCompleteTextView newTypeInput;
    private TextInputEditText oldDataInput;
    private TextInputEditText newDataInput;
    private View sectionRewriteResponse;
    private TextInputEditText statusCodeInput;
    private TextInputEditText headersInput;
    private TextInputEditText responseDataInput;
    private View sectionHosts;
    private TextInputEditText oldHostInput;
    private TextInputEditText newHostInput;
    private TextInputEditText noteInput;

    private RuleEditFormHelper(@NonNull Context context, @NonNull View root, @NonNull CaptureRule.Type ruleType) {
        this.context = context;
        this.root = root;
        this.ruleType = ruleType;
        bindViews();
        configureVisibility();
        setupDropdowns();
    }

    @NonNull
    public static RuleEditFormHelper bind(@NonNull Context context, @NonNull View root,
                                          @NonNull CaptureRule.Type ruleType,
                                          @Nullable CaptureRule existing) {
        RuleEditFormHelper helper = new RuleEditFormHelper(context, root, ruleType);
        if (existing != null) {
            helper.populate(existing);
        } else {
            helper.applyDefaults();
        }
        return helper;
    }

    @Nullable
    public String validate() {
        switch (ruleType) {
            case REPLACE:
                if (readScopes().isEmpty()) {
                    return context.getString(R.string.rules_error_scopes_required);
                }
                if (textOf(oldDataInput).isEmpty()) {
                    return context.getString(R.string.rules_error_old_data_required);
                }
                if (!CaptureRuleConstants.MATCH_STRING.equals(readTypeValue(newTypeInput,
                        replaceNewTypeLabels(), replaceNewTypeValues()))
                        && textOf(newDataInput).isEmpty()) {
                    return context.getString(R.string.rules_error_new_data_required);
                }
                return null;
            case REWRITE:
                String url = textOf(matchUrlInput);
                if (url.isEmpty()) {
                    return context.getString(R.string.rules_error_match_url_required);
                }
                String lower = url.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                    return context.getString(R.string.rules_error_match_url_scheme);
                }
                String status = textOf(statusCodeInput);
                if (!status.isEmpty() && !status.matches("\\d{3}")) {
                    return context.getString(R.string.rules_error_status_code);
                }
                return null;
            case INTERCEPT:
                if (readScopes().isEmpty()) {
                    return context.getString(R.string.rules_error_scopes_required);
                }
                if (readMatchIns().isEmpty()) {
                    return context.getString(R.string.rules_error_match_in_required);
                }
                if (textOf(matchDataInput).isEmpty()) {
                    return context.getString(R.string.rules_error_match_data_required);
                }
                return null;
            case BLOCK:
                if (readScopes().isEmpty()) {
                    return context.getString(R.string.rules_error_scopes_required);
                }
                if (textOf(matchDataInput).isEmpty()) {
                    return context.getString(R.string.rules_error_match_data_required);
                }
                return null;
            case HOSTS:
                if (textOf(oldHostInput).isEmpty() || textOf(newHostInput).isEmpty()) {
                    return context.getString(R.string.rules_error_hosts_required);
                }
                return null;
            default:
                return null;
        }
    }

    @NonNull
    public CaptureRule readInto(@NonNull CaptureRule rule) {
        rule.setType(ruleType);
        rule.setNote(textOf(noteInput));
        switch (ruleType) {
            case REPLACE:
                rule.setScopes(readScopes());
                rule.setOldType(readTypeValue(oldTypeInput, CaptureRuleConstants.matchTypeLabels(),
                        CaptureRuleConstants.matchTypeValues()));
                rule.setOldData(textOf(oldDataInput));
                rule.setNewType(readTypeValue(newTypeInput, replaceNewTypeLabels(), replaceNewTypeValues()));
                rule.setNewData(textOf(newDataInput));
                break;
            case REWRITE:
                rule.setMatchURL(stripQuery(textOf(matchUrlInput)));
                rule.setMatchMethod(textOf(methodInput).toUpperCase(Locale.ROOT));
                rule.setStatusCode(textOf(statusCodeInput));
                rule.setHeaders(textOf(headersInput));
                rule.setResponseType(CaptureRuleConstants.RESPONSE_TYPE_TEXT);
                rule.setResponseData(textOf(responseDataInput));
                break;
            case INTERCEPT:
                rule.setScopes(readScopes());
                rule.setMatchIns(readMatchIns());
                rule.setMatchType(readTypeValue(matchTypeInput, CaptureRuleConstants.matchTypeLabels(),
                        CaptureRuleConstants.matchTypeValues()));
                rule.setMatchData(textOf(matchDataInput));
                break;
            case BLOCK:
                rule.setScopes(readScopes());
                rule.setMatchType(readTypeValue(matchTypeInput, CaptureRuleConstants.matchTypeLabels(),
                        CaptureRuleConstants.matchTypeValues()));
                rule.setMatchData(textOf(matchDataInput));
                break;
            case HOSTS:
                rule.setOldHost(textOf(oldHostInput));
                rule.setNewHost(textOf(newHostInput));
                break;
            default:
                break;
        }
        return rule;
    }

    private void bindViews() {
        subtitleView = root.findViewById(R.id.rule_edit_subtitle);
        sectionScopes = root.findViewById(R.id.rule_section_scopes);
        scopesContainer = root.findViewById(R.id.rule_scopes_container);
        sectionRewriteMatch = root.findViewById(R.id.rule_section_rewrite_match);
        methodInput = root.findViewById(R.id.rule_edit_method);
        matchUrlInput = root.findViewById(R.id.rule_edit_match_url);
        sectionMatchIn = root.findViewById(R.id.rule_section_match_in);
        matchInContainer = root.findViewById(R.id.rule_match_in_container);
        sectionMatch = root.findViewById(R.id.rule_section_match);
        matchTypeInput = root.findViewById(R.id.rule_edit_match_type);
        matchDataInput = root.findViewById(R.id.rule_edit_match_data);
        sectionReplace = root.findViewById(R.id.rule_section_replace);
        oldTypeInput = root.findViewById(R.id.rule_edit_old_type);
        newTypeInput = root.findViewById(R.id.rule_edit_new_type);
        oldDataInput = root.findViewById(R.id.rule_edit_old_data);
        newDataInput = root.findViewById(R.id.rule_edit_new_data);
        sectionRewriteResponse = root.findViewById(R.id.rule_section_rewrite_response);
        statusCodeInput = root.findViewById(R.id.rule_edit_status_code);
        headersInput = root.findViewById(R.id.rule_edit_headers);
        responseDataInput = root.findViewById(R.id.rule_edit_response_data);
        sectionHosts = root.findViewById(R.id.rule_section_hosts);
        oldHostInput = root.findViewById(R.id.rule_edit_old_host);
        newHostInput = root.findViewById(R.id.rule_edit_new_host);
        noteInput = root.findViewById(R.id.rule_edit_note);
    }

    private void configureVisibility() {
        subtitleView.setText(subtitleResForType());
        hideAllSections();
        switch (ruleType) {
            case REPLACE:
                show(sectionScopes, sectionReplace);
                bindScopes(false);
                break;
            case REWRITE:
                show(sectionRewriteMatch, sectionRewriteResponse);
                break;
            case INTERCEPT:
                show(sectionScopes, sectionMatchIn, sectionMatch);
                bindScopes(true);
                bindMatchIns();
                break;
            case BLOCK:
                show(sectionScopes, sectionMatch);
                bindScopes(false);
                break;
            case HOSTS:
                show(sectionHosts);
                break;
            default:
                break;
        }
    }

    private void hideAllSections() {
        sectionScopes.setVisibility(View.GONE);
        sectionRewriteMatch.setVisibility(View.GONE);
        sectionMatchIn.setVisibility(View.GONE);
        sectionMatch.setVisibility(View.GONE);
        sectionReplace.setVisibility(View.GONE);
        sectionRewriteResponse.setVisibility(View.GONE);
        sectionHosts.setVisibility(View.GONE);
    }

    private void show(@NonNull View... views) {
        for (View view : views) {
            view.setVisibility(View.VISIBLE);
        }
    }

    private void setupDropdowns() {
        bindTypeDropdown(matchTypeInput, CaptureRuleConstants.matchTypeLabels(),
                CaptureRuleConstants.matchTypeValues(), CaptureRuleConstants.MATCH_STRING);
        bindTypeDropdown(oldTypeInput, CaptureRuleConstants.matchTypeLabels(),
                CaptureRuleConstants.matchTypeValues(), CaptureRuleConstants.MATCH_STRING);
        bindTypeDropdown(newTypeInput, replaceNewTypeLabels(), replaceNewTypeValues(),
                CaptureRuleConstants.MATCH_STRING);
        methodInput.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1,
                CaptureRuleConstants.rewriteMethods()));
    }

    private void applyDefaults() {
        setDropdownValue(methodInput, CaptureRuleConstants.REWRITE_METHOD_ALL);
        setDropdownValue(matchTypeInput, labelForType(CaptureRuleConstants.MATCH_STRING));
        setDropdownValue(oldTypeInput, labelForType(CaptureRuleConstants.MATCH_STRING));
        setDropdownValue(newTypeInput, labelForType(CaptureRuleConstants.MATCH_STRING));
        if (ruleType == CaptureRule.Type.REPLACE || ruleType == CaptureRule.Type.BLOCK) {
            CheckBox first = findFirstCheckedScopeCandidate();
            if (first != null) {
                first.setChecked(true);
            }
        }
        if (ruleType == CaptureRule.Type.INTERCEPT) {
            CheckBox scopeReq = findScopeCheckbox(CaptureRuleConstants.SCOPE_HTTP_REQUEST);
            if (scopeReq != null) {
                scopeReq.setChecked(true);
            }
            CheckBox urlIn = findMatchInCheckbox(CaptureRuleConstants.MATCH_IN_URL);
            if (urlIn != null) {
                urlIn.setChecked(true);
            }
        }
    }

    private void populate(@NonNull CaptureRule rule) {
        noteInput.setText(rule.getNote());
        switch (ruleType) {
            case REPLACE:
                setCheckedScopes(rule.getScopes());
                setDropdownValue(oldTypeInput, labelForType(rule.getOldType(),
                        CaptureRuleConstants.matchTypeLabels(), CaptureRuleConstants.matchTypeValues()));
                oldDataInput.setText(rule.getOldData());
                setDropdownValue(newTypeInput, labelForType(rule.getNewType(),
                        replaceNewTypeLabels(), replaceNewTypeValues()));
                newDataInput.setText(rule.getNewData());
                break;
            case REWRITE:
                methodInput.setText(rule.getMatchMethod(), false);
                matchUrlInput.setText(rule.getMatchURL());
                statusCodeInput.setText(rule.getStatusCode());
                headersInput.setText(rule.getHeaders());
                responseDataInput.setText(rule.getResponseData());
                break;
            case INTERCEPT:
                setCheckedScopes(rule.getScopes());
                setCheckedMatchIns(rule.getMatchIns());
                setDropdownValue(matchTypeInput, labelForType(rule.getMatchType(),
                        CaptureRuleConstants.matchTypeLabels(), CaptureRuleConstants.matchTypeValues()));
                matchDataInput.setText(rule.getMatchData());
                break;
            case BLOCK:
                setCheckedScopes(rule.getScopes());
                setDropdownValue(matchTypeInput, labelForType(rule.getMatchType(),
                        CaptureRuleConstants.matchTypeLabels(), CaptureRuleConstants.matchTypeValues()));
                matchDataInput.setText(rule.getMatchData());
                break;
            case HOSTS:
                oldHostInput.setText(rule.getOldHost());
                newHostInput.setText(rule.getNewHost());
                break;
            default:
                break;
        }
    }

    private void bindScopes(boolean interceptOnly) {
        scopesContainer.removeAllViews();
        CaptureRuleConstants.ScopeOption[] options = interceptOnly
                ? CaptureRuleConstants.interceptScopes()
                : CaptureRuleConstants.allScopes();
        bindCheckboxGrid(scopesContainer, options);
    }

    /** 作用域 CheckBox 两列网格排列。 */
    private void bindCheckboxGrid(@NonNull LinearLayout container,
                                  @NonNull CaptureRuleConstants.ScopeOption[] options) {
        container.setOrientation(LinearLayout.VERTICAL);
        int marginEndPx = (int) (8 * context.getResources().getDisplayMetrics().density);
        for (int i = 0; i < options.length; i += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            addScopeCheckbox(row, options[i], marginEndPx);
            if (i + 1 < options.length) {
                addScopeCheckbox(row, options[i + 1], marginEndPx);
            } else {
                View spacer = new View(context);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
            }
            container.addView(row);
        }
    }

    private void addScopeCheckbox(@NonNull LinearLayout row,
                                  @NonNull CaptureRuleConstants.ScopeOption option,
                                  int marginEndPx) {
        CheckBox box = new CheckBox(context);
        box.setText(option.label);
        box.setTag(option.id);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(marginEndPx);
        row.addView(box, lp);
    }

    private void forEachScopeCheckbox(@NonNull ScopeCheckboxVisitor visitor) {
        for (int i = 0; i < scopesContainer.getChildCount(); i++) {
            View row = scopesContainer.getChildAt(i);
            if (!(row instanceof ViewGroup)) {
                continue;
            }
            ViewGroup group = (ViewGroup) row;
            for (int j = 0; j < group.getChildCount(); j++) {
                View child = group.getChildAt(j);
                if (child instanceof CheckBox) {
                    visitor.visit((CheckBox) child);
                }
            }
        }
    }

    private interface ScopeCheckboxVisitor {
        void visit(@NonNull CheckBox box);
    }

    private void bindMatchIns() {
        matchInContainer.removeAllViews();
        for (CaptureRuleConstants.MatchInOption option : CaptureRuleConstants.interceptMatchIns()) {
            CheckBox box = new CheckBox(context);
            box.setText(option.label);
            box.setTag(option.id);
            matchInContainer.addView(box);
        }
    }

    @NonNull
    private List<String> readScopes() {
        List<String> out = new ArrayList<>();
        forEachScopeCheckbox(box -> {
            if (box.isChecked()) {
                Object tag = box.getTag();
                if (tag instanceof String) {
                    out.add((String) tag);
                }
            }
        });
        return out;
    }

    @NonNull
    private List<String> readMatchIns() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < matchInContainer.getChildCount(); i++) {
            View child = matchInContainer.getChildAt(i);
            if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                Object tag = child.getTag();
                if (tag instanceof String) {
                    out.add((String) tag);
                }
            }
        }
        return out;
    }

    private void setCheckedScopes(@NonNull List<String> scopes) {
        Set<String> set = new HashSet<>(scopes);
        forEachScopeCheckbox(box -> {
            Object tag = box.getTag();
            box.setChecked(tag instanceof String && set.contains(tag));
        });
    }

    private void setCheckedMatchIns(@NonNull List<String> matchIns) {
        Set<String> set = new HashSet<>(matchIns);
        for (int i = 0; i < matchInContainer.getChildCount(); i++) {
            View child = matchInContainer.getChildAt(i);
            if (child instanceof CheckBox) {
                Object tag = child.getTag();
                ((CheckBox) child).setChecked(tag instanceof String && set.contains(tag));
            }
        }
    }

    @Nullable
    private CheckBox findFirstCheckedScopeCandidate() {
        final CheckBox[] found = new CheckBox[1];
        forEachScopeCheckbox(box -> {
            if (found[0] == null) {
                found[0] = box;
            }
        });
        return found[0];
    }

    @Nullable
    private CheckBox findScopeCheckbox(@NonNull String scopeId) {
        final CheckBox[] found = new CheckBox[1];
        forEachScopeCheckbox(box -> {
            if (found[0] == null && scopeId.equals(box.getTag())) {
                found[0] = box;
            }
        });
        return found[0];
    }

    @Nullable
    private CheckBox findMatchInCheckbox(@NonNull String matchInId) {
        for (int i = 0; i < matchInContainer.getChildCount(); i++) {
            View child = matchInContainer.getChildAt(i);
            if (child instanceof CheckBox && matchInId.equals(child.getTag())) {
                return (CheckBox) child;
            }
        }
        return null;
    }

    private void bindTypeDropdown(@NonNull MaterialAutoCompleteTextView view,
                                  @NonNull String[] labels, @NonNull String[] values,
                                  @NonNull String defaultValue) {
        view.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
        view.setTag(R.id.rule_edit_match_type, values);
        setDropdownValue(view, labelForType(defaultValue, labels, values));
    }

    @NonNull
    private String readTypeValue(@NonNull MaterialAutoCompleteTextView view,
                                 @NonNull String[] labels, @NonNull String[] values) {
        String label = textOf(view);
        for (int i = 0; i < labels.length && i < values.length; i++) {
            if (labels[i].equals(label)) {
                return values[i];
            }
        }
        return values.length > 0 ? values[0] : CaptureRuleConstants.MATCH_STRING;
    }

    private void setDropdownValue(@NonNull MaterialAutoCompleteTextView view, @NonNull String label) {
        view.setText(label, false);
    }

    @NonNull
    private String labelForType(@NonNull String typeValue, @NonNull String[] labels,
                                @NonNull String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(typeValue) && i < labels.length) {
                return labels[i];
            }
        }
        return labels.length > 0 ? labels[0] : "";
    }

    @NonNull
    private String labelForType(@NonNull String typeValue) {
        return labelForType(typeValue, CaptureRuleConstants.matchTypeLabels(),
                CaptureRuleConstants.matchTypeValues());
    }

    @NonNull
    private static String[] replaceNewTypeLabels() {
        return new String[]{"字符串", "十六进制", "Base64"};
    }

    @NonNull
    private static String[] replaceNewTypeValues() {
        return new String[]{
                CaptureRuleConstants.MATCH_STRING,
                CaptureRuleConstants.MATCH_HEX,
                CaptureRuleConstants.MATCH_BASE64
        };
    }

    @StringRes
    private int subtitleResForType() {
        switch (ruleType) {
            case REPLACE:
                return R.string.rules_form_replace_subtitle;
            case REWRITE:
                return R.string.rules_form_rewrite_subtitle;
            case INTERCEPT:
                return R.string.rules_form_intercept_subtitle;
            case HOSTS:
                return R.string.rules_form_hosts_subtitle;
            case BLOCK:
            default:
                return R.string.rules_form_block_subtitle;
        }
    }

    @NonNull
    private static String textOf(@Nullable android.widget.TextView view) {
        if (view == null || view.getText() == null) {
            return "";
        }
        return view.getText().toString().trim();
    }

    @NonNull
    private static String stripQuery(@NonNull String url) {
        int q = url.indexOf('?');
        if (q >= 0) {
            return url.substring(0, q).trim();
        }
        return url.trim();
    }
}
