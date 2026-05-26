package com.sunnynet.tools.session;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.capture.CaptureStreamEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话包导入/导出（ObjectBox JSON 行格式）。.syn3 / .sy4 解析待移植。
 */
public final class SessionBundleManager {

    public static final class Result {
        public final boolean success;
        public final String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private SessionBundleManager() {
    }

    public static Result importFromUri(Context context, Uri uri) {
        String name = queryDisplayName(context, uri);
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".syn3") || lower.endsWith(".sy4") || lower.endsWith(".sy5")) {
                return new Result(false,
                        "检测到 " + name + "：.syn3/.sy4 解析正在移植，暂请使用 .sunny.jsonl 导出文件。");
            }
        }
        return importJsonLines(context, uri);
    }

    public static Result exportToUri(Context context, Uri uri) {
        List<CaptureRecord> records = CaptureRepository.get().snapshot();
        if (records.isEmpty()) {
            return new Result(false, "当前无记录可导出");
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                context.getContentResolver().openOutputStream(uri), StandardCharsets.UTF_8)) {
            for (CaptureRecord record : records) {
                writer.write(toJsonLine(record));
                writer.write('\n');
            }
            return new Result(true, "已导出 " + records.size() + " 条（.sunny.jsonl / ObjectBox 格式）");
        } catch (Exception e) {
            return new Result(false, "导出失败: " + e.getMessage());
        }
    }

    private static String toJsonLine(CaptureRecord record) throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", record.getId());
        root.put("protocol", record.getProtocol());
        root.put("theologyId", record.getTheologyId());
        root.put("timestampMs", record.getTimestampMs());
        root.put("streamSession", record.isStreamSession());
        root.put("title", record.getTitle());
        root.put("summary", record.getSummary());
        root.put("detail", record.getDetail());
        root.put("connectionState", record.getConnectionState());
        root.put("packageName", record.getPackageName());
        root.put("clientIp", record.getClientIp());
        root.put("requestHttpProto", record.getRequestHttpProto());
        root.put("responseHttpProto", record.getResponseHttpProto());
        root.put("httpStatusCode", record.getHttpStatusCode());
        root.put("responseTimestampMs", record.getResponseTimestampMs());
        root.put("requestBodyBytes", record.getRequestBodyBytes());
        root.put("responseBodyBytes", record.getResponseBodyBytes());
        JSONArray streams = new JSONArray();
        for (CaptureStreamEntry entry : record.getStreamEntries()) {
            JSONObject stream = new JSONObject();
            stream.put("id", entry.getId());
            stream.put("timestampMs", entry.getTimestampMs());
            stream.put("direction", entry.getDirection());
            stream.put("body", entry.getBody());
            streams.put(stream);
        }
        root.put("streams", streams);
        return root.toString();
    }

    private static Result importJsonLines(Context context, Uri uri) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                CaptureRecord record = parseRecordLine(line);
                if (record != null) {
                    CaptureRepository.get().importRecord(record);
                    count++;
                }
            }
            if (count == 0) {
                return new Result(false, "未识别文件内容；请使用 .sunny.jsonl 或等待 .syn3/.sy4 支持");
            }
            return new Result(true, "已导入 " + count + " 条记录");
        } catch (Exception e) {
            return new Result(false, "导入失败: " + e.getMessage());
        }
    }

    @Nullable
    private static CaptureRecord parseRecordLine(String line) throws Exception {
        if (line.startsWith("{")) {
            return parseJsonObject(new JSONObject(line));
        }
        return parseLegacyJsonLine(line);
    }

    private static CaptureRecord parseJsonObject(JSONObject root) throws Exception {
        long id = root.optLong("id", 0);
        if (id == 0) {
            id = System.currentTimeMillis();
        }
        String protocol = root.optString("protocol", CaptureRecord.TYPE_HTTP);
        long theologyId = root.optLong("theologyId", 0);
        long timestampMs = root.optLong("timestampMs", System.currentTimeMillis());
        boolean streamSession = root.optBoolean("streamSession", CaptureRecord.isStreamProtocol(protocol));
        String title = root.optString("title", "");
        String summary = root.optString("summary", "");
        String detail = root.optString("detail", "");
        String connectionState = root.optString("connectionState", CaptureRecord.STATE_UNKNOWN);
        String packageName = root.optString("packageName", "");
        String clientIp = root.optString("clientIp", "");
        String requestHttpProto = root.optString("requestHttpProto", "");
        String responseHttpProto = root.optString("responseHttpProto", "");
        int httpStatusCode = root.optInt("httpStatusCode", 0);
        long responseTimestampMs = root.optLong("responseTimestampMs", 0);
        long requestBodyBytes = root.optLong("requestBodyBytes", -1);
        long responseBodyBytes = root.optLong("responseBodyBytes", -1);
        List<CaptureStreamEntry> streams = new ArrayList<>();
        JSONArray streamArray = root.optJSONArray("streams");
        if (streamArray != null) {
            for (int i = 0; i < streamArray.length(); i++) {
                JSONObject stream = streamArray.getJSONObject(i);
                streams.add(new CaptureStreamEntry(
                        stream.optLong("id", i + 1),
                        stream.optString("direction", ""),
                        stream.optString("body", ""),
                        stream.optLong("timestampMs", timestampMs)
                ));
            }
        }
        return CaptureRecord.fromStore(id, protocol, theologyId, timestampMs, title, summary, detail,
                streamSession, connectionState, packageName, clientIp, requestHttpProto, responseHttpProto,
                httpStatusCode, responseTimestampMs, requestBodyBytes, responseBodyBytes, streams);
    }

    /** 兼容旧版简易 JSON 行 */
    private static CaptureRecord parseLegacyJsonLine(String json) {
        String protocol = extractJsonField(json, "protocol");
        String title = extractJsonField(json, "title");
        String summary = extractJsonField(json, "summary");
        String detail = extractJsonField(json, "detail");
        if (protocol == null || protocol.isEmpty()) {
            protocol = CaptureRecord.TYPE_HTTP;
        }
        long id = System.nanoTime();
        return CaptureRecord.fromStore(id, protocol, 0, System.currentTimeMillis(),
                title, summary, detail, false, CaptureRecord.STATE_UNKNOWN,
                "", "", "", "", 0, 0, -1, -1, null);
    }

    private static String extractJsonField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                } else if (next == '"') {
                    sb.append('"');
                } else {
                    sb.append(next);
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String queryDisplayName(Context context, Uri uri) {
        try (android.database.Cursor c = context.getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return c.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
