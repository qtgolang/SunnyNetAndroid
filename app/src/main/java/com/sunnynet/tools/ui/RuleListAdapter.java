package com.sunnynet.tools.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRule;
import com.sunnynet.tools.capture.CaptureRuleConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则列表适配器：按类型展示摘要，支持开关与编辑。
 */
public class RuleListAdapter extends RecyclerView.Adapter<RuleListAdapter.ViewHolder> {

    public interface Listener {
        void onRuleEnabledChanged(@NonNull CaptureRule rule, boolean enabled);

        void onRuleClick(@NonNull CaptureRule rule);

        void onRuleLongClick(@NonNull CaptureRule rule);
    }

    private final CaptureRule.Type listType;
    @Nullable
    private Listener listener;
    private final List<CaptureRule> items = new ArrayList<>();

    public RuleListAdapter(@NonNull CaptureRule.Type listType) {
        this.listType = listType;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setItems(@NonNull List<CaptureRule> rules) {
        items.clear();
        items.addAll(rules);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rule_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CaptureRule rule = items.get(position);
        holder.titleView.setText(buildTitle(holder.itemView, rule));
        holder.detailView.setText(buildDetail(holder.itemView, rule));
        holder.detailView.setVisibility(holder.detailView.getText().length() == 0
                ? View.GONE : View.VISIBLE);
        bindNote(holder, rule);
        holder.enabledSwitch.setOnCheckedChangeListener(null);
        holder.enabledSwitch.setChecked(rule.isEnabled());
        holder.enabledSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (listener != null) {
                listener.onRuleEnabledChanged(rule, checked);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRuleClick(rule);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onRuleLongClick(rule);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    private String buildTitle(@NonNull View itemView, @NonNull CaptureRule rule) {
        switch (listType) {
            case REWRITE:
                return rule.getMatchURL().isEmpty()
                        ? itemView.getContext().getString(R.string.rules_card_empty_match)
                        : rule.getMatchURL();
            case HOSTS:
                return rule.getOldHost().isEmpty()
                        ? itemView.getContext().getString(R.string.rules_card_empty_match)
                        : rule.getOldHost();
            case REPLACE:
                return truncate(rule.getOldData(), 48);
            case INTERCEPT:
            case BLOCK:
            default:
                return truncate(rule.getMatchData(), 48);
        }
    }

    @NonNull
    private String buildDetail(@NonNull View itemView, @NonNull CaptureRule rule) {
        switch (listType) {
            case HOSTS:
                return rule.getOldHost() + " → " + rule.getNewHost();
            case REPLACE:
                return scopeSummary(rule.getScopes()) + " · " + rule.getOldData() + " → " + rule.getNewData();
            case REWRITE:
                return rule.getMatchMethod() + " · HTTP " + (rule.getStatusCode().isEmpty() ? "200" : rule.getStatusCode());
            case INTERCEPT:
                return scopeSummary(rule.getScopes()) + " · " + matchInSummary(rule.getMatchIns());
            case BLOCK:
                return scopeSummary(rule.getScopes()) + " · " + labelForMatchType(rule.getMatchType());
            default:
                return "";
        }
    }

    private void bindNote(@NonNull ViewHolder holder, @NonNull CaptureRule rule) {
        String note = rule.getNote() != null ? rule.getNote().trim() : "";
        if (note.isEmpty()) {
            holder.noteView.setVisibility(View.GONE);
            return;
        }
        holder.noteView.setVisibility(View.VISIBLE);
        holder.noteView.setText(holder.itemView.getContext().getString(R.string.rules_card_note, note));
    }

    @NonNull
    private static String scopeSummary(@NonNull List<String> scopes) {
        if (scopes.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scopes.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(shortScope(scopes.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    private static String matchInSummary(@NonNull List<String> matchIns) {
        if (matchIns.isEmpty()) {
            return itemViewFallback();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matchIns.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(shortMatchIn(matchIns.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    private static String itemViewFallback() {
        return "-";
    }

    @NonNull
    private static String shortScope(@NonNull String scope) {
        switch (scope) {
            case CaptureRuleConstants.SCOPE_HTTP_REQUEST:
                return "HTTP↑";
            case CaptureRuleConstants.SCOPE_HTTP_RESPONSE:
                return "HTTP↓";
            case CaptureRuleConstants.SCOPE_WS_SEND:
                return "WS↑";
            case CaptureRuleConstants.SCOPE_WS_RECEIVE:
                return "WS↓";
            case CaptureRuleConstants.SCOPE_TCP_SEND:
                return "TCP↑";
            case CaptureRuleConstants.SCOPE_TCP_RECEIVE:
                return "TCP↓";
            case CaptureRuleConstants.SCOPE_UDP_SEND:
                return "UDP↑";
            case CaptureRuleConstants.SCOPE_UDP_RECEIVE:
                return "UDP↓";
            default:
                return scope;
        }
    }

    @NonNull
    private static String shortMatchIn(@NonNull String matchIn) {
        switch (matchIn) {
            case CaptureRuleConstants.MATCH_IN_URL:
                return "URL";
            case CaptureRuleConstants.MATCH_IN_REQUEST_HEADER:
                return "ReqHdr";
            case CaptureRuleConstants.MATCH_IN_REQUEST_BODY:
                return "ReqBody";
            case CaptureRuleConstants.MATCH_IN_RESPONSE_HEADER:
                return "RspHdr";
            case CaptureRuleConstants.MATCH_IN_RESPONSE_BODY:
                return "RspBody";
            default:
                return matchIn;
        }
    }

    @NonNull
    private static String labelForMatchType(@NonNull String type) {
        switch (type.toLowerCase()) {
            case CaptureRuleConstants.MATCH_HEX:
                return "Hex";
            case CaptureRuleConstants.MATCH_BASE64:
                return "Base64";
            case CaptureRuleConstants.MATCH_REGEX:
                return "Regex";
            case CaptureRuleConstants.MATCH_STRING:
            default:
                return "String";
        }
    }

    @NonNull
    private static String truncate(@NonNull String text, int max) {
        if (text.length() <= max) {
            return text.isEmpty() ? "—" : text;
        }
        return text.substring(0, max) + "…";
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView detailView;
        final TextView noteView;
        final MaterialSwitch enabledSwitch;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.rule_item_match);
            detailView = itemView.findViewById(R.id.rule_item_detail);
            noteView = itemView.findViewById(R.id.rule_item_note);
            enabledSwitch = itemView.findViewById(R.id.rule_item_enabled);
        }
    }
}
