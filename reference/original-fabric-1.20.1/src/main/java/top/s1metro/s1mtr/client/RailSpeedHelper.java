package top.s1metro.s1mtr.client;

import org.mtr.core.data.Rail;
import org.mtr.core.data.Rail.Shape;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Position;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

import java.lang.reflect.Constructor;

public final class RailSpeedHelper {

	private static final String DOOR_OPEN_DELAY_PREFIX = "s1mtr:doorOpenDelay=";
	private static final String DOOR_CLOSE_DELAY_PREFIX = "s1mtr:doorCloseDelay=";

	private RailSpeedHelper() {
	}

	private static volatile Constructor<Rail> RAIL_CONSTRUCTOR;

	private static Constructor<Rail> getRailConstructor() throws ReflectiveOperationException {
		if (RAIL_CONSTRUCTOR != null) {
			return RAIL_CONSTRUCTOR;
		}
		for (Constructor<?> constructor : Rail.class.getDeclaredConstructors()) {
			Class<?>[] params = constructor.getParameterTypes();
			if (params.length >= 7 &&
					params[0] == Position.class &&
					params[1] == Angle.class &&
					params[2] == Position.class &&
					params[3] == Angle.class &&
					params[4] == Shape.class &&
					params[5] == double.class &&
					params[6] == ObjectArrayList.class) {
				constructor.setAccessible(true);
				RAIL_CONSTRUCTOR = (Constructor<Rail>) constructor;
				return RAIL_CONSTRUCTOR;
			}
		}
		throw new NoSuchMethodException("Cannot find Rail constructor");
	}

	public static Rail copyWithCustomParams(Rail original, Shape shape, double radius,
			ObjectArrayList<String> styles, long speed, long doorOpenDelay, long doorCloseDelay) {
		RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) original;

		ObjectArrayList<String> newStyles = new ObjectArrayList<>(styles);
		removeDelayStyles(newStyles);
		if (doorOpenDelay > 0) {
			newStyles.add(DOOR_OPEN_DELAY_PREFIX + doorOpenDelay);
		}
		if (doorCloseDelay > 0) {
			newStyles.add(DOOR_CLOSE_DELAY_PREFIX + doorCloseDelay);
		}

		long speedLimit1 = (accessor.s1mtr$getSpeedLimit1() == 0) ? 0L : speed;
		long speedLimit2 = speed;

		try {
			return getRailConstructor().newInstance(
					accessor.s1mtr$getPosition1(), accessor.s1mtr$getAngle1(),
					accessor.s1mtr$getPosition2(), accessor.s1mtr$getAngle2(),
					shape, radius, newStyles,
					speedLimit1, speedLimit2,
					accessor.s1mtr$isPlatform(), accessor.s1mtr$isSiding(), accessor.s1mtr$canAccelerate(),
					accessor.s1mtr$canTurnBack(), accessor.s1mtr$canConnectRemotely(), accessor.s1mtr$canHaveSignal(),
					accessor.s1mtr$getTransportMode());
		} catch (ReflectiveOperationException e) {
			return Rail.copy(original, newStyles);
		}
	}

	private static void removeDelayStyles(ObjectArrayList<String> styles) {
		for (int i = styles.size() - 1; i >= 0; i--) {
			String style = styles.get(i);
			if (style.startsWith(DOOR_OPEN_DELAY_PREFIX) || style.startsWith(DOOR_CLOSE_DELAY_PREFIX)) {
				styles.remove(i);
			}
		}
	}

	public static long getSpeedLimit1(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getSpeedLimit1();
	}

	public static long getSpeedLimit2(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getSpeedLimit2();
	}

	public static ObjectArrayList<String> getStyles(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
	}

	public static Shape getShape(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getShape();
	}

	public static double getRadius(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getVerticalRadius();
	}

	public static boolean isPlatform(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$isPlatform();
	}

	public static long getDoorOpenDelay(Rail rail) {
		ObjectArrayList<String> styles = ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
		for (String style : styles) {
			if (style.startsWith(DOOR_OPEN_DELAY_PREFIX)) {
				try {
					return Long.parseLong(style.substring(DOOR_OPEN_DELAY_PREFIX.length())) * 1000;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	public static long getDoorCloseDelay(Rail rail) {
		ObjectArrayList<String> styles = ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
		for (String style : styles) {
			if (style.startsWith(DOOR_CLOSE_DELAY_PREFIX)) {
				try {
					return Long.parseLong(style.substring(DOOR_CLOSE_DELAY_PREFIX.length())) * 1000;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	public static long calculateRecommendedSpeed(Rail rail) {
		RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
		
		boolean isPlatform = accessor.s1mtr$isPlatform();
		boolean isSiding = accessor.s1mtr$isSiding();
		boolean canTurnBack = accessor.s1mtr$canTurnBack();
		
		if (isPlatform || isSiding || canTurnBack) {
			return 0;
		}
		
		Position pos1 = accessor.s1mtr$getPosition1();
		Position pos2 = accessor.s1mtr$getPosition2();
		
		double dx = pos2.getX() - pos1.getX();
		double dy = pos2.getY() - pos1.getY();
		double dz = pos2.getZ() - pos1.getZ();
		
		double fullLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
		
		if (fullLength <= 0) {
			return -1;
		}
		
		double slope = Math.abs(dy) / fullLength;
		
		RailMath railMath = new RailMath(
				pos1, accessor.s1mtr$getAngle1(),
				pos2, accessor.s1mtr$getAngle2(),
				accessor.s1mtr$getShape(),
				accessor.s1mtr$getVerticalRadius()
		);
		
		DoubleDoubleImmutablePair radii = railMath.getHorizontalRadii();
		double r1 = radii.firstDouble();
		double r2 = radii.secondDouble();
		
		double minHorizontalRadius = r1 > 0 && r2 > 0 ? Math.min(r1, r2) : (r1 > 0 ? r1 : (r2 > 0 ? r2 : -1));
		
		long curveSpeed = calculateCurveSpeed(minHorizontalRadius);
		long slopeSpeed = calculateSlopeSpeed(slope);
		
		if (curveSpeed < 0 && slopeSpeed < 0) {
			return -1;
		} else if (curveSpeed < 0) {
			return slopeSpeed;
		} else if (slopeSpeed < 0) {
			return curveSpeed;
		} else {
			return Math.min(curveSpeed, slopeSpeed);
		}
	}

	private static long calculateCurveSpeed(double radius) {
		if (radius <= 0) {
			return -1;
		}
		
		if (radius < 60) {
			return 20;
		} else if (radius < 100) {
			return 40;
		} else if (radius < 150) {
			return 60;
		} else if (radius < 220) {
			return 80;
		} else if (radius < 350) {
			return 100;
		} else if (radius < 600) {
			return 120;
		} else if (radius < 1000) {
			return 160;
		} else if (radius < 1500) {
			return 200;
		} else {
			return -1;
		}
	}

	private static long calculateSlopeSpeed(double slope) {
		if (slope < 0.005) {
			return -1;
		} else if (slope < 0.01) {
			return 160;
		} else if (slope < 0.02) {
			return 120;
		} else if (slope < 0.03) {
			return 100;
		} else if (slope < 0.04) {
			return 80;
		} else if (slope < 0.05) {
			return 60;
		} else if (slope < 0.06) {
			return 40;
		} else {
			return 20;
		}
	}
}