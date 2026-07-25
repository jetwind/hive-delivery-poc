package com.hive.delivery.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final String LOG_PATH = "logs/hive-delivery.log";
    private static final int POLL_MS = 500;

    @GetMapping
    public Map<String, Object> recent(@RequestParam(defaultValue = "100") int lines) {
        var result = new ArrayList<String>();
        var file = new File(LOG_PATH);
        if (!file.exists()) return Map.of("lines", List.of(), "exists", false);

        try (var raf = new RandomAccessFile(file, "r")) {
            long buf = Math.max(0, raf.length() - 8192L * lines);
            raf.seek(buf);
            if (buf > 0) raf.readLine();
            String line;
            while ((line = raf.readLine()) != null) {
                result.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                        .replaceAll("\\u0000", ""));
            }
            int size = result.size();
            return Map.of("lines", size > lines ? result.subList(size - lines, size) : result, "exists", true);
        } catch (IOException e) {
            return Map.of("lines", List.of(), "error", e.getMessage());
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        var emitter = new SseEmitter(0L);
        var file = new File(LOG_PATH);

        if (!file.exists()) {
            try { emitter.send(SseEmitter.event().name("log").data("log file not found: " + LOG_PATH)); } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try (var raf = new RandomAccessFile(file, "r")) {
                raf.seek(raf.length());
                while (true) {
                    try {
                        var line = raf.readLine();
                        if (line != null) {
                            var decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                                    .replaceAll("\\u0000", "");
                            emitter.send(SseEmitter.event().name("log").data(decoded));
                        } else {
                            Thread.sleep(500);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                        break;
                    }
                }
            } catch (IOException e) {
                try { emitter.send(SseEmitter.event().name("log").data("[日志读取错误: " + e.getMessage() + "]")); } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
