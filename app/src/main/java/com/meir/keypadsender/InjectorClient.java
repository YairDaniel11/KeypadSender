package com.meir.keypadsender;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** לקוח לתהליך ה-root הקבוע (Injector). */
final class InjectorClient {
    private final Process proc;
    private final BufferedWriter out;
    private final BlockingQueue<String> lines = new ArrayBlockingQueue<String>(64);

    private InjectorClient(Process p) {
        proc = p;
        out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
        final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String l;
                    while ((l = in.readLine()) != null) lines.offer(l);
                } catch (IOException ignored) {
                }
                lines.offer("EOF");
            }
        }, "injector-read");
        t.setDaemon(true);
        t.start();
    }

    /** null = לא הצליח (אז חוזרים ל-input keyevent). */
    static InjectorClient open(Context ctx) {
        try {
            Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            InjectorClient c = new InjectorClient(p);
            c.out.write("CLASSPATH='" + ctx.getPackageCodePath()
                    + "' exec app_process /system/bin com.meir.keypadsender.Injector\n");
            c.out.flush();
            // מדלגים על כל פלט לא קשור עד READY (או FAIL / סיום)
            long end = System.currentTimeMillis() + 20000; // כולל חלון אישור Superuser
            while (System.currentTimeMillis() < end) {
                String l = c.lines.poll(500, TimeUnit.MILLISECONDS);
                if (l == null) continue;
                if (l.equals("READY")) return c;
                if (l.equals("FAIL") || l.equals("EOF")) break;
            }
            c.close();
        } catch (Exception ignored) {
        }
        return null;
    }

    /** שולח מקשים (קודים) עם מרווח ביניהם; true = הוזרקו. חוסם עד סיום. */
    boolean send(int gapMs, int[] codes) {
        try {
            StringBuilder sb = new StringBuilder().append(gapMs);
            for (int c : codes) sb.append(' ').append(c);
            out.write(sb.append('\n').toString());
            out.flush();
            long wait = 10000L + (long) codes.length * (gapMs + 100);
            String l = lines.poll(wait, TimeUnit.MILLISECONDS);
            return "OK".equals(l);
        } catch (Exception e) {
            return false;
        }
    }

    void close() {
        try { out.close(); } catch (Exception ignored) {}
        try { proc.destroy(); } catch (Exception ignored) {}
    }
}
