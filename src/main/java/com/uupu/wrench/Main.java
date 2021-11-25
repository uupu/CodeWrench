package com.uupu.wrench;

import com.uupu.wrench.logic.Generator;
import com.uupu.wrench.logic.PropertiesProvider;

/**
 * @author qianguangfu
 * @since 2021/11/25
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Generator g = new Generator();
        String createMode = PropertiesProvider.getProperty("createMode", "rebuild");
        if (!"cover".equals(createMode)) {
            g.clean();
        }
        g.generateSelectTables();
        System.exit(0);
    }

}
