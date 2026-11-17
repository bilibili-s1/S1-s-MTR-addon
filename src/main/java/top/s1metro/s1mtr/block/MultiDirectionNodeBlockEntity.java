package top.s1metro.s1mtr.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 万向节点方块实体：只保存三个状态 —— 方向、是否已绑定方向、是否已连接轨道。
 * <p>
 * 方速原版还保存节点平移、俯仰、翻滚、外轨超高与半轨距，这些依赖其自研 RailMath 几何内核，
 * 最小可用版全部砍掉，仅保留「任意角度」这一核心能力。
 *
 * <p>方向、绑定、连接状态通过原版 {@code readNbt/writeNbt} 持久化，因此 Ctrl+中键拾取
 * （{@code BlockEntityTag}）会自动带上绑定状态；服务端改动经 {@code updateListeners} 同步给客户端渲染。
 *
 * <p>移植自 FangSu For MTR 4（MIT），按 Yarn 映射重写。
 */
public class MultiDirectionNodeBlockEntity extends BlockEntity {

	private static final String KEY_DIRECTION = "direction";
	private static final String KEY_CONNECTED = "connected";
	private static final String KEY_DIRECTION_BONDED = "directionBonded";

	private double direction;
	private boolean connected;
	private boolean directionBonded;

	public MultiDirectionNodeBlockEntity(BlockPos pos, BlockState state) {
		super(top.s1metro.s1mtr.S1mtraddon.MULTI_DIRECTION_NODE_BLOCK_ENTITY, pos, state);
	}

	/** 当前方向（度，恒在 [0,360)，0=E、90=S、180=W、270=N）。 */
	public double getDirectionDegrees() {
		return direction;
	}

	public boolean isConnected() {
		return connected;
	}

	public boolean isDirectionBonded() {
		return directionBonded;
	}

	/** 方向是周期量：回绕到 [0,360) 而非钳制；NaN/无穷归零。 */
	public static double wrapDirectionDegrees(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return 0.0D;
		}
		double degrees = value % 360.0D;
		if (degrees < 0.0D) {
			degrees += 360.0D;
		}
		return degrees == 0.0D ? 0.0D : degrees;
	}

	public void setDirectionAndBind(double degrees) {
		this.direction = wrapDirectionDegrees(degrees);
		this.directionBonded = true;
		markDirtyAndSync();
	}

	/** 只写方向，不改变绑定状态（界面「旋转绑定：否」或建轨失败回滚时使用）。 */
	public void setDirectionUnbound(double degrees) {
		this.direction = wrapDirectionDegrees(degrees);
		markDirtyAndSync();
	}

	public void setRotationBonded(boolean bonded) {
		this.directionBonded = bonded;
		markDirtyAndSync();
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
		markDirtyAndSync();
	}

	private void markDirtyAndSync() {
		markDirty();
		final World world = getWorld();
		if (world != null && !world.isClient()) {
			world.updateListeners(getPos(), getCachedState(), getCachedState(), Block.NOTIFY_ALL);
		}
	}

	@Override
	public void readNbt(NbtCompound tag) {
		super.readNbt(tag);
		direction = wrapDirectionDegrees(tag.getDouble(KEY_DIRECTION));
		connected = tag.getBoolean(KEY_CONNECTED);
		directionBonded = tag.getBoolean(KEY_DIRECTION_BONDED);
	}

	@Override
	protected void writeNbt(NbtCompound tag) {
		super.writeNbt(tag);
		tag.putDouble(KEY_DIRECTION, direction);
		tag.putBoolean(KEY_CONNECTED, connected);
		tag.putBoolean(KEY_DIRECTION_BONDED, directionBonded);
	}

	@Override
	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		final NbtCompound tag = new NbtCompound();
		writeNbt(tag);
		return tag;
	}
}
