package net.lootr.serveronly.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent home for the mod id and logger (COMMON_MODULE_PLAN.md section 3b). The NeoForge and Fabric
 * main classes keep their own {@code MOD_ID} / {@code LOGGER} fields; once those delegate here, files that
 * reference either one can move into this module without caring which loader they run on.
 */
public final class LootrServerOnlyConstants {

    public static final String MOD_ID = "lootr_serveronly";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private LootrServerOnlyConstants() {}
}
