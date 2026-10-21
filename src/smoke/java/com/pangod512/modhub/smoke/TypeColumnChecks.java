package com.pangod512.modhub.smoke;

import com.pangod512.modhub.core.*;

import com.pangod512.modhub.client.ModHubScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.resources.ResourceLocation;

/** Checks the actual row drawing commands, including the selected row's border. */
final class TypeColumnChecks {
    private record Rect(int x, int y, int width, int height, int color) {
        boolean contains(Rect other) {
            return other.x >= x && other.y >= y && other.x + other.width <= x + width
                    && other.y + other.height <= y + height;
        }
    }
    private record Text(String value, int x, int y, int width, int color) {}

    static List<String> verify(ModHubScreen screen, AbstractSelectionList<?> table) throws Exception {
        var results = new ArrayList<String>();
        var graphics = new RecordingGraphics();
        var itemHeight = AbstractSelectionList.class.getDeclaredField("itemHeight");
        itemHeight.setAccessible(true);
        // AbstractSelectionList passes itemHeight minus its four-pixel row spacing to entries.
        int rowHeight = itemHeight.getInt(table) - 4;
        Object entry = table.getSelected();
        if (entry == null) throw new AssertionError("Falta una fila para comprobar Tipo");
        var render = entry.getClass().getDeclaredMethod("render", GuiGraphics.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, boolean.class, float.class);
        render.setAccessible(true);
        render.invoke(entry, graphics, 0, 110, table.getRowLeft(), table.getRowWidth(), rowHeight, 0, 0, false, 0F);
        Rect row = new Rect(table.getRowLeft(), 110, table.getRowWidth(), rowHeight - 1, 0);
        Rect badge = graphics.outlines.stream().filter(rect -> (rect.color >>> 24) == 0x60)
                .findFirst().orElseThrow(() -> new AssertionError("Falta la etiqueta de Tipo"));
        require(row.contains(badge), "La etiqueta Tipo queda dentro de la fila " + screen.width, results);
        var nativeSelection = AbstractSelectionList.class.getDeclaredField("renderSelection");
        nativeSelection.setAccessible(true);
        if (nativeSelection.getBoolean(table)) {
            int firstFill = graphics.fills.size();
            var selection = AbstractSelectionList.class.getDeclaredMethod("renderSelection", GuiGraphics.class,
                    int.class, int.class, int.class, int.class, int.class);
            selection.setAccessible(true);
            selection.invoke(table, graphics, 110, table.getRowWidth(), rowHeight, -1, 0xFF000000);
            require(graphics.fills.get(firstFill).contains(badge), "Tipo queda dentro de la seleccion nativa " + screen.width, results);
        }
        require(Math.abs((badge.y * 2 + badge.height) - (row.y * 2 + row.height)) <= 1,
                "La etiqueta Tipo queda centrada verticalmente " + screen.width, results);
        for (Text text : graphics.texts) {
            if (text.color == 0xFFEEEFEA || text.color == 0xFFB3BAB8) {
                require(text.x + text.width <= badge.x - 4,
                        "El nombre y los datos no invaden Tipo " + screen.width, results);
            }
        }
        graphics.texts.clear();
        var header = ModHubScreen.class.getDeclaredMethod("drawTableHeader", GuiGraphics.class);
        header.setAccessible(true);
        header.invoke(screen, graphics);
        Text title = graphics.texts.stream().filter(text -> text.value.equals("Tipo")).findFirst().orElseThrow();
        require(title.x == badge.x + 4, "El encabezado Tipo se alinea con sus etiquetas " + screen.width, results);
        return results;
    }

    static List<String> verifyCounter(ModHubScreen screen, boolean expected) throws Exception {
        var graphics = new RecordingGraphics();
        var method = ModHubScreen.class.getDeclaredMethod("drawResultCount", GuiGraphics.class);
        method.setAccessible(true); method.invoke(screen, graphics);
        var results = new ArrayList<String>();
        require(graphics.texts.size() == (expected ? 1 : 0), "Contador contextual " + (expected ? "visible" : "oculto"), results);
        require(screen.getTitle().getString().equals("Mod Hub"), "El titulo interno conserva el nombre para accesibilidad", results);
        return results;
    }

    private static void require(boolean condition, String message, List<String> results) {
        if (!condition) throw new AssertionError(message);
        results.add("OK: " + message);
    }

    private static final class RecordingGraphics extends GuiGraphics {
        final List<Rect> fills = new ArrayList<>();
        final List<Rect> outlines = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();

        RecordingGraphics() {
            super(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            fills.add(new Rect(x1, y1, x2 - x1, y2 - y1, color));
        }
        @Override public void renderOutline(int x, int y, int width, int height, int color) {
            outlines.add(new Rect(x, y, width, height, color));
        }
        @Override public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
            int width = font.width(value);
            texts.add(new Text(value, x, y, width, color));
            return x + width;
        }
        @Override public void drawCenteredString(Font font, String value, int x, int y, int color) {}
        @Override public void blit(ResourceLocation texture, int x, int y, int width, int height,
                float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {}
    }
}
