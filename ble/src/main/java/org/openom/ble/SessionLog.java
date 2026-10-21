// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.ble;

import android.util.Log;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

/** Main-thread-owned, bounded in-memory log; export is an explicit UI action. */
public final class SessionLog {
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public void add(String message) {
        String line = TIME.format(LocalTime.now()) + " " + message;
        if (lines.size() == 250) lines.removeFirst();
        lines.addLast(line);
        Log.d("OpenOM", message);
    }

    public String text() { return String.join("\n", lines); }
    public void clear() { lines.clear(); }
}
