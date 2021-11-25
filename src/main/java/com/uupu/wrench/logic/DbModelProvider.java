package com.uupu.wrench.logic;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


import com.uupu.wrench.model.Column;
import com.uupu.wrench.model.Table;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author qianguangfu
 * @since 2021/11/25
 */
public class DbModelProvider {
    /**
     * Logger for this class
     */
    private static final Log _log = LogFactory.getLog(Generator.class);

    //	Properties props;
    public String catalog;
    public String schema;

    private Connection connection;
    private static DbModelProvider instance = new DbModelProvider();

    private DbModelProvider() {
        init();
    }

    private void init() {

        this.schema = PropertiesProvider.getProperty("jdbc.schema", "");
        if ("".equals(schema.trim())) {
            this.schema = null;
        }
        this.catalog = PropertiesProvider.getProperty("jdbc.catalog", "");
        if ("".equals(catalog.trim())) {
            this.catalog = null;
        }

        System.out.println("jdbc.schema=" + this.schema + " jdbc.catalog=" + this.catalog);
        String driver = PropertiesProvider.getProperty("jdbc_driver");
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static DbModelProvider getInstance() {
        return instance;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(PropertiesProvider.getProperty("jdbc_url"),
                    PropertiesProvider.getProperty("jdbc_username"), PropertiesProvider.getProperty("jdbc_password"));
        }
        return connection;
    }

    public List getAllTables() throws Exception {
        Connection conn = getConnection();
        return getAllTables(conn);
    }

    public Table getTable(String sqlTableName) throws Exception {
        Connection conn = getConnection();
        DatabaseMetaData dbMetaData = conn.getMetaData();
        ResultSet rs = dbMetaData.getTables(catalog, schema, sqlTableName, null);
        while (rs.next()) {
            Table table = createTable(conn, rs);
            return table;
        }
        throw new RuntimeException("not found table with give name:" + sqlTableName);
    }

    private Table createTable(Connection conn, ResultSet rs) throws SQLException {
        ResultSetMetaData rsMetaData = rs.getMetaData();
        String schemaName = rs.getString("TABLE_SCHEM") == null ? "" : rs.getString("TABLE_SCHEM");
        String realTableName = rs.getString("TABLE_NAME");
        String tableType = rs.getString("TABLE_TYPE");
        String tableRemarks = rs.getString("REMARKS") ;
        Table table = new Table();
        table.setSqlName(realTableName);
        table.setRemark(tableRemarks);
        if ("SYNONYM".equals(tableType) && isOracleDataBase()) {
            table.setOwnerSynonymName(getSynonymOwner(realTableName));
        }

        retriveTableColumns(table);

        table.initExportedKeys(conn.getMetaData());
        table.initImportedKeys(conn.getMetaData());
        return table;
    }

    public List getAllTables(Connection conn) throws SQLException {
        DatabaseMetaData dbMetaData = conn.getMetaData();
        ResultSet rs = dbMetaData.getTables(catalog, schema, null, new String[]{"TABLE"});
        List tables = new ArrayList();
        while (rs.next()) {
            Table table = createTable(conn, rs);
            tables.add(table);
        }
        return tables;
    }

    private boolean isOracleDataBase() {
        boolean ret = false;
        try {
            ret = (getMetaData().getDatabaseProductName().toLowerCase().indexOf("oracle") != -1);
        } catch (Exception ignore) {
        }
        return ret;
    }

    private String getSynonymOwner(String synonymName) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        String ret = null;
        try {
            ps = getConnection()
                    .prepareStatement("SELECT table_owner FROM sys.all_synonyms WHERE table_name=? AND owner=?");
            ps.setString(1, synonymName);
            ps.setString(2, schema);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret = rs.getString(1);
            } else {
                String databaseStructure = getDatabaseStructureInfo();
                throw new RuntimeException(
                        "Wow! Synonym " + synonymName + " not found. How can it happen? " + databaseStructure);
            }
        } catch (SQLException e) {
            String databaseStructure = getDatabaseStructureInfo();
            _log.error(e.getMessage(), e);
            throw new RuntimeException("Exception in getting synonym owner " + databaseStructure);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {
                }
            }
            if (ps != null) {
                try {
                    ps.close();
                } catch (Exception e) {
                }
            }
        }
        return ret;
    }

    private String getDatabaseStructureInfo() {
        ResultSet schemaRs = null;
        ResultSet catalogRs = null;
        String nl = System.getProperty("line.separator");
        StringBuffer sb = new StringBuffer(nl);
        sb.append("Configured schema:").append(schema).append(nl);
        sb.append("Configured catalog:").append(catalog).append(nl);

        try {
            schemaRs = getMetaData().getSchemas();
            sb.append("Available schemas:").append(nl);
            while (schemaRs.next()) {
                sb.append("  ").append(schemaRs.getString("TABLE_SCHEM")).append(nl);
            }
        } catch (SQLException e2) {
            _log.warn("Couldn't get schemas", e2);
            sb.append("  ?? Couldn't get schemas ??").append(nl);
        } finally {
            try {
                schemaRs.close();
            } catch (Exception ignore) {
            }
        }

        try {
            catalogRs = getMetaData().getCatalogs();
            sb.append("Available catalogs:").append(nl);
            while (catalogRs.next()) {
                sb.append("  ").append(catalogRs.getString("TABLE_CAT")).append(nl);
            }
        } catch (SQLException e2) {
            _log.warn("Couldn't get catalogs", e2);
            sb.append("  ?? Couldn't get catalogs ??").append(nl);
        } finally {
            try {
                catalogRs.close();
            } catch (Exception ignore) {
            }
        }
        return sb.toString();
    }

    private DatabaseMetaData getMetaData() throws SQLException {
        return getConnection().getMetaData();
    }

    private void retriveTableColumns(Table table) throws SQLException {
        _log.debug("-------setColumns(" + table.getSqlName() + ")");

        List primaryKeys = getTablePrimaryKeys(table);
        table.setPrimaryKeyColumns(primaryKeys);

        // get the indices and unique columns
        List indices = new LinkedList();
        // maps index names to a list of columns in the index
        Map uniqueIndices = new HashMap();
        // maps column names to the index name.
        Map uniqueColumns = new HashMap();
        ResultSet indexRs = null;

        try {

            if (table.getOwnerSynonymName() != null) {
                indexRs =
                        getMetaData().getIndexInfo(catalog, table.getOwnerSynonymName(), table.getSqlName(), false, true);
            } else {
                indexRs = getMetaData().getIndexInfo(catalog, schema, table.getSqlName(), false, true);
            }
            while (indexRs.next()) {
                String columnName = indexRs.getString("COLUMN_NAME");


                if (columnName != null) {
                    _log.debug("index:" + columnName);
                    indices.add(columnName);
                }

                // now look for unique columns
                String indexName = indexRs.getString("INDEX_NAME");
                boolean nonUnique = indexRs.getBoolean("NON_UNIQUE");

                if (!nonUnique && columnName != null && indexName != null) {
                    List l = (List) uniqueColumns.get(indexName);
                    if (l == null) {
                        l = new ArrayList();
                        uniqueColumns.put(indexName, l);
                    }
                    l.add(columnName);
                    uniqueIndices.put(columnName, indexName);
                    _log.debug("unique:" + columnName + " (" + indexName + ")");
                }


            }
        } catch (Throwable t) {
        } finally {
            if (indexRs != null) {
                indexRs.close();
            }
        }

        List columns = getTableColumns(table, primaryKeys, indices, uniqueIndices, uniqueColumns);

        for (Iterator i = columns.iterator(); i.hasNext(); ) {
            Column column = (Column) i.next();
            _log.debug(column.getColumnName() + "-javaType:" + column.getJavaType());
            table.addColumn(column);
            if (Objects.equals(column.getJavaType(), "java.math.BigDecimal")) {
                table.setHasBigDecimal(true);
            }

            //判断是否有status字段
            if(column.getColumnName().equalsIgnoreCase("status")){
                table.setHasStatus(true);
            }

        }

        // In case none of the columns were primary keys, issue a warning.
        if (primaryKeys.size() == 0) {
            _log.warn("WARNING: The JDBC driver didn't report any primary key columns in " + table.getSqlName());
        }
    }

    private List getTableColumns(Table table, List primaryKeys, List indices, Map uniqueIndices, Map uniqueColumns)
            throws SQLException {
        // get the columns
        List columns = new LinkedList();
        ResultSet columnRs = getColumnsResultSet(table);

        while (columnRs.next()) {
            int sqlType = columnRs.getInt("DATA_TYPE");
            String sqlTypeName = columnRs.getString("TYPE_NAME");
            String columnName = columnRs.getString("COLUMN_NAME");
            String columnDefaultValue = columnRs.getString("COLUMN_DEF");
            String columnComment = columnRs.getString("REMARKS");
            if (StringUtils.isNotBlank(columnComment)) {
                columnComment = columnComment.replaceAll("[\\t\\n\\r]", "");
            }
            // if columnNoNulls or columnNullableUnknown assume "not nullable"
            boolean isNullable = (DatabaseMetaData.columnNullable == columnRs.getInt("NULLABLE"));
            int size = columnRs.getInt("COLUMN_SIZE");
            int decimalDigits = columnRs.getInt("DECIMAL_DIGITS");

            boolean isPk = primaryKeys.contains(columnName);
            boolean isIndexed = indices.contains(columnName);
            String uniqueIndex = (String) uniqueIndices.get(columnName);
            List columnsInUniqueIndex = null;
            if (uniqueIndex != null) {
                columnsInUniqueIndex = (List) uniqueColumns.get(uniqueIndex);
            }

            boolean isUnique = columnsInUniqueIndex != null && columnsInUniqueIndex.size() == 1;
            if (isUnique) {
                _log.debug("unique column:" + columnName);
            }
            Column column =
                    new Column(table, sqlType, sqlTypeName, columnName, size, decimalDigits, isPk, isNullable, isIndexed,
                            isUnique, columnDefaultValue, columnComment);
            columns.add(column);
        }
        columnRs.close();
        return columns;
    }

    private ResultSet getColumnsResultSet(Table table) throws SQLException {
        ResultSet columnRs = null;
        if (table.getOwnerSynonymName() != null) {
            columnRs = getMetaData().getColumns(catalog, table.getOwnerSynonymName(), table.getSqlName(), null);
        } else {
            columnRs = getMetaData().getColumns(catalog, schema, table.getSqlName(), null);
        }
        return columnRs;
    }

    private List getTablePrimaryKeys(Table table) throws SQLException {
        // get the primary keys
        List primaryKeys = new LinkedList();
        ResultSet primaryKeyRs = null;
        if (table.getOwnerSynonymName() != null) {
            primaryKeyRs = getMetaData().getPrimaryKeys(catalog, table.getOwnerSynonymName(), table.getSqlName());
        } else {
            primaryKeyRs = getMetaData().getPrimaryKeys(catalog, schema, table.getSqlName());
        }
        while (primaryKeyRs.next()) {
            String columnName = primaryKeyRs.getString("COLUMN_NAME");
            _log.debug("primary key:" + columnName);
            primaryKeys.add(columnName);
        }
        primaryKeyRs.close();
        return primaryKeys;
    }
}
