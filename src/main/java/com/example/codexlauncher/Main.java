package com.example.codexlauncher;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                CodexLauncherFrame frame = new CodexLauncherFrame();
                frame.setVisible(true);
            }
        });
    }
}
