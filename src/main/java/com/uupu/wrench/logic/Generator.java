package com.uupu.wrench.logic;

import com.uupu.wrench.model.Table;
import com.uupu.wrench.util.FileHelper;
import com.uupu.wrench.util.IOHelper;
import com.uupu.wrench.util.StringHelper;
import com.uupu.wrench.util.StringTemplate;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;

/**
 * @author qianguangfu
 * @since 2021/11/25
 */
public class Generator {
    private static final String WEBAPP_GENERATOR_INSERT_LOCATION = "webapp-generator-insert-location";

    public Generator() {
    }

    public void generateAllTable() throws Exception {
        List tables = DbModelProvider.getInstance().getAllTables();
        File templateRootDir = getTemplatePath();
        Configuration config = getConfiguration(templateRootDir);
        for (int j = 0; j < tables.size(); j++) {
            generateTable((Table)tables.get(j), config, templateRootDir);
        }
    }

    private Configuration getConfiguration(File templateRootDir) throws IOException {
        Configuration config = new Configuration(Configuration.VERSION_2_3_22);
        System.out.println("template root:" + templateRootDir.getAbsolutePath());
        config.setDefaultEncoding("UTF-8");
        config.setOutputEncoding("UTF-8");
        config.setEncoding(Locale.SIMPLIFIED_CHINESE,"UTF-8");
        config.setDirectoryForTemplateLoading(templateRootDir);
        config.setNumberFormat("###############");
        config.setBooleanFormat("true,false");
        return config;
    }

    private File getTemplatePath() {
        String template = PropertiesProvider.getProperty("templatePath", "template");
        return new File(template).getAbsoluteFile();
    }

    public void generateSelectTables() throws Exception {
        String tables = PropertiesProvider.getProperty("tables", "");
        if("".equals(tables)){
            generateAllTable();
        }else {
            String[] tableArr = tables.split(",");
            File templateRootDir = getTemplatePath();
            Configuration config = getConfiguration(templateRootDir);
            for (int j = 0; j < tableArr.length; j++) {
                generateTable(tableArr[j], config, templateRootDir);
            }
        }
    }

    public void generateTable(String tableName, Configuration config, File templateRootDir) throws Exception {
        Table table = DbModelProvider.getInstance().getTable(tableName);
        generateTable(table, config, templateRootDir);
    }

    private void generateTable(Table table, Configuration config, File templateRootDir)
            throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            TemplateException {

        List files = new ArrayList();
        FileHelper.listFiles(templateRootDir, files);

        for (int i = 0; i < files.size(); i++) {
            File file = (File)files.get(i);
            String templateRelativePath = FileHelper.getRelativePath(templateRootDir, file);
            String outputFilePath = templateRelativePath;
            if (file.isDirectory() || file.isHidden()) {
                continue;
            }
            if (templateRelativePath.trim().equals("")) {
                continue;
            }
            if (file.getName().toLowerCase().endsWith(".include")) {
                System.out.println("[skip]\t\t endsWith '.include' template:" + templateRelativePath);
                continue;
            }
            int testExpressionIndex = -1;
            if ((testExpressionIndex = templateRelativePath.indexOf('@')) != -1) {
                outputFilePath = templateRelativePath.substring(0, testExpressionIndex);
                String testExpressionKey = templateRelativePath.substring(testExpressionIndex + 1);
                Map map = getFilepathDataModel(table);
                Object expressionValue = map.get(testExpressionKey);
                if (!"true".equals(expressionValue.toString())) {
                    System.out.println(
                            "[not-generate]\t test expression '@" + testExpressionKey + "' is false,template:"
                                    + templateRelativePath);
                    continue;
                }
            }
            try {
                generateFile(table, config, templateRelativePath, outputFilePath);
            } catch (Exception e) {
                throw new RuntimeException(
                        "generate table '" + table.getSqlName() + "' oucur error,template is:" + templateRelativePath, e);
            }
        }
    }

    @SuppressWarnings("all")
    private void generateFile(Table table, Configuration config, String templateRelativePath, String outputFilePath)
            throws IOException, IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            TemplateException {
        Template template = config.getTemplate(templateRelativePath);

        //将sys_user 前缀去掉，修改className首字母大写
        String sqlName = table.getSqlName();
        String tableRemovePrefixes = (String)PropertiesProvider.getProperties().get("tableRemovePrefixes");
        String[] prefixs = tableRemovePrefixes.split(",");
        for (String prefix : prefixs) {
            if (sqlName.contains(prefix)) {
                sqlName = sqlName.replace(prefix, "").trim();
                table.setClassName(StringHelper.makeAllWordFirstLetterUpperCase(sqlName));
                table.setClassNameLower(
                        StringHelper.uncapitalize(StringHelper.makeAllWordFirstLetterUpperCase(sqlName)));
            }
        }

        String targetFilename = getTargetFilename(table, outputFilePath);

        Map templateDataModel = getTemplateDataModel(table);
        File absoluteOutputFilePath = getAbsoluteOutputFilePath(targetFilename);
        if (absoluteOutputFilePath.exists()) {
            StringWriter newFileContentCollector = new StringWriter();
            if (isFoundInsertLocation(template, templateDataModel, absoluteOutputFilePath, newFileContentCollector)) {
                IOHelper.saveFile(absoluteOutputFilePath, newFileContentCollector.toString());
                return;
            }
        }

        saveNewOutputFileContent(template, templateDataModel, absoluteOutputFilePath);
    }

    @SuppressWarnings("all")
    private String getTargetFilename(Table table, String templateFilepath)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Map fileModel = getFilepathDataModel(table);
        String targetFilename = resolveFile(templateFilepath, fileModel);
        return targetFilename;
    }

    /**
     * 得到生成"文件目录/文件路径"的Model
     **/
    private Map getFilepathDataModel(Table table)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Map fileModel = BeanUtils.describe(table);
        fileModel.putAll(PropertiesProvider.getProperties());
        return fileModel;
    }

    /**
     * 得到FreeMarker的Model
     **/
    @SuppressWarnings("all")
    private Map getTemplateDataModel(Table table) {
        Map model = new HashMap();
        model.putAll(PropertiesProvider.getProperties());
        model.put("table", table);
        return model;
    }

    private File getAbsoluteOutputFilePath(String targetFilename) {
        String outRoot = getOutRootDir();
        File outputFile = new File(outRoot, targetFilename);
        outputFile.getParentFile().mkdirs();
        return outputFile;
    }

    private boolean isFoundInsertLocation(Template template, Map model, File outputFile, StringWriter newFileContent)
            throws IOException, TemplateException {
        LineNumberReader reader = new LineNumberReader(new FileReader(outputFile));
        String line = null;
        boolean isFoundInsertLocation = false;

        PrintWriter writer = new PrintWriter(newFileContent);
        while ((line = reader.readLine()) != null) {
            writer.println(line);
            // only insert once
            if (!isFoundInsertLocation && line.indexOf(WEBAPP_GENERATOR_INSERT_LOCATION) >= 0) {
                template.process(model, writer);
                writer.println();
                isFoundInsertLocation = true;
            }
        }

        writer.close();
        reader.close();
        return isFoundInsertLocation;
    }

    private void saveNewOutputFileContent(Template template, Map model, File outputFile)
            throws IOException, TemplateException {
        Writer out=new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        template.process(model, out);
        out.close();
    }

    @SuppressWarnings("all")
    private String resolveFile(String templateFilepath, Map fileModel) {
        return new StringTemplate(templateFilepath, fileModel).toString();
    }

    public void clean() throws IOException {
        String outRoot = getOutRootDir();
        File srcFile = new File(outRoot);
        if (srcFile.exists()) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddhh24mmss");
            if (outRoot.endsWith(File.separator)) {
                outRoot = outRoot.substring(0, outRoot.length() - 1);
            }
            outRoot = outRoot + "-bak" + File.separator;
            String backupRoot = outRoot + dtf.format(LocalDateTime.now());
            System.out.println(srcFile.getAbsolutePath() + ":" + backupRoot);
            FileUtils.moveDirectoryToDirectory(srcFile, new File(backupRoot), true);
            System.out.println("[Backup Dir]	" + backupRoot);
        }
    }

    private String getOutRootDir() {
        String outRootDir = PropertiesProvider.getProperties().getProperty("outRootDir");
        String projectFolder = PropertiesProvider.getProperties().getProperty("projectName");
        String fullDir;
        if (outRootDir != null && !outRootDir.isEmpty()) {
            if (!outRootDir.endsWith(File.separator)) {
                outRootDir += File.separator;
            }
            fullDir = outRootDir + projectFolder;
        } else {
            fullDir = projectFolder;
        }
        return fullDir;
    }
}
