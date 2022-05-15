package redstonedubstep.mods.serverdataaccessor.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public class TagFormatUtil {
	public static ListTag formatResourceEntriesToKeys(ListTag listTag) {
		if (listTag.getElementType() == Tag.TAG_COMPOUND) { //When the id tag gets referenced directly, only show the resource keys as string tags to truncate all the {} and resource ids, since these are not worth showing
			ListTag onlyKeyList = new ListTag();

			listTag.forEach(tag -> onlyKeyList.add(StringTag.valueOf(((CompoundTag)tag).getString("K"))));
			return onlyKeyList;
		}

		return listTag;
	}

	public static void removeNestedCollectionTags(CompoundTag tag) {
		if (tag.size() > 0) {
			List<Pair<String, Integer>> nestedTags = tag.getAllKeys().stream().filter(s -> tag.get(s) instanceof CollectionTag || tag.get(s) instanceof CompoundTag).map(s -> Pair.of(s, TagFormatUtil.getTagSize(tag.get(s)))).toList();

			nestedTags.forEach(p -> tag.put(p.getLeft(), StringTag.valueOf("{" + p.getRight() + " entries}")));
		}
	}

	public static void splitTagToPage(Tag tag, int currentPage, int entriesPerPage) {
		List<Pair<String, Tag>> tagList = new ArrayList<>();

		if (tag instanceof CollectionTag<? extends Tag> collectionTag)
			collectionTag.forEach(t -> tagList.add(Pair.of(null, t)));
		else if (tag instanceof CompoundTag compoundTag)
			compoundTag.getAllKeys().forEach(s -> tagList.add(Pair.of(s, compoundTag.get(s))));

		List<Pair<String, Tag>> truncatedTagList = FormatUtil.splitToPage(tagList, currentPage, entriesPerPage);
		clearAndAddCollection(tag, truncatedTagList);
	}

	public static void clearAndAddCollection(Tag tag, List<Pair<String, Tag>> list) {
		clearCollectionTag(tag);

		list.forEach(p -> addToCollectionTag(tag, p.getRight(), p.getLeft()));
	}

	public static void addToCollectionTag(Tag tag, Tag toAdd, String compoundKey) {
		if (tag instanceof CollectionTag) {
			if (tag instanceof ByteArrayTag arrayTag && toAdd instanceof ByteTag byteTag)
				arrayTag.add(byteTag);
			else if (tag instanceof IntArrayTag arrayTag && toAdd instanceof IntTag intTag)
				arrayTag.add(intTag);
			else if (tag instanceof ListTag listTag)
				listTag.add(toAdd);
			else if (tag instanceof LongArrayTag arrayTag && toAdd instanceof LongTag longTag)
				arrayTag.add(longTag);
		}
		else if (tag instanceof CompoundTag compoundTag)
			compoundTag.put(compoundKey, toAdd);
	}

	public static void clearCollectionTag(Tag tag) {
		if (tag instanceof CollectionTag collectionTag)
			collectionTag.clear();
		else if (tag instanceof CompoundTag compoundTag)
			new HashSet<>(compoundTag.getAllKeys()).forEach(compoundTag::remove);
	}

	public static int getTagSize(Tag tag) {
		if (tag instanceof CollectionTag collectionTag)
			return collectionTag.size();
		else if (tag instanceof CompoundTag compoundTag)
			return compoundTag.size();

		return 1;
	}
}
