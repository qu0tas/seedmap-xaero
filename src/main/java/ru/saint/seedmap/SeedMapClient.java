package ru.saint.seedmap;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeedMapClient implements ClientModInitializer {
	public static final String MOD_ID = "seedmapxaero";
	public static final Logger LOG = LoggerFactory.getLogger("SeedMapXaero");

	@Override
	public void onInitializeClient() {
		SeedMapConfig.load();
		LOG.info("SeedMap for Xaero's World Map initialized");
	}
}
