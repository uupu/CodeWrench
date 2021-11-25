package com.uupu.wrench.model;

import com.uupu.wrench.logic.DbModelProvider;
import com.uupu.wrench.util.StringHelper;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * @author qianguangfu
 * @since 2021/11/25
 */
public class Table {

    String sqlName;
    String className;
    String classNameLower;
    String remark;

    boolean hasStatus = false;

    boolean hasBigDecimal = false;

    public boolean isHasStatus(){
        return hasStatus;
    }

    public boolean isHasBigDecimal() {
        return hasBigDecimal;
    }

    public void setHasStatus(boolean hasStatus){
        this.hasStatus = hasStatus;
    }
    public void setHasBigDecimal(boolean hasBigDecimal) {
        this.hasBigDecimal = hasBigDecimal;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * the name of the owner of the synonym if this table is a synonym
     */
    private String ownerSynonymName = null;
    List columns = new ArrayList();
    List primaryKeyColumns = new ArrayList();

    public String getClassName() {
        return className == null ? StringHelper.makeAllWordFirstLetterUpperCase(getSqlName()) : className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassNameLower() {
        return classNameLower == null ? StringHelper.makeAllWordFirstLetterLowerCase(getSqlName()) : classNameLower;
    }

    public void setClassNameLower(String classNameLower) {
        this.classNameLower = classNameLower;
    }

    public List getColumns() {
        return columns;
    }

    public void setColumns(List columns) {
        this.columns = columns;
    }

    public String getOwnerSynonymName() {
        return ownerSynonymName;
    }

    public void setOwnerSynonymName(String ownerSynonymName) {
        this.ownerSynonymName = ownerSynonymName;
    }

    public List getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public void setPrimaryKeyColumns(List primaryKeyColumns) {
        this.primaryKeyColumns = primaryKeyColumns;
    }

    public String getSqlName() {
        return sqlName;
    }

    public void setSqlName(String sqlName) {
        this.sqlName = sqlName;
    }

    public void addColumn(Column column) {
        columns.add(column);
    }

    public boolean isSingleId() {
        int pkCount = 0;
        for (int i = 0; i < columns.size(); i++) {
            Column c = (Column) columns.get(i);
            if (c.isPk()) {
                pkCount++;
            }
        }
        return pkCount > 1 ? false : true;
    }

    public boolean isCompositeId() {
        return !isSingleId();
    }

    public List getCompositeIdColumns() {
        List results = new ArrayList();
        List columns = getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column c = (Column) columns.get(i);
            if (c.isPk()) {
                results.add(c);
            }
        }
        return results;
    }

    public Column getIdColumn() {
        List columns = getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column c = (Column) columns.get(i);
            if (c.isPk()) {
                return c;
            }
        }
        return null;
    }

    /**
     * This method was created in VisualAge.
     */
    public void initImportedKeys(DatabaseMetaData dbmd) throws java.sql.SQLException {

        // get imported keys a

        ResultSet fkeys = dbmd.getImportedKeys(catalog, schema, this.sqlName);

        while (fkeys.next()) {
            System.out.println("initImportedKeys:");
            System.out.println(fkeys);
            String pktable = fkeys.getString(PKTABLE_NAME);
            String pkcol = fkeys.getString(PKCOLUMN_NAME);
            String fktable = fkeys.getString(FKTABLE_NAME);
            String fkcol = fkeys.getString(FKCOLUMN_NAME);
            String seq = fkeys.getString(KEY_SEQ);
            Integer iseq = new Integer(seq);
            getImportedKeys().addForeignKey(pktable, pkcol, fkcol, iseq);
        }
        fkeys.close();
    }

    /**
     * This method was created in VisualAge.
     */
    public void initExportedKeys(DatabaseMetaData dbmd) throws java.sql.SQLException {
        // get Exported keys

        ResultSet fkeys = dbmd.getExportedKeys(catalog, schema, this.sqlName);

        while (fkeys.next()) {
            System.out.println("initExportedKeys:");
            System.out.println(fkeys);
            String pktable = fkeys.getString(PKTABLE_NAME);
            String pkcol = fkeys.getString(PKCOLUMN_NAME);
            String fktable = fkeys.getString(FKTABLE_NAME);
            String fkcol = fkeys.getString(FKCOLUMN_NAME);
            String seq = fkeys.getString(KEY_SEQ);
            Integer iseq = new Integer(seq);
            getExportedKeys().addForeignKey(fktable, fkcol, pkcol, iseq);
        }
        fkeys.close();
    }

    /**
     * @return Returns the exportedKeys.
     */
    public ForeignKeys getExportedKeys() {
        if (exportedKeys == null) {
            exportedKeys = new ForeignKeys(this);
        }
        return exportedKeys;
    }

    /**
     * @return Returns the importedKeys.
     */
    public ForeignKeys getImportedKeys() {
        if (importedKeys == null) {
            importedKeys = new ForeignKeys(this);
        }
        return importedKeys;
    }

    String catalog = DbModelProvider.getInstance().catalog;
    String schema = DbModelProvider.getInstance().schema;

    private ForeignKeys exportedKeys;
    private ForeignKeys importedKeys;

    public static final String PKTABLE_NAME = "PKTABLE_NAME";
    public static final String PKCOLUMN_NAME = "PKCOLUMN_NAME";
    public static final String FKTABLE_NAME = "FKTABLE_NAME";
    public static final String FKCOLUMN_NAME = "FKCOLUMN_NAME";
    public static final String KEY_SEQ = "KEY_SEQ";
}
