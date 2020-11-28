package codechicken.diffpatch.util;

import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Created by covers1624 on 20/12/18.
 */
public class ConsumingOutputStream extends OutputStream {

    private static final char CR = '\r';
    private static final char LF = '\n';

    private final Consumer<String> consumer;
    private final StringBuilder buffer = new StringBuilder();

    public ConsumingOutputStream(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void write(int b) {
        char ch = (char) (b & 0xFF);
        if (ch == CR) {
            return;
        }
        buffer.append(ch);
        if (ch == LF) {
            flush();
        }
    }

    @Override
    public void flush() {
        String str = buffer.toString();
        if (str.endsWith("\n")) {
            str = str.replaceAll("\n", "");
            consumer.accept(str);
            buffer.setLength(0);
        }
    }
}
