/*
Copyright (C) 2023 CYS4 Srl
See the file 'LICENSE' for copying permission
*/
package com.cys4.sensitivediscoverer;

import com.cys4.sensitivediscoverer.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cys4.sensitivediscoverer.Messages.getLocaleString;


/**
 * Utils package
 */
public class Utils {

    /**
     * Read the content of a resource file.
     *
     * @param filepath Path to the resource file.
     * @return A UTF-8 string with the content of the file read.
     */
    public static String readResourceFile(String filepath) {
        try {
            InputStream inputStream = Utils.getResourceAsStream(filepath);
            if (Objects.isNull(inputStream)) return null;

            InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr);

            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Open JFileChooser to save lines to a file
     *
     * @param extensionName the extension of the saved file
     * @param lines         The lines to write in the file
     */
    public static void saveToFile(String extensionName, List<String> lines) {
        JFrame parentFrame = new JFrame();
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("." + extensionName, extensionName);
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle(getLocaleString("utils-saveToFile-exportFile"));

        int userSelection = fileChooser.showSaveDialog(parentFrame);
        if (userSelection != JFileChooser.APPROVE_OPTION) return;

        String exportFilePath = fileChooser.getSelectedFile().getAbsolutePath();
        if (!exportFilePath.endsWith("." + extensionName)) {
            exportFilePath += "." + extensionName;
        }

        try {
            PrintWriter pwt = new PrintWriter(exportFilePath, StandardCharsets.UTF_8);
            lines.forEach(pwt::println);
            pwt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Open JFileChooser to get lines from a file
     *
     * @param extensionName the extension to filter files
     * @return The lines from the file, or null if there was an error
     */
    public static List<String> linesFromFile(String extensionName) {
        JFrame parentFrame = new JFrame();
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("." + extensionName, extensionName);
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle(getLocaleString("utils-linesFromFile-importFile"));

        int userSelection = fileChooser.showOpenDialog(parentFrame);
        if (userSelection != JFileChooser.APPROVE_OPTION) return null;

        File selectedFile = fileChooser.getSelectedFile();
        try {
            return Files.readAllLines(selectedFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Recursively disable all components that have a certain property set.
     * When a component with the property specified is found, the component and all the recursive children are enabled to the state specified.
     *
     * @param component parent component
     * @param enabled   enabled state to set
     */
    public static void setEnabledRecursiveComponentsWithProperty(Component component, boolean enabled, String propertyName) {
        // if component has the property, stop searching this branch and disable everything
        if (component instanceof JComponent jComponent && Objects.nonNull(jComponent.getClientProperty(propertyName))) {
            setEnabledRecursive(component, enabled);
            return;
        }

        // otherwise, continue the search on the children
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setEnabledRecursiveComponentsWithProperty(child, enabled, propertyName);
            }
        }
    }

    private static void setEnabledRecursive(Component component, boolean enabled) {
        boolean newState = enabled;

        if (component instanceof JComponent jComponent) {
            if (component.isEnabled() == enabled) {
                // if component is already in the required state, save this information in case the operation needs to be reversed
                jComponent.putClientProperty("previouslyEnabled", enabled);
            } else {
                // set the state to the previous if present, otherwise use the passed one
                Object previousState = jComponent.getClientProperty("previouslyEnabled");
                jComponent.putClientProperty("previouslyEnabled", null);
                newState = (Objects.nonNull(previousState)) ? (boolean) previousState : enabled;
            }
        }

        component.setEnabled(newState);

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setEnabledRecursive(child, enabled);
            }
        }
    }

    public static Properties loadConfigFile() throws Exception {
        Properties properties;

        InputStream input = Utils.getResourceAsStream("config.properties");
        properties = new Properties();
        properties.load(input);
        return properties;
    }

    public static String getExtensionVersion() {
        return Utils.class.getPackage().getImplementationVersion();
    }

    /**
     * Returns an input stream for reading the specified resource.
     *
     * @param name The resource name
     * @return An input stream for reading the resource; null if the resource could not be found or there was an error.
     */
    public static InputStream getResourceAsStream(String name) {
        return Utils.class.getClassLoader().getResourceAsStream(name);
    }

    public static void saveListToCSV(List<RegexEntity> regexEntities) {
        List<String> lines = new ArrayList<>();

        lines.add("\"description\",\"regex\",\"sections\"");
        regexEntities.forEach(regexEntity -> {
            String description = regexEntity.getDescription().replaceAll("\"", "\"\"");
            String regex = regexEntity.getRegex().replaceAll("\"", "\"\"");
            String sections = String
                    .join("|", ProxyItemSection.serializeSections(regexEntity.getSections()))
                    .replaceAll("\"", "\"\"");
            lines.add(String.format("\"%s\",\"%s\",\"%s\"", description, regex, sections));
        });

        Utils.saveToFile("csv", lines);
    }

    public static void saveListToJSON(List<RegexEntity> regexEntities) {
        List<JsonObject> lines = new ArrayList<>();

        regexEntities.forEach(regexEntity -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("description", regexEntity.getDescription());
            obj.addProperty("regex", regexEntity.getRegex());
            JsonArray sections = new JsonArray();
            ProxyItemSection.serializeSections(regexEntity.getSections()).forEach(sections::add);
            obj.add("sections", sections);
            lines.add(obj);
        });

        GsonBuilder builder = new GsonBuilder().disableHtmlEscaping();
        Gson gson = builder.create();
        Type tListEntries = (new TypeToken<ArrayList<JsonObject>>() {
        }).getType();
        Utils.saveToFile("json", List.of(gson.toJson(lines, tListEntries)));
    }

    public static void openListFromCSV(RegexListContext ctx) {
        StringBuilder alreadyAddedMsg = new StringBuilder();

        List<String> lines = Utils.linesFromFile("csv");
        if (Objects.isNull(lines)) return;

        // for each line after the first (Headers Line)
        lines.subList(1, lines.size()).forEach(line -> {
            Matcher matcher = RegexEntity.checkRegexEntityFromCSV(line);
            if (!matcher.find()) return;

            String description = matcher.group(1).replaceAll("\"\"", "\"");
            String regex = matcher.group(2).replaceAll("\"\"", "\"");
            List<String> sections = List.of(matcher.group(3).replaceAll("\"\"", "\"").split("\\|"));

            RegexEntity newRegexEntity = new RegexEntity(
                    description,
                    regex,
                    true,
                    ProxyItemSection.deserializeSections(sections)
            );

            if (!ctx.getRegexEntities().contains(newRegexEntity)) {
                ctx.getRegexEntities().add(newRegexEntity);
            } else {
                alreadyAddedMsg.append(String.format("%s - %s\n", newRegexEntity.getDescription(), newRegexEntity.getRegex()));
            }
        });

        showMessageDialog(
                getLocaleString("options-list-open-alreadyPresentTitle"),
                getLocaleString("options-list-open-alreadyPresentWarn"),
                alreadyAddedMsg.toString());
    }

    public static void openListFromJSON(RegexListContext ctx) {
        Gson gson = new Gson();
        StringBuilder alreadyAddedMsg = new StringBuilder();

        List<String> lines = Utils.linesFromFile("json");
        if (Objects.isNull(lines)) return;

        Type tArrayListRegexEntity = new TypeToken<ArrayList<JsonRegexEntity>>() {
        }.getType();
        Stream.of(String.join("", lines))
                .<List<JsonRegexEntity>>map(regexList -> gson.fromJson(regexList, tArrayListRegexEntity))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(element -> new RegexEntity(
                        element.getDescription(),
                        element.getRegex(),
                        true,
                        ProxyItemSection.deserializeSections(element.getSections())))
                .forEachOrdered(regexEntity -> {
                    if (!ctx.getRegexEntities().contains(regexEntity)) {
                        ctx.getRegexEntities().add(regexEntity);
                    } else {
                        alreadyAddedMsg
                                .append(regexEntity.getDescription())
                                .append(" - ")
                                .append(regexEntity.getRegex())
                                .append("\n");
                    }
                });

        showMessageDialog(
                getLocaleString("options-list-open-alreadyPresentTitle"),
                getLocaleString("options-list-open-alreadyPresentWarn"),
                alreadyAddedMsg.toString());
    }

    /**
     * Shows an information dialog containing a header paragraph, and a message below.
     * If the message is empty, the dialog is not shown.
     *
     * @param title         The dialog window title
     * @param headerMessage The Message to show in the header at the top
     * @param message       The message to show under the headerMessage
     */
    private static void showMessageDialog(String title, String headerMessage, String message) {
        if (message.isBlank()) return;

        JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
        JLabel headerLabel = new JLabel(headerMessage + ":");
        headerLabel.setFont(UIOptions.H1_FONT);
        mainPanel.add(headerLabel, BorderLayout.NORTH);
        JTextArea messageTextArea = new JTextArea(message);
        messageTextArea.setEditable(false);
        mainPanel.add(messageTextArea, BorderLayout.CENTER);

        JDialog alreadyAddedDialog = new JDialog();
        JOptionPane.showMessageDialog(
                alreadyAddedDialog,
                mainPanel,
                title,
                JOptionPane.INFORMATION_MESSAGE);
        alreadyAddedDialog.setVisible(true);
    }
}