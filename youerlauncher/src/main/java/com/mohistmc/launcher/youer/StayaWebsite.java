/*
 * Copyright (C) MohistMC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.mohistmc.launcher.youer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Built-in Staya website (v1.0.1).
 * <p>
 * Serves exactly two things on its own port: the public page at / and the mods
 * archive at /mods.zip. Enabled via server.properties:
 * staya-website-enabled=true (default false), staya-website-port (default 8080).
 * Player count and status come from a Minecraft protocol ping to
 * 127.0.0.1:server-port, so the page shows "запускается" while the server is
 * still booting.
 */
public final class StayaWebsite {

    private static final int PROTOCOL_1_21_1 = 767;
    private static final Pattern P_ONLINE = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");
    private static final Pattern P_MAX = Pattern.compile("\"max\"\\s*:\\s*(\\d+)");
    private static final Pattern P_VERSION = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private static int mcPort = 25565;

    private StayaWebsite() {
    }

    public static void start() {
        Properties props = new Properties();
        File file = new File("server.properties");
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                // unreadable -> defaults
            }
        }
        if (!Boolean.parseBoolean(props.getProperty("staya-website-enabled", "false"))) {
            return;
        }
        int port = parseInt(props.getProperty("staya-website-port", "8080"), 8080);
        mcPort = parseInt(props.getProperty("server-port", "25565"), 25565);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", StayaWebsite::handle);
            server.setExecutor(daemonPool());
            server.start();
            System.out.println("[StayaWebsite] сайт включён: http://<адрес-сервера>:" + port + "/ (моды: /mods.zip)");
        } catch (IOException e) {
            System.err.println("[StayaWebsite] не удалось занять порт " + port + ": " + e.getMessage());
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static ExecutorService daemonPool() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "staya-website");
            t.setDaemon(true);
            return t;
        });
    }

    private static void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if ("/mods.zip".equals(path)) {
                serveModsZip(ex);
            } else if ("/".equals(path) || "/index.html".equals(path)) {
                servePage(ex);
            } else {
                send(ex, 404, "text/plain; charset=utf-8", "404 — не найдено".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            try {
                send(ex, 500, "text/plain; charset=utf-8", ("500 — " + e).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // response already closed
            }
        } finally {
            ex.close();
        }
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static void servePage(HttpExchange ex) throws IOException {
        Status status = pingMinecraft();
        Map<String, String> info = StayaBanner.buildInfo();
        String statusHtml = status.online()
            ? "<span class=\"dot\"></span>онлайн · " + status.onlineCount() + " / " + status.maxCount()
            : "<span class=\"dot off\"></span>сервер запускается…";
        String core = "v" + info.get("staya.product.version") + " (build b" + info.get("staya.build.number") + ")";
        String mc = (Main.MCVERSION != null && !Main.MCVERSION.isEmpty())
            ? Main.MCVERSION
            : (status.online() && !status.mcVersion().isEmpty() ? status.mcVersion() : "—");
        String html = PAGE_TEMPLATE
            .replace("__STATUS__", statusHtml)
            .replace("__CORE__", core)
            .replace("__MC__", mc);
        send(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private static void serveModsZip(HttpExchange ex) throws IOException {
        File modsDir = new File("mods");
        List<File> files = new ArrayList<>();
        File[] found = modsDir.listFiles();
        if (found != null) {
            for (File f : Arrays.asList(found)) {
                if (f.isFile()) {
                    files.add(f);
                }
            }
        }
        ex.getResponseHeaders().set("Content-Type", "application/zip");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"staya-mods.zip\"");
        ex.sendResponseHeaders(200, -1);
        try (OutputStream raw = ex.getResponseBody();
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
            for (File f : files) {
                zip.putNextEntry(new ZipEntry(f.getName()));
                try (FileInputStream in = new FileInputStream(f)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            if (files.isEmpty()) {
                zip.putNextEntry(new ZipEntry("info.txt"));
                zip.write("Папка mods/ пуста".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private record Status(boolean online, int onlineCount, int maxCount, String mcVersion) {
        static Status down() {
            return new Status(false, 0, 0, "");
        }
    }

    private static Status pingMinecraft() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", mcPort), 1500);
            socket.setSoTimeout(2500);
            OutputStream out = socket.getOutputStream();
            out.write(handshake(mcPort));
            out.write(0x01);
            out.flush();
            return parseJson(readStatusJson(socket.getInputStream()));
        } catch (Exception e) {
            return Status.down();
        }
    }

    private static byte[] handshake(int port) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((PROTOCOL_1_21_1 >>> 24) & 0xFF);
        payload.write((PROTOCOL_1_21_1 >>> 16) & 0xFF);
        payload.write((PROTOCOL_1_21_1 >>> 8) & 0xFF);
        payload.write(PROTOCOL_1_21_1 & 0xFF);
        byte[] host = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
        writeVarInt(payload, host.length);
        for (byte b : host) {
            payload.write(b);
        }
        payload.write((port >>> 8) & 0xFF);
        payload.write(port & 0xFF);
        writeVarInt(payload, 1);
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x00);
        writeVarInt(packet, payload.size());
        packet.write(payload.toByteArray(), 0, payload.size());
        return packet.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static String readStatusJson(InputStream in) throws IOException {
        StreamReader r = new StreamReader(in);
        int threshold = r.readVarInt();
        if (threshold >= 0) {
            int dataLen = r.readVarInt();
            if (dataLen == 0) {
                return "";
            }
            byte[] deflated = new byte[dataLen];
            r.readFully(deflated);
            byte[] packet = inflate(deflated);
            if (packet.length == 0 || packet[0] != 0x00) {
                return "";
            }
            int[] pos = {1};
            int len = readVarInt(packet, pos);
            if (pos[0] + len > packet.length) {
                return "";
            }
            return new String(packet, pos[0], len, StandardCharsets.UTF_8);
        }
        if (r.read() != 0x00) {
            return "";
        }
        int len = r.readVarInt();
        byte[] json = new byte[len];
        r.readFully(json);
        return new String(json, StandardCharsets.UTF_8);
    }

    private static int readVarInt(byte[] data, int[] pos) throws IOException {
        int value = 0, shift = 0;
        while (true) {
            if (pos[0] >= data.length) {
                throw new IOException("varint past end");
            }
            int b = data[pos[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 32) {
                throw new IOException("varint too long");
            }
        }
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!inflater.finished()) {
            int n = inflater.inflate(buf);
            if (n > 0) {
                out.write(buf, 0, n);
            } else if (inflater.needsInput()) {
                break;
            }
        }
        inflater.end();
        return out.toByteArray();
    }

    private static Status parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return Status.down();
        }
        Matcher mo = P_ONLINE.matcher(json);
        Matcher mm = P_MAX.matcher(json);
        Matcher mv = P_VERSION.matcher(json);
        int online = mo.find() ? Integer.parseInt(mo.group(1)) : 0;
        int max = mm.find() ? Integer.parseInt(mm.group(1)) : 0;
        String version = mv.find() ? mv.group(1) : "";
        return new Status(true, online, max, version);
    }

    private static final class StreamReader {
        private final InputStream in;

        StreamReader(InputStream in) {
            this.in = in;
        }

        int read() throws IOException {
            return in.read();
        }

        int readVarInt() throws IOException {
            int value = 0, shift = 0;
            while (true) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("EOF in varint");
                }
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
                if (shift >= 32) {
                    throw new IOException("varint too long");
                }
            }
        }

        void readFully(byte[] buf) throws IOException {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    throw new IOException("EOF");
                }
                off += n;
            }
        }
    }

    private static final String PAGE_TEMPLATE = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>STAYA — сервер</title>
        <style>
          :root{
            --bg:#0d1117; --card:#161b22; --line:#2d333b;
            --txt:#e6edf3; --dim:#8b949e; --amber:#f0a35e; --green:#3fb950; --red:#f85149;
          }
          *{box-sizing:border-box; margin:0; padding:0}
          body{
            background:var(--bg); color:var(--txt);
            font-family:'Segoe UI', system-ui, sans-serif;
            min-height:100vh; display:flex; align-items:center; justify-content:center;
            background-image: radial-gradient(1200px 600px at 50% -10%, #1a2332 0%, transparent 60%);
          }
          .card{
            width:min(520px, 92vw); background:var(--card); border:1px solid var(--line);
            border-radius:14px; padding:32px 34px; box-shadow:0 20px 60px rgba(0,0,0,.5);
          }
          .head{display:flex; align-items:center; gap:20px; margin-bottom:6px}
          pre.fox{font-size:14px; line-height:1.2; color:var(--amber); font-family:Consolas, monospace}
          h1{font-size:42px; letter-spacing:7px; font-weight:800}
          .sub{color:var(--dim); margin-bottom:22px; font-size:14px}
          .row{display:flex; justify-content:space-between; padding:10px 0; border-bottom:1px dashed var(--line); font-size:15px}
          .row b{font-weight:600}
          .dot{display:inline-block; width:9px; height:9px; border-radius:50%; background:var(--green); margin-right:8px; box-shadow:0 0 8px var(--green)}
          .dot.off{background:var(--dim); box-shadow:none}
          .empty{color:var(--dim); font-style:italic}
          button{
            margin-top:24px; width:100%; padding:13px; font-size:16px; font-weight:600;
            background:var(--amber); color:#1a1205; border:none; border-radius:10px; cursor:pointer;
          }
          button:hover{filter:brightness(1.08)}
          button:disabled{background:#3a4150; color:#aeb7c2; cursor:default}
          .state{margin-top:10px; text-align:center; font-size:14px; color:var(--dim); min-height:18px}
          footer{margin-top:22px; text-align:center; color:var(--dim); font-size:12px}
        </style>
        </head>
        <body>
          <main class="card">
            <div class="head">
              <pre class="fox"> /\   /\
        //\\_//\\
        \\_     _/
         / . . \\
         \\_\\O/_/
          /   \\</pre>
              <h1>STAYA</h1>
            </div>
            <div class="sub">Minecraft-сервер · ядро Staya</div>

            <div class="row"><span>Статус</span><b>__STATUS__</b></div>
            <div class="row"><span>Ядро</span><b>__CORE__</b></div>
            <div class="row"><span>Minecraft</span><b>__MC__</b></div>
            <div class="row"><span>Адрес</span><b class="empty">— пока пусто</b></div>

            <button id="dl">Скачать моды</button>
            <div class="state" id="state"></div>

            <footer>Staya core · Based on Youer (GPL v3)</footer>
          </main>
        <script>
          const btn = document.getElementById('dl');
          const state = document.getElementById('state');
          btn.addEventListener('click', () => {
            btn.disabled = true;
            btn.textContent = '⏳ Собираем архив…';
            state.textContent = '';
            window.location.href = '/mods.zip';
          });
        </script>
        </body>
        </html>
        """;
}
