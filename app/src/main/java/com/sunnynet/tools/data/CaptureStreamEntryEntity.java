package com.sunnynet.tools.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

/**
 * ObjectBox 持久化：会话内单条流。
 */
@Entity
public class CaptureStreamEntryEntity {

    @Id(assignable = true)
    public long id;

    /** 所属 {@link CaptureRecordEntity#id} */
    public long recordRecordId;
    public long timestampMs;
    public String direction;
    public String body;
    /** 越小越新（列表顶部） */
    public int sortIndex;
}
