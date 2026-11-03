package org.polyfrost.playback.ui;

import java.util.ArrayList;
import java.util.List;

public final class UiLayout {

    public static final int START = 0;
    public static final int CENTER = 1;
    public static final int END = 2;
    public static final int STRETCH = 3;

    public static final int STRETCHABLE = 1;
    public static final int FOLLOW_MOUSE = 2;

    public static final class Node {
        public final int flags;
        public final List<Node> children = new ArrayList<>();
        public boolean bounded;
        public float x0, y0, x1, y1;
        public float tx0, ty0, tx1, ty1;
        public float dx, dy;

        public Node(int flags) {
            this.flags = flags;
        }

        public Node(int flags, float x0, float y0, float x1, float y1) {
            this(flags);
            include(x0, y0, x1, y1);
        }

        public void include(float x0, float y0, float x1, float y1) {
            if (!this.bounded) {
                this.bounded = true;
                this.x0 = Math.min(x0, x1);
                this.y0 = Math.min(y0, y1);
                this.x1 = Math.max(x0, x1);
                this.y1 = Math.max(y0, y1);
                return;
            }
            this.x0 = Math.min(this.x0, Math.min(x0, x1));
            this.y0 = Math.min(this.y0, Math.min(y0, y1));
            this.x1 = Math.max(this.x1, Math.max(x0, x1));
            this.y1 = Math.max(this.y1, Math.max(y0, y1));
        }

        public Node add(Node child) {
            this.children.add(child);
            return child;
        }

        public void close() {
            for (Node child : this.children) {
                if (child.bounded) {
                    include(child.x0, child.y0, child.x1, child.y1);
                }
            }
        }

        public boolean stretchedX() {
            return this.tx1 - this.tx0 != this.x1 - this.x0;
        }

        public boolean stretchedY() {
            return this.ty1 - this.ty0 != this.y1 - this.y0;
        }

        boolean contains(float x, float y) {
            return this.bounded && x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    public static Node root(float width, float height) {
        return new Node(STRETCHABLE, 0.0f, 0.0f, width, height);
    }

    public static float fitScale(float sourceWidth, float sourceHeight, float targetWidth, float targetHeight) {
        return Math.min(targetWidth / sourceWidth, targetHeight / sourceHeight);
    }

    public static int anchor(float c0, float c1, float p0, float p1, boolean stretchable) {
        float size = p1 - p0;
        float touch = Math.max(2.0f, size * 0.02f);
        float start = c0 - p0;
        float end = p1 - c1;
        boolean touchStart = start <= touch;
        boolean touchEnd = end <= touch;
        if (touchStart && touchEnd) {
            return stretchable ? STRETCH : CENTER;
        }
        if (touchStart) {
            return START;
        }
        if (touchEnd) {
            return END;
        }
        float mid = (p0 + p1) / 2.0f;
        float center = c0 <= mid && mid <= c1 ? 0.0f : Math.min(Math.abs(c0 - mid), Math.abs(c1 - mid));
        if (center <= start && center <= end) {
            return CENTER;
        }
        return start <= end ? START : END;
    }

    private static float place(int anchor, float c0, float p0, float p1, float t0, float t1) {
        return switch (anchor) {
            case CENTER -> (t0 + t1) / 2.0f + (c0 - (p0 + p1) / 2.0f);
            case END -> t1 - (p1 - c0);
            default -> t0 + (c0 - p0);
        };
    }

    public static float[] layout(Node root, float targetWidth, float targetHeight, float mouseX, float mouseY) {
        root.close();
        root.tx0 = 0.0f;
        root.ty0 = 0.0f;
        root.tx1 = targetWidth;
        root.ty1 = targetHeight;
        root.dx = root.tx0 - root.x0;
        root.dy = root.ty0 - root.y0;
        layoutChildren(root);
        Node over = deepest(root, mouseX, mouseY);
        float[] mouse = point(over, mouseX, mouseY);
        followMouse(root, mouse[0] - mouseX, mouse[1] - mouseY);
        return mouse;
    }

    private static void layoutChildren(Node parent) {
        for (Node child : parent.children) {
            if ((child.flags & FOLLOW_MOUSE) == 0) {
                place(parent, child);
                layoutChildren(child);
            }
        }
    }

    private static void place(Node parent, Node child) {
        if (!child.bounded) {
            child.x0 = parent.x0;
            child.y0 = parent.y0;
            child.x1 = parent.x1;
            child.y1 = parent.y1;
            child.tx0 = parent.tx0;
            child.ty0 = parent.ty0;
            child.tx1 = parent.tx1;
            child.ty1 = parent.ty1;
            child.dx = parent.dx;
            child.dy = parent.dy;
            return;
        }
        boolean stretchable = (child.flags & STRETCHABLE) != 0;
        int ax = anchor(child.x0, child.x1, parent.x0, parent.x1, stretchable);
        int ay = anchor(child.y0, child.y1, parent.y0, parent.y1, stretchable);
        if (ax == STRETCH) {
            child.tx0 = parent.tx0 + (child.x0 - parent.x0);
            child.tx1 = parent.tx1 - (parent.x1 - child.x1);
        } else {
            child.tx0 = place(ax, child.x0, parent.x0, parent.x1, parent.tx0, parent.tx1);
            child.tx1 = child.tx0 + (child.x1 - child.x0);
        }
        if (ay == STRETCH) {
            child.ty0 = parent.ty0 + (child.y0 - parent.y0);
            child.ty1 = parent.ty1 - (parent.y1 - child.y1);
        } else {
            child.ty0 = place(ay, child.y0, parent.y0, parent.y1, parent.ty0, parent.ty1);
            child.ty1 = child.ty0 + (child.y1 - child.y0);
        }
        child.dx = child.tx0 - child.x0;
        child.dy = child.ty0 - child.y0;
    }

    private static Node deepest(Node node, float x, float y) {
        for (int i = node.children.size() - 1; i >= 0; i--) {
            Node child = node.children.get(i);
            if ((child.flags & FOLLOW_MOUSE) == 0 && child.contains(x, y)) {
                return deepest(child, x, y);
            }
        }
        return node;
    }

    private static float[] point(Node node, float x, float y) {
        int ax = anchor(x, x, node.x0, node.x1, false);
        int ay = anchor(y, y, node.y0, node.y1, false);
        return new float[]{place(ax, x, node.x0, node.x1, node.tx0, node.tx1), place(ay, y, node.y0, node.y1, node.ty0, node.ty1)};
    }

    private static void followMouse(Node node, float dx, float dy) {
        for (Node child : node.children) {
            if ((child.flags & FOLLOW_MOUSE) != 0) {
                shift(child, dx, dy);
            } else {
                followMouse(child, dx, dy);
            }
        }
    }

    private static void shift(Node node, float dx, float dy) {
        node.dx = dx;
        node.dy = dy;
        node.tx0 = node.x0 + dx;
        node.tx1 = node.x1 + dx;
        node.ty0 = node.y0 + dy;
        node.ty1 = node.y1 + dy;
        for (Node child : node.children) {
            shift(child, dx, dy);
        }
    }

}
