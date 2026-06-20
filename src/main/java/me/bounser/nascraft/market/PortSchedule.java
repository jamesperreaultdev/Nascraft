package me.bounser.nascraft.market;

import me.bounser.nascraft.Nascraft;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-world (server local time) opening hours of a port.
 *
 * A schedule is a set of daily-recurring open windows. Outside every window the
 * port is closed and trading is refused. A window may wrap past midnight
 * (e.g. 22:00-02:00). A port with no configured windows is {@link #alwaysOpen()}.
 */
public final class PortSchedule {

    /** A single open interval, expressed as minutes-of-day in [0, 1440). */
    private static final class Window {

        final int start;
        final int end;

        Window(int start, int end) {
            this.start = start;
            this.end = end;
        }

        /** end <= start means the window wraps over midnight. */
        boolean contains(int minute) {
            if (start == end) return true;            // full day
            if (start < end) return minute >= start && minute < end;
            return minute >= start || minute < end;   // wraps midnight
        }
    }

    private final boolean alwaysOpen;
    private final List<Window> windows;
    private final List<String> display;

    private PortSchedule(boolean alwaysOpen, List<Window> windows, List<String> display) {
        this.alwaysOpen = alwaysOpen;
        this.windows = windows;
        this.display = display;
    }

    public static PortSchedule alwaysOpen() {
        return new PortSchedule(true, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Parses a list of "HH:mm-HH:mm" windows. Malformed entries are warned about
     * and skipped; if nothing valid remains the port is treated as always open.
     */
    public static PortSchedule fromStrings(List<String> raw, String portId) {

        List<Window> windows = new ArrayList<>();
        List<String> display = new ArrayList<>();

        for (String entry : raw) {

            if (entry == null) continue;

            String[] parts = entry.trim().split("-");

            if (parts.length != 2) {
                warn(portId, entry);
                continue;
            }

            Integer start = parseMinute(parts[0]);
            Integer end = parseMinute(parts[1]);

            if (start == null || end == null) {
                warn(portId, entry);
                continue;
            }

            windows.add(new Window(start, end));
            display.add(format(start) + "-" + format(end));
        }

        if (windows.isEmpty()) return alwaysOpen();

        return new PortSchedule(false, windows, display);
    }

    private static Integer parseMinute(String hhmm) {

        String[] hm = hhmm.trim().split(":");

        if (hm.length != 2) return null;

        try {
            int h = Integer.parseInt(hm[0].trim());
            int m = Integer.parseInt(hm[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void warn(String portId, String entry) {
        Nascraft.getInstance().getLogger().warning(
                "Port " + portId + " has an invalid schedule window '" + entry + "'. "
                        + "Expected format HH:mm-HH:mm (e.g. 08:00-20:00). Skipping.");
    }

    private static String format(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
    }

    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public boolean isAlwaysOpen() { return alwaysOpen; }

    public boolean isOpen(LocalTime now) {

        if (alwaysOpen) return true;

        int minute = minuteOfDay(now);

        for (Window window : windows)
            if (window.contains(minute)) return true;

        return false;
    }

    /** The configured windows as display strings (e.g. "08:00-20:00"). Empty if always open. */
    public List<String> getDisplayWindows() { return display; }

    /**
     * Minutes until the open/closed state next flips, or -1 if it never changes
     * (always open, or — defensively — no windows). The caller pairs this with
     * {@link #isOpen} to label it as "opens in" or "closes in".
     */
    public int minutesUntilChange(LocalTime now) {

        if (alwaysOpen || windows.isEmpty()) return -1;

        int minute = minuteOfDay(now);
        int best = Integer.MAX_VALUE;

        for (Window window : windows) {
            best = Math.min(best, delta(minute, window.start));
            best = Math.min(best, delta(minute, window.end));
        }

        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /** Minutes from now until the boundary, in (0, 1440]; a boundary now counts as the next day. */
    private static int delta(int now, int boundary) {
        int d = (boundary - now) % 1440;
        if (d <= 0) d += 1440;
        return d;
    }

    /** Formats a minute count as "Xh Ym", "Xh" or "Ym" for menus. */
    public static String formatMinutes(int totalMinutes) {

        if (totalMinutes < 0) return "";

        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h";
        return minutes + "m";
    }
}
