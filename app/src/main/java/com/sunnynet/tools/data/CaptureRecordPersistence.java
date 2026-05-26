package com.sunnynet.tools.data;

import android.content.Context;

import com.sunnynet.tools.data.MyObjectBox;
import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.CaptureStreamEntry;

import java.util.ArrayList;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;

/**
 * ObjectBox 读写：抓包记录实时落盘。
 */
public final class CaptureRecordPersistence {

    private static BoxStore boxStore;

    private CaptureRecordPersistence() {
    }

    public static void init(Context context) {
        if (boxStore == null) {
            boxStore = MyObjectBox.builder()
                    .androidContext(context.getApplicationContext())
                    .build();
        }
        clearAll();
    }

    public static void clearAll() {
        if (boxStore == null) {
            return;
        }
        boxStore.runInTx(() -> {
            boxStore.boxFor(CaptureStreamEntryEntity.class).removeAll();
            boxStore.boxFor(CaptureRecordEntity.class).removeAll();
        });
    }

    public static void save(CaptureRecord record) {
        if (boxStore == null || record == null) {
            return;
        }
        boxStore.runInTx(() -> {
            Box<CaptureRecordEntity> recordBox = boxStore.boxFor(CaptureRecordEntity.class);
            Box<CaptureStreamEntryEntity> streamBox = boxStore.boxFor(CaptureStreamEntryEntity.class);
            recordBox.put(CaptureRecordMapper.toEntity(record));

            List<CaptureStreamEntryEntity> oldStreams = streamBox.query()
                    .equal(CaptureStreamEntryEntity_.recordRecordId, record.getId())
                    .build()
                    .find();
            streamBox.remove(oldStreams);

            List<CaptureStreamEntry> streams = record.getStreamEntries();
            for (int i = 0; i < streams.size(); i++) {
                CaptureStreamEntry stream = streams.get(i);
                CaptureStreamEntryEntity entity = new CaptureStreamEntryEntity();
                entity.id = stream.getId();
                entity.recordRecordId = record.getId();
                entity.timestampMs = stream.getTimestampMs();
                entity.direction = stream.getDirection();
                entity.body = stream.getBody();
                entity.sortIndex = i;
                streamBox.put(entity);
            }
        });
    }

    public static void delete(long recordId) {
        if (boxStore == null) {
            return;
        }
        boxStore.runInTx(() -> {
            Box<CaptureStreamEntryEntity> streamBox = boxStore.boxFor(CaptureStreamEntryEntity.class);
            List<CaptureStreamEntryEntity> streams = streamBox.query()
                    .equal(CaptureStreamEntryEntity_.recordRecordId, recordId)
                    .build()
                    .find();
            streamBox.remove(streams);
            boxStore.boxFor(CaptureRecordEntity.class).remove(recordId);
        });
    }

    public static CaptureRecord loadById(long id) {
        if (boxStore == null) {
            return null;
        }
        CaptureRecordEntity entity = boxStore.boxFor(CaptureRecordEntity.class).get(id);
        if (entity == null) {
            return null;
        }
        return CaptureRecordMapper.toRecord(entity, loadStreamsForRecord(id));
    }

    public static CaptureRecord loadByTheologyId(long theologyId) {
        if (boxStore == null || theologyId == 0) {
            return null;
        }
        CaptureRecordEntity entity = boxStore.boxFor(CaptureRecordEntity.class).query()
                .equal(CaptureRecordEntity_.theologyId, theologyId)
                .build()
                .findFirst();
        if (entity == null) {
            return null;
        }
        return CaptureRecordMapper.toRecord(entity, loadStreamsForRecord(entity.id));
    }

    public static List<CaptureRecord> loadAllOrdered(int limit) {
        if (boxStore == null) {
            return new ArrayList<>();
        }
        List<CaptureRecordEntity> entities = boxStore.boxFor(CaptureRecordEntity.class).query()
                .orderDesc(CaptureRecordEntity_.timestampMs)
                .build()
                .find(0, limit);
        List<CaptureRecord> result = new ArrayList<>(entities.size());
        for (CaptureRecordEntity entity : entities) {
            result.add(CaptureRecordMapper.toRecord(entity, loadStreamsForRecord(entity.id)));
        }
        return result;
    }

    private static List<CaptureStreamEntryEntity> loadStreamsForRecord(long recordId) {
        return boxStore.boxFor(CaptureStreamEntryEntity.class).query()
                .equal(CaptureStreamEntryEntity_.recordRecordId, recordId)
                .order(CaptureStreamEntryEntity_.sortIndex)
                .build()
                .find();
    }
}
