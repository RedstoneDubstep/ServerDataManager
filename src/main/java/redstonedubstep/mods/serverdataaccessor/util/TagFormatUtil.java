package redstonedubstep.mods.serverdataaccessor.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.nbt.ByteArrayNBT;
import net.minecraft.nbt.ByteNBT;
import net.minecraft.nbt.CollectionNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongArrayNBT;
import net.minecraft.nbt.LongNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraftforge.common.util.Constants;

public class TagFormatUtil {
	public static ListNBT formatResourceEntriesToKeys(ListNBT listTag) {
		if (listTag.getElementType() == Constants.NBT.TAG_COMPOUND) { //When the id tag gets referenced directly, only show the resource keys as string tags to truncate all the {} and resource ids, since these are not worth showing
			ListNBT onlyKeyList = new ListNBT();

			listTag.forEach(tag -> onlyKeyList.add(StringNBT.valueOf(((CompoundNBT)tag).getString("K"))));
			return onlyKeyList;
		}

		return listTag;
	}

	public static void removeNestedCollectionTags(CompoundNBT tag) {
		if (tag.size() > 0) {
			List<Pair<String, Integer>> nestedTags = tag.getAllKeys().stream().filter(s -> tag.get(s) instanceof CollectionNBT || tag.get(s) instanceof CompoundNBT).map(s -> Pair.of(s, TagFormatUtil.getTagSize(tag.get(s)))).collect(Collectors.toList());

			nestedTags.forEach(p -> tag.put(p.getLeft(), StringNBT.valueOf("{" + p.getRight() + " entries}")));
		}
	}

	public static void splitTagToPage(INBT tag, int currentPage, int entriesPerPage) {
		List<Pair<String, INBT>> tagList = new ArrayList<>();

		if (tag instanceof CollectionNBT)
			((CollectionNBT<?>)tag).forEach(t -> tagList.add(Pair.of(null, t)));
		else if (tag instanceof CompoundNBT)
			((CompoundNBT)tag).getAllKeys().forEach(s -> tagList.add(Pair.of(s, ((CompoundNBT)tag).get(s))));

		List<Pair<String, INBT>> truncatedTagList = FormatUtil.splitToPage(tagList, currentPage, entriesPerPage);
		clearAndAddCollection(tag, truncatedTagList);
	}

	public static void clearAndAddCollection(INBT tag, List<Pair<String, INBT>> list) {
		clearCollectionTag(tag);

		list.forEach(p -> addToCollectionTag(tag, p.getRight(), p.getLeft()));
	}

	public static void addToCollectionTag(INBT tag, INBT toAdd, String compoundKey) {
		if (tag instanceof CollectionNBT) {
			if (tag instanceof ByteArrayNBT && toAdd instanceof ByteNBT)
				((ByteArrayNBT)tag).add(((ByteNBT)toAdd));
			else if (tag instanceof IntArrayNBT && toAdd instanceof IntNBT)
				((IntArrayNBT)tag).add(((IntNBT)toAdd));
			else if (tag instanceof ListNBT)
				((ListNBT)tag).add(toAdd);
			else if (tag instanceof LongArrayNBT && toAdd instanceof LongNBT)
				((LongArrayNBT)tag).add(((LongNBT)toAdd));
		}
		else if (tag instanceof CompoundNBT)
			((CompoundNBT)tag).put(compoundKey, toAdd);
	}

	public static void clearCollectionTag(INBT tag) {
		if (tag instanceof CollectionNBT)
			((CollectionNBT<?>)tag).clear();
		else if (tag instanceof CompoundNBT)
			new HashSet<>(((CompoundNBT)tag).getAllKeys()).forEach(((CompoundNBT)tag)::remove);
	}

	public static int getTagSize(INBT tag) {
		if (tag instanceof CollectionNBT)
			return ((CollectionNBT<?>)tag).size();
		else if (tag instanceof CompoundNBT)
			return ((CompoundNBT)tag).size();

		return 1;
	}
}
