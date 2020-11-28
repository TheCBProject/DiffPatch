package codechicken.diffpatch.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by covers1624 on 19/11/20.
 */
public class NullOutputStream extends OutputStream {

    public static final NullOutputStream INSTANCE = new NullOutputStream();

    //@formatter:off
    @Override public void write(int b) throws IOException { }
    @Override public void write(byte[] b) throws IOException { }
    @Override public void write(byte[] b, int off, int len) throws IOException { }
    //@formatter:on
}
