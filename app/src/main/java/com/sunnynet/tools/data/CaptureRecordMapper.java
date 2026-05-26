package com.sunnynet.tools.data;

import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureStreamEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CaptureRecord} 与 ObjectBox 实体互转。
 */
public final class CaptureRecordMapper {

    private CaptureRecordMapper() {
    }

    public static CaptureRecordEntity toEntity(CaptureRecord record) {
        CaptureRecordEntity entity = new CaptureRecordEntity();
        entity.id = record.getId();
        entity.protocol = record.getProtocol();
        entity.theologyId = record.getTheologyId();
        entity.timestampMs = record.getTimestampMs();
        entity.streamSession = record.isStreamSession();
        entity.title = record.getTitle();
        entity.summary = record.getSummary();
        entity.detail = record.getDetail();
        entity.connectionState = record.getConnectionState();
        entity.packageName = record.getPackageName();
        entity.clientIp = record.getClientIp();
        entity.requestHttpProto = record.getRequestHttpProto();
        entity.responseHttpProto = record.getResponseHttpProto();
        entity.httpStatusCode = record.getHttpStatusCode();
        entity.responseTimestampMs = record.getResponseTimestampMs();
        entity.requestBodyBytes = record.getRequestBodyBytes();
        entity.responseBodyBytes = record.getResponseBodyBytes();
        return entity;
    }

    public static CaptureRecord toRecord(CaptureRecordEntity entity, List<CaptureStreamEntryEntity> streamEntities) {
        List<CaptureStreamEntry> streams = new ArrayList<>();
        if (streamEntities != null) {
            for (CaptureStreamEntryEntity streamEntity : streamEntities) {
                streams.add(new CaptureStreamEntry(
                        streamEntity.id,
                        streamEntity.direction,
                        streamEntity.body,
                        streamEntity.timestampMs
                ));
            }
        }
        return CaptureRecord.fromStore(
                entity.id,
                entity.protocol,
                entity.theologyId,
                entity.timestampMs,
                entity.title,
                entity.summary,
                entity.detail,
                entity.streamSession,
                entity.connectionState,
                entity.packageName,
                entity.clientIp,
                entity.requestHttpProto,
                entity.responseHttpProto,
                entity.httpStatusCode,
                entity.responseTimestampMs,
                entity.requestBodyBytes,
                entity.responseBodyBytes,
                streams
        );
    }
}
