package com.sunnynet.tools.capture;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.data.CaptureRecordPersistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 抓包记录仓库：内存索引 + ObjectBox 实时读写。
 * 主列表快照 {@link #snapshot()} 顺序为 **捕获时间升序**（最旧在前、**最新在列表底部**）；超 {@link #MAX_RECORDS} 时丢弃队头最旧记录。
 */
public final class CaptureRepository {

    private static final int MAX_RECORDS = 500;
    public static final int MAX_BODY_CHARS = 32_768;
    /** 与 {@link #truncate} 末尾一致；连续的 Hex 正文被截断时需先去掉此后缀再解码。 */
    public static final String TRUNCATE_MARKER = "\n…(已截断)";

    private static CaptureRepository instance;

    private final AtomicLong idSeq = new AtomicLong(1);
    private final Map<Long, CaptureRecord> byTheologyId = new HashMap<>();
    /** TCP/UDP 收到首条数据前暂存，不进入列表与持久化 */
    private final Map<Long, CaptureRecord> pendingTcpUdpByTheologyId = new HashMap<>();
    private final List<CaptureRecord> records = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 为 true 时不向主线程发列表刷新（抓包进行中） */
    private volatile boolean suppressLiveUi;

    public interface Listener {
        void onRecordAdded(CaptureRecord record);

        void onRecordUpdated(CaptureRecord record);

        void onRecordsCleared();

        default void onRecordsBatchChanged() {
        }

        default void onRecordsRefreshRequested() {
        }
    }

    public static synchronized CaptureRepository get() {
        if (instance == null) {
            instance = new CaptureRepository();
        }
        return instance;
    }

    /** Application 启动时调用：清空内存索引（ObjectBox 已在 init 时清空） */
    public synchronized void resetSession() {
        records.clear();
        byTheologyId.clear();
        pendingTcpUdpByTheologyId.clear();
        idSeq.set(1);
    }

    public static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_BODY_CHARS) {
            return text;
        }
        return text.substring(0, MAX_BODY_CHARS) + TRUNCATE_MARKER;
    }

    public void setSuppressLiveUi(boolean suppress) {
        suppressLiveUi = suppress;
    }

    public boolean isSuppressLiveUi() {
        return suppressLiveUi;
    }

    public synchronized CaptureRecord add(String protocol, long theologyId, String title, String summary, String detail) {
        return add(protocol, theologyId, title, summary, detail, null);
    }

    public synchronized CaptureRecord add(String protocol, long theologyId, String title, String summary,
                                            String detail, @Nullable String packageName) {
        long id = idSeq.getAndIncrement();
        CaptureRecord record = new CaptureRecord(id, protocol, theologyId, title, summary, truncate(detail));
        applyPackageName(record, packageName);
        putRecord(record);
        if (!suppressLiveUi) {
            notifyAddedOnMain(record);
        }
        return record;
    }

    public synchronized CaptureRecord findByTheologyId(long theologyId) {
        CaptureRecord cached = byTheologyId.get(theologyId);
        if (cached != null) {
            return cached;
        }
        CaptureRecord loaded = CaptureRecordPersistence.loadByTheologyId(theologyId);
        if (loaded != null) {
            putRecordWithoutPersist(loaded);
        }
        return loaded;
    }

    /**
     * 查找已展示或 pending 的 TCP/UDP 流会话（不含 WebSocket 专用 pending）。
     */
    @Nullable
    public synchronized CaptureRecord findTcpUdpSessionIncludingPending(long theologyId) {
        CaptureRecord visible = findByTheologyId(theologyId);
        if (visible != null) {
            return visible;
        }
        return pendingTcpUdpByTheologyId.get(theologyId);
    }

    public synchronized boolean isPendingTcpUdpSession(long theologyId) {
        return pendingTcpUdpByTheologyId.containsKey(theologyId);
    }

    /** TCP/UDP 连接阶段暂存，收到首条数据后再 {@link #promotePendingTcpUdpSession(long)}。 */
    @NonNull
    public synchronized CaptureRecord createPendingTcpUdpSession(String protocol, long theologyId,
                                                                 String title, String detail,
                                                                 @Nullable String packageName) {
        CaptureRecord pending = pendingTcpUdpByTheologyId.get(theologyId);
        if (pending != null) {
            applyPackageName(pending, packageName);
            return pending;
        }
        CaptureRecord visible = findByTheologyId(theologyId);
        if (visible != null) {
            applyPackageName(visible, packageName);
            return visible;
        }
        long id = idSeq.getAndIncrement();
        CaptureRecord record = new CaptureRecord(id, protocol, theologyId, title,
                CaptureRecord.stateLabel(CaptureRecord.STATE_UNKNOWN) + " · 0 条流",
                detail, true);
        record.setConnectionState(CaptureRecord.STATE_UNKNOWN);
        applyPackageName(record, packageName);
        pendingTcpUdpByTheologyId.put(theologyId, record);
        return record;
    }

    /** 首条 TCP/UDP 数据到达：pending 会话写入列表并持久化。 */
    @Nullable
    public synchronized CaptureRecord promotePendingTcpUdpSession(long theologyId) {
        CaptureRecord pending = pendingTcpUdpByTheologyId.remove(theologyId);
        if (pending == null) {
            return findByTheologyId(theologyId);
        }
        putRecord(pending);
        return pending;
    }

    public synchronized void removePendingTcpUdpSession(long theologyId) {
        pendingTcpUdpByTheologyId.remove(theologyId);
    }

    public synchronized void clearPendingTcpUdpSessions() {
        pendingTcpUdpByTheologyId.clear();
    }

    /**
     * WebSocket 连接成功时，按与 HTTP 相同的 theologyId 找到卡片并升级为 WebSocket 会话。
     *
     * @return 升级后的记录；未找到可升级 HTTP 卡片时返回 null
     */
    @Nullable
    public synchronized CaptureRecord upgradeHttpToWebSocket(long theologyId, String wsUrl,
                                                             @Nullable String method,
                                                             @Nullable String packageName) {
        CaptureRecord existing = findByTheologyId(theologyId);
        if (existing != null) {
            if (existing.isStreamSession()
                    || CaptureRecord.TYPE_WEBSOCKET.equals(existing.getProtocol())) {
                applyPackageName(existing, packageName);
                return existing;
            }
            if (CaptureRecord.TYPE_HTTP.equals(existing.getProtocol())) {
                existing.promoteToWebSocketSession(wsUrl, method);
                applyPackageName(existing, packageName);
                persist(existing);
                return existing;
            }
        }
        // theologyId 未命中时，按 URL 兜底（如 HTTP 卡片尚未写入索引）
        CaptureRecord http = findHttpRecordForWebSocketUpgrade(wsUrl);
        if (http == null) {
            return null;
        }
        http.promoteToWebSocketSession(wsUrl, method);
        applyPackageName(http, packageName);
        persist(http);
        return http;
    }

    @Nullable
    private CaptureRecord findHttpRecordForWebSocketUpgrade(@Nullable String wsUrl) {
        CaptureRecord fallback = null;
        for (CaptureRecord record : records) {
            if (!WebSocketUpgradeMatcher.matchesHttpRecord(record, wsUrl)) {
                continue;
            }
            if (WebSocketUpgradeMatcher.isSwitchingProtocolsResponse(record)) {
                return record;
            }
            if (fallback == null) {
                fallback = record;
            }
        }
        return fallback;
    }

    @Nullable
    public synchronized CaptureRecord findById(long id) {
        for (CaptureRecord record : records) {
            if (record.getId() == id) {
                return record;
            }
        }
        CaptureRecord loaded = CaptureRecordPersistence.loadById(id);
        if (loaded != null) {
            putRecordWithoutPersist(loaded);
            return loaded;
        }
        return null;
    }

    public synchronized CaptureRecord createStreamSession(String protocol, long theologyId,
                                                          String title, String detail) {
        return createStreamSession(protocol, theologyId, title, detail, null);
    }

    public synchronized CaptureRecord createStreamSession(String protocol, long theologyId,
                                                          String title, String detail,
                                                          @Nullable String packageName) {
        CaptureRecord existing = findByTheologyId(theologyId);
        if (existing != null) {
            applyPackageName(existing, packageName);
            return existing;
        }
        long id = idSeq.getAndIncrement();
        CaptureRecord record = new CaptureRecord(id, protocol, theologyId, title,
                CaptureRecord.stateLabel(CaptureRecord.STATE_UNKNOWN) + " · 0 条流",
                detail, true);
        record.setConnectionState(CaptureRecord.STATE_UNKNOWN);
        applyPackageName(record, packageName);
        putRecord(record);
        return record;
    }

    private static void applyPackageName(CaptureRecord record, @Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        record.setPackageName(packageName);
    }

    public synchronized void notifyStreamSessionChanged(CaptureRecord record, boolean added) {
        persist(record);
        if (suppressLiveUi) {
            return;
        }
        if (added) {
            notifyAddedOnMain(record);
        } else {
            notifyUpdatedOnMain(record);
        }
    }

    public synchronized void update(CaptureRecord record) {
        persist(record);
        if (!suppressLiveUi) {
            notifyUpdatedOnMain(record);
        }
    }

    /** 导入会话包时写入（含流列表） */
    public synchronized CaptureRecord importRecord(CaptureRecord record) {
        if (record.getId() >= idSeq.get()) {
            idSeq.set(record.getId() + 1);
        }
        putRecord(record);
        if (!suppressLiveUi) {
            notifyAddedOnMain(record);
        }
        return record;
    }

    public synchronized List<CaptureRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public synchronized int recordCount() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
        byTheologyId.clear();
        pendingTcpUdpByTheologyId.clear();
        CaptureRecordPersistence.clearAll();
        mainHandler.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onRecordsCleared();
            }
        });
    }

    public void requestUiRefresh() {
        mainHandler.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onRecordsRefreshRequested();
            }
        });
    }

    public synchronized void markOpenStreamSessionsDisconnected() {
        clearPendingTcpUdpSessions();
        for (CaptureRecord record : records) {
            if (!record.isStreamSession()) {
                continue;
            }
            String state = record.getConnectionState();
            if (CaptureRecord.STATE_CONNECTED.equals(state)
                    || CaptureRecord.STATE_CONNECTING.equals(state)) {
                StreamSessionProcessor.disconnectSession(record);
                persist(record);
            }
        }
    }

    private void putRecord(CaptureRecord record) {
        records.removeIf(r -> r.getId() == record.getId());
        records.add(record);
        if (record.getTheologyId() != 0) {
            byTheologyId.put(record.getTheologyId(), record);
        }
        trimIfNeeded();
        persist(record);
    }

    private void putRecordWithoutPersist(CaptureRecord record) {
        records.removeIf(r -> r.getId() == record.getId());
        records.add(record);
        if (record.getTheologyId() != 0) {
            byTheologyId.put(record.getTheologyId(), record);
        }
    }

    private void persist(CaptureRecord record) {
        CaptureRecordPersistence.save(record);
    }

    private void trimIfNeeded() {
        while (records.size() > MAX_RECORDS) {
            // 列表为时间升序：队头为最旧，超限先丢最旧
            CaptureRecord removed = records.remove(0);
            CaptureRecordPersistence.delete(removed.getId());
            if (removed.getTheologyId() != 0) {
                CaptureRecord current = byTheologyId.get(removed.getTheologyId());
                if (current == removed) {
                    byTheologyId.remove(removed.getTheologyId());
                }
            }
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyAddedOnMain(CaptureRecord record) {
        mainHandler.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onRecordAdded(record);
            }
        });
    }

    private void notifyUpdatedOnMain(CaptureRecord record) {
        mainHandler.post(() -> {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onRecordUpdated(record);
            }
        });
    }
}
