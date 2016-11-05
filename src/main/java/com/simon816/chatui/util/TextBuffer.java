package com.simon816.chatui.util;

import com.google.common.collect.Lists;
import org.spongepowered.api.text.Text;

import java.util.ArrayList;

public class TextBuffer {

    private final ArrayList<Text> buffer = Lists.newArrayList();
    private int width;

    private final boolean forceUnicode;

    public TextBuffer(boolean forceUnicode) {
        this.forceUnicode = forceUnicode;
    }

    public void append(Text text) {
        this.buffer.add(text);
        this.width += TextUtils.getWidth(text, this.forceUnicode);
    }

    public int getWidth() {
        return this.width;
    }

    public ArrayList<Text> getContents() {
        return this.buffer;
    }

    public void clear() {
        this.buffer.clear();
        this.width = 0;
    }
}
