package dev.voidmc.audit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * Audit only (branch audit-server): hosts Fabric's in-process dedicated server with Fabric API but no Mettle on port
 * 25598 (and joins it), so a client with Mettle can try it. Writes {@code bare.server}; stops at {@code M.done}.
 */
public class BareHost implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext c) {
		Path dir = Path.of(System.getProperty("mettle.audit.dir", "audit"));
		Properties props = new Properties();
		props.setProperty("server-port", "25598");
		props.setProperty("max-players", "5");
		props.setProperty("white-list", "false");
		props.setProperty("online-mode", "false");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				Files.createDirectories(dir);
				Files.writeString(dir.resolve("bare.server"), "");
				long end = System.currentTimeMillis() + 600_000;
				while (!Files.exists(dir.resolve("M.done")) && !Files.exists(dir.resolve("M.failed")) && System.currentTimeMillis() < end) {
					c.waitTicks(20);
				}
				Files.writeString(dir.resolve("bare.log"), server.computeOnServer(s -> String.join(",", s.getPlayerList().getPlayerNamesArray())));
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
