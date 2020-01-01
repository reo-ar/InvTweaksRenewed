package invtweaks.util;

//import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import com.google.common.base.Equivalence;
import com.google.common.collect.Streams;

import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.item.*;
//import net.minecraftforge.fml.common.*;
import net.minecraftforge.items.*;

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
	
	public static int[] gridSpecToSlots(String str, boolean global) {
		if (str.endsWith("rv")) {
			return gridSpecToSlots(str.substring(0, str.length()-2) + "vr", global);
		}
		if (str.endsWith("r")) {
			return IntArrays.reverse(gridSpecToSlots(str.substring(0, str.length()-1), global));
		}
		boolean vertical = false;
		if (str.endsWith("v")) {
			vertical = true;
			str = str.substring(0, str.length()-1);
		}
		String[] parts = str.split("-");
		if (parts.length == 1) { // single point or row/column
			if (str.length() == 1) { // row/column
				try {
					int row = gridRowToInt(str);
					if (global) return gridSpecToSlots("A1-D9", false);
					return IntStream.range(0, 8)
							.map(col -> gridToPlayerSlot(row, col))
							.toArray();
				} catch (IllegalArgumentException e) {
					int col = gridColToInt(str);
					if (global) return gridSpecToSlots("D1-A9v", false);
					return directedRangeInclusive(3, 0)
							.map(row -> gridToPlayerSlot(row, col))
							.toArray();
				}
			} else if (str.length() == 2) { // single point
				if (global) return gridSpecToSlots("A1-D9", false);
				return new int[] { gridToPlayerSlot(
						gridRowToInt(str.substring(0, 1)), gridColToInt(str.substring(1, 2))
						) };
			} else {
				throw new IllegalArgumentException("Bad grid spec: "+str);
			}
		} else if (parts.length == 2) { // rectangle
			if (parts[0].length() == 2 && parts[1].length() == 2) {
				int row0 = gridRowToInt(parts[0].substring(0, 1));
				int col0 = gridColToInt(parts[0].substring(1, 2));
				int row1 = gridRowToInt(parts[1].substring(0, 1));
				int col1 = gridColToInt(parts[1].substring(1, 2));
				
				if (global) {
					if (row0 > row1) {
						row0 = 3; row1 = 0;
					} else {
						row0 = 0; row1 = 3;
					}
					if (col0 > col1) {
						col0 = 8; col1 = 0;
					} else {
						col0 = 0; col1 = 8;
					}
				}
				
				int _row0 = row0, _row1 = row1, _col0 = col0, _col1 = col1;
				
				if (vertical) {
					return directedRangeInclusive(col0, col1)
							.flatMap(col -> directedRangeInclusive(_row0, _row1)
									.map(row -> gridToPlayerSlot(row, col)))
							.toArray();
				} else {
					return directedRangeInclusive(row0, row1)
							.flatMap(row -> directedRangeInclusive(_col0, _col1)
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
	
	public static final Equivalence<ItemStack> STACKABLE = new Equivalence<ItemStack>() {

		@Override
		protected boolean doEquivalent(ItemStack a, ItemStack b) {
			return ItemHandlerHelper.canItemStacksStack(a, b);
		}

		@Override
		protected int doHash(ItemStack t) {
			List<Object> objs = new ArrayList<>(2);
			if (!t.isEmpty()) {
				objs.add(t.getItem());
				if (t.hasTag()) {
					objs.add(t.getTag());
				}
			}
			return Arrays.hashCode(objs.toArray());
		}
		
	};
	
	public static List<ItemStack> collated(Iterable<ItemStack> iterable) {
		Map<Equivalence.Wrapper<ItemStack>, List<ItemStack>> mapping = Streams.stream(iterable)
				.collect(Collectors.groupingBy(st -> STACKABLE.wrap(st), LinkedHashMap::new, Collectors.toList()));
		return mapping.values().stream().flatMap(ls -> ls.stream()).collect(Collectors.toList());
	}
	
	//@SuppressWarnings("unchecked")
	public static List<ItemStack> condensed(Iterable<ItemStack> iterable) {
		List<ItemStack> coll = collated(iterable);
		// TODO special handling for Nether Chests-esque mods?
		ItemStackHandler stackBuffer = new ItemStackHandler(coll.size());
		int index = 0;
		for (ItemStack stack: coll) {
			stack = stack.copy();
			while (!(stack = stackBuffer.insertItem(index, stack, false)).isEmpty()) { ++index; }
		}
		return IntStream.range(0, stackBuffer.getSlots())
				.mapToObj(stackBuffer::getStackInSlot)
				.filter(is -> !is.isEmpty())
				.collect(Collectors.toList());
	}
	
	public static final Comparator<ItemStack> FALLBACK_COMPARATOR = Comparator.comparing(is -> is.getItem().getRegistryName());
}
