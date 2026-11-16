package com.meir.keypadsender;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/** סשן su יחיד. כל פקודה חוסמת עד שהסתיימה (סמן echo), כך שהמרווחים בין הקשות דטרמיניסטיים. */
final class RootShell {
    private static final String MARK = "__KS_DONE__";

    private final Process proc;
    private final BufferedWriter out;
    private final BufferedReader in;

    private RootShell(Process p) {
        proc = p;
        out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
        in = new BufferedReader(new InputStreamReader(p.getInputStream()));
    }

    /** null = אין רוט / נדחה. עלול לחסום עד שהמשתמש מאשר בחלון ה-Superuser. */
    static RootShell open() {
        try {
            Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            RootShell sh = new RootShell(p);
            String r = sh.run("id");
            if (r != null && r.contains("uid=0")) return sh;
            sh.close();
        } catch (Exception ignored) {
        }
        return null;
    }

    /** מריץ פקודה ומחזיר את הפלט שלה, או null אם ה-shell נסגר. */
    synchronized String run(String cmd) throws IOException {
        out.write(cmd + "\necho " + MARK + "\n");
        out.flush();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains(MARK)) return sb.toString();
            sb.append(line).append('\n');
        }
        return null;
    }

    void close() {
        try { out.write("exit\n"); out.flush(); } catch (Exception ignored) {}
        try { proc.destroy(); } catch (Exception ignored) {}
    }
}
