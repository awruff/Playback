package org.polyfrost.playback.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiLayoutTest {

    private static final float W = 640.0f;
    private static final float H = 360.0f;
    private static final float WIDE = 860.0f;

    @Test
    void anchorsFollowNearestEdgeOrCentre() {
        UiLayout.Node root = UiLayout.root(W, H);
        UiLayout.Node hotbar = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 229, 338, 411, 360));
        UiLayout.Node sidebar = root.add(new UiLayout.Node(0, 540, 120, 638, 240));
        UiLayout.Node chat = root.add(new UiLayout.Node(0, 2, 200, 330, 340));
        UiLayout.Node leftButton = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 165, 100, 315, 120));
        UiLayout.Node background = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 0, 0, W, H));
        UiLayout.layout(root, WIDE, H, 0, 0);

        assertEquals(110.0f, hotbar.dx);
        assertEquals(220.0f, sidebar.dx);
        assertEquals(0.0f, chat.dx);
        assertEquals(110.0f, leftButton.dx);
        assertEquals(0.0f, background.tx0);
        assertEquals(WIDE, background.tx1);
        assertEquals(0.0f, hotbar.dy);
    }

    @Test
    void childrenOfStretchedGroupReanchorInsideIt() {
        UiLayout.Node root = UiLayout.root(W, H);
        UiLayout.Node list = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 0, 32, W, 300));
        UiLayout.Node row = list.add(new UiLayout.Node(0, 210, 40, 430, 60));
        UiLayout.Node scrollbar = list.add(new UiLayout.Node(UiLayout.STRETCHABLE, 630, 32, 636, 80));
        UiLayout.layout(root, WIDE, H, 0, 0);

        assertEquals(WIDE, list.tx1);
        assertEquals(110.0f, row.dx);
        assertEquals(220.0f, scrollbar.dx);
    }

    @Test
    void cursorAndTooltipStayOnHoveredWidget() {
        UiLayout.Node root = UiLayout.root(W, H);
        UiLayout.Node button = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 220, 100, 420, 120));
        UiLayout.Node tooltip = root.add(new UiLayout.Node(UiLayout.FOLLOW_MOUSE, 312, 90, 400, 110));
        UiLayout.Node line = tooltip.add(new UiLayout.Node(0, 316, 94, 380, 103));
        float[] mouse = UiLayout.layout(root, WIDE, H, 300, 110);

        assertEquals(410.0f, mouse[0]);
        assertEquals(110.0f, mouse[1]);
        assertEquals(button.dx, tooltip.dx);
        assertEquals(button.dx, line.dx);
    }

    @Test
    void sameSizeIsIdentity() {
        UiLayout.Node root = UiLayout.root(W, H);
        UiLayout.Node a = root.add(new UiLayout.Node(0, 10, 10, 50, 20));
        UiLayout.Node b = root.add(new UiLayout.Node(UiLayout.STRETCHABLE, 0, 0, W, 20));
        UiLayout.Node marker = root.add(new UiLayout.Node(0));
        UiLayout.layout(root, W, H, 0, 0);

        assertEquals(0.0f, a.dx);
        assertEquals(0.0f, b.dx);
        assertEquals(W, b.tx1);
        assertEquals(0.0f, marker.dx);
    }

    @Test
    void tallerTargetMovesBottomAnchoredDown() {
        UiLayout.Node root = UiLayout.root(W, H);
        UiLayout.Node hotbar = root.add(new UiLayout.Node(0, 229, 338, 411, 360));
        UiLayout.Node title = root.add(new UiLayout.Node(0, 280, 20, 360, 29));
        UiLayout.layout(root, W, 480, 0, 0);

        assertEquals(120.0f, hotbar.dy);
        assertEquals(0.0f, title.dy);
        assertEquals(0.0f, hotbar.dx);
    }

}
