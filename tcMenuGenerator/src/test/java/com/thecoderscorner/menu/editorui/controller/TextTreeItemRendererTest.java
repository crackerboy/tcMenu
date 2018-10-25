package com.thecoderscorner.menu.editorui.controller;

import com.thecoderscorner.menu.domain.state.MenuTree;
import com.thecoderscorner.menu.editorui.util.TestUtils;
import org.junit.jupiter.api.Test;

class TextTreeItemRendererTest {

    @Test
    void testRenderingOfComplexTree() {
        MenuTree tree = TestUtils.buildCompleteTree();
        TextTreeItemRenderer renderer = new TextTreeItemRenderer(tree);
        String actual = renderer.getTreeAsText();

        TestUtils.assertEqualsIgnoringCRLF(
                " Extra                    test\n" +
                " test                    100dB\n" +
                " sub                       >>>\n" +
                "   test                  100dB\n" +
                " BoolTest                   ON\n" +
                " BoolTest           AAAAAAAAAA\n" +
                " BoolTest          -12345.1235\n" +
                " BoolTest              No Link\n" +
                " BoolTest                    \n",
                actual);
    }
}