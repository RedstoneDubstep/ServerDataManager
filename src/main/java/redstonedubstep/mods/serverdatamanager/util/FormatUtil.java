package redstonedubstep.mods.serverdatamanager.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

public class FormatUtil {
	public static List<String> splitStringsToPage(List<String> list, int currentPage, int maxSizePerPage) {
		int listContentSize = list.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();

		if (listContentSize > maxSizePerPage) { //If the tag is too large, truncate it to only display the entries on the given page (there are 50 entries per page)
			int totalPages = (int)Math.ceil(listContentSize / (double)maxSizePerPage);
			int entriesPerPage = (int)Math.ceil(list.size() / (double)totalPages);

			return splitToPage(list, currentPage, entriesPerPage);
		}

		return list;
	}

	public static <T> List<T> splitToPage(List<T> list, int currentPage, int entriesPerPage) {
		if (list.size() > entriesPerPage) { //If the tag is too large, truncate it to only display the entries on the given page (there are 50 entries per page)
			int totalPages = (int)Math.ceil(list.size() / (double)entriesPerPage);

			if (currentPage > totalPages - 1)
				currentPage = totalPages - 1;

			List<T> truncatedList = new ArrayList<>();

			for (int i = currentPage * entriesPerPage; i <= list.size() - 1 && i < (currentPage + 1) * entriesPerPage; i++)
				truncatedList.add(list.get(i));

			return truncatedList;
		}

		return list;
	}

	public static List<Pair<String, String>> splitLogLines(List<String> logLines) {
		return logLines.stream().map(s -> Pair.of(s, s.split("(\\[[^]]*].){3}")[1])).map(p -> Pair.of(p.getLeft().substring(0, p.getLeft().length() - p.getRight().length()), p.getRight())).toList();
	}

	public static <T> Stream<T> safeArrayStream(T[] array) {
		if (array != null)
			return Arrays.stream(array);
		else
			return Stream.of();
	}
}
