package com.sunnynet.tools.capture;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 规则持久化：SharedPreferences JSON 存储，供 UI 编辑与抓包回调读取。
 */
public final class CaptureRuleStore {

    private static final String PREFS = "capture_rules";
    private static final String KEY_RULES_JSON = "rules_json";
    private static final String KEY_RULES_ENABLED = "rules_enabled";
    private static final String KEY_NEXT_ID = "next_id";

    private static volatile CaptureRuleStore instance;

    private final SharedPreferences prefs;
    private final List<CaptureRule> rules = new ArrayList<>();
    private boolean rulesEnabled = true;

    private CaptureRuleStore(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    public static synchronized CaptureRuleStore get(@NonNull Context context) {
        if (instance == null) {
            instance = new CaptureRuleStore(context);
        }
        return instance;
    }

    public static synchronized CaptureRuleStore get() {
        if (instance == null) {
            throw new IllegalStateException("CaptureRuleStore not initialized");
        }
        return instance;
    }

    public static void init(@NonNull Context context) {
        get(context);
    }

    public boolean isRulesEnabled() {
        return rulesEnabled;
    }

    public void setRulesEnabled(boolean enabled) {
        rulesEnabled = enabled;
        prefs.edit().putBoolean(KEY_RULES_ENABLED, enabled).apply();
    }

    @NonNull
    public List<CaptureRule> getAllRules() {
        synchronized (rules) {
            return Collections.unmodifiableList(new ArrayList<>(rules));
        }
    }

    @NonNull
    public List<CaptureRule> getEnabledRules() {
        synchronized (rules) {
            List<CaptureRule> out = new ArrayList<>();
            for (CaptureRule rule : rules) {
                if (rule.isEnabled()) {
                    out.add(rule);
                }
            }
            return out;
        }
    }

    @NonNull
    public List<CaptureRule> getRulesByType(@NonNull CaptureRule.Type type) {
        synchronized (rules) {
            List<CaptureRule> out = new ArrayList<>();
            for (CaptureRule rule : rules) {
                if (rule.getType() == type) {
                    out.add(rule);
                }
            }
            out.sort(Comparator.comparingLong(CaptureRule::getId));
            return out;
        }
    }

    public int countByType(@NonNull CaptureRule.Type type) {
        synchronized (rules) {
            int count = 0;
            for (CaptureRule rule : rules) {
                if (rule.getType() == type) {
                    count++;
                }
            }
            return count;
        }
    }

    public int countEnabledByType(@NonNull CaptureRule.Type type) {
        synchronized (rules) {
            int count = 0;
            for (CaptureRule rule : rules) {
                if (rule.getType() == type && rule.isEnabled()) {
                    count++;
                }
            }
            return count;
        }
    }

    public void upsert(@NonNull CaptureRule rule) {
        synchronized (rules) {
            if (rule.getId() <= 0) {
                rule.setId(nextId());
            }
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getId() == rule.getId()) {
                    rules.set(i, cloneRule(rule));
                    persistLocked();
                    return;
                }
            }
            rules.add(cloneRule(rule));
            persistLocked();
        }
    }

    public void delete(long id) {
        synchronized (rules) {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getId() == id) {
                    rules.remove(i);
                    persistLocked();
                    return;
                }
            }
        }
    }

    public void setRuleEnabled(long id, boolean enabled) {
        synchronized (rules) {
            for (CaptureRule rule : rules) {
                if (rule.getId() == id) {
                    rule.setEnabled(enabled);
                    persistLocked();
                    return;
                }
            }
        }
    }

    @Nullable
    public CaptureRule findById(long id) {
        synchronized (rules) {
            for (CaptureRule rule : rules) {
                if (rule.getId() == id) {
                    return cloneRule(rule);
                }
            }
        }
        return null;
    }

    private void loadFromPrefs() {
        rulesEnabled = prefs.getBoolean(KEY_RULES_ENABLED, true);
        rules.clear();
        String raw = prefs.getString(KEY_RULES_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(raw != null ? raw : "[]");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                rules.add(CaptureRule.fromJson(o));
            }
        } catch (JSONException ignored) {
            rules.clear();
        }
        rules.sort(Comparator.comparingLong(CaptureRule::getId));
    }

    private void persistLocked() {
        JSONArray arr = new JSONArray();
        for (CaptureRule rule : rules) {
            try {
                arr.put(rule.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit()
                .putString(KEY_RULES_JSON, arr.toString())
                .apply();
    }

    private long nextId() {
        long id = prefs.getLong(KEY_NEXT_ID, 1L);
        prefs.edit().putLong(KEY_NEXT_ID, id + 1L).apply();
        return id;
    }

    @NonNull
    private static CaptureRule cloneRule(@NonNull CaptureRule source) {
        CaptureRule copy = new CaptureRule();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setEnabled(source.isEnabled());
        copy.setNote(source.getNote());
        copy.setScopes(source.getScopes());
        copy.setMatchType(source.getMatchType());
        copy.setMatchData(source.getMatchData());
        copy.setMatchIns(source.getMatchIns());
        copy.setOldType(source.getOldType());
        copy.setOldData(source.getOldData());
        copy.setNewType(source.getNewType());
        copy.setNewData(source.getNewData());
        copy.setMatchURL(source.getMatchURL());
        copy.setMatchMethod(source.getMatchMethod());
        copy.setStatusCode(source.getStatusCode());
        copy.setHeaders(source.getHeaders());
        copy.setResponseType(source.getResponseType());
        copy.setResponseData(source.getResponseData());
        copy.setOldHost(source.getOldHost());
        copy.setNewHost(source.getNewHost());
        return copy;
    }
}
