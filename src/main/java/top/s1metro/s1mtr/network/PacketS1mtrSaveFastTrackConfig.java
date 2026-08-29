package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;

/**
 * 客户端 → 服务端:把快速建造工具的配置(速度/样式/剖面)保存到服务端物品 NBT。
 * <p>
 * 配置界面在客户端修改的 NBT 不会被服务端认可, 服务端重新同步背包时会覆盖客户端修改,
 * 导致配置丢失。因此需要把配置发送到服务端, 由服务端写入玩家主手物品的 NBT。
 */
public class PacketS1mtrSaveFastTrackConfig extends PacketHandler {

	private final long speed;
	private final String styles;
	private final String schedule;

	public PacketS1mtrSaveFastTrackConfig(PacketBufferReceiver receiver) {
		speed = receiver.readLong();
		styles = receiver.readString();
		schedule = receiver.readString();
	}

	public PacketS1mtrSaveFastTrackConfig(long speed, String styles, String schedule) {
		this.speed = Math.max(1, speed);
		this.styles = styles == null ? "" : styles;
		this.schedule = schedule == null ? "" : schedule;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeLong(speed);
		sender.writeString(styles);
		sender.writeString(schedule);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		final org.mtr.mapping.holder.ItemStack holderStack = player.getMainHandStack();
		final net.minecraft.item.ItemStack stack = holderStack == null ? null : holderStack.data;
		if (stack != null && stack.getItem() instanceof ItemFastTrackBuilder) {
			ItemFastTrackBuilder.setSpeed(stack, speed);
			final java.util.Set<String> set = new java.util.LinkedHashSet<>();
			if (!styles.isEmpty()) {
				for (String s : styles.split(",")) {
					if (!s.isEmpty()) {
						set.add(s);
					}
				}
			}
			ItemFastTrackBuilder.setStyles(stack, set);
			if (!schedule.isEmpty()) {
				stack.getOrCreateNbt().putString(ItemFastTrackBuilder.KEY_SCHEDULE, schedule);
			}
		}
	}
}
