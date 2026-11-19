package cz.lollywood.townbuilder.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Subset matching both ways (Arag's examples need both): an entry fits when its tags are all among the command's
 * ({@code fences.json}'s {@code [city, empire]} serves {@code city,empire,residential}) or the command's are all among its
 * (a module tagged {@code city, empire, residential, timber} serves {@code city,empire,residential}). The closest fit wins:
 * the most shared tags, then the fewest extra ones.
 */
public final class TagMatcher {
    private TagMatcher() {}

    public static Set<String> parse(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static Set<String> own(Defs.Tagged e) {
        return e.tags().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public static boolean matches(Defs.Tagged entry, Set<String> requested) {
        Set<String> own = own(entry);
        if (own.isEmpty()) return false;
        return own.containsAll(requested) || requested.containsAll(own);
    }

    public static <T extends Defs.Tagged> List<T> all(List<T> entries, Set<String> requested) {
        List<T> out = new ArrayList<>();
        for (T e : entries) if (matches(e, requested)) out.add(e);
        return out;
    }

    /** The closest fit; ties broken at random. */
    public static <T extends Defs.Tagged> T best(List<T> entries, Set<String> requested, Random rnd) {
        List<T> m = all(entries, requested);
        if (m.isEmpty()) return null;
        int bestShared = -1, bestExtra = Integer.MAX_VALUE;
        List<T> top = new ArrayList<>();
        for (T e : m) {
            Set<String> own = own(e);
            Set<String> shared = new HashSet<>(own);
            shared.retainAll(requested);
            int s = shared.size(), x = own.size() - s;
            if (s > bestShared || (s == bestShared && x < bestExtra)) { bestShared = s; bestExtra = x; top.clear(); }
            if (s == bestShared && x == bestExtra) top.add(e);
        }
        return top.get(rnd.nextInt(top.size()));
    }

    public static <T> T weighted(List<T> items, java.util.function.ToIntFunction<T> weight, Random rnd) {
        int total = 0;
        for (T t : items) total += Math.max(0, weight.applyAsInt(t));
        if (total <= 0) return items.isEmpty() ? null : items.get(rnd.nextInt(items.size()));
        int r = rnd.nextInt(total);
        for (T t : items) {
            r -= Math.max(0, weight.applyAsInt(t));
            if (r < 0) return t;
        }
        return items.get(items.size() - 1);
    }
}
