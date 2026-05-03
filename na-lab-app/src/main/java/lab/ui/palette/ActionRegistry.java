package lab.ui.palette;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Process-wide registry of palette actions. New screens, node kinds, etc. register at startup.
 * Order is registration order; the palette resorts by score per query.
 */
public final class ActionRegistry {

    private final List<Action> actions = new ArrayList<>();

    public void register(Action a) {
        actions.add(a);
    }

    /** Remove every action matching {@code pred}. Used when bulk-resyncing DSL-derived actions. */
    public void removeIf(Predicate<Action> pred) {
        actions.removeIf(pred);
    }

    public List<Action> all() {
        return actions;
    }

    /**
     * Substring filter, falling back to subsequence matching with gap penalty. Returns a fresh
     * sorted list. Empty / null query returns everything in registration order.
     */
    public List<Action> search(String query) {
        if (query == null || query.isEmpty()) return new ArrayList<>(actions);
        String q = query.toLowerCase();
        record Scored(Action a, int score) {}
        List<Scored> hits = new ArrayList<>();
        for (Action a : actions) {
            int s = score(a.label.toLowerCase(), q);
            if (s == Integer.MIN_VALUE) {
                // Subsequence fallback over label only — keeps results explainable.
                int sub = subsequenceScore(a.label.toLowerCase(), q);
                if (sub == Integer.MIN_VALUE) continue;
                s = sub;
            }
            hits.add(new Scored(a, s));
        }
        hits.sort((x, y) -> Integer.compare(y.score, x.score));
        List<Action> out = new ArrayList<>(hits.size());
        for (Scored sc : hits) out.add(sc.a);
        return out;
    }

    private static int score(String label, String q) {
        int idx = label.indexOf(q);
        if (idx < 0) return Integer.MIN_VALUE;
        // Earlier-in-string = higher score; exact prefix = bonus.
        int base = 1000 - idx;
        if (idx == 0) base += 200;
        return base;
    }

    /** Score by character-positions-in-order with a penalty per gap. MIN_VALUE if not a subseq. */
    private static int subsequenceScore(String label, String q) {
        int li = 0, qi = 0, gap = 0, lastMatch = -1;
        while (li < label.length() && qi < q.length()) {
            if (label.charAt(li) == q.charAt(qi)) {
                if (lastMatch >= 0) gap += (li - lastMatch - 1);
                lastMatch = li;
                qi++;
            }
            li++;
        }
        if (qi < q.length()) return Integer.MIN_VALUE;
        return 500 - gap * 4 - lastMatch;
    }
}
