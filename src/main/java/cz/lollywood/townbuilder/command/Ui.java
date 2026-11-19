package cz.lollywood.townbuilder.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 0.8.0: the dialog helpers, as in Fallouťák (Marek's rules there, 11-16/17: no cycling input over 3 values, lists of buttons
 * with a filter above 10, a button as wide as its label with the details on hover, numbers as sliders).
 */
final class Ui {
    private Ui() {}

    private static final Map<Integer, Integer> ADVANCE = new HashMap<>();

    static {
        try (var in = Ui.class.getResourceAsStream("/data/fontwidths.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) ADVANCE.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
            }
        } catch (Exception ignored) {}
    }

    /** The label's pixels in the client's default font (its own glyph widths), unknown glyphs 6. */
    static int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += ADVANCE.getOrDefault(cp, 6);
        }
        return w;
    }

    /** A button as wide as its label + a margin, 150 to 400 px. */
    static int width(String label) { return Math.max(150, Math.min(400, textWidth(label) + 24)); }

    private static ClickCallback.Options once() {
        return ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(30)).build();
    }

    static ActionButton btn(String label, String tip, Consumer<Player> then) {
        return ActionButton.create(Component.text(label), Component.text(tip), width(label),
                DialogAction.customClick((view, audience) -> { if (audience instanceof Player p) then.accept(p); }, once()));
    }

    /** A button that reads the dialog's inputs. */
    static ActionButton form(String label, String tip, BiConsumer<DialogResponseView, Player> then) {
        return ActionButton.create(Component.text(label), Component.text(tip), width(label),
                DialogAction.customClick((view, audience) -> { if (audience instanceof Player p) then.accept(view, p); }, once()));
    }

    static Component line(String s, NamedTextColor c) { return Component.text(s, c); }

    /** Lines of text, inputs, then the buttons in columns; the last button goes back (or closes). */
    static void menu(Player p, String title, List<Component> lines, List<DialogInput> inputs, List<ActionButton> buttons, Consumer<Player> back, int columns) {
        List<DialogBody> body = new ArrayList<>();
        for (Component l : lines) body.add(DialogBody.plainMessage(l, 500));
        ActionButton exit = back == null ? btn("Close", "close the menu", x -> {}) : btn("Back", "back", back);
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text(title)).canCloseWithEscape(true).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons, exit, columns)));
        p.showDialog(d);
    }

    static final int FILTER_AT = 10;

    /** A list to pick from, one button a value, the details on hover; a filter box above a list longer than 10. */
    static void pick(Player p, String title, String text, List<String> values, Function<String, String> label, Function<String, String> tip,
                     BiConsumer<Player, String> then, Consumer<Player> back, String filter) {
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<ActionButton> b = new ArrayList<>();
        List<DialogInput> in = new ArrayList<>();
        if (values.size() > FILTER_AT) {
            in.add(DialogInput.text("f", Component.text("Filter")).initial(filter == null ? "" : filter).maxLength(48).build());
            b.add(form("Filter", "show only what contains the text", (v, x) -> pick(x, title, text, values, label, tip, then, back, v.getText("f"))));
        }
        int n = 0;
        for (String v : values) {
            String l = label.apply(v);
            if (!f.isEmpty() && !l.toLowerCase(Locale.ROOT).contains(f) && !tip.apply(v).toLowerCase(Locale.ROOT).contains(f)) continue;
            b.add(btn(l, tip.apply(v), x -> then.accept(x, v)));
            n++;
        }
        List<Component> lines = new ArrayList<>();
        if (!text.isEmpty()) lines.add(line(text, NamedTextColor.GRAY));
        if (n == 0) lines.add(line("Nothing matches: change the filter.", NamedTextColor.RED));
        menu(p, title, lines, in, b, back, 2);
    }

    static DialogInput text(String key, String label, String initial, int max) {
        return DialogInput.text(key, Component.text(label)).initial(initial == null ? "" : initial).maxLength(max).width(300).build();
    }

    static DialogInput slider(String key, String label, float min, float max, float step, float initial) {
        return DialogInput.numberRange(key, Component.text(label), min, max).step(step).initial(initial).width(300).build();
    }

    static DialogInput tick(String key, String label, boolean initial) {
        return DialogInput.bool(key, Component.text(label)).initial(initial).build();
    }

    static int num(DialogResponseView v, String key, int dflt) {
        Float f = v.getFloat(key);
        return f == null ? dflt : Math.round(f);
    }

    static boolean on(DialogResponseView v, String key) {
        return Boolean.TRUE.equals(v.getBoolean(key));
    }

    static String str(DialogResponseView v, String key) {
        String s = v.getText(key);
        return s == null ? "" : s.trim();
    }
}
