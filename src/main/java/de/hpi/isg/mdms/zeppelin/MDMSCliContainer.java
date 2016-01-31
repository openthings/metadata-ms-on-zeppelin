package de.hpi.isg.mdms.zeppelin;

import de.hpi.isg.mdms.cli.apps.MDMSCliApp;
import de.hpi.isg.mdms.cli.reader.LineBuffer;
import de.hpi.isg.mdms.cli.variables.ContextList;
import de.hpi.isg.mdms.cli.variables.ContextObject;
import de.hpi.isg.mdms.cli.variables.Namespace;
import de.hpi.isg.mdms.cli.variables.StringValue;
import de.hpi.isg.mdms.util.IoUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides a container for a {@link MDMSCliApp}, including textual input and output.
 */
public class MDMSCliContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MDMSCliContainer.class);

    private LineBuffer cliReader, cliSink;
    private PrintStream cliWriter;
    private MDMSCliApp mdmsCli;

    public MDMSCliContainer() {
        initialize();
    }

    private void initialize() {
        LOGGER.info("Initialize...");
        this.cliReader = new LineBuffer();
        this.cliSink = new LineBuffer();
        this.cliWriter = new PrintStream(new WriterOutputStream(this.cliSink), true);
        this.mdmsCli = new MDMSCliApp(this.cliReader, this.cliWriter);
        LOGGER.info("...finished opening.");
    }

    public void close() {
        LOGGER.info("Closing...");
        IoUtils.closeSafe(this.cliReader);
        IoUtils.closeSafe(this.cliWriter);
        this.mdmsCli = null;
        LOGGER.info("...finished closing.");
    }

    public InterpreterResult interpret(String s, InterpreterContext interpreterContext) {
        LOGGER.info("Asked to interpret \"{}\".", s);
        this.cliReader.feed(s);
        this.mdmsCli.run();
        final String result = this.cliSink.readAll();
        LOGGER.info("Result: {}", result);
        final ContextObject returnValue = this.mdmsCli.getSessionContext().getReturnValue();

        // Hacky: if the last return value was a list, we assume it is a table.
        if (returnValue instanceof ContextList) {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            List<Iterator<ContextObject>> columnVectorIterators = new LinkedList<>();
            for (ContextObject column : (ContextList) returnValue) {
                final StringValue columnName = ((Namespace) column).get("name", StringValue.class);
                sb.append(separator).append(columnName.toReadableString());
                separator = "\t";
                columnVectorIterators.add(((Namespace) column).get("data", ContextList.class).iterator());
            }

            Outer:
            while (true) {
                separator = "";
                sb.append('\n');
                for (Iterator<ContextObject> columnVectorIterator : columnVectorIterators) {
                    if (!columnVectorIterator.hasNext()) {
                        break Outer;
                    }
                    final ContextObject next = columnVectorIterator.next();
                    sb.append(separator).append(next.toReadableString());
                    separator = "\t";
                }
            }

            return new InterpreterResult(InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TABLE, sb.toString());
        }


        return new InterpreterResult(InterpreterResult.Code.SUCCESS, InterpreterResult.Type.TEXT, result);
    }

    public void loadStateFrom(File stateFile) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(stateFile))) {
            final Object o = in.readObject();
            this.mdmsCli.getSessionContext().setGlobalNamespace((Namespace) o);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not interpret state file.", e);
        }
    }

    public void saveStateTo(File stateFile) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(stateFile))) {
            out.writeObject(this.mdmsCli.getSessionContext().getGlobalNamespace());
        }
    }
}
