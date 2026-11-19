package cz.lollywood.townbuilder.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.8.0: what a command says, collected, so the same result goes to the chat (a typed command) or into a dialog (the menu).
 */
public final class Report {
    public enum Kind { OK, INFO, WARN, ERR }

    public record Line(Kind kind, String text) {}

    public final List<Line> lines = new ArrayList<>();
    /** false once an error was added */
    public boolean ok = true;
    /** for the menu: the last phase's seed (or 0) */
    public long seed;

    public Report ok(String s) { lines.add(new Line(Kind.OK, s)); return this; }
    public Report info(String s) { lines.add(new Line(Kind.INFO, s)); return this; }
    public Report warn(String s) { lines.add(new Line(Kind.WARN, s)); return this; }
    public Report err(String s) { ok = false; lines.add(new Line(Kind.ERR, s)); return this; }

    public static ChatColor color(Kind k) {
        return switch (k) {
            case OK -> ChatColor.GREEN;
            case INFO -> ChatColor.GRAY;
            case WARN -> ChatColor.YELLOW;
            case ERR -> ChatColor.RED;
        };
    }

    public void send(CommandSender s) {
        for (Line l : lines) s.sendMessage(color(l.kind()) + l.text());
    }
}
