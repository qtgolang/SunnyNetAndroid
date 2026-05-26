package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单条抓包规则，字段对齐 SunnyNetV5；并非所有类型都需要 URL。
 */
public final class CaptureRule {

    public enum Type {
        REPLACE,
        REWRITE,
        INTERCEPT,
        BLOCK,
        HOSTS
    }

    private long id;
    @NonNull
    private Type type = Type.BLOCK;
    private boolean enabled = true;
    @NonNull
    private String note = "";

    @NonNull
    private List<String> scopes = new ArrayList<>();
    @NonNull
    private String matchType = CaptureRuleConstants.MATCH_STRING;
    @NonNull
    private String matchData = "";
    @NonNull
    private List<String> matchIns = new ArrayList<>();

    @NonNull
    private String oldType = CaptureRuleConstants.MATCH_STRING;
    @NonNull
    private String oldData = "";
    @NonNull
    private String newType = CaptureRuleConstants.MATCH_STRING;
    @NonNull
    private String newData = "";

    @NonNull
    private String matchURL = "";
    @NonNull
    private String matchMethod = CaptureRuleConstants.REWRITE_METHOD_ALL;
    @NonNull
    private String statusCode = "";
    @NonNull
    private String headers = "";
    @NonNull
    private String responseType = CaptureRuleConstants.RESPONSE_TYPE_TEXT;
    @NonNull
    private String responseData = "";

    @NonNull
    private String oldHost = "";
    @NonNull
    private String newHost = "";

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public void setType(@NonNull Type type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    public String getNote() {
        return note;
    }

    public void setNote(@NonNull String note) {
        this.note = note;
    }

    @NonNull
    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(@NonNull List<String> scopes) {
        this.scopes = new ArrayList<>(scopes);
    }

    @NonNull
    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(@NonNull String matchType) {
        this.matchType = matchType;
    }

    @NonNull
    public String getMatchData() {
        return matchData;
    }

    public void setMatchData(@NonNull String matchData) {
        this.matchData = matchData;
    }

    @NonNull
    public List<String> getMatchIns() {
        return matchIns;
    }

    public void setMatchIns(@NonNull List<String> matchIns) {
        this.matchIns = new ArrayList<>(matchIns);
    }

    @NonNull
    public String getOldType() {
        return oldType;
    }

    public void setOldType(@NonNull String oldType) {
        this.oldType = oldType;
    }

    @NonNull
    public String getOldData() {
        return oldData;
    }

    public void setOldData(@NonNull String oldData) {
        this.oldData = oldData;
    }

    @NonNull
    public String getNewType() {
        return newType;
    }

    public void setNewType(@NonNull String newType) {
        this.newType = newType;
    }

    @NonNull
    public String getNewData() {
        return newData;
    }

    public void setNewData(@NonNull String newData) {
        this.newData = newData;
    }

    @NonNull
    public String getMatchURL() {
        return matchURL;
    }

    public void setMatchURL(@NonNull String matchURL) {
        this.matchURL = matchURL;
    }

    @NonNull
    public String getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(@NonNull String matchMethod) {
        this.matchMethod = matchMethod;
    }

    @NonNull
    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(@NonNull String statusCode) {
        this.statusCode = statusCode;
    }

    @NonNull
    public String getHeaders() {
        return headers;
    }

    public void setHeaders(@NonNull String headers) {
        this.headers = headers;
    }

    @NonNull
    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(@NonNull String responseType) {
        this.responseType = responseType;
    }

    @NonNull
    public String getResponseData() {
        return responseData;
    }

    public void setResponseData(@NonNull String responseData) {
        this.responseData = responseData;
    }

    @NonNull
    public String getOldHost() {
        return oldHost;
    }

    public void setOldHost(@NonNull String oldHost) {
        this.oldHost = oldHost;
    }

    @NonNull
    public String getNewHost() {
        return newHost;
    }

    public void setNewHost(@NonNull String newHost) {
        this.newHost = newHost;
    }

    public boolean hasScope(@NonNull String scope) {
        for (String s : scopes) {
            if (scope.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type.name());
        o.put("enabled", enabled);
        o.put("note", note);
        o.put("scopes", stringListToJson(scopes));
        o.put("matchType", matchType);
        o.put("matchData", matchData);
        o.put("matchIns", stringListToJson(matchIns));
        o.put("oldType", oldType);
        o.put("oldData", oldData);
        o.put("newType", newType);
        o.put("newData", newData);
        o.put("matchURL", matchURL);
        o.put("matchMethod", matchMethod);
        o.put("statusCode", statusCode);
        o.put("headers", headers);
        o.put("responseType", responseType);
        o.put("responseData", responseData);
        o.put("oldHost", oldHost);
        o.put("newHost", newHost);
        return o;
    }

    @NonNull
    static CaptureRule fromJson(@NonNull JSONObject o) throws JSONException {
        CaptureRule rule = new CaptureRule();
        rule.id = o.getLong("id");
        rule.type = Type.valueOf(o.getString("type"));
        rule.enabled = o.optBoolean("enabled", true);
        rule.note = o.optString("note", "");
        rule.scopes = jsonToStringList(o.optJSONArray("scopes"));
        rule.matchType = o.optString("matchType", CaptureRuleConstants.MATCH_STRING);
        rule.matchData = o.optString("matchData", "");
        rule.matchIns = jsonToStringList(o.optJSONArray("matchIns"));
        rule.oldType = o.optString("oldType", CaptureRuleConstants.MATCH_STRING);
        rule.oldData = o.optString("oldData", "");
        rule.newType = o.optString("newType", CaptureRuleConstants.MATCH_STRING);
        rule.newData = o.optString("newData", "");
        rule.matchURL = o.optString("matchURL", "");
        rule.matchMethod = o.optString("matchMethod", CaptureRuleConstants.REWRITE_METHOD_ALL);
        rule.statusCode = o.optString("statusCode", "");
        rule.headers = o.optString("headers", "");
        rule.responseType = o.optString("responseType", CaptureRuleConstants.RESPONSE_TYPE_TEXT);
        rule.responseData = o.optString("responseData", "");
        rule.oldHost = o.optString("oldHost", "");
        rule.newHost = o.optString("newHost", "");
        migrateLegacyFields(rule, o);
        return rule;
    }

    /** 兼容旧版 match / value1 / value2 / phase 字段。 */
    private static void migrateLegacyFields(@NonNull CaptureRule rule, @NonNull JSONObject o) {
        String legacyMatch = o.optString("match", "");
        String legacyV1 = o.optString("value1", "");
        String legacyV2 = o.optString("value2", "");
        if (rule.matchData.isEmpty() && !legacyMatch.isEmpty()) {
            rule.matchData = legacyMatch;
        }
        switch (rule.type) {
            case HOSTS:
                if (rule.oldHost.isEmpty() && !legacyV1.isEmpty()) {
                    rule.oldHost = legacyV1;
                }
                if (rule.newHost.isEmpty() && !legacyV2.isEmpty()) {
                    rule.newHost = legacyV2;
                }
                break;
            case REPLACE:
                if (rule.oldData.isEmpty() && !legacyV1.isEmpty()) {
                    rule.oldData = legacyV1;
                }
                if (rule.newData.isEmpty() && !legacyV2.isEmpty()) {
                    rule.newData = legacyV2;
                }
                if (rule.scopes.isEmpty()) {
                    String phase = o.optString("phase", "REQUEST");
                    if ("RESPONSE".equals(phase)) {
                        rule.scopes = Collections.singletonList(CaptureRuleConstants.SCOPE_HTTP_RESPONSE);
                    } else if ("BOTH".equals(phase)) {
                        rule.scopes = new ArrayList<>();
                        rule.scopes.add(CaptureRuleConstants.SCOPE_HTTP_REQUEST);
                        rule.scopes.add(CaptureRuleConstants.SCOPE_HTTP_RESPONSE);
                    } else {
                        rule.scopes = Collections.singletonList(CaptureRuleConstants.SCOPE_HTTP_REQUEST);
                    }
                }
                break;
            case REWRITE:
                if (rule.matchURL.isEmpty() && !legacyV1.isEmpty()) {
                    rule.matchURL = legacyV1;
                }
                if (rule.responseData.isEmpty() && !legacyV2.isEmpty()) {
                    rule.responseData = legacyV2;
                }
                break;
            case BLOCK:
            case INTERCEPT:
            default:
                break;
        }
    }

    @NonNull
    public static Type parseType(@Nullable String name, @NonNull Type fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Type.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @NonNull
    private static JSONArray stringListToJson(@NonNull List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) {
            arr.put(s);
        }
        return arr;
    }

    @NonNull
    private static List<String> jsonToStringList(@Nullable JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.optString(i, ""));
        }
        return out;
    }
}
