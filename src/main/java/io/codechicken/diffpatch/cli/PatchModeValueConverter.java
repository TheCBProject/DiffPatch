package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.util.PatchMode;
import joptsimple.util.EnumConverter;

/**
 * Created by covers1624 on 11/8/20.
 */
public class PatchModeValueConverter extends EnumConverter<PatchMode> {

    public PatchModeValueConverter() {
        super(PatchMode.class);
    }
}
