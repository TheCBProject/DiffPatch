package codechicken.diffpatch.cli;

import codechicken.diffpatch.util.archiver.ArchiveFormat;
import joptsimple.util.EnumConverter;

/**
 * Created by covers1624 on 19/7/20.
 */
public class ArchiveFormatValueConverter extends EnumConverter<ArchiveFormat> {

    public ArchiveFormatValueConverter() {
        super(ArchiveFormat.class);
    }
}
