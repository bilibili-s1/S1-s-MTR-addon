package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.item.ItemNodeCopier;

/**
 * 客户端 → 服务端:保存轨道节点复制工具的复制模式(连接/完全复制)到手持物品 NBT。
 * <p>
 * 客户端界面修改的物品 NBT 会被服务端权威同步覆盖,因此需要显式发送到服务端持久化。
 */
public class PacketS1mtrSaveNodeCopierMode extends PacketHandler {

	private final int mode;
	private final boolean offHand;

	public PacketS1mtrSaveNodeCopierMode(PacketBufferReceiver receiver) {
		mode = receiver.readInt();
		offHand = receiver.readBoolean();
	}

	public PacketS1mtrSaveNodeCopierMode(int mode, boolean offHand) {
		this.mode = mode;
		this.offHand = offHand;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeInt(mode);
		sender.writeBoolean(offHand);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		final net.minecraft.item.ItemStack stack = offHand
				? player.getOffHandStack().data
				: player.getMainHandStack().data;
		if (stack != null && stack.getItem() instanceof ItemNodeCopier) {
			ItemNodeCopier.setMode(stack, mode);
		}
	}
}
