package com.sunnynet.tools.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

/**
 * ObjectBox 持久化：抓包记录主表。
 */
@Entity
public class CaptureRecordEntity {

    @Id(assignable = true)
    public long id;

    public String protocol;
    public long theologyId;
    public long timestampMs;
    public boolean streamSession;
    public String title;
    public String summary;
    public String detail;
    public String connectionState;
    /** 产生流量的应用包名 */
    public String packageName;
    /** HTTP 客户端 IP（SDK ClientIP） */
    public String clientIp;
    /** 请求 HTTP 版本，如 HTTP/1.1 */
    public String requestHttpProto;
    /** 响应 HTTP 版本，如 HTTP/2 */
    public String responseHttpProto;
    /** HTTP 响应状态码 */
    public int httpStatusCode;
    /** 响应到达时间戳 */
    public long responseTimestampMs;
    /** 请求 Body 字节数 */
    public long requestBodyBytes;
    /** 响应 Body 字节数 */
    public long responseBodyBytes;
}
