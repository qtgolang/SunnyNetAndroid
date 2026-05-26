package com.SunnyNet.Internal;

import com.SunnyNet.Compress;
import com.SunnyNet.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HTTPEvent extends BaseClass {
    private final long MessageId;
    private final long EventType;
    private final String Method;
    private final String URL;
    private final String PackageName;
    private final String Error;
    private Request Request;
    private Response Response;
    private boolean Debug;

    /**
     * 构造一个 HTTPEvent 实例。
     *
     * @param SunnyContext SunnyNet 的上下文句柄。
     * @param TheologyID   请求的唯一标识。
     * @param MessageId    消息 ID。
     * @param EventType    消息类型。请使用 Const.EventType_HTTP_。
     * @param Method       HTTP 请求的方法（如 GET、POST 等）。
     * @param URL          HTTP 请求的 URL。
     * @param Error        HTTP 请求错误信息。 如果 消息类型 为 发起请求 或 完成请求 时,
     *                     此处错误信息为“Debug”表示由脚本代码处理通知此请求需要下断。
     * @param PackageName  请求来源包名（仅安卓下有效）,
     * @param PID          发起请求的进程 ID。返回0表示,远程设备通过代理连接
     *                     <font color="#bb7060">(Android系统无法得到PID)</font>
     */
    public HTTPEvent(long SunnyContext, long TheologyID, long MessageId, long EventType, String Method, String URL, String Error, String PackageName, long PID) {
        super(SunnyContext, MessageId, TheologyID, PID, PackageName);
        this.MessageId = MessageId;
        this.PackageName = PackageName;
        this.EventType = EventType;
        this.Method = Method;
        this.URL = URL;
        Debug = Error.equals("Debug");
        if (!Debug) {
            this.Error = Error;
        } else {
            this.Error = "";
        }
    }


    /**
     * 事件类型。
     *
     * @return <p>
     * 请使用以下常量之一来判断：
     * <ul>
     * <li>{@link Const#EventType_HTTP_Request}发起请求</li>
     * <li>{@link Const#EventType_HTTP_Response}请求完成</li>
     * <li>{@link Const#EventType_HTTP_Error}请求错误</li>
     * </ul>
     */
    public int Type() {
        return (int) EventType;
    }

    /**
     * 脚本代码是否通知下断。
     *
     * @return 如果返回 true 表示脚本代码处理通知此请求需要下断,请在回调函数中处理为下断状态
     */
    public boolean Debug() {
        return Debug;
    }

    /**
     * @return 获取请求方法
     */
    public String Method() {
        return Method;
    }

    /**
     * @return 获取请求的URL
     */
    public String URL() {
        return URL;
    }

    /**
     * @return 请求错误的信息 当{@link HTTPEvent#Type()} 为 {@link Const#EventType_HTTP_Error} 时有效
     */
    public String Error() {
        return Error;
    }

    /**
     * @return 数据来源客户端IP
     */
    public String ClientIP() {
        return api.GetRequestClientIp(MessageId);
    }

    /**
     * @return 请求对象 当{@link HTTPEvent#Type()} 为 {@link Const#EventType_HTTP_Request} 时有效
     */
    public Request Request() {
        if (Request == null) {
            Request = new Request(MessageId);
        }
        return Request;
    }

    /**
     * @return 响应对象 当{@link HTTPEvent#Type()} 为
     * <li>{@link Const#EventType_HTTP_Request} 发起请求</li>
     * <li>或</li>
     * <li>{@link Const#EventType_HTTP_Response}请求完成</li>
     * <li>时有效</li>
     */
    public Response Response() {
        if (Response == null) {
            Response = new Response(MessageId);
        }
        return Response;
    }

    /**
     * 请求对象 当{@link HTTPEvent#Type()} 为 {@link Const#EventType_HTTP_Request} 时有效
     */
    public static class Request {
        private final long MessageId;

        public Request(long MessageId) {
            this.MessageId = MessageId;
        }

        /**
         * 将原始请求数据保存到文件。
         * <p>
         * 请使用 "Conn.Request().IsRequestRawBody()" 来检查当前请求是否为原始 body。
         * 如果是，将无法修改提交的 Body，请使用此命令来储存原始提交数据到文件。
         * </p>
         *
         * @param saveFile 要保存的文件路径。
         * @return 如果成功保存数据，返回 true；否则返回 false。
         */
        public boolean RawRequestDataToFile(String saveFile) {
            return api.RawRequestDataToFile(MessageId, saveFile);
        }

        /**
         * 仅限在发起请求时使用
         * 你也可以在中间件设置全局的出口IP
         *
         * @param ip 请输入网卡对应的内网IP地址,输入空文本,则让系统自动选择。
         * @return 如果设置成功返回 true，否则返回 false。
         */
        public boolean SetOutRouterIP(String ip) {
            return api.RequestSetOutRouterIP(MessageId, ip);
        }

        /**
         * 检查当前请求是否为原始 body。
         * 如果是，将无法修改提交的 Body，请使用 "Conn.Request().RawRequestDataToFile(filePath)" 命令来储存到文件。
         *
         * @return 如果当前请求为原始 body，返回 true；否则返回 false。
         */
        public boolean IsRequestRawBody() {
            return api.IsRequestRawBody(MessageId);
        }

        /**
         * 获取 POST 提交数据。
         *
         * @return 返回 POST 请求的原始字节数组。
         */
        public byte[] Body() {
            return api.GetRequestBody(MessageId);
        }

        /**
         * 获取 UTF-8编码的 POST 提交数据,
         *
         * @return 返回 UTF-8 编码的 POST 数据字符串。
         */
        public String BodyToUTF8() {
            return new String(Body(), StandardCharsets.UTF_8);
        }

        /**
         * 获取 GBK 编码的 POST 提交数据,并将GBK编码的提交数据转为UTF8编码
         *
         * @return 返回 UTF8 编码的 POST 数据字符串。
         */
        public String BodyToGBK() {
            return new String(Body(), Const.GBKCharset);
        }

        /**
         * 将UTF8编码的字符串 转为GBK编码,并且替换当前请求提交的数据
         *
         * @param data 要设置的新数据，需要传入GBK字符串
         */
        public void BodyToGBK(String data) {
            Body(data.getBytes(Const.GBKCharset));
        }

        /**
         * 修改 POST 提交数据。
         *
         * @param data 要设置的新数据，需要传入UTF8字符串
         */
        public void BodyToUTF8(String data) {
            Body(data.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 修改 POST 提交数据。
         *
         * @param data 要设置的新数据，字节数组形式。
         */
        public void Body(byte[] data) {
            api.SetRequestData(MessageId, data);
        }

        /**
         * 停止当前请求，不再发送。
         * 使用本命令后，这个请求将不会被发送出去。
         */
        public void Stop() {
            api.SetResponseHeader(MessageId, "Connection", "Close");
        }

        /**
         * 设置请求超时。
         *
         * @param OutTime 超时设置，单位为毫秒。
         */
        public void SetOutTime(long OutTime) {
            api.SetRequestOutTime(MessageId, OutTime);
        }

        /**
         * 设置HTTP2指纹,若服务器支持则使用,若服务器不支持，设置了也不会使用。
         * <p>
         * 如果强制请求发送时使用HTTP/1.1 请填入参数 http/1.1
         * <p>
         * 请使用以下常量模板之一，<font color="#d24428">(你可以将模板中的数值随机，以达到随机指纹的效果)</font>：
         * <ul>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Firefox}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Opera}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Chrome_103_105}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Chrome_106_116}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Chrome_117_120_124}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Safari_IOS_17_0}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Safari_IOS_16_0}</li>
         *     <li>{@link Const#HTTP2_Fingerprint_Config_Safari}</li>
         * </ul>
         * </p>
         *
         * @param h2Config HTTP/2 配置字符串。如果强制请求发送时使用HTTP/1.1 请填入参数 http/1.1
         * @return 如果成功设置配置，返回 true；否则返回 false。
         */
        public boolean SetH2Config(String h2Config) {
            return api.SetRequestHTTP2Config(MessageId, h2Config);
        }

        /**
         * 随机化请求的  JA3 指纹。
         * <p>
         * 若全局设置中没有开启 TLS 指纹随机，你可以单独使用此命令来对此请求随机化 JA3 指纹。
         * </p>
         *
         * @return 如果成功随机化指纹，返回 true；否则返回 false。
         */
        public boolean RandomJA3() {
            return api.RandomRequestCipherSuites(MessageId);
        }

        /**
         * 设置代理。
         *
         * @param ProxyUrl 代理 URL，指定要使用的代理地址。
         *                 <p>
         *                 例如，以下示例格式：
         *                 <ul>
         *                     <li>HTTP代理,有账号密码</li>
         *                     <li><font color="#bb7060">http://admin:123456@127.0.0.1:8888</font></li>
         *                     <li>S5代理,有账号密码</li>
         *                     <li><font color="#bb7060">socket5://admin:123456@127.0.0.1:8888</font></li>
         *                     <li>HTTP代理,无账号密码</li>
         *                     <li><font color="#bb7060">http://127.0.0.1:8888</font></li>
         *                     <li>S5代理,无账号密码</li>
         *                     <li><font color="#bb7060">socket5://admin:123456@127.0.0.1:8888</font></li>
         *                 </ul>
         *                 </p>
         * @param OutTime  代理超时，单位毫秒。
         * @return 如果成功设置代理，返回 true；否则返回 false。
         */
        public boolean SetProxy(String ProxyUrl, long OutTime) {
            return api.SetRequestProxy(MessageId, ProxyUrl, OutTime);
        }

        /**
         * 重置完整的协议头。
         *
         * @param Headers 完整的协议头字符串。
         */
        public void SetALLHeader(String Headers) {
            api.SetRequestALLHeader(MessageId, Headers);
        }

        /**
         * 设置请求的全部 Cookies。
         *
         * @param Cookies Cookies 字符串，例如 a=1;b=2;c=3。
         */
        public void SetAllCookie(String Cookies) {
            api.SetRequestAllCookie(MessageId, Cookies);
        }

        /**
         * 设置多个同名的协议头。
         * <p>
         * 若本身无指定的协议头，即为新增；若有则为修改。
         * </p>
         *
         * @param key   协议头名称。
         * @param value 协议头值的数组。
         */
        public void SetHeaderArray(String key, String[] value) {
            api.SetRequestHeader(MessageId, key, String.join("\r\n", Arrays.stream(value).filter(header -> !header.trim().isEmpty()).toArray(String[]::new)));
        }

        /**
         * 设置单个协议头。
         * <p>
         * 若本身无指定的协议头，即为新增；若有则为修改。
         * 请使用 SetHeaderArray 方法来设置多个同名的协议头。
         * </p>
         *
         * @param key   协议头名称。
         * @param value 协议头值。
         */
        public void SetHeader(String key, String value) {
            api.SetRequestHeader(MessageId, key, value.replaceAll("\r", "").replaceAll("\n", ""));
        }

        /**
         * 修改请求的 URL。
         * <p>
         * 可以用于转向，例如从网址 A 转向网址 B。
         * </p>
         *
         * @param newUrl 新的 URL。
         */
        public void SetUrl(String newUrl) {
            api.SetRequestUrl(MessageId, newUrl);
        }

        /**
         * 设置 Cookie。
         * <p>
         * key 为 Cookie 名，value 为 Cookie 值。
         * 如果 key 存在则修改，不存在则新增。
         * </p>
         *
         * @param key   Cookie 名。
         * @param value Cookie 值。
         */
        public void SetCookie(String key, String value) {
            api.SetRequestCookie(MessageId, key, value);
        }

        /**
         * 删除指定的协议头。
         *
         * @param name 协议头名称。
         */
        public void DelHeader(String name) {
            api.DelRequestHeader(MessageId, name);
        }

        /**
         * 删除协议头中的压缩标记。
         */
        public void RemoveCompressionMark() {
            DelHeader("Accept-Encoding");
        }

        /**
         * 获取全部协议头。
         *
         * @return 返回完整的协议头字符串。
         */
        public String GetAllHeader() {
            return api.GetRequestAllHeader(MessageId);
        }

        /**
         * 获取指定协议头。
         * <p>
         * 如果有多个同名协议头，将返回第一个。
         * </p>
         *
         * @param name 协议头名称。
         * @return 返回指定名称的协议头值。
         */
        public String GetHeader(String name) {
            String[] array = GetHeaderArray(name);
            if (array.length > 0) {
                return array[0];
            }
            return "";
        }

        /**
         * 获取指定协议头的数组。
         * <p>
         * 将返回数组。
         * </p>
         *
         * @param name 协议头名称。
         * @return 返回指定名称的协议头值数组。
         */
        public String[] GetHeaderArray(String name) {
            String raw = api.GetRequestHeader(MessageId, name);
            // 使用流过滤空字符串并收集结果
            return Arrays.stream(raw.split("\n"))
                    .filter(header -> !header.trim().isEmpty())
                    .toArray(String[]::new);
        }

        /**
         * 获取请求的协议版本，例如 HTTP/1.1。
         *
         * @return 返回请求的协议版本字符串。
         */
        public String GetProto() {
            return api.GetRequestProto(MessageId);
        }

        /**
         * 获取全部 Cookies。
         *
         * @return 返回所有 Cookies 的字符串。
         */
        public String GetCookies() {
            return api.GetRequestALLCookie(MessageId);
        }

        /**
         * 获取指定 Cookie。
         * <p>
         * 例如，全部 Cookies 为 [a=1; b=2; c=3]，获取 b 将返回 b=2;
         * </p>
         *
         * @param name Cookie 名。
         * @return 返回指定 Cookie 的字符串。
         */
        public String GetCookie(String name) {
            return api.GetRequestCookie(MessageId, name);
        }

        /**
         * 获取指定 Cookie，不包含键名。
         * <p>
         * 例如，全部 Cookies 为 [a=1; b=2; c=3]，获取 b 将返回 2;
         * </p>
         *
         * @param name Cookie 名。
         * @return 返回指定 Cookie 的值。
         */
        public String GetCookie_value(String name) {
            String res = GetCookie(name);
            String[] resList = res.split(name + "=");
            res = "";
            // 检查数组成员数是否大于等于 2
            if (resList.length >= 2) {
                res = resList[1]; // 获取 cookie 值
                res = res.replace(";", ""); // 替换分号
                res = res.trim(); // 去除首尾空格
            }
            return res; // 返回结果
        }

        /**
         * 删除全部协议头。
         */
        public void DelAllHeader() {
            String fullHeaders = GetAllHeader();
            String[] headers = fullHeaders.split("\n");
            for (String header : headers) {
                String[] headerParts = header.split(":", 2);
                if (headerParts.length > 0) {
                    api.DelRequestHeader(MessageId, headerParts[0]);
                }
            }
        }
    }

    /**
     * 响应对象 当{@link HTTPEvent#Type()} 为
     * <li>{@link Const#EventType_HTTP_Request} 发起请求</li>
     * <li>或</li>
     * <li>{@link Const#EventType_HTTP_Response}请求完成</li>
     * <li>时有效</li>
     */
    public static class Response extends Compress {
        private final long MessageId;

        public Response(long MessageId) {
            this.MessageId = MessageId;
        }

        /**
         * 修改响应状态码
         */
        public void StatusCode(long Status) {
            api.SetResponseStatus(MessageId, Status);
        }

        /**
         * 获取响应状态码
         */
        public long StatusCode() {
            return api.GetResponseStatusCode(MessageId);
        }

        /**
         * 获取响应状态文本
         */
        public String Status() {
            return api.GetResponseStatus(MessageId);
        }

        /**
         * 获取取服务器响应IP地址
         */
        public String ServerAddress() {
            return api.GetResponseServerAddress(MessageId);
        }

        /**
         * 获取 响应数据 数据。
         *
         * @return 返回 响应的原始字节数组。
         */
        public byte[] Body() {
            return api.GetResponseBody(MessageId);
        }

        /**
         * 获取 响应数据 数据。自动判断压缩类型,并且解压缩
         *
         * @return 解压缩后的字节数据
         */
        public byte[] BodyAuto() {
            String Encoding = GetHeader("Content-Encoding").toLowerCase();
            switch (Encoding) {
                case "gzip": {
                    byte[] raw2 = GzipUnCompress(Body());
                    if (raw2 != null && raw2.length > 0) {
                        DelHeader("Content-Encoding");
                        Body(raw2);
                    }
                    break;
                }
                case "br": {
                    byte[] raw2 = BrUnCompress(Body());
                    if (raw2 != null && raw2.length > 0) {
                        DelHeader("Content-Encoding");
                        Body(raw2);
                    }
                    break;
                }
                case "deflate": {
                    byte[] raw2 = DeflateUnCompress(Body());
                    if (raw2 != null && raw2.length > 0) {
                        DelHeader("Content-Encoding");
                        Body(raw2);
                    }
                    break;
                }
                case "zstd": {
                    byte[] raw2 = ZSTDUnCompress(Body());
                    if (raw2 != null && raw2.length > 0) {
                        DelHeader("Content-Encoding");
                        Body(raw2);
                    }
                    break;
                }
                case "zlib": {
                    byte[] raw2 = ZlibUnCompress(Body());
                    if (raw2 != null && raw2.length > 0) {
                        DelHeader("Content-Encoding");
                        Body(raw2);
                    }
                    break;
                }
            }
            return Body();
        }

        /**
         * 自动判断压缩类型,并且解压缩,并且将GBK编码的字符串转为UTF-8
         *
         * @return 返回UTF-8字符串
         */
        public String BodyAutoGBK() {
            return new String(BodyAuto(), Const.GBKCharset);
        }

        /**
         * 自动判断压缩类型,并且解压缩,并且将GBK编码的字符串转为UTF-8
         *
         * @return 返回UTF-8字符串
         */
        public String BodyAutoUTF8() {
            return new String(BodyAuto(), StandardCharsets.UTF_8);
        }

        /**
         * 获取 UTF-8 编码的 响应数据 字符串。
         *
         * @return 返回 UTF-8 编码的 响应数据 字符串
         */
        public String BodyToUTF8() {
            return new String(Body(), StandardCharsets.UTF_8);
        }

        /**
         * 获取 GBK 编码的 响应数据,并将GBK编码的响应数据转为UTF8编码
         *
         * @return 返回 UTF8 编码的 响应数据 字符串。
         */
        public String BodyToGBK() {
            return new String(Body(), Const.GBKCharset);
        }

        /**
         * 修改 响应数据 数据。
         * <p>
         * 将UTF8编码的字符串 转为GBK编码,并且替换当前请求响应数据
         *
         * @param data 要设置的新数据，需要传入GBK字符串
         */
        public void BodyToGBK(String data) {
            Body(data.getBytes(Const.GBKCharset));
        }

        /**
         * 修改 响应数据 数据。
         *
         * @param data 要设置的新数据，需要传入UTF8字符串
         */
        public void BodyToUTF8(String data) {
            Body(data.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 修改 响应数据 数据。
         *
         * @param data 要设置的新数据，字节数组形式。
         */
        public void Body(byte[] data) {
            api.SetResponseData(MessageId, data);
        }

        /**
         * 设置多个同名的协议头。
         * <p>
         * 若本身无指定的协议头，即为新增；若有则为修改。
         * </p>
         *
         * @param key   协议头名称。
         * @param value 协议头值的数组。
         */
        public void SetHeaderArray(String key, String[] value) {
            api.SetResponseHeader(MessageId, key, String.join("\r\n", Arrays.stream(value).filter(header -> !header.trim().isEmpty()).toArray(String[]::new)));
        }

        /**
         * 设置单个协议头。
         * <p>
         * 若本身无指定的协议头，即为新增；若有则为修改。
         * 请使用 SetHeaderArray 方法来设置多个同名的协议头。
         * </p>
         *
         * @param key   协议头名称。
         * @param value 协议头值。
         */
        public void SetHeader(String key, String value) {
            api.SetResponseHeader(MessageId, key, value.replaceAll("\r", "").replaceAll("\n", ""));
        }

        /**
         * 重置完整的协议头。
         *
         * @param Headers 完整的协议头字符串。
         */
        public void SetResponseAllHeader(String Headers) {
            api.SetResponseAllHeader(MessageId, Headers);
        }

        /**
         * 删除指定的协议头。
         *
         * @param name 协议头名称。
         */
        public void DelHeader(String name) {
            api.DelResponseHeader(MessageId, name);
        }

        /**
         * 获取全部协议头。
         *
         * @return 返回完整的协议头字符串。
         */
        public String GetAllHeader() {
            return api.GetResponseAllHeader(MessageId);
        }

        /**
         * 删除全部协议头。
         */
        public void DelAllHeader() {
            String fullHeaders = GetAllHeader();
            String[] headers = fullHeaders.split("\n");
            for (String header : headers) {
                String[] headerParts = header.split(":", 2);
                if (headerParts.length > 0) {
                    api.DelResponseHeader(MessageId, headerParts[0]);
                }
            }
        }

        /**
         * 获取响应的协议版本，例如 HTTP/1.1。
         *
         * @return 返回响应的协议版本字符串。
         */
        public String GetProto() {
            return api.GetResponseProto(MessageId);
        }

        /**
         * 获取指定协议头。
         * <p>
         * 如果有多个同名协议头，将返回第一个。
         * </p>
         *
         * @param name 协议头名称。
         * @return 返回指定名称的协议头值。
         */
        public String GetHeader(String name) {
            String[] array = GetHeaderArray(name);
            if (array.length > 0) {
                return array[0];
            }
            return "";
        }

        /**
         * 获取指定协议头的数组。
         * <p>
         * 如果有多个同名协议头，将返回数组。
         * </p>
         *
         * @param name 协议头名称。
         * @return 返回指定名称的协议头值数组。
         */
        public String[] GetHeaderArray(String name) {
            String raw = api.GetResponseHeader(MessageId, name);
            // 使用流过滤空字符串并收集结果
            return Arrays.stream(raw.split("\n"))
                    .filter(header -> !header.trim().isEmpty())
                    .toArray(String[]::new);
        }

    }
}
