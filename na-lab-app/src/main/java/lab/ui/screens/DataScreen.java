package lab.ui.screens;

import com.raylib.Canvas;
import com.raylib.Color;

import lab.ui.AppContext;
import lab.ui.Screen;
import lab.ui.Theme;
import lab.ui.miniviz.MiniViz;
import lab.ui.miniviz.PointSet;

/** Stub data inspector. CSV list on the left; sample rows in the middle; mini-viz on the right. */
public final class DataScreen implements Screen {

    private static final String[] DATASETS = { "xor.csv", "spiral.csv", "make_moons.csv" };
    private static final String[] COLUMNS  = { "x1: scalar", "x2: scalar", "label: scalar" };

    /** Sample rows hardcoded — three labels, plotted as two colored point sets. */
    private static final float[][] SAMPLES = {
            { 0.00f, 0.00f, 0 },
            { 0.00f, 1.00f, 1 },
            { 1.00f, 0.00f, 1 },
            { 1.00f, 1.00f, 0 },
            { 0.50f, 0.42f, 0 },
            { 0.71f, 0.18f, 1 },
    };

    private static final Color LABEL_0 = new Color(40, 110, 200, 255);
    private static final Color LABEL_1 = new Color(220, 70, 70, 255);

    private int selectedDataset;

    private MiniViz scatter;

    @Override public String id()    { return "screen.data"; }
    @Override public String title() { return "Data"; }

    @Override
    public void onEnter(AppContext ctx) {
        // Two PointSets — one per label class — share the scatter viz.
        scatter = new MiniViz(0, 0, 0, 0, "x1 vs x2 (scatter)")
                .worldRect(-0.2f, -0.2f, 1.2f, 1.2f);

        scatter.add(new PointSet(buildPoints(0f), pointCount(0f), LABEL_0).size(5f));
        scatter.add(new PointSet(buildPoints(1f), pointCount(1f), LABEL_1).size(5f));
    }

    @Override
    public void onExit(AppContext ctx) {
        if (scatter != null) {
            scatter.close();
            scatter = null;
        }
    }

    @Override
    public void render(AppContext ctx, Canvas c) {
        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW, h = ctx.contentH;

        int sidebarW = 200;
        int vizW     = 320;
        int gap      = 8;
        int tableX   = x + sidebarW + gap;
        int tableW   = w - sidebarW - vizW - gap * 2;

        // Sidebar — datasets list.
        c.fillRect(x, y, sidebarW, h, Theme.PANEL);
        c.drawRect(x, y, sidebarW, h, Theme.FRAME);
        ctx.font.draw("Datasets", x + 12, y + 8, Theme.INK);
        for (int i = 0; i < DATASETS.length; i++) {
            int row = y + 36 + i * 22;
            if (i == selectedDataset) {
                c.fillRect(x + 8, row - 2, sidebarW - 16, 20, Theme.ACCENT_SOFT);
            }
            ctx.font.draw(DATASETS[i], x + 14, row, Theme.INK);
            if (ctx.window.isMouseButtonPressed(com.raylib.MouseButtons.LEFT)
                    && ctx.mouseX >= x + 8 && ctx.mouseX <= x + sidebarW - 8
                    && ctx.mouseY >= row - 2 && ctx.mouseY <= row + 18) {
                selectedDataset = i;
            }
        }

        // Table area — header + sample rows.
        c.fillRect(tableX, y, tableW, h, Theme.SURFACE);
        c.drawRect(tableX, y, tableW, h, Theme.FRAME);
        ctx.font.draw("data/" + DATASETS[selectedDataset],
                tableX + 12, y + 8, Theme.INK);

        int colW = tableW / COLUMNS.length;
        int headerY = y + 40;
        c.fillRect(tableX + 1, headerY, tableW - 2, 24, Theme.PANEL);
        c.drawLine(tableX, headerY + 24, tableX + tableW, headerY + 24, Theme.FRAME);
        for (int i = 0; i < COLUMNS.length; i++) {
            ctx.fontSmall.draw(COLUMNS[i], tableX + 12 + i * colW, headerY + 5, Theme.INK_DIM);
        }
        for (int r = 0; r < SAMPLES.length; r++) {
            int rowY = headerY + 30 + r * 22;
            if ((r & 1) == 1) {
                c.fillRect(tableX + 1, rowY - 2, tableW - 2, 22, Theme.PANEL);
            }
            ctx.font.draw(String.format("%.2f", SAMPLES[r][0]),
                    tableX + 12, rowY, Theme.INK);
            ctx.font.draw(String.format("%.2f", SAMPLES[r][1]),
                    tableX + 12 + colW, rowY, Theme.INK);
            ctx.font.draw(String.valueOf((int) SAMPLES[r][2]),
                    tableX + 12 + colW * 2, rowY, Theme.INK);
        }
        ctx.fontSmall.draw("…  (6 of 6 rows shown — `xor.csv` is a tiny dataset)",
                tableX + 12, y + h - 26, Theme.INK_DIM);

        // Mini-viz — colored scatter of the sample points.
        scatter.rect(x + w - vizW, y, vizW, 280).render(ctx, c);

        // Subset / filter editor stub below.
        int subY = y + 290;
        c.fillRect(x + w - vizW, subY, vizW, h - (subY - y), Theme.PANEL);
        c.drawRect(x + w - vizW, subY, vizW, h - (subY - y), Theme.FRAME);
        ctx.font.draw("Subsets", x + w - vizW + 12, subY + 8, Theme.INK);
        String[] subsets = {
                "all       (6 rows)",
                "train     (4 rows)",
                "test      (2 rows)"
        };
        for (int i = 0; i < subsets.length; i++) {
            ctx.fontSmall.draw(subsets[i], x + w - vizW + 16, subY + 32 + i * 18, Theme.INK_DIM);
        }
    }

    private static float[] buildPoints(float forLabel) {
        int n = pointCount(forLabel);
        float[] out = new float[n * 2];
        int j = 0;
        for (float[] s : SAMPLES) {
            if (s[2] == forLabel) {
                out[j++] = s[0];
                out[j++] = s[1];
            }
        }
        return out;
    }

    private static int pointCount(float forLabel) {
        int n = 0;
        for (float[] s : SAMPLES) if (s[2] == forLabel) n++;
        return n;
    }
}
