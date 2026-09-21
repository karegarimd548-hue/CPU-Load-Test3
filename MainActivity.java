package com.mehdi.cpuloadtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private final List<byte[]> ramBlocks = new ArrayList<>();
    private TextView statusText;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 50, 36, 36);

        TextView title = new TextView(this);
        title.setText("CPU Load Test");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView info = new TextView(this);
        info.setText("تست ۱۰ ثانیه‌ای\nفشار CPU + فشار محدود RAM");
        info.setTextSize(18f);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        info.setPadding(0, 22, 0, 30);

        statusText = new TextView(this);
        statusText.setText("آماده");
        statusText.setTextSize(20f);
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusText.setPadding(0, 0, 0, 30);

        startButton = new Button(this);
        startButton.setText("شروع تست ۱۰ ثانیه‌ای");
        startButton.setOnClickListener(v -> startStressTest());

        root.addView(title);
        root.addView(info);
        root.addView(statusText);
        root.addView(startButton);
        setContentView(root);
    }

    private void startStressTest() {
        if (!running.compareAndSet(false, true)) return;
        startButton.setEnabled(false);
        final long endTime = SystemClock.elapsedRealtime() + 10_000L;
        final int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        executor = Executors.newFixedThreadPool(cpuThreads + 1);

        for (int i = 0; i < cpuThreads; i++) {
            executor.execute(() -> {
                long x = 0x1234ABCDL;
                while (running.get() && SystemClock.elapsedRealtime() < endTime) {
                    x ^= (x << 13); x ^= (x >>> 7); x ^= (x << 17); x += 0x9E3779B97F4A7C15L;
                }
            });
        }
        executor.execute(() -> runRamPressure(endTime));

        Thread uiThread = new Thread(() -> {
            try {
                while (running.get() && SystemClock.elapsedRealtime() < endTime) {
                    long remaining = endTime - SystemClock.elapsedRealtime();
                    int seconds = Math.max(0, (int)Math.ceil(remaining / 1000.0));
                    runOnUiThread(() -> statusText.setText("در حال تست — " + seconds + " ثانیه باقی مانده"));
                    Thread.sleep(200);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                stopStressTest();
            }
        });
        uiThread.start();
    }

    private void runRamPressure(long endTime) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxHeap = runtime.maxMemory();
            long target = Math.min((long)(maxHeap * 0.60), 192L * 1024L * 1024L);
            long blockSize = 1024L * 1024L;
            while (running.get() && SystemClock.elapsedRealtime() < endTime
                    && totalRamAllocated() + blockSize <= target) {
                byte[] block = new byte[(int)blockSize];
                for (int i = 0; i < block.length; i += 4096) block[i] = (byte)(i & 0xFF);
                synchronized (ramBlocks) { ramBlocks.add(block); }
            }
        } catch (OutOfMemoryError ignored) {
        }
    }

    private long totalRamAllocated() {
        synchronized (ramBlocks) { return (long)ramBlocks.size() * 1024L * 1024L; }
    }

    private void stopStressTest() {
        if (!running.compareAndSet(true, false)) return;
        if (executor != null) { executor.shutdownNow(); executor = null; }
        synchronized (ramBlocks) { ramBlocks.clear(); }
        System.gc();
        runOnUiThread(() -> {
            statusText.setText("تست تمام شد؛ منابع آزاد شدند");
            startButton.setEnabled(true);
        });
    }

    @Override protected void onDestroy() {
        stopStressTest();
        super.onDestroy();
    }
}
