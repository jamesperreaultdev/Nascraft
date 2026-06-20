package me.bounser.nascraft.market;

import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.util.List;

/**
 * Lang-driven helpers describing a port's opening hours and current open/closed
 * state. Shared by the trade-gating messages, the port menu info item and the
 * ports directory so the wording lives in one place.
 */
public final class PortStatus {

    private PortStatus() { }

    /** "Open" / "Closed" word for the port's current state. */
    public static String statusText(Port port) {
        return Lang.get().message(port.isOpen() ? Message.PORT_STATUS_OPEN : Message.PORT_STATUS_CLOSED);
    }

    /** The configured windows joined with PORT_HOURS_SEGMENT, or PORT_HOURS_ALWAYS when always open. */
    public static String hoursText(Port port) {

        List<String> windows = port.getSchedule().getDisplayWindows();

        if (windows.isEmpty()) return Lang.get().message(Message.PORT_HOURS_ALWAYS);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(Lang.get().message(Message.PORT_HOURS_SEGMENT, "[WINDOW]", windows.get(i)));
        }

        return builder.toString();
    }

    /**
     * "Opens in 2h 15m" / "Closes in 35m" for the current state, or an empty
     * string when the port never changes state (always open).
     */
    public static String nextChangeText(Port port) {

        int minutes = port.getSchedule().minutesUntilChange(LocalTime.now());

        if (minutes < 0) return "";

        String time = PortSchedule.formatMinutes(minutes);

        return Lang.get().message(port.isOpen() ? Message.PORT_CLOSES_IN : Message.PORT_OPENS_IN, "[TIME]", time);
    }

    /** Tells the player the port is shut and when it reopens. */
    public static void sendClosed(Player player, Port port) {
        Lang.get().message(player, Message.PORT_CLOSED,
                "[PORT]", port.getDisplayName(),
                "[HOURS]", hoursText(port));
    }
}
