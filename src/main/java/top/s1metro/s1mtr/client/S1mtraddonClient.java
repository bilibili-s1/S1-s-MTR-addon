package top.s1metro.s1mtr.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.registry.RegistryClient;
import top.s1metro.s1mtr.S1mtraddon;

/**
 * 客户端入口。负责建立本模组自己的数据包发送通道，
 * 供客户端界面（如 {@code RailAdvancedSettingsScreen}）向服务端发送放样请求。
 */
public class S1mtraddonClient implements ClientModInitializer {

	public static final RegistryClient REGISTRY_CLIENT = new RegistryClient(S1mtraddon.REGISTRY);

	/** 与服务端 PlacementQueue.PROGRESS_CHANNEL 同字符串的进度推送通道。 */
	private static final net.minecraft.util.Identifier PROGRESS_CHANNEL = new net.minecraft.util.Identifier(S1mtraddon.MOD_ID, "placement_progress");

	@Override
	public void onInitializeClient() {
		REGISTRY_CLIENT.setupPackets(new Identifier(S1mtraddon.MOD_ID, "packet"));

		// 自动速度连接器的预览线 + 快捷栏上方预期速度提示
		HudSpeedPreview.register();

		// 复合构建器的 3D 世界内方块预览
		CompositePreviewRenderer.register();

		// 接收服务端放样进度,在 ActionBar 显示(底部进度文字)
		ClientPlayNetworking.registerGlobalReceiver(PROGRESS_CHANNEL, (client, handler, buf, sender) -> {
			final boolean completed = buf.readBoolean();
			final int done = buf.readInt();
			final int total = buf.readInt();
			final String label = buf.readString();
			client.execute(() -> {
				final ClientPlayerEntity player = MinecraftClient.getInstance().player;
				if (player == null) {
					return;
				}
				if (completed) {
					player.sendMessage(Text.literal("§a" + label + " 完成"), true);
					return;
				}
				final int pct = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
				player.sendMessage(Text.literal(String.format("§e[S1MTR] %s %d%%  (%d/%d)", label, pct, done, total)), true);
			});
		});
	}
}
