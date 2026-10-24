package org.jabref.gui.icon;

import javafx.scene.Node;
import javafx.scene.paint.Color;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SvgIcon implements JabRefIcon {

    private static final int DEFAULT_SIZE = 24;

    private final String name;
    private final String svgPath;
    private final int size;
    private final @Nullable Color color;
    private final boolean explicitSize;

    public SvgIcon(String name, String svgPath) {
        this(name, svgPath, DEFAULT_SIZE, null, false);
    }

    private SvgIcon(String name, String svgPath, int size, @Nullable Color color, boolean explicitSize) {
        this.name = name;
        this.svgPath = svgPath;
        this.size = size;
        this.color = color;
        this.explicitSize = explicitSize;
    }

    @Override
    public Node getGraphicNode() {
        JabRefSvgIcon node = new JabRefSvgIcon(svgPath, size);
        // BUG FIX: SVG and font-backed icons share the same adaptive sizing rule.
        if (!explicitSize) {
            node.getStyleClass().add("inherited-icon-size");
        }
        if (color != null) {
            node.setColor(color);
        }
        return node;
    }

    @Override
    public boolean matches(Node graphicNode) {
        return (graphicNode instanceof JabRefSvgIcon svgIcon) && svgIcon.getPath().equals(svgPath);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public JabRefIcon withColor(Color color) {
        return new SvgIcon(name, svgPath, size, color, explicitSize);
    }

    @Override
    public JabRefIcon withSize(int size) {
        return new SvgIcon(name, svgPath, size, color, true);
    }

    @Override
    public JabRefIcon disabled() {
        return withColor(IconTheme.DEFAULT_DISABLED_COLOR);
    }
}
