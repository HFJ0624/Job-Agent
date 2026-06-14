package com.job.test;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;

/**
 * 作者:hfj
 * 功能:
 * 日期: 2026/6/10 11:24
 */
public class Eat {

    @Test
    public void test(){
        Random random = new Random();
        HashMap<Integer, String> map = new HashMap<>();
        map.put(0,"面馆");
        map.put(1,"炒菜");
        map.put(2,"馅饼烧卖");

        System.out.println(map.get(random.nextInt(2)));
    }
}
