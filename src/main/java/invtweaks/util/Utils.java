package invtweaks.util;

import java.util.stream.*;

import it.unimi.dsi.fastutil.ints.*;

public class Utils {
	public static int gridToPlayerSlot(int row, int col) {
		if (row < 0 || row >= 4 || col < 0 || col >= 9) {
			throw new IllegalArgumentException("Invalid coordinates ("+row+", "+col+")");
		}
		return ((row+1) % 4) * 9 + col;
	}
	
	public static int gridRowToInt(String str) {
		if (str.length() != 1 || str.charAt(0) < 'A' || str.charAt(0) > 'D') {
			throw new IllegalArgumentException("Invalid grid row: "+str);
		}
		return str.charAt(0) - 'A';
	}
	
	public static int gridColToInt(String str) {
		if (str.length() != 1 || str.charAt(0) < '1' || str.charAt(0) > '9') {
			throw new IllegalArgumentException("Invalid grid column: "+str);
		}
		return str.charAt(0) - '1';
	}
	
	public static int[] gridSpecToSlots(String str) {
		if (str.endsWith("rv")) {
			return gridSpecToSlots(str.substring(0, str.length()-2) + "vr");
		}
		if (str.endsWith("r")) {
			return IntArrays.reverse(gridSpecToSlots(str.substring(0, str.length()-1)));
		}
		boolean vertical = false;
		if (str.endsWith("v")) {
			vertical = true;
			str = str.substring(0, str.length()-1);
		}
		String[] parts = str.split("-");
		if (parts.length == 1) {
			if (str.length() == 1) {
				try {
					int row = gridRowToInt(str);
					return IntStream.range(0, 8)
							.map(col -> gridToPlayerSlot(row, col))
							.toArray();
				} catch (IllegalArgumentException e) {
					int col = gridColToInt(str);
					return IntStream.of(3,2,1,0) // bottom to top
							.map(row -> gridToPlayerSlot(row, col))
							.toArray();
				}
			} else if (str.length() == 2) {
				return new int[] { gridToPlayerSlot(
						gridRowToInt(str.substring(0, 1)), gridColToInt(str.substring(1, 2))
						) };
			} else {
				throw new IllegalArgumentException("Bad grid spec: "+str);
			}
		} else if (parts.length == 2) {
			if (parts[0].length() == 2 && parts[1].length() == 2) {
				int row0 = gridRowToInt(parts[0].substring(0, 1));
				int col0 = gridColToInt(parts[0].substring(1, 2));
				int row1 = gridRowToInt(parts[1].substring(0, 1));
				int col1 = gridColToInt(parts[1].substring(1, 2));
				
				if (vertical) {
					return directedRangeInclusive(col0, col1)
							.flatMap(col -> directedRangeInclusive(row0, row1)
									.map(row -> gridToPlayerSlot(row, col)))
							.toArray();
				} else {
					return directedRangeInclusive(row0, row1)
							.flatMap(row -> directedRangeInclusive(col0, col1)
									.map(col -> gridToPlayerSlot(row, col)))
							.toArray();
				}
			} else {
				throw new IllegalArgumentException("Bad grid spec: "+str);
			}
		} else {
			throw new IllegalArgumentException("Bad grid spec: "+str);
		}
	}
	
	public static IntStream directedRangeInclusive(int start, int end) {
		return IntStream.iterate(start, v -> (start > end ? v-1 : v+1)).limit(Math.abs(end - start) + 1);
	}
}
