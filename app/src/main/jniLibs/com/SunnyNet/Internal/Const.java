package com.SunnyNet.Internal;

import java.nio.charset.Charset;

public class Const {
    /**
     * WebSocket 消息类型常量 文本消息。
     */
    public static final int WsMessage_Text = 1;
    /**
     * WebSocket 消息类型常量 二进制消息。
     */
    public static final int WsMessage_Binary = 2;
    /**
     * WebSocket 消息类型常量 关闭消息。
     */
    public static final int WsMessage_Close = 8;
    /**
     * WebSocket 消息类型常量 Ping 消息。
     */
    public static final int WsMessage_Ping = 9;
    /**
     * WebSocket 消息类型常量 Pong 消息。
     */
    public static final int WsMessage_Pong = 10;
    /**
     * WebSocket 消息类型常量 无效消息。
     */
    public static final int WsMessage_Invalid = -1;

    /**
     * WebSocket 事件类型常量 连接成功
     */
    public static final int EventType_Websocket_OK = 1;
    /**
     * WebSocket 事件类型常量 客户端发送数据。
     */
    public static final int EventType_Websocket_Send = 2;
    /**
     * WebSocket 事件类型常量 客户端收到数据。
     */
    public static final int EventType_Websocket_Receive = 3;
    /**
     * WebSocket 事件类型常量 断开连接。
     */
    public static final int EventType_Websocket_Close = 4;

    /**
     * HTTP 事件类型常量 发起请求。
     */
    public static final int EventType_HTTP_Request = 1;
    /**
     * HTTP 事件类型常量 请求完成。
     */
    public static final int EventType_HTTP_Response = 2;
    /**
     * HTTP 事件类型常量 请求错误。
     */
    public static final int EventType_HTTP_Error = 3;

    /**
     * TCP 事件类型常量 即将开始连接。
     */
    public static final int EventType_TCP_About = 4;
    /**
     * TCP 事件类型常量  连接成功。
     */
    public static final int EventType_TCP_OK = 0;
    /**
     * TCP 事件类型常量 客户端发送数据。
     */
    public static final int EventType_TCP_Send = 1;
    /**
     * TCP 事件类型常量 客户端收到数据。
     */
    public static final int EventType_TCP_Receive = 2;
    /**
     * TCP 事件类型常量 连接关闭或连接失败。
     */
    public static final int EventType_TCP_Close = 3;

    /**
     * UDP 事件类型常量 关闭。
     */
    public static final int EventType_UDP_Closed = 1;
    /**
     * UDP 事件类型常量 客户端发送数据。
     */
    public static final int EventType_UDP_Send = 2;
    /**
     * UDP 事件类型常量 客户端收到数据。
     */
    public static final int EventType_UDP_Receive = 3;

    /**
     * 证书使用规则常量 仅发送使用。
     */
    public static final int CertRequestRules_Send = 1;
    /**
     * 证书使用规则常量 发送及解析使用。
     */
    public static final int CertRequestRules_Send_Receive = 2;
    /**
     * 证书使用规则常量 仅解析使用。
     */
    public static final int CertRequestRules_Receive = 3;

    /**
     * 不请求客户端证书。
     */
    public static final int SSL_ClientAuth_NoClientCert = 0;
    /**
     * 请求客户端证书。
     */
    public static final int SSL_ClientAuth_RequestClientCert = 1;
    /**
     * 至少发送一个证书。
     */
    public static final int SSL_ClientAuth_RequireAnyClientCert = 2;
    /**
     * 验证客户端证书（如果提供）。
     */
    public static final int SSL_ClientAuth_VerifyClientCertIfGiven = 3;
    /**
     * 要求并验证客户端证书。
     */
    public static final int SSL_ClientAuth_RequireAndVerifyClientCert = 4;

    /**
     * 消息发送目标常量 发送到客户端
     */
    public static final int SendToClient = 1;
    /**
     * 消息发送目标常量 发送到服务器。
     */
    public static final int SendToServer = 2;


    /**
     * 证书使用规则 仅请求时使用
     */
    public static final int CertRules_Request = 1;
    /**
     * 消息发送目标常量 解析和请求时都使用
     */
    public static final int CertRules_Request_Response = 2;
    /**
     * 消息发送目标常量 仅解析时使用
     */
    public static final int CertRules_Response = 3;
    /**
     * 强制走TCP规则，规则之内走TCP
     */
    public static final boolean MustTcpRegexp_Within = false;
    /**
     * 强制走TCP规则，规则之外走TCP
     */
    public static final boolean MustTcpRegexp_Outside = true;


    /**
     * Proxifier 驱动模式 (仅Windows有效)
     */
    public static final int OpenDrive_Proxifier = 0;
    /**
     * NFAPI 驱动模式 (仅Windows有效)
     */
    public static final int OpenDrive_NFAPI = 1;
    /**
     * Tun 驱动模式 (Windows/Android有效)
     */
    public static final int OpenDrive_Tun = 2;

    public static final String HTTP2_Fingerprint_Config_Firefox = "{\"ConnectionFlow\":12517377,\"HeaderPriority\":{\"StreamDep\":13,\"Exclusive\":false,\"Weight\":41},\"Priorities\":[{\"PriorityParam\":{\"StreamDep\":0,\"Exclusive\":false,\"Weight\":200},\"StreamID\":3},{\"PriorityParam\":{\"StreamDep\":0,\"Exclusive\":false,\"Weight\":100},\"StreamID\":5},{\"PriorityParam\":{\"StreamDep\":0,\"Exclusive\":false,\"Weight\":0},\"StreamID\":7},{\"PriorityParam\":{\"StreamDep\":7,\"Exclusive\":false,\"Weight\":0},\"StreamID\":9},{\"PriorityParam\":{\"StreamDep\":3,\"Exclusive\":false,\"Weight\":0},\"StreamID\":11},{\"PriorityParam\":{\"StreamDep\":0,\"Exclusive\":false,\"Weight\":240},\"StreamID\":13}],\"PseudoHeaderOrder\":[\":method\",\":path\",\":authority\",\":scheme\"],\"Settings\":{\"1\":65536,\"4\":131072,\"5\":16384},\"SettingsOrder\":[1,4,5]}";
    public static final String HTTP2_Fingerprint_Config_Opera = "{\"ConnectionFlow\":15663105,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":authority\",\":scheme\",\":path\"],\"Settings\":{\"1\":65536,\"3\":1000,\"4\":6291456,\"6\":262144},\"SettingsOrder\":[1,3,4,6]}";
    public static final String HTTP2_Fingerprint_Config_Safari_IOS_17_0 = "{\"ConnectionFlow\":10485760,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":scheme\",\":path\",\":authority\"],\"Settings\":{\"2\":0,\"3\":100,\"4\":2097152},\"SettingsOrder\":[2,4,3]}";
    public static final String HTTP2_Fingerprint_Config_Safari_IOS_16_0 = "{\"ConnectionFlow\":10485760,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":scheme\",\":path\",\":authority\"],\"Settings\":{\"3\":100,\"4\":2097152},\"SettingsOrder\":[4,3]}";
    public static final String HTTP2_Fingerprint_Config_Safari = "{\"ConnectionFlow\":10485760,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":scheme\",\":path\",\":authority\"],\"Settings\":{\"3\":100,\"4\":4194304},\"SettingsOrder\":[4,3]}";
    public static final String HTTP2_Fingerprint_Config_Chrome_117_120_124 = "{\"ConnectionFlow\":15663105,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":authority\",\":scheme\",\":path\"],\"Settings\":{\"1\":65536,\"2\":0,\"4\":6291456,\"6\":262144},\"SettingsOrder\":[1,2,4,6]}";
    public static final String HTTP2_Fingerprint_Config_Chrome_106_116 = "{\"ConnectionFlow\":15663105,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":authority\",\":scheme\",\":path\"],\"Settings\":{\"1\":65536,\"2\":0,\"3\":1000,\"4\":6291456,\"6\":262144},\"SettingsOrder\":[1,2,3,4,6]}";
    public static final String HTTP2_Fingerprint_Config_Chrome_103_105 = "{\"ConnectionFlow\":15663105,\"HeaderPriority\":null,\"Priorities\":null,\"PseudoHeaderOrder\":[\":method\",\":authority\",\":scheme\",\":path\"],\"Settings\":{\"1\":65536,\"3\":1000,\"4\":6291456,\"6\":262144},\"SettingsOrder\":[1,3,4,6]}";

    public static final Charset GBKCharset = Charset.forName("GBK");
}
