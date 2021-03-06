package io.katharsis.utils;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class WrappedListTest {

	@Test
	public void test() {
		WrappedList<String> list = new WrappedList<>(new ArrayList<String>());
		Assert.assertEquals(0, list.size());
		Assert.assertTrue(list.isEmpty());
		Assert.assertFalse(list.contains("something"));
		Assert.assertFalse(list.iterator().hasNext());
		Assert.assertEquals(0, list.toArray().length);
		Assert.assertEquals(0, list.toArray(new String[0]).length);
		Assert.assertTrue(list.add("1"));
		Assert.assertTrue(list.remove("1"));
		Assert.assertFalse(list.containsAll(Arrays.asList("1")));
		Assert.assertTrue(list.addAll(Arrays.asList("1", "2")));
		Assert.assertTrue(list.removeAll(Arrays.asList("2")));
		list.retainAll(Arrays.asList("1", "3"));
		Assert.assertEquals(1, list.size());
		Assert.assertEquals("1", list.get(0));
		Assert.assertEquals("1", list.set(0, "2"));
		list.clear();
		Assert.assertTrue(list.isEmpty());
		list.add("1");
		list.remove(0);
		Assert.assertTrue(list.isEmpty());
		list.add("1");
		Assert.assertEquals(0, list.indexOf("1"));
		Assert.assertEquals(0, list.lastIndexOf("1"));
		Assert.assertEquals(-1, list.indexOf("2"));
		Assert.assertTrue(list.listIterator().hasNext());
		Assert.assertFalse(list.listIterator(1).hasNext());
		Assert.assertEquals(1, list.subList(0, 1).size());
	}
}
