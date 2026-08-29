package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.item.ItemNodeCopier;

/**
 * 客户端 → 服务端:把复制到的轨道节点连接数据保存到服务端手持物品 NBT。
 * <p>
 * 复制数据在<b>客户端</b>用 {@link MinecraftClientData} 读取(与服务端权威数据一致),
 * 序列化后经本包写入手持物品 NBT,供后续"粘贴"时使用。
 */
public class PacketS1mtrSaveNodeCopy extends PacketHandler {

	private final String json;

	public PacketS1mtrSaveNodeCopy(PacketBufferReceiver receiver) {
		json = receiver.readString();
	}

	public PacketS1mtrSaveNodeCopy(String json) {
		this.json = json == null ? "" : json;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeString(json);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		final org.mtr.mapping.holder.ItemStack holderStack = player.getMainHandStack();
		final net.minecraft.item.ItemStack stack = holderStack == null ? null : holderStack.data;
		if (stack != null && stack.getItem() instanceof ItemNodeCopier && !json.isEmpty()) {
			ItemNodeCopier.setCopiedData(stack, json);
		}
	}
}
