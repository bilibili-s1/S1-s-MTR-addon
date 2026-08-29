package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.item.ItemRailConnectorAuto;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 客户端 → 服务端:把自动连接器的配置(样式多选/单向/剖面)保存到服务端手持物品 NBT。
 * <p>
 * 与快速建造器一致:配置界面在客户端修改的 NBT 会被服务端权威同步覆盖,因此需要显式发送到
 * 服务端,由服务端写入手持物品 NBT,连接时 {@code ItemRailConnectorAuto.onConnect} 才能读到。
 */
public class PacketS1mtrSaveAutoConnectorConfig extends PacketHandler {

	private final String styles;
	private final boolean oneWay;
	private final String schedule;

	public PacketS1mtrSaveAutoConnectorConfig(PacketBufferReceiver receiver) {
		styles = receiver.readString();
		oneWay = receiver.readBoolean();
		schedule = receiver.readString();
	}

	public PacketS1mtrSaveAutoConnectorConfig(String styles, boolean oneWay, String schedule) {
		this.styles = styles == null ? "" : styles;
		this.oneWay = oneWay;
		this.schedule = schedule == null ? "" : schedule;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeString(styles);
		sender.writeBoolean(oneWay);
		sender.writeString(schedule);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		final org.mtr.mapping.holder.ItemStack holderStack = player.getMainHandStack();
		final net.minecraft.item.ItemStack stack = holderStack == null ? null : holderStack.data;
		if (stack != null && stack.getItem() instanceof ItemRailConnectorAuto) {
			final org.mtr.mapping.holder.ItemStack mappingStack = new org.mtr.mapping.holder.ItemStack(stack);
			final Set<String> set = new LinkedHashSet<>();
			if (!styles.isEmpty()) {
				for (String s : styles.split(",")) {
					if (!s.isEmpty()) {
						set.add(s);
					}
				}
			}
			ItemRailConnectorAuto.setStyles(mappingStack, set);
			ItemRailConnectorAuto.setOneWay(mappingStack, oneWay);
			if (!schedule.isEmpty()) {
				mappingStack.getOrCreateTag().putString(ItemRailConnectorAuto.KEY_SCHEDULE, schedule);
			}
		}
	}
}
