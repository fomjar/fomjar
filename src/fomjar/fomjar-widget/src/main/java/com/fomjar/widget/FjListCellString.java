package com.fomjar.widget;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;

public class FjListCellString extends FjListCell<String> {

    private static final long serialVersionUID = 615786048674394192L;

    public FjListCellString(String major) {
        this(major, null);
    }

    public FjListCellString(String major, String minor) {
        super(null != minor ? (major + minor) : major);
        setLayout(new BorderLayout());
        add(new JLabel(major), BorderLayout.CENTER);
        if (null != minor) add(new JLabel(minor), BorderLayout.EAST);
    }
    
    @Override
    public void setForeground(Color color) {
        for (Component c : getComponents()) c.setForeground(color);
    }
}
