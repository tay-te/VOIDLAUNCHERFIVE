package dev.voidmc.bestiary.gametest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.Connection;

/**
 * A dev latency injector (ROADMAP G-MP) on this client's own connection, both ways: every packet it receives and every
 * packet it sends is held for half the round trip, in order. Works on the integrated server's in-memory connection too.
 */
public final class Latency {
	private static volatile int oneWayMs;
	private static Channel installed;

	private Latency() {
	}

	/** Sets the round trip in ms (0 turns it off); installs the two handlers on first use. */
	public static void set(ClientGameTestContext c, int rttMs) {
		oneWayMs = rttMs / 2;
		c.runOnClient(mc -> {
			try {
				Connection conn = mc.getConnection().getConnection();
				Field f = Connection.class.getDeclaredField("channel");
				f.setAccessible(true);
				Channel ch = (Channel) f.get(conn);
				if (ch == installed) {
					return;
				}
				installed = ch;
				ch.pipeline().addBefore("packet_handler", "fray_lag_in", new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) {
						int ms = oneWayMs;
						if (ms <= 0) {
							ctx.fireChannelRead(msg);
						} else {
							ctx.channel().eventLoop().schedule(() -> ctx.fireChannelRead(msg), ms, TimeUnit.MILLISECONDS);
						}
					}
				});
				ch.pipeline().addBefore("packet_handler", "fray_lag_out", new ChannelOutboundHandlerAdapter() {
					@Override
					public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
						int ms = oneWayMs;
						if (ms <= 0) {
							ctx.write(msg, promise);
						} else {
							ctx.channel().eventLoop().schedule(() -> ctx.writeAndFlush(msg, promise), ms, TimeUnit.MILLISECONDS);
						}
					}
				});
				Stage.log("latency injector installed; pipeline " + ch.pipeline().names());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		Stage.log("latency: " + rttMs + " ms round trip (" + oneWayMs + " ms each way)");
	}
}
