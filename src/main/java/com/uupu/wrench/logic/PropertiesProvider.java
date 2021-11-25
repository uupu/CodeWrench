package com.uupu.wrench.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author qianguangfu
 * @since 2021/11/25
 */
public class PropertiesProvider {

    static Properties props;

    private PropertiesProvider() {
    }

    private static void initProperties() {
        try {
            //读取配置文件，如果没有指定配置文件，则读取默认配置文件config.properties（先查找同级目录，没有再找resources目录）
            String envConfig = System.getProperty("config");
            if (envConfig == null || "".equals(envConfig)) {
                envConfig = "config.properties";
            }
            if (!envConfig.endsWith(".properties")) {
                envConfig += ".properties";
            }
            props = loadAllProperties(envConfig);
            //把类包处理成目录并放到props里以待用。
            String basepackage = props.getProperty("basepackage");
            String basepackage_dir = basepackage.replace('.', '/');
            props.put("basepackage_dir", basepackage_dir);

            //把工程名处理成驼峰写法并放到props里以待用。
            String projectname = props.getProperty("projectName");
            String projectName="";
            for (String s : projectname.split("-")) {
                projectName += s.substring(0,1).toUpperCase()+s.substring(1);
            }
            props.put("projectNameCamel",projectName);
            //输出全部props属性
            props.forEach((key, value) -> System.out.println("[Property] " + key + "=" + value));

        } catch (IOException e) {
            throw new RuntimeException("Load Properties error", e);
        }
    }

    public static Properties getProperties() {
        if (props == null) {
            initProperties();
        }
        return props;
    }

    public static String getProperty(String key, String defaultValue) {
        return getProperties().getProperty(key, defaultValue);
    }

    public static String getProperty(String key) {
        return getProperties().getProperty(key);
    }

    public static Properties loadAllProperties(String resourceName) throws IOException {
        Properties properties = new Properties();
        InputStream is;
        //读取当前文件夹的
        if (new File(resourceName).exists()) {
            is = new FileInputStream(resourceName);
        } else {
            //读取resources文件夹的
            is = PropertiesProvider.class.getClassLoader().getResourceAsStream(resourceName);
        }
        properties.load(is);
        return properties;
    }
}
