package top.s1metro.s1mtr.service;

import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 跨 tick 的方块放置队列。
 * <p>
 * 放样(CompositeBuilder)原来在一个调用栈内对所有命中方块同步写入世界,
 * 轨道越长迭代量与 setBlockState 次数越大,会瞬间占满服务端主线程导致 CPU 100% 甚至崩服。
 * 这里改为:放样只负责"计算并收集"待放置任务,由本队列在每 tick 末尾分批写入,
 * 把压力摊到成百上千个 tick,削平 CPU 峰值。
 * <p>
 * 队列按 {@link World} 区分,避免不同维度/世界互相干扰;空队列会在 tick 后自动移除。
 * 每个放样调用对应一个 {@link Batch},用于向客户端推送进度条。
 */
public final class PlacementQueue {

	/** 服务端 → 客户端 的进度推送通道。客户端使用同字符串的 Identifier 接收。 */
	public static final Identifier PROGRESS_CHANNEL = new Identifier("s1mtraddon", "placement_progress");

	/** 每 tick 最多写入的方块数上限(实际上限由配置 S1mtrConfig.placementPerTick 决定,此处仅作硬顶)。 */
	private static final int PER_TICK_HARD_LIMIT = 1024;

	/** 进度推送间隔(单位 tick),避免每 tick 都发包刷屏。 */
	private static final int PROGRESS_INTERVAL = 20;

	private static final Map<World, ArrayDeque<Task>> QUEUES = new ConcurrentHashMap<>();
	private static final AtomicInteger NEXT_BATCH_ID = new AtomicInteger(1);
	private static final Map<Integer, Batch> BATCHES = new ConcurrentHashMap<>();
	private static boolean hadActive = false;

	/** 一次放样的进度批次。 */
	public static final class Batch {
		final String label;
		volatile int total = 0;
		volatile int done = 0;
		volatile boolean ended = false;

		Batch(String label) {
			this.label = label;
		}

		public double progress() {
			return total == 0 ? 0 : Math.min(1.0, (double) done / total);
		}

		public boolean finished() {
			return ended && done >= total;
		}
	}

	private static final class Task {
		final int batchId;
		final BlockPos pos;
		final BlockState state;

		Task(int batchId, BlockPos pos, BlockState state) {
			this.batchId = batchId;
			this.pos = pos;
			this.state = state;
		}
	}

	private PlacementQueue() {
	}

	/** 在 ModInitializer 中调用一次,注册每 tick 末尾的写入调度。 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlacementQueue::onTick);
	}

	/** 开始一个放样批次,返回批次 id(用于后续 enqueue/endBatch)。 */
	public static int beginBatch(String label) {
		final int id = NEXT_BATCH_ID.getAndIncrement();
		BATCHES.put(id, new Batch(label == null ? "放样" : label));
		return id;
	}

	/** 收集一个待放置方块(不立即写入),计入对应批次总量。 */
	public static void enqueue(int batchId, World world, BlockPos pos, BlockState state) {
		if (world == null || pos == null || state == null) {
			return;
		}
		final Batch batch = BATCHES.get(batchId);
		if (batch != null) {
			batch.total++;
		}
		QUEUES.computeIfAbsent(world, w -> new ArrayDeque<>()).addLast(new Task(batchId, pos, state));
	}

	/** 标记一个放样批次的入队结束(此时 total 即为本次放样总量)。 */
	public static void endBatch(int batchId) {
		final Batch batch = BATCHES.get(batchId);
		if (batch != null) {
			batch.ended = true;
		}
	}

	/** 当前所有队列中待放置的方块总数(用于调试)。 */
	public static int pendingCount() {
		int total = 0;
		for (final ArrayDeque<Task> queue : QUEUES.values()) {
			total += queue.size();
		}
		return total;
	}

	private static void onTick(MinecraftServer server) {
		for (final Iterator<Map.Entry<World, ArrayDeque<Task>>> it = QUEUES.entrySet().iterator(); it.hasNext(); ) {
			final Map.Entry<World, ArrayDeque<Task>> entry = it.next();
			final ArrayDeque<Task> queue = entry.getValue();
			int n = 0;
			final int perTick = Math.min(PER_TICK_HARD_LIMIT, S1mtrConfig.placementPerTick());
			while (!queue.isEmpty() && n < perTick) {
				final Task task = queue.pollFirst();
				entry.getKey().setBlockState(task.pos, task.state, 3);
				n++;
				final Batch batch = BATCHES.get(task.batchId);
				if (batch != null) {
					batch.done++;
				}
			}
			if (queue.isEmpty()) {
				it.remove();
			}
		}
		// 清理已完成的批次
		BATCHES.entrySet().removeIf(e -> e.getValue().finished());
		pushProgress(server);
	}

	private static void pushProgress(MinecraftServer server) {
		Batch active = null;
		int maxRemain = -1;
		for (final Batch batch : BATCHES.values()) {
			if (batch.finished()) {
				continue;
			}
			final int remain = batch.total - batch.done;
			if (remain > maxRemain) {
				maxRemain = remain;
				active = batch;
			}
		}

		if (active == null) {
			if (hadActive) {
				// 刚刚全部完成,推送一次"完成"提示
				sendProgress(server, "✓ 放样", 1, 1, true);
				hadActive = false;
			}
			return;
		}

		hadActive = true;
		if (server.getTicks() % PROGRESS_INTERVAL != 0) {
			return;
		}
		sendProgress(server, active.label, active.done, active.total, false);
	}

	private static void sendProgress(MinecraftServer server, String label, int done, int total, boolean completed) {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(completed);
		buf.writeInt(done);
		buf.writeInt(total);
		buf.writeString(label);
		for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, PROGRESS_CHANNEL, buf);
		}
	}
}
