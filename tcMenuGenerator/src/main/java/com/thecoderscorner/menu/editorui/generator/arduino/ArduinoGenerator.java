/*
 * Copyright (c)  2016-2019 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 *
 */

package com.thecoderscorner.menu.editorui.generator.arduino;

import com.thecoderscorner.menu.domain.MenuItem;
import com.thecoderscorner.menu.domain.state.MenuTree;
import com.thecoderscorner.menu.domain.util.MenuItemHelper;
import com.thecoderscorner.menu.pluginapi.CodeGenerator;
import com.thecoderscorner.menu.pluginapi.EmbeddedCodeCreator;
import com.thecoderscorner.menu.pluginapi.model.HeaderDefinition;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.thecoderscorner.menu.editorui.generator.arduino.ArduinoItemGenerator.LINE_BREAK;
import static com.thecoderscorner.menu.editorui.generator.arduino.ArduinoItemGenerator.makeNameToVar;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ArduinoGenerator implements CodeGenerator {
    private final System.Logger logger = System.getLogger(getClass().getSimpleName());
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private static final String COMMENT_HEADER = "/*\n" +
            "    The code in this file uses open source libraries provided by thecoderscorner" + LINE_BREAK + LINE_BREAK +
            "    DO NOT EDIT THIS FILE, IT WILL BE GENERATED EVERY TIME YOU USE THE UI DESIGNER" + LINE_BREAK +
            "    INSTEAD EITHER PUT CODE IN YOUR SKETCH OR CREATE ANOTHER SOURCE FILE." + LINE_BREAK + LINE_BREAK +
            "    All the variables you may need access to are marked extern in this file for easy" + LINE_BREAK +
            "    use elsewhere." + LINE_BREAK +
            " */" + LINE_BREAK + LINE_BREAK;

    private static final String HEADER_TOP = "#ifndef MENU_GENERATED_CODE_H" + LINE_BREAK +
                                             "#define MENU_GENERATED_CODE_H" + LINE_BREAK + LINE_BREAK;
    private final ArduinoLibraryInstaller installer;
    private final ArduinoSketchFileAdjuster arduinoSketchAdjuster;

    private Consumer<String> uiLogger = null;

    public ArduinoGenerator(ArduinoSketchFileAdjuster adjuster,
                            ArduinoLibraryInstaller installer) {
        this.installer = installer;
        this.arduinoSketchAdjuster = adjuster;
    }

    @Override
    public boolean startConversion(Path directory, List<EmbeddedCodeCreator> generators, MenuTree menuTree) {
        logLine("Starting Arduino generate: " + directory);

        String inoFile = toSourceFile(directory, ".ino");
        String cppFile = toSourceFile(directory, ".cpp");
        String headerFile = toSourceFile(directory, ".h");
        String projectName = directory.getFileName().toString();

        checkIfUpToDateWarningNeeded();

        String root = getFirstMenuVariable(menuTree);

        Collection<BuildStructInitializer> menuStructure = generateMenusInOrder(menuTree);

        generators.forEach(gen -> gen.initialise(root));

        if (generateHeaders(generators, menuTree, headerFile, menuStructure) &&
                generateSource(generators, cppFile, menuStructure, projectName, root)) {

            updateArduinoSketch(inoFile, projectName, menuTree);

            addAnyRequiredPluginsToSketch(generators, directory);

        } else {
            return false;
        }

        logLine("Process has completed, make sure the code in your IDE is up-to-date.");
        logLine("You may need to close the project and then re-open it to pick up changes..");

        return true;
    }

    private boolean generateSource(List<EmbeddedCodeCreator> generators, String cppFile,
                                   Collection<BuildStructInitializer> menuStructure,
                                   String projectName, String root) {

        try (Writer writer = new BufferedWriter(new FileWriter(cppFile))) {
            logLine("Writing out source CPP file: " + cppFile);

            writer.write(COMMENT_HEADER);

            writer.write("#include <tcMenu.h>");
            writer.write(LINE_BREAK);
            writer.write("#include \"" + projectName + ".h\"");
            writer.write(LINE_BREAK + LINE_BREAK);

            writer.write("// Global variable declarations" + LINE_BREAK);
            writer.write(generators.stream()
                    .map(EmbeddedCodeCreator::getGlobalVariables)
                    .collect(Collectors.joining(LINE_BREAK)));

            writer.write(LINE_BREAK);

            writer.write("// Global Menu Item declarations" + LINE_BREAK);
            writer.write(menuStructure.stream()
                    .map(BuildStructInitializer::toSource)
                    .collect(Collectors.joining(LINE_BREAK)));

            writer.write(LINE_BREAK);

            writer.write(LINE_BREAK + "// Set up code" + LINE_BREAK);
            writer.write("void setupMenu() {" + LINE_BREAK);
            writer.write(generators.stream()
                    .map(ecc -> ecc.getSetupCode(root))
                    .collect(Collectors.joining(LINE_BREAK)));

            writer.write("}" + LINE_BREAK);
            writer.write(LINE_BREAK);

            logLine("Finished processing source file.");

        } catch (Exception e) {
            logLine("Failed to generate CPP: " + e.getMessage());
            logger.log(ERROR, "CPP Code Generation failed", e);
            return false;
        }

        return true;
    }

    private boolean generateHeaders(List<EmbeddedCodeCreator> generators, MenuTree menuTree,
                                    String headerFile, Collection<BuildStructInitializer> menuStructure) {
        try (Writer writer = new BufferedWriter(new FileWriter(headerFile))) {
            logLine("Writing out header file: " + headerFile);

            writer.write(COMMENT_HEADER);
            writer.write(HEADER_TOP);

            var includeList = generators.stream().flatMap(g -> g.getIncludes().stream()).collect(Collectors.toList());

            includeList.add(new HeaderDefinition("tcMenu.h", false));

            writer.write(includeList.stream()
                    .distinct()
                    .map(HeaderDefinition::getHeaderCode)
                    .collect(Collectors.joining(LINE_BREAK))
            );

            writer.write(LINE_BREAK + LINE_BREAK + "// all export definitions" + LINE_BREAK);

            writer.write(generators.stream()
                    .map(EmbeddedCodeCreator::getExportDefinitions)
                    .collect(Collectors.joining(LINE_BREAK))
            );

            writer.write(LINE_BREAK + LINE_BREAK + "// all menu item forward references." + LINE_BREAK);

            writer.write(menuStructure.stream()
                    .map(BuildStructInitializer::toHeader)
                    .filter(item-> !item.isEmpty())
                    .collect(Collectors.joining(LINE_BREAK))
            );

            writer.write(LINE_BREAK + LINE_BREAK);

            writer.write("// all callback functions must have this define on them, it is what the menu designer looks for."
                            + LINE_BREAK + "#define CALLBACK_FUNCTION" + LINE_BREAK + LINE_BREAK);

            for (String callback : callBackFunctions(menuTree)) {
                writer.write("void CALLBACK_FUNCTION " + callback + "(int id);" + LINE_BREAK);
            }

            writer.write(LINE_BREAK + "void setupMenu();" + LINE_BREAK);
            writer.write(LINE_BREAK + "#endif // MENU_GENERATED_CODE_H" + LINE_BREAK);

            logLine("Finished processing header file.");
        } catch (Exception e) {
            logLine("Failed to generate header file: " + e.getMessage());
            logger.log(ERROR, "Header Code Generation failed", e);
            return false;
        }

        return true;
    }

    private void checkIfUpToDateWarningNeeded() {
        if(!installer.statusOfAllLibraries().isUpToDate()) {
            logLine("WARNING===============================================================");
            logLine("The embedded libraries are not up-to-date, build problems are likely");
            logLine("Select ROOT menu item and choose update libraries from the editor");
            logLine("WARNING===============================================================");
        }
    }

    private void updateArduinoSketch(String inoFile, String projectName, MenuTree menuTree) {
        logLine("Making adjustments to " + inoFile);

        try {
            arduinoSketchAdjuster.makeAdjustments(this::logLine, inoFile, projectName, callBackFunctions(menuTree));
        } catch (IOException e) {
            logLine("Failed to make changes to sketch" +  e.getMessage());
            logger.log(ERROR, "Sketch modification failed", e);
        }
    }

    private void addAnyRequiredPluginsToSketch(List<EmbeddedCodeCreator> generators, Path directory) {
        logLine("Finding any required rendering / remote plugins to add to project");

        generators.stream().flatMap(gen-> gen.getRequiredFiles().stream()).forEach(file -> {
            try {
                Path fileToCopy = installer.findLibraryInstall("tcMenu")
                        .orElseThrow(IOException::new).resolve(file);

                Path nameOfFile = Paths.get(file).getFileName();
                Files.copy(fileToCopy, directory.resolve(nameOfFile), REPLACE_EXISTING);
                logLine("Copied with replacement " + file);
            } catch (IOException e) {
                logLine("Copy failed for required plugin: " + file);
                logger.log(ERROR, "Copy failed for " + file, e);
            }
        });
    }

    @Override
    public void setLoggerFunction(Consumer<String> uiLogger) {
        this.uiLogger = uiLogger;
    }

    private String getFirstMenuVariable(MenuTree menuTree) {
        return menuTree.getMenuItems(MenuTree.ROOT).stream().findFirst()
                .map(menuItem -> "menu" + makeNameToVar(menuItem.getName()))
                .orElse("");
    }

    private Collection<BuildStructInitializer> generateMenusInOrder(MenuTree menuTree) {
        List<MenuItem> root = menuTree.getMenuItems(MenuTree.ROOT);
        List<List<BuildStructInitializer>> itemsInOrder = renderMenu(menuTree, root);
        Collections.reverse(itemsInOrder);
        return itemsInOrder.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<List<BuildStructInitializer>> renderMenu(MenuTree menuTree, Collection<MenuItem> itemsColl) {
        ArrayList<MenuItem> items = new ArrayList<>(itemsColl);
        List<List<BuildStructInitializer>> itemsInOrder = new ArrayList<>(100);
        for (int i = 0; i < items.size(); i++) {

            if (items.get(i).hasChildren()) {
                int nextIdx = i + 1;
                String nextSub = (nextIdx < items.size()) ? items.get(nextIdx).getName() : null;

                List<MenuItem> childItems = menuTree.getMenuItems(items.get(i));
                String nextChild = (!childItems.isEmpty()) ? childItems.get(0).getName() : null;
                itemsInOrder.add(MenuItemHelper.visitWithResult(items.get(i),
                        new ArduinoItemGenerator(nextSub, nextChild)).orElse(Collections.emptyList()));
                itemsInOrder.addAll(renderMenu(menuTree, childItems));
            } else {
                int nextIdx = i + 1;
                String next = (nextIdx < items.size()) ? items.get(nextIdx).getName() : null;
                itemsInOrder.add(MenuItemHelper.visitWithResult(items.get(i),
                        new ArduinoItemGenerator(next)).orElse(Collections.emptyList()));
            }
        }
        return itemsInOrder;
    }

    private List<String> callBackFunctions(MenuTree menuTree) {
        return menuTree.getAllSubMenus().stream()
                .flatMap(menuItem -> menuTree.getMenuItems(menuItem).stream())
                .filter(menuItem -> menuItem.getFunctionName() != null && !menuItem.getFunctionName().isEmpty())
                .map(MenuItem::getFunctionName)
                .collect(Collectors.toList());

    }

    private String toSourceFile(Path directory, String ext) {
        Path file = directory.getFileName();
        return Paths.get(directory.toString(), file.toString() + ext).toString();
    }

    private void logLine(String s) {
        if(uiLogger != null) uiLogger.accept(DATE_TIME_FORMATTER.format(Instant.now()) + " - " + s);
        logger.log(INFO, s);
    }
}
