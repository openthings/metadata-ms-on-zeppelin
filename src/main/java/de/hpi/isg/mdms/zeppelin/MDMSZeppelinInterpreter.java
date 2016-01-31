package de.hpi.isg.mdms.zeppelin;

import de.hpi.isg.mdms.cli.apps.MDMSCliApp;
import de.hpi.isg.mdms.cli.reader.LineBuffer;
import org.apache.zeppelin.interpreter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Interpreter for the MDMS on Apache Zeppelin. Basically a wrapper of {@link MDMSCliApp}.
 */
public class MDMSZeppelinInterpreter extends Interpreter {

    private static boolean IS_EAGER_STATE_SAVE = true;

    public static final String STATE_DIRECTORY_PATH_KEY = "state-dir";

    public static final String DEFAULT_STATE_DIRECTORY_PATH = "mdms" + File.separator + "states";

    private static final Logger LOGGER = LoggerFactory.getLogger(MDMSZeppelinInterpreter.class);

    public static final String INTERPRETER_GROUP = "mdms";

    public static final String INTERPRETER_NAME = "mdms";

    static {
        LOGGER.info("Registering...");
        final Map<String, InterpreterProperty> defaultProperties = new InterpreterPropertyBuilder()
                .add(STATE_DIRECTORY_PATH_KEY, DEFAULT_STATE_DIRECTORY_PATH, "directory to persist interpreter states")
                .build();
        Interpreter.register(INTERPRETER_NAME,
                INTERPRETER_GROUP,
                MDMSZeppelinInterpreter.class.getCanonicalName(),
                defaultProperties);
        LOGGER.info("...finished registering.");
    }

    private Map<String, MDMSCliContainer> mdmsCliContainers = new HashMap<>();

    public MDMSZeppelinInterpreter(Properties property) {
        super(property);
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
        LOGGER.info("Requested to close...");
        for (Map.Entry<String, MDMSCliContainer> entry : mdmsCliContainers.entrySet()) {
            String noteId = entry.getKey();
            final MDMSCliContainer mdmsCliContainer = entry.getValue();
            mdmsCliContainer.close();
            try {
                final File stateDirectory = getStateDirectory();
                final File stateFile = new File(stateDirectory, noteId);
                mdmsCliContainer.saveStateTo(stateFile);
            } catch (Exception e) {
                LOGGER.error("Could not save interpreter state for " + noteId, e);
            }
        }
        LOGGER.info("...closing done.");
    }

    @Override
    public InterpreterResult interpret(String s, InterpreterContext interpreterContext) {
        final String noteId = interpreterContext.getNoteId();
        final MDMSCliContainer mdmsCliContainer = getMDMSCliContainer(noteId);
        try {
            return mdmsCliContainer.interpret(s, interpreterContext);
        } catch (Throwable t) {
            LOGGER.error("Unexpected error.", t);
            final LineBuffer lineBuffer = new LineBuffer();
            t.printStackTrace(new PrintWriter(lineBuffer, true));
            return new InterpreterResult(InterpreterResult.Code.ERROR, InterpreterResult.Type.TEXT, lineBuffer.readAll());
        } finally {
            try {
                mdmsCliContainer.saveStateTo(new File(getStateDirectory(), noteId));
            } catch (IOException e) {
                LOGGER.error("Could not save state of MDMS CLI container " + noteId);
            }
        }

    }

    private MDMSCliContainer getMDMSCliContainer(String noteId) {
        MDMSCliContainer mdmsCliContainer = this.mdmsCliContainers.get(noteId);
        if (mdmsCliContainer == null) {
            mdmsCliContainer = new MDMSCliContainer();
            this.mdmsCliContainers.put(noteId, mdmsCliContainer);
            try {
                final File stateDirectory = getStateDirectory();
                final File stateFile = new File(stateDirectory, noteId);
                if (stateFile.isFile()) {
                    mdmsCliContainer.loadStateFrom(stateFile);
                }
            } catch (Exception e) {
                LOGGER.error("Could not load state file.", e);
            }
        }
        return mdmsCliContainer;
    }

    private File getStateDirectory() throws IOException {
        final File stateDir = new File(this.property.getProperty(STATE_DIRECTORY_PATH_KEY, DEFAULT_STATE_DIRECTORY_PATH));
        if (!stateDir.exists()) {
            final boolean isCreatedDirs = stateDir.mkdirs();
            if (!isCreatedDirs) {
                throw new IOException("Could not create the state directory.");
            }
        } else if (!stateDir.isDirectory()) {
            throw new IOException("The state directory is not a directory.");
        }
        return stateDir;
    }

    @Override
    public void cancel(InterpreterContext interpreterContext) {

    }

    @Override
    public FormType getFormType() {
        return FormType.SIMPLE;
    }

    @Override
    public int getProgress(InterpreterContext interpreterContext) {
        return 0;
    }

    @Override
    public List<String> completion(String s, int i) {
        return null;
    }
}
