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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Staya startup banner: STAYA + fox art, core/Minecraft version, latest changelog line,
 * and an empty box. Art lives in /staya/banner.txt, build info in /staya/staya-build.properties.
 */
public final class StayaBanner {

    private static volatile Map<String, String> buildInfoCache;

    private StayaBanner() {
    }

    public static void print() {
        System.out.println();
        for (String line : loadArt()) {
            System.out.println(line);
        }
        Map<String, String> info = buildInfo();
        String mc = (Main.MCVERSION != null && !Main.MCVERSION.isEmpty()) ? Main.MCVERSION : Main.getVersion();
        String welcome = (Main.i18n != null) ? Main.i18n.as("youer.launch.welcomemessage") : "Thanks for using Staya";
        System.out.println("        " + welcome + " - " + mc + ", v" + info.get("staya.product.version")
            + " (build b" + info.get("staya.build.number") + "), Java " + System.getProperty("java.version")
            + " PID: " + ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        String changelog = info.get("staya.changelog.line");
        if (changelog != null && !changelog.isEmpty()) {
            System.out.println("        Latest: v" + info.get("staya.product.version") + " — " + changelog);
        }
        System.out.println("        +------------------------------------------------------+");
        System.out.println("        |                                                      |");
        System.out.println("        |                                                      |");
        System.out.println("        |                                                      |");
        System.out.println("        +------------------------------------------------------+");
    }

    private static List<String> loadArt() {
        try (InputStream in = StayaBanner.class.getResourceAsStream("/staya/banner.txt")) {
            if (in == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Key=value pairs from /staya/staya-build.properties (generated at build time),
     * with fallbacks when the file is absent.
     */
    public static Map<String, String> buildInfo() {
        Map<String, String> cached = buildInfoCache;
        if (cached != null) {
            return cached;
        }
        Map<String, String> info = new LinkedHashMap<>();
        info.put("staya.product.version", "1.0.0");
        info.put("staya.build.number", "0");
        info.put("staya.changelog.line", "");
        try (InputStream in = StayaBanner.class.getResourceAsStream("/staya/staya-build.properties")) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            info.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fallbacks stay
        }
        buildInfoCache = info;
        return info;
    }
}
