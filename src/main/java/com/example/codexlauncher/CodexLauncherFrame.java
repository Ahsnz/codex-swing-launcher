package com.example.codexlauncher;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodexLauncherFrame extends JFrame {
    private static final Color BACKGROUND = new Color(245, 247, 250);
    private static final Color PANEL_BACKGROUND = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(218, 224, 232);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color PRIMARY_DARK = new Color(29, 78, 216);
    private static final Color SOFT_BLUE = new Color(239, 246, 255);
    private static final Color TEXT_DARK = new Color(31, 41, 55);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final int MAX_GUIDELINE_PROMPT_CHARS = 30000;
    private static final int MAX_SKILL_PREVIEW_CHARS = 4000;

    private final JTextField projectDirField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JTextField dataHomeField = new JTextField();
    private final JTextField guidelineMdField = new JTextField();
    private final JTextArea conversationArea = new JTextArea();
    private final JTextArea promptArea = new JTextArea();
    private final JTextArea guidelinePreviewArea = new JTextArea();
    private final JTextArea skillDetailArea = new JTextArea();
    private final DefaultListModel<SessionEntry> historyModel = new DefaultListModel<SessionEntry>();
    private final JList<SessionEntry> historyList = new JList<SessionEntry>(historyModel);
    private final DefaultListModel<SkillEntry> skillModel = new DefaultListModel<SkillEntry>();
    private final JList<SkillEntry> skillList = new JList<SkillEntry>(skillModel);

    private final JButton sendButton = new JButton("\u53d1\u9001");
    private final JButton stopButton = new JButton("\u505c\u6b62");
    private final JButton newChatButton = new JButton("\u65b0\u5bf9\u8bdd");
    private final JButton clearButton = new JButton("\u6e05\u7a7a\u9875\u9762");
    private final JButton refreshHistoryButton = new JButton("\u5237\u65b0\u5386\u53f2");
    private final JButton chooseProjectButton = new JButton("\u6d4f\u89c8");
    private final JButton chooseConfigButton = new JButton("\u6d4f\u89c8");
    private final JButton chooseDataButton = new JButton("\u6d4f\u89c8");
    private final JButton chooseGuidelineButton = new JButton("\u9009\u62e9 MD");
    private final JButton clearGuidelineButton = new JButton("\u6e05\u9664");
    private final JButton refreshSkillsButton = new JButton("\u5237\u65b0 Skills");
    private final JCheckBox saveSessionCheckBox = new JCheckBox("\u4fdd\u5b58 Codex \u4f1a\u8bdd", true);
    private final JCheckBox autoApproveCheckBox = new JCheckBox("\u81ea\u52a8\u6267\u884c\uff0c\u4e0d\u518d\u8be2\u95ee\u786e\u8ba4", false);
    private final JLabel statusLabel = new JLabel("\u5c31\u7eea");

    private String activeSessionId = "";
    private File activeDataHome;
    private Process runningProcess;
    private SwingWorker<Integer, String> runningWorker;

    public CodexLauncherFrame() {
        setTitle("Codex Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 780));
        setLocationRelativeTo(null);
        applyLookAndFeel();
        initComponents();
        reloadHistory();
        reloadSkills();
    }

    private void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(16, 16, 12, 16));
        setContentPane(root);

        root.add(createHeaderPanel(), BorderLayout.NORTH);
        root.add(createContentPanel(), BorderLayout.CENTER);
        root.add(createStatusPanel(), BorderLayout.SOUTH);

        chooseProjectButton.addActionListener(e -> chooseDirectory(projectDirField));
        chooseConfigButton.addActionListener(e -> chooseConfigPath());
        chooseDataButton.addActionListener(e -> chooseDirectory(dataHomeField));
        chooseGuidelineButton.addActionListener(e -> chooseGuidelineMd());
        clearGuidelineButton.addActionListener(e -> clearGuidelineMd());
        clearButton.addActionListener(e -> conversationArea.setText(""));
        newChatButton.addActionListener(e -> startNewChat());
        refreshHistoryButton.addActionListener(e -> reloadHistory());
        refreshSkillsButton.addActionListener(e -> reloadSkills());
        sendButton.addActionListener(e -> sendPrompt());
        stopButton.addActionListener(e -> stopRunningProcess());
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedHistory();
            }
        });
        skillList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSkillDetail();
            }
        });
        applyControlStyles();
    }

    private void applyControlStyles() {
        styleSecondaryButton(chooseProjectButton);
        styleSecondaryButton(chooseConfigButton);
        styleSecondaryButton(chooseDataButton);
        styleSecondaryButton(chooseGuidelineButton);
        styleSecondaryButton(clearGuidelineButton);
        styleSecondaryButton(refreshHistoryButton);
        styleSecondaryButton(refreshSkillsButton);

        styleTextField(projectDirField);
        styleTextField(configPathField);
        styleTextField(dataHomeField);
        styleTextField(guidelineMdField);
    }

    private JPanel createHeaderPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setOpaque(false);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("Codex Launcher");
        title.setForeground(TEXT_DARK);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        JLabel subtitle = new JLabel("\u9879\u76ee\u5f00\u53d1\u63a7\u5236\u53f0");
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        titlePanel.add(title, BorderLayout.WEST);
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        wrapper.add(titlePanel, BorderLayout.NORTH);
        wrapper.add(createSettingsPanel(), BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = createCardPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addSettingsRow(panel, gbc, 0, "\u9879\u76ee\u76ee\u5f55", projectDirField, chooseProjectButton);
        projectDirField.setText(System.getProperty("user.dir"));

        addSettingsRow(panel, gbc, 1, "config.toml", configPathField, chooseConfigButton);
        configPathField.setText(defaultConfigPath());

        addSettingsRow(panel, gbc, 2, "Codex \u6570\u636e\u76ee\u5f55", dataHomeField, chooseDataButton);
        dataHomeField.setText(defaultDataHome());

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        saveSessionCheckBox.setOpaque(false);
        saveSessionCheckBox.setForeground(TEXT_DARK);
        panel.add(saveSessionCheckBox, gbc);

        gbc.gridy = 4;
        autoApproveCheckBox.setOpaque(false);
        autoApproveCheckBox.setForeground(TEXT_DARK);
        autoApproveCheckBox.setToolTipText("\u4f1a\u4f7f\u7528 Codex CLI \u7684 --dangerously-bypass-approvals-and-sandbox\uff0c\u53ea\u5efa\u8bae\u5728\u4f60\u4fe1\u4efb\u7684\u9879\u76ee\u76ee\u5f55\u4e2d\u5f00\u542f\u3002");
        panel.add(autoApproveCheckBox, gbc);
        gbc.gridwidth = 1;

        return panel;
    }

    private JSplitPane createContentPanel() {
        JTabbedPane sideTabs = new JTabbedPane();
        sideTabs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        sideTabs.addTab("\u5386\u53f2", createHistoryPanel());
        sideTabs.addTab("\u89c4\u8303", createGuidelinePanel());
        sideTabs.addTab("Skills", createSkillsPanel());
        sideTabs.setMinimumSize(new Dimension(300, 520));
        sideTabs.setPreferredSize(new Dimension(340, 620));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideTabs, createChatPanel());
        splitPane.setResizeWeight(0.30);
        splitPane.setDividerSize(8);
        splitPane.setBorder(null);
        return splitPane;
    }

    private JPanel createHistoryPanel() {
        JPanel historyPanel = createCardPanel(new BorderLayout(8, 8));
        historyPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel historyTitle = new JLabel("\u5386\u53f2\u5bf9\u8bdd");
        historyTitle.setForeground(TEXT_DARK);
        historyTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        historyPanel.add(historyTitle, BorderLayout.NORTH);

        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        historyList.setFixedCellHeight(30);
        historyPanel.add(new JScrollPane(historyList), BorderLayout.CENTER);

        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        historyActions.setOpaque(false);
        historyActions.add(refreshHistoryButton);
        historyPanel.add(historyActions, BorderLayout.SOUTH);
        return historyPanel;
    }

    private JPanel createGuidelinePanel() {
        JPanel panel = createCardPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setOpaque(false);
        JLabel title = new JLabel("\u89c4\u8303 MD");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        title.setForeground(TEXT_DARK);
        top.add(title, BorderLayout.NORTH);
        top.add(guidelineMdField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(clearGuidelineButton);
        buttons.add(chooseGuidelineButton);
        top.add(buttons, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        guidelinePreviewArea.setEditable(false);
        guidelinePreviewArea.setLineWrap(true);
        guidelinePreviewArea.setWrapStyleWord(true);
        guidelinePreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        guidelinePreviewArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        guidelinePreviewArea.setText("\u9009\u62e9\u4e00\u4e2a .md \u6587\u6863\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u9884\u89c8\u3002\u53d1\u9001\u65f6\u4f1a\u5c06\u8be5\u89c4\u8303\u4f5c\u4e3a\u4e0a\u4e0b\u6587\u6ce8\u5165\u7ed9 Codex\u3002");
        panel.add(new JScrollPane(guidelinePreviewArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSkillsPanel() {
        JPanel panel = createCardPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Codex Skills");
        title.setForeground(TEXT_DARK);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(title, BorderLayout.NORTH);

        skillList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        skillList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        skillList.setFixedCellHeight(34);
        JScrollPane listScroll = new JScrollPane(skillList);
        listScroll.setPreferredSize(new Dimension(240, 210));

        skillDetailArea.setEditable(false);
        skillDetailArea.setLineWrap(true);
        skillDetailArea.setWrapStyleWord(true);
        skillDetailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        skillDetailArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JSplitPane skillSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, new JScrollPane(skillDetailArea));
        skillSplit.setResizeWeight(0.45);
        skillSplit.setDividerSize(6);
        panel.add(skillSplit, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(refreshSkillsButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(8, 8));
        chatPanel.setOpaque(false);

        JPanel conversationPanel = createCardPanel(new BorderLayout(8, 8));
        conversationPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel conversationHeader = new JPanel(new BorderLayout());
        conversationHeader.setOpaque(false);
        JLabel conversationTitle = new JLabel("\u5f53\u524d\u5bf9\u8bdd");
        conversationTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        conversationTitle.setForeground(TEXT_DARK);
        conversationHeader.add(conversationTitle, BorderLayout.WEST);
        conversationPanel.add(conversationHeader, BorderLayout.NORTH);

        conversationArea.setEditable(false);
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        conversationArea.setForeground(TEXT_DARK);
        conversationArea.setBackground(Color.WHITE);
        conversationArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane conversationScroll = new JScrollPane(conversationArea);
        conversationScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        conversationPanel.add(conversationScroll, BorderLayout.CENTER);
        chatPanel.add(conversationPanel, BorderLayout.CENTER);

        JPanel inputPanel = createCardPanel(new BorderLayout(8, 8));
        inputPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel inputTitle = new JLabel("\u8f93\u5165\u4e0b\u4e00\u8f6e\u95ee\u9898");
        inputTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        inputTitle.setForeground(TEXT_MUTED);
        inputPanel.add(inputTitle, BorderLayout.NORTH);

        promptArea.setRows(4);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        promptArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        inputPanel.add(new JScrollPane(promptArea), BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        styleSecondaryButton(clearButton);
        styleSecondaryButton(newChatButton);
        styleSecondaryButton(stopButton);
        stylePrimaryButton(sendButton);
        stopButton.setEnabled(false);
        actionPanel.add(clearButton);
        actionPanel.add(newChatButton);
        actionPanel.add(stopButton);
        actionPanel.add(sendButton);
        inputPanel.add(actionPanel, BorderLayout.SOUTH);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(7, 10, 7, 10)
        ));
        statusLabel.setForeground(TEXT_DARK);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    private JPanel createCardPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        return panel;
    }

    private void addSettingsRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JTextField field, JButton button) {
        JLabel label = new JLabel(labelText);
        label.setForeground(TEXT_DARK);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        field.setPreferredSize(new Dimension(300, 30));
        panel.add(field, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        button.setPreferredSize(new Dimension(76, 30));
        panel.add(button, gbc);
    }

    private void stylePrimaryButton(JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_DARK),
                new EmptyBorder(7, 18, 7, 18)
        ));
    }

    private void styleSecondaryButton(JButton button) {
        button.setBackground(SOFT_BLUE);
        button.setForeground(PRIMARY_DARK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                new EmptyBorder(6, 12, 6, 12)
        ));
    }

    private void styleTextField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(5, 8, 5, 8)
        ));
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    private String defaultConfigPath() {
        File desktopConfig = new File(new File(System.getProperty("user.home"), "Desktop"), "config.toml");
        if (desktopConfig.isFile()) {
            return desktopConfig.getAbsolutePath();
        }
        File defaultConfig = new File(new File(System.getProperty("user.home"), ".codex"), "config.toml");
        if (defaultConfig.isFile()) {
            return defaultConfig.getAbsolutePath();
        }
        return desktopConfig.getAbsolutePath();
    }

    private String defaultDataHome() {
        return new File(new File(System.getProperty("user.home"), ".codex-launcher"), "codex-home").getAbsolutePath();
    }

    private void chooseDirectory(JTextField targetField) {
        JFileChooser chooser = new JFileChooser(targetField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseConfigPath() {
        JFileChooser chooser = new JFileChooser(configPathField.getText());
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            configPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseGuidelineMd() {
        JFileChooser chooser = new JFileChooser(projectDirField.getText());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".md")) {
                showWarning("\u8bf7\u9009\u62e9 .md \u6587\u6863\u3002");
                return;
            }
            guidelineMdField.setText(file.getAbsolutePath());
            updateGuidelinePreview();
        }
    }

    private void clearGuidelineMd() {
        guidelineMdField.setText("");
        guidelinePreviewArea.setText("\u5df2\u6e05\u9664\u89c4\u8303\u6587\u6863\u3002");
    }

    private void updateGuidelinePreview() {
        String path = guidelineMdField.getText().trim();
        if (path.length() == 0) {
            guidelinePreviewArea.setText("");
            return;
        }
        try {
            String content = readTextFile(new File(path), 8000);
            guidelinePreviewArea.setText(content);
            guidelinePreviewArea.setCaretPosition(0);
        } catch (Exception ex) {
            guidelinePreviewArea.setText("\u8bfb\u53d6\u5931\u8d25\uff1a" + ex.getMessage());
        }
    }

    private String buildPromptWithContext(String userPrompt) throws IOException {
        StringBuilder builder = new StringBuilder();
        String guideline = readSelectedGuidelineForPrompt();
        List<SkillEntry> selectedSkills = skillList.getSelectedValuesList();

        if (guideline.length() > 0 || !selectedSkills.isEmpty()) {
            builder.append("\u8bf7\u5728\u672c\u8f6e\u56de\u7b54\u548c\u4ee3\u7801\u4fee\u6539\u4e2d\u4f18\u5148\u9075\u5b88\u4ee5\u4e0b\u4e0a\u4e0b\u6587\u3002\n\n");
        }

        if (guideline.length() > 0) {
            builder.append("<project_guideline_md path=\"").append(guidelineMdField.getText().trim()).append("\">\n");
            builder.append(guideline).append("\n");
            builder.append("</project_guideline_md>\n\n");
        }

        if (!selectedSkills.isEmpty()) {
            builder.append("<selected_codex_skills>\n");
            for (SkillEntry skill : selectedSkills) {
                builder.append("- name: ").append(skill.name).append("\n");
                if (skill.description.length() > 0) {
                    builder.append("  description: ").append(skill.description).append("\n");
                }
                builder.append("  path: ").append(skill.skillFile.getAbsolutePath()).append("\n");
            }
            builder.append("</selected_codex_skills>\n\n");
            builder.append("\u5982\u679c\u4e0a\u9762\u7684 skill \u4e0e\u7528\u6237\u4efb\u52a1\u76f8\u5173\uff0c\u8bf7\u5148\u6839\u636e path \u8bfb\u53d6\u5bf9\u5e94 SKILL.md\uff0c\u518d\u6309\u8be5 skill \u7684\u5de5\u4f5c\u6d41\u7a0b\u6267\u884c\u3002\u5982\u679c\u4e0d\u76f8\u5173\uff0c\u4e0d\u8981\u8ba9 skill \u5e72\u6270\u7528\u6237\u8bf7\u6c42\u3002\n\n");
        }

        builder.append("<user_request>\n");
        builder.append(userPrompt);
        builder.append("\n</user_request>");
        return builder.toString();
    }

    private String readSelectedGuidelineForPrompt() throws IOException {
        String path = guidelineMdField.getText().trim();
        if (path.length() == 0) {
            return "";
        }
        File file = new File(path);
        if (!file.isFile()) {
            throw new IOException("\u89c4\u8303 MD \u4e0d\u5b58\u5728\u6216\u4e0d\u662f\u6587\u4ef6\u3002");
        }
        if (!file.getName().toLowerCase().endsWith(".md")) {
            throw new IOException("\u89c4\u8303\u6587\u6863\u5fc5\u987b\u662f .md \u6587\u4ef6\u3002");
        }
        return readTextFile(file, MAX_GUIDELINE_PROMPT_CHARS);
    }

    private String readTextFile(File file, int maxChars) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String content = new String(bytes, StandardCharsets.UTF_8);
        return limitText(content, maxChars);
    }

    private String limitText(String content, int maxChars) {
        if (content.length() > maxChars) {
            return content.substring(0, maxChars) + "\n\n[Codex Launcher: \u5185\u5bb9\u8f83\u957f\uff0c\u5df2\u622a\u53d6\u524d " + maxChars + " \u5b57\u7b26\u3002]";
        }
        return content;
    }

    private void startNewChat() {
        activeSessionId = "";
        conversationArea.setText("");
        promptArea.setText("");
        historyList.clearSelection();
        setStatus("\u5df2\u5f00\u59cb\u65b0\u5bf9\u8bdd");
    }

    private void sendPrompt() {
        String projectDir = projectDirField.getText().trim();
        String configPath = configPathField.getText().trim();
        String dataHome = dataHomeField.getText().trim();
        String prompt = promptArea.getText().trim();

        ValidationResult validation = validateInputs(projectDir, configPath, dataHome, prompt);
        if (!validation.valid) {
            showWarning(validation.message);
            return;
        }

        try {
            File project = new File(projectDir).getAbsoluteFile();
            File codexHome = prepareCodexHome(resolveConfigFile(configPath), new File(dataHome).getAbsoluteFile());
            activeDataHome = codexHome;
            boolean saveSession = saveSessionCheckBox.isSelected();
            boolean autoApprove = autoApproveCheckBox.isSelected();
            boolean resume = saveSession && activeSessionId.length() > 0;
            String promptWithContext = buildPromptWithContext(prompt);

            setRunningState(true);
            appendUserMessage(prompt);
            promptArea.setText("");
            setStatus(resume ? "\u6b63\u5728\u6062\u590d\u5e76\u7ee7\u7eed\u5386\u53f2\u5bf9\u8bdd..." : "\u6b63\u5728\u521b\u5efa\u65b0\u5bf9\u8bdd...");

            runningWorker = createCodexWorker(project, codexHome, promptWithContext, resume, saveSession, autoApprove);
            runningWorker.execute();
        } catch (Exception ex) {
            showWarning(ex.getMessage());
        }
    }

    private ValidationResult validateInputs(String projectDir, String configPath, String dataHome, String prompt) {
        if (projectDir.length() == 0) {
            return ValidationResult.invalid("\u8bf7\u9009\u62e9\u6216\u8f93\u5165\u9879\u76ee\u76ee\u5f55\u3002");
        }
        if (configPath.length() == 0) {
            return ValidationResult.invalid("\u8bf7\u9009\u62e9\u6216\u8f93\u5165 config.toml \u6587\u4ef6\u6216\u6240\u5728\u76ee\u5f55\u3002");
        }
        if (dataHome.length() == 0) {
            return ValidationResult.invalid("\u8bf7\u9009\u62e9\u6216\u8f93\u5165 Codex \u6570\u636e\u76ee\u5f55\u3002");
        }
        if (prompt.length() == 0) {
            return ValidationResult.invalid("\u8bf7\u8f93\u5165\u8981\u4ea4\u7ed9 Codex \u7684\u95ee\u9898\u3002");
        }
        File project = new File(projectDir);
        if (!project.exists() || !project.isDirectory()) {
            return ValidationResult.invalid("\u9879\u76ee\u76ee\u5f55\u4e0d\u5b58\u5728\u3002");
        }
        try {
            resolveConfigFile(configPath);
        } catch (Exception ex) {
            return ValidationResult.invalid(ex.getMessage());
        }
        return ValidationResult.valid();
    }

    private File resolveConfigFile(String configPath) throws IOException {
        File path = new File(configPath);
        if (!path.exists()) {
            throw new IOException("config.toml \u8def\u5f84\u4e0d\u5b58\u5728\u3002");
        }
        if (path.isDirectory()) {
            File configFile = new File(path, "config.toml");
            if (!configFile.isFile()) {
                throw new IOException("\u6240\u9009\u76ee\u5f55\u4e0b\u6ca1\u6709 config.toml\u3002");
            }
            return configFile.getAbsoluteFile();
        }
        if (!"config.toml".equalsIgnoreCase(path.getName())) {
            throw new IOException("\u8bf7\u9009\u62e9 config.toml \u6587\u4ef6\u6216\u5b83\u6240\u5728\u7684\u76ee\u5f55\u3002");
        }
        return path.getAbsoluteFile();
    }

    private File prepareCodexHome(File configFile, File dataHome) throws IOException {
        if (!dataHome.exists() && !dataHome.mkdirs()) {
            throw new IOException("\u65e0\u6cd5\u521b\u5efa Codex \u6570\u636e\u76ee\u5f55\u3002");
        }
        if (!dataHome.isDirectory()) {
            throw new IOException("Codex \u6570\u636e\u76ee\u5f55\u4e0d\u662f\u76ee\u5f55\u3002");
        }
        File targetConfig = new File(dataHome, "config.toml");
        if (!sameFile(configFile, targetConfig)) {
            Files.copy(configFile.toPath(), targetConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        importExistingSessions(configFile.getParentFile(), dataHome);
        return dataHome.getAbsoluteFile();
    }

    private void importExistingSessions(File configDir, File dataHome) throws IOException {
        if (configDir == null) {
            return;
        }
        File sourceSessions = new File(configDir, "sessions");
        File targetSessions = new File(dataHome, "sessions");
        if (sourceSessions.isDirectory() && !targetSessions.exists()) {
            copyDirectory(sourceSessions, targetSessions);
        }
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("\u65e0\u6cd5\u521b\u5efa\u5386\u53f2\u4f1a\u8bdd\u76ee\u5f55\u3002");
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyDirectory(child, new File(target, child.getName()));
            }
        } else {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean sameFile(File left, File right) {
        try {
            return left.exists() && right.exists() && left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException ex) {
            return false;
        }
    }

    private SwingWorker<Integer, String> createCodexWorker(File project, File codexHome, String prompt, boolean resume, boolean saveSession, boolean autoApprove) {
        return new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                List<String> command = buildCodexCommand(project, resume, saveSession, autoApprove);
                publish(commandLinePreview(command, codexHome));

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(project);
                builder.redirectErrorStream(true);
                Map<String, String> environment = builder.environment();
                environment.put("CODEX_HOME", codexHome.getAbsolutePath());

                runningProcess = builder.start();
                runningProcess.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
                runningProcess.getOutputStream().close();

                AtomicLong lastOutputAt = new AtomicLong(System.currentTimeMillis());
                AtomicBoolean completionSeen = new AtomicBoolean(false);
                Thread readerThread = new Thread(() -> readProcessOutput(runningProcess, lastOutputAt, completionSeen));
                readerThread.setDaemon(true);
                readerThread.start();

                while (true) {
                    try {
                        int exitCode = runningProcess.exitValue();
                        readerThread.join(1000L);
                        return exitCode;
                    } catch (IllegalThreadStateException stillRunning) {
                        if (completionSeen.get() && System.currentTimeMillis() - lastOutputAt.get() > 2500L) {
                            runningProcess.destroy();
                            return 0;
                        }
                        Thread.sleep(250L);
                    }
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendAssistantOutput(line);
                }
            }

            @Override
            protected void done() {
                setRunningState(false);
                try {
                    int exitCode = get();
                    if (exitCode == 0) {
                        if (saveSession) {
                            SessionEntry newest = findNewestSession(activeDataHome);
                            if (newest != null) {
                                activeSessionId = newest.id;
                            }
                        } else {
                            activeSessionId = "";
                        }
                        reloadHistory();
                        setStatus(saveSession ? "\u5c31\u7eea\uff0c\u4e0b\u4e00\u6761\u5c06\u7ee7\u7eed\u5f53\u524d\u5bf9\u8bdd" : "\u5c31\u7eea\uff0c\u672a\u4fdd\u5b58 Codex \u4f1a\u8bdd");
                    } else {
                        setStatus("Codex \u9000\u51fa\u7801\uff1a" + exitCode);
                    }
                    appendSystemLine("\u8fdb\u7a0b\u7ed3\u675f\uff0c\u9000\u51fa\u7801\uff1a" + exitCode);
                } catch (Exception ex) {
                    setStatus("\u8fd0\u884c\u5931\u8d25");
                    appendSystemLine("\u8fd0\u884c\u5931\u8d25\uff1a" + ex.getMessage());
                } finally {
                    runningProcess = null;
                    runningWorker = null;
                }
            }
        };
    }

    private void readProcessOutput(Process process, AtomicLong lastOutputAt, AtomicBoolean completionSeen) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                lastOutputAt.set(System.currentTimeMillis());
                if (line.contains("\"type\":\"task_complete\"") || "tokens used".equalsIgnoreCase(line.trim())) {
                    completionSeen.set(true);
                }
                publishFromReader(line);
            }
        } catch (IOException ex) {
            publishFromReader("[reader] " + ex.getMessage());
        }
    }

    private void publishFromReader(String line) {
        SwingUtilities.invokeLater(() -> appendAssistantOutput(line));
    }

    private List<String> buildCodexCommand(File project, boolean resume, boolean saveSession, boolean autoApprove) {
        List<String> command = new ArrayList<String>();
        command.add(findCodexCommand());
        if (autoApprove) {
            command.add("--dangerously-bypass-approvals-and-sandbox");
        }
        command.add("exec");
        if (resume) {
            command.add("resume");
            command.add(activeSessionId);
            command.add("--skip-git-repo-check");
            if (!saveSession) {
                command.add("--ephemeral");
            }
            command.add("-");
        } else {
            command.add("--skip-git-repo-check");
            if (!saveSession) {
                command.add("--ephemeral");
            }
            command.add("-C");
            command.add(project.getAbsolutePath());
            command.add("-");
        }
        return command;
    }

    private String commandLinePreview(List<String> command, File codexHome) {
        StringBuilder builder = new StringBuilder();
        builder.append("CODEX_HOME=").append(codexHome.getAbsolutePath()).append(" ");
        for (int i = 0; i < command.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(command.get(i));
        }
        return "[cmd] " + builder.toString();
    }

    private void stopRunningProcess() {
        if (runningProcess != null) {
            runningProcess.destroy();
            appendSystemLine("\u5df2\u8bf7\u6c42\u505c\u6b62\u5f53\u524d Codex \u8fdb\u7a0b");
        }
        if (runningWorker != null) {
            runningWorker.cancel(true);
        }
        setRunningState(false);
    }

    private void setRunningState(boolean running) {
        sendButton.setEnabled(!running);
        stopButton.setEnabled(running);
        newChatButton.setEnabled(!running);
        refreshHistoryButton.setEnabled(!running);
        refreshSkillsButton.setEnabled(!running);
        promptArea.setEnabled(!running);
    }

    private void reloadSkills() {
        List<String> selectedNames = new ArrayList<String>();
        for (SkillEntry entry : skillList.getSelectedValuesList()) {
            selectedNames.add(entry.name);
        }

        skillModel.clear();
        File skillsRoot = new File(new File(dataHomeField.getText().trim()), "skills");
        List<SkillEntry> entries = loadSkills(skillsRoot);
        for (SkillEntry entry : entries) {
            skillModel.addElement(entry);
        }

        List<Integer> indexes = new ArrayList<Integer>();
        for (int i = 0; i < skillModel.size(); i++) {
            if (selectedNames.contains(skillModel.get(i).name)) {
                indexes.add(i);
            }
        }
        int[] selectedIndexes = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
            selectedIndexes[i] = indexes.get(i);
        }
        skillList.setSelectedIndices(selectedIndexes);
        updateSkillDetail();
        setStatus("\u5df2\u52a0\u8f7d Skills\uff1a" + entries.size());
    }

    private List<SkillEntry> loadSkills(File skillsRoot) {
        List<SkillEntry> entries = new ArrayList<SkillEntry>();
        if (!skillsRoot.isDirectory()) {
            return entries;
        }
        List<File> skillFiles = new ArrayList<File>();
        collectSkillFiles(skillsRoot, skillFiles);
        for (File file : skillFiles) {
            SkillEntry entry = parseSkillFile(file);
            if (entry != null) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, new Comparator<SkillEntry>() {
            @Override
            public int compare(SkillEntry left, SkillEntry right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        return entries;
    }

    private void collectSkillFiles(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectSkillFiles(child, files);
            } else if ("SKILL.md".equalsIgnoreCase(child.getName())) {
                files.add(child);
            }
        }
    }

    private SkillEntry parseSkillFile(File file) {
        try {
            String content = readTextFile(file, 12000);
            String name = extractFrontMatterValue(content, "name");
            String description = extractFrontMatterValue(content, "description");
            if (name.length() == 0) {
                name = file.getParentFile().getName();
            }
            return new SkillEntry(name, description, file, content);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractFrontMatterValue(String content, String key) {
        BufferedReader reader = new BufferedReader(new java.io.StringReader(content));
        try {
            String line = reader.readLine();
            if (line == null || !line.trim().equals("---")) {
                return "";
            }
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equals("---")) {
                    break;
                }
                String prefix = key + ":";
                if (trimmed.startsWith(prefix)) {
                    String value = trimmed.substring(prefix.length()).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private void updateSkillDetail() {
        List<SkillEntry> selected = skillList.getSelectedValuesList();
        if (selected.isEmpty()) {
            skillDetailArea.setText("\u4ece\u4e0a\u65b9\u5217\u8868\u9009\u62e9 skill\u3002\u88ab\u9009\u4e2d\u7684 skill \u4f1a\u4f5c\u4e3a\u672c\u5bf9\u8bdd\u4e0a\u4e0b\u6587\u6ce8\u5165\u3002");
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\u5df2\u9009\u4e2d Skills\uff1a").append(selected.size()).append("\n\n");
        for (SkillEntry entry : selected) {
            builder.append("# ").append(entry.name).append("\n");
            if (entry.description.length() > 0) {
                builder.append(entry.description).append("\n");
            }
            builder.append(entry.skillFile.getAbsolutePath()).append("\n\n");
            builder.append(limitText(entry.content, MAX_SKILL_PREVIEW_CHARS)).append("\n\n");
        }
        skillDetailArea.setText(builder.toString());
        skillDetailArea.setCaretPosition(0);
    }

    private void reloadHistory() {
        historyModel.clear();
        File dataHome = new File(dataHomeField.getText().trim());
        List<SessionEntry> entries = loadSessions(dataHome);
        for (SessionEntry entry : entries) {
            historyModel.addElement(entry);
        }
        setStatus("\u5df2\u52a0\u8f7d\u5386\u53f2\u5bf9\u8bdd\uff1a" + entries.size());
    }

    private void loadSelectedHistory() {
        SessionEntry entry = historyList.getSelectedValue();
        if (entry == null) {
            return;
        }
        activeSessionId = entry.id;
        activeDataHome = new File(dataHomeField.getText().trim());
        conversationArea.setText(entry.transcript.length() == 0 ? "[System] \u8be5\u4f1a\u8bdd\u6682\u65e0\u53ef\u5c55\u793a\u5185\u5bb9\n" : entry.transcript);
        scrollConversationToEnd();
        setStatus("\u5df2\u6062\u590d\u5386\u53f2\u5bf9\u8bdd\uff1a" + entry.id);
    }

    private List<SessionEntry> loadSessions(File dataHome) {
        List<SessionEntry> entries = new ArrayList<SessionEntry>();
        File sessionsDir = new File(dataHome, "sessions");
        if (!sessionsDir.isDirectory()) {
            return entries;
        }
        List<File> files = new ArrayList<File>();
        collectJsonlFiles(sessionsDir, files);
        for (File file : files) {
            SessionEntry entry = parseSessionFile(file);
            if (entry != null) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, new Comparator<SessionEntry>() {
            @Override
            public int compare(SessionEntry left, SessionEntry right) {
                return Long.compare(right.lastModified, left.lastModified);
            }
        });
        return entries;
    }

    private void collectJsonlFiles(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectJsonlFiles(child, files);
            } else if (child.getName().endsWith(".jsonl")) {
                files.add(child);
            }
        }
    }

    private SessionEntry findNewestSession(File dataHome) {
        List<SessionEntry> sessions = loadSessions(dataHome);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private SessionEntry parseSessionFile(File file) {
        String id = sessionIdFromFileName(file.getName());
        String cwd = "";
        String firstUserMessage = "";
        StringBuilder transcript = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("\"type\":\"session_meta\"")) {
                    String metaId = extractJsonString(line, "id");
                    if (metaId.length() > 0) {
                        id = metaId;
                    }
                    cwd = extractJsonString(line, "cwd");
                } else if (line.contains("\"type\":\"event_msg\"") && line.contains("\"type\":\"user_message\"")) {
                    String message = extractJsonString(line, "message");
                    if (message.length() > 0) {
                        if (firstUserMessage.length() == 0) {
                            firstUserMessage = message;
                        }
                        transcript.append("[You]\n").append(message).append("\n\n");
                    }
                } else if (line.contains("\"type\":\"event_msg\"") && line.contains("\"type\":\"agent_message\"")) {
                    String message = extractJsonString(line, "message");
                    if (message.length() > 0) {
                        transcript.append("[Codex]\n").append(message).append("\n\n");
                    }
                }
            }
            reader.close();
        } catch (IOException ex) {
            return null;
        }
        if (id.length() == 0) {
            return null;
        }
        String title = firstUserMessage.length() > 0 ? firstUserMessage : id;
        return new SessionEntry(id, title, cwd, file, file.lastModified(), transcript.toString());
    }

    private String sessionIdFromFileName(String name) {
        Matcher matcher = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})").matcher(name);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractJsonString(String line, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(line);
        return matcher.find() ? jsonUnescape(matcher.group(1)) : "";
    }

    private String jsonUnescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'u':
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            try {
                                result.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                result.append("\\u").append(hex);
                                i += 4;
                            }
                        } else {
                            result.append(next);
                        }
                        break;
                    default: result.append(next); break;
                }
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private String findCodexCommand() {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.trim().length() == 0) {
            return "codex";
        }
        String[] executableNames = System.getProperty("os.name").toLowerCase().contains("win")
                ? new String[] {"codex.cmd", "codex.exe", "codex.bat", "codex"}
                : new String[] {"codex"};
        String[] directories = pathValue.split(File.pathSeparator);
        for (String directory : directories) {
            for (String executableName : executableNames) {
                File candidate = new File(directory, executableName);
                if (candidate.isFile()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        return "codex";
    }

    private void appendUserMessage(String prompt) {
        conversationArea.append("\n[You " + now() + "]\n");
        conversationArea.append(prompt + "\n\n");
        conversationArea.append("[Codex]\n");
        scrollConversationToEnd();
    }

    private void appendAssistantOutput(String text) {
        conversationArea.append(text + "\n");
        scrollConversationToEnd();
    }

    private void appendSystemLine(String text) {
        conversationArea.append("\n[System] " + text + "\n");
        scrollConversationToEnd();
    }

    private void scrollConversationToEnd() {
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "\u63d0\u793a", JOptionPane.WARNING_MESSAGE);
    }

    private static class SessionEntry {
        private final String id;
        private final String title;
        private final String cwd;
        private final File file;
        private final long lastModified;
        private final String transcript;

        private SessionEntry(String id, String title, String cwd, File file, long lastModified, String transcript) {
            this.id = id;
            this.title = title;
            this.cwd = cwd;
            this.file = file;
            this.lastModified = lastModified;
            this.transcript = transcript;
        }

        @Override
        public String toString() {
            String time = new SimpleDateFormat("MM-dd HH:mm").format(new Date(lastModified));
            String shortTitle = title.replace('\n', ' ').replace('\r', ' ');
            if (shortTitle.length() > 18) {
                shortTitle = shortTitle.substring(0, 18) + "...";
            }
            return time + "  " + shortTitle;
        }
    }

    private static class SkillEntry {
        private final String name;
        private final String description;
        private final File skillFile;
        private final String content;

        private SkillEntry(String name, String description, File skillFile, String content) {
            this.name = name;
            this.description = description;
            this.skillFile = skillFile;
            this.content = content;
        }

        @Override
        public String toString() {
            return description.length() == 0 ? name : name + " - " + description;
        }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        private static ValidationResult valid() {
            return new ValidationResult(true, "");
        }

        private static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
