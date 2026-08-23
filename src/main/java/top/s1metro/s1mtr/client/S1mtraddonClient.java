package top.s1metro.s1mtr.client;

import net.fabricmc.api.ClientModInitializer;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.registry.RegistryClient;
import top.s1metro.s1mtr.S1mtraddon;

/**
 * 客户端入口。负责建立本模组自己的数据包发送通道，
 * 供客户端界面（如 {@code RailAdvancedSettingsScreen}）向服务端发送放样请求。
 */
public class S1mtraddonClient implements ClientModInitializer {

	public static final RegistryClient REGISTRY_CLIENT = new RegistryClient(S1mtraddon.REGISTRY);

	@Override
	public void onInitializeClient() {
		REGISTRY_CLIENT.setupPackets(new Identifier(S1mtraddon.MOD_ID, "packet"));
	}
}
