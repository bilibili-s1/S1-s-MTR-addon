package top.s1metro.s1mtr;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import top.s1metro.s1mtr.client.screen.S1mtrConfigScreen;

/**
 * Mod Menu 集成入口。
 * <p>
 * 通过 {@code fabric.mod.json} 的 {@code entrypoints.modmenu} 注册。仅当 Mod Menu 已安装时,
 * Mod Menu 才会加载本类(此时 {@link ModMenuApi} 接口也在 classpath 上)。未安装 Mod Menu 时本类
 * 不会被加载,模组仍可正常运行,配置改 {@code config/s1mtr/config.json} 即可。
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory getModConfigScreenFactory() {
		return S1mtrConfigScreen::new;
	}
}
