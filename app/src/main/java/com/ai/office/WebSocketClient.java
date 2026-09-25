package com.ai.office;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 极简 WebSocket 客户端（RFC6455），零依赖，手写实现。
 *
 * 使用方式：
 *   WebSocketClient c = new WebSocketClient();
 *   c.connect("wss://example.com/path", headers, listener);
 *   c.send("{\"type\":\"...\"}");
 *   c.close();
 *
 * 注意事项：
 *   - 所有回调在后台读线程上触发，UI 层需自行 post 到主线程
 *   - 只支持 wss://（TLS）和 ws://（明文，仅调试用）
 *   - 只支持文本帧（GLM-Realtime 与 OpenAI Realtime 都只用文本帧）
 *   - 客户端发送帧必须 mask（RFC6455 强制），服务端回复不 mask
 *   - 读到 ping 帧自动回 pong；读到 close 帧自动回 close 并断开
 */
public class WebSocketClient {

    private static final String TAG = "WebSocketClient";

    public interface Listener {
        void onOpen();
        void onMessage(String text);
        void onClose(int code, String reason);
        void onError(String message);
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Listener listener;
    private volatile boolean opened = false;
    private volatile boolean closed = false;
    private volatile boolean wantClose = false;
    private Thread readerThread;
    private final Object sendLock = new Object();

    /** 建立连接。headers 里的键值会附加到握手请求（如 Authorization: Bearer xxx） */
    public void connect(final String url, final Map<String, String> headers, final Listener l) {
        this.listener = l;
        this.opened = false;
        this.closed = false;
        this.wantClose = false;

        new Thread(new Runnable() {
            @Override public void run() {
                doConnect(url, headers);
            }
        }, "ws-connect").start();
    }

    public boolean isOpen() { return opened && !closed; }

    public void send(String text) {
        if (!isOpen() || text == null) return;
        try {
            byte[] payload = text.getBytes("UTF-8");
            synchronized (sendLock) {
                writeFrame(out, 0x1, payload);
                out.flush();
            }
        } catch (Throwable t) {
            notifyError("send 失败: " + t.getMessage());
            close();
        }
    }

    public void close() {
        if (closed) return;
        wantClose = true;
        try {
            if (out != null) {
                synchronized (sendLock) {
                    writeFrame(out, 0x8, new byte[0]);
                    out.flush();
                }
            }
        } catch (Throwable t) {}
        try { if (socket != null) socket.close(); } catch (Throwable t) {}
        closed = true;
    }

    // ============================================================
    // 握手
    // ============================================================

    private void doConnect(String url, Map<String, String> headers) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();

            boolean tls = "wss".equalsIgnoreCase(scheme);
            if (port <= 0) port = tls ? 443 : 80;
            if (path == null || path.length() == 0) path = "/";
            if (query != null && query.length() > 0) path = path + "?" + query;

            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(host, port), 15000);
            raw.setTcpNoDelay(true);

            if (tls) {
                SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket ssl = (SSLSocket) f.createSocket(raw, host, port, true);
                ssl.startHandshake();
                socket = ssl;
            } else {
                socket = raw;
            }

            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Sec-WebSocket-Key：16 字节随机数 base64
            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String key = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP);

            StringBuilder req = new StringBuilder();
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(":").append(port).append("\r\n");
            req.append("Upgrade: websocket\r\n");
            req.append("Connection: Upgrade\r\n");
            req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
            req.append("Sec-WebSocket-Version: 13\r\n");
            req.append("User-Agent: AI-Office-Android\r\n");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    req.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
                }
            }
            req.append("\r\n");

            out.write(req.toString().getBytes("UTF-8"));
            out.flush();

            // 读响应头
            String respLine = readLine(in);
            if (respLine == null || !respLine.contains("101")) {
                notifyError("WebSocket 握手失败: " + respLine);
                closeSocketQuiet();
                return;
            }

            Map<String, String> respHeaders = new HashMap<String, String>();
            while (true) {
                String line = readLine(in);
                if (line == null || line.length() == 0) break;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String k = line.substring(0, colon).trim().toLowerCase();
                    String v = line.substring(colon + 1).trim();
                    respHeaders.put(k, v);
                }
            }

            // 校验 Sec-WebSocket-Accept（可选，但严格校验更安全）
            String accept = respHeaders.get("sec-websocket-accept");
            if (accept != null) {
                String expected = computeAccept(key);
                if (!expected.equals(accept)) {
                    notifyError("Sec-WebSocket-Accept 校验失败");
                    closeSocketQuiet();
                    return;
                }
            }

            opened = true;
            closed = false;
            if (listener != null) listener.onOpen();

            readerThread = new Thread(new Runnable() {
                @Override public void run() { readerLoop(); }
            }, "ws-reader");
            readerThread.start();

        } catch (Throwable t) {
            notifyError("连接失败: " + t.getMessage());
            closeSocketQuiet();
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        int last = -1;
        while ((b = in.read()) >= 0) {
            if (b == '\n' && last == '\r') {
                byte[] arr = bos.toByteArray();
                // 去掉尾部的 \r
                int len = arr.length;
                if (len > 0 && arr[len - 1] == '\r') len--;
                return new String(arr, 0, len, "UTF-8");
            }
            bos.write(b);
            last = b;
        }
        return null;
    }

    private static String computeAccept(String key) throws Exception {
        String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest((key + magic).getBytes("UTF-8"));
        return android.util.Base64.encodeToString(sha1, android.util.Base64.NO_WRAP);
    }

    // ============================================================
    // 帧读取
    // ============================================================

    private void readerLoop() {
        try {
            while (!closed && !wantClose) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;

                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long payloadLen = b1 & 0x7F;

                if (payloadLen == 126) {
                    payloadLen = ((long) readByte() << 8) | readByte();
                } else if (payloadLen == 127) {
                    payloadLen = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLen = (payloadLen << 8) | readByte();
                    }
                }

                byte[] maskKey = null;
                if (masked) {
                    maskKey = new byte[4];
                    readFully(maskKey, 0, 4);
                }

                if (payloadLen > 16 * 1024 * 1024) {
                    notifyError("帧过大: " + payloadLen);
                    break;
                }

                byte[] payload = new byte[(int) payloadLen];
                readFully(payload, 0, payload.length);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
                    }
                }

                if (opcode == 0x1) {  // text
                    String text = new String(payload, "UTF-8");
                    if (listener != null) listener.onMessage(text);
                } else if (opcode == 0x8) {  // close
                    int code = 1000;
                    String reason = "";
                    if (payload.length >= 2) {
                        code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                        if (payload.length > 2) {
                            reason = new String(payload, 2, payload.length - 2, "UTF-8");
                        }
                    }
                    if (!wantClose) {
                        try {
                            synchronized (sendLock) {
                                writeFrame(out, 0x8, new byte[0]);
                                out.flush();
                            }
                        } catch (Throwable t) {}
                    }
                    closed = true;
                    if (listener != null) listener.onClose(code, reason);
                    break;
                } else if (opcode == 0x9) {  // ping
                    try {
                        synchronized (sendLock) {
                            writeFrame(out, 0xA, payload);
                            out.flush();
                        }
                    } catch (Throwable t) {}
                } else if (opcode == 0xA) {  // pong
                    // 忽略
                } else if (opcode == 0x0) {  // continuation（GLM 一般不拆帧，忽略）
                    // 不做处理
                }
            }
        } catch (Throwable t) {
            if (!wantClose) notifyError("读取帧失败: " + t.getMessage());
        } finally {
            boolean wasOpen = opened;
            opened = false;
            closed = true;
            if (wasOpen && listener != null) {
                try { listener.onClose(1006, "连接断开"); } catch (Throwable t) {}
            }
        }
    }

    private int readByte() throws Exception {
        int b = in.read();
        if (b < 0) throw new Exception("EOF");
        return b & 0xFF;
    }

    private void readFully(byte[] buf, int off, int len) throws Exception {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, off + read, len - read);
            if (n < 0) throw new Exception("EOF");
            read += n;
        }
    }

    // ============================================================
    // 帧写入
    // ============================================================

    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws Exception {
        if (payload == null) payload = new byte[0];
        ByteArrayOutputStream header = new ByteArrayOutputStream();

        // Byte 0: FIN=1, RSV=0, opcode
        header.write(0x80 | (opcode & 0x0F));

        // Byte 1: MASK=1, length
        int len = payload.length;
        if (len <= 125) {
            header.write(0x80 | len);
        } else if (len <= 0xFFFF) {
            header.write(0x80 | 126);
            header.write((len >> 8) & 0xFF);
            header.write(len & 0xFF);
        } else {
            header.write(0x80 | 127);
            long l = len;
            for (int i = 7; i >= 0; i--) {
                header.write((int) ((l >> (i * 8)) & 0xFF));
            }
        }

        // masking key
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        header.write(mask);

        out.write(header.toByteArray());

        // masked payload
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        out.write(masked);
    }

    // ============================================================
    // 工具
    // ============================================================

    private void closeSocketQuiet() {
        try { if (in != null) in.close(); } catch (Throwable t) {}
        try { if (out != null) out.close(); } catch (Throwable t) {}
        try { if (socket != null) socket.close(); } catch (Throwable t) {}
        in = null;
        out = null;
        socket = null;
        opened = false;
        closed = true;
    }

    private void notifyError(String msg) {
        if (listener != null) {
            try { listener.onError(msg); } catch (Throwable t) {}
        }
    }
}