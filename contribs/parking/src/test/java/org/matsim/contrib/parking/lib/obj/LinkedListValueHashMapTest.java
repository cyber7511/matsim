package org.matsim.contrib.parking.lib.obj;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.Test;
import org.matsim.contrib.parking.parkingchoice.lib.obj.HashMapInverter;
import org.matsim.contrib.parking.parkingchoice.lib.obj.LinkedListValueHashMap;

public class LinkedListValueHashMapTest {

	@Test public void testInteger(){
		LinkedListValueHashMap<Integer,Integer> hm1=new LinkedListValueHashMap<Integer, Integer>();

		hm1.put(1, 1);
		hm1.get(1);
		assertEquals(1, hm1.size());
		assertEquals(1, (int) hm1.get(1).get(0));
	}

	@Test public void testStrings(){
		LinkedListValueHashMap<String,String> hm1=new LinkedListValueHashMap<String, String>();

		hm1.put("1", "1");
		hm1.get("1");
		assertEquals(1, hm1.size());
		assertEquals("1", hm1.get("1").get(0));
	}

	@Test public void testHashMapInverter(){
		HashMap<String, String> hashMap=new HashMap<String, String>();

		hashMap.put("0", "1");
		hashMap.put("1", "1");

		LinkedListValueHashMap<String,String> hm1=new HashMapInverter(hashMap).getLinkedListValueHashMap();

		assertEquals(2, hm1.get("1").size());
	}

}
