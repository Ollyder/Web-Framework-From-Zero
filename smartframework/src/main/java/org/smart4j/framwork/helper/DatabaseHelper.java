package org.smart4j.framwork.helper;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.taglibs.standard.tag.el.sql.QueryTag;
import org.omg.SendingContext.RunTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smart4j.framwork.util.CollectionUtil;
import org.smart4j.framwork.util.PropsUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class DatabaseHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHelper.class);
    private static final QueryRunner QUERY_RUNNER = new QueryRunner();
    private static final BasicDataSource DATA_SOURCE;
    private static final ThreadLocal<Connection> CONNECTION_HOLDER = new ThreadLocal<Connection>();

    static {

        Properties conf = PropsUtil.loadProps("config.properties");
        String DRIVER = conf.getProperty("jdbc.driver");
        String URL = conf.getProperty("jdbc.url");
        String USERNAME = conf.getProperty("jdbc.username");
        String PASSWORD = conf.getProperty("jdbc.password");
        DATA_SOURCE = new BasicDataSource();
        DATA_SOURCE.setDriverClassName(DRIVER);
        DATA_SOURCE.setUrl(URL);
        DATA_SOURCE.setUsername(USERNAME);
        DATA_SOURCE.setPassword(PASSWORD);
    }

    public static Connection getConnection() {
        Connection conn = CONNECTION_HOLDER.get();
        if(conn == null) {
            try {
                conn = DATA_SOURCE.getConnection();
            } catch (SQLException e) {
                LOGGER.error("get connection failure", e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.set(conn);
            }
        }
        return conn;
    }

    public static <T> List<T> queryEntityList(Class<T> entityClass,String sql,Object... params) {
        List<T> entityList;
        try {
            Connection conn = getConnection();
            entityList = QUERY_RUNNER.query(conn,sql,new BeanListHandler<T>(entityClass),params);
        }catch(SQLException e) {
            LOGGER.error("query entity list failure",e);
            throw new RuntimeException(e);
        }
        return entityList;
    }

    public static <T> T queryEntity(Class<T> entityClass,String sql,Object... params) {
        T entity;
        try {
            Connection conn = getConnection();
            entity = QUERY_RUNNER.query(conn,sql,new BeanHandler<T>(entityClass),params);
        }catch(SQLException e) {
            LOGGER.error("query entity failure",e);
            throw new RuntimeException(e);
        }
        return entity;
    }

    public static List<Map<String,Object>> executeQuery(String sql,Object... params) {
        List<Map<String,Object>> result;
        try {
            Connection conn = getConnection();
            result = QUERY_RUNNER.query(conn,sql,new MapListHandler(),params);
        }catch(SQLException e) {
            LOGGER.error("execute query failure",e);
            throw new RuntimeException(e);
        }
        return result;
    }

    public static int executeUpdate(String sql,Object... params) {
        int rows = 0;
        try {
            Connection conn = getConnection();
            rows = QUERY_RUNNER.update(conn,sql,params);
        }catch(SQLException e) {
            LOGGER.error("execute update failure",e);
            throw new RuntimeException(e);
        }
        return rows;
    }


    public static <T> boolean insertEntity(Class<T> entityClass,Map<String,Object> fieldMap) {
        if(CollectionUtil.isEmpty(fieldMap)) {
            LOGGER.error("can not insert entity: fieldMap is empty");;
            return false;
        }

        String sql = " INSERT INTO "+ getTableName(entityClass);
        StringBuilder columns = new StringBuilder("(");
        StringBuilder values = new StringBuilder("(");
        for(String fieldName:fieldMap.keySet()) {
            columns.append(fieldName).append(", ");
            values.append("?, ");
        }

        columns.replace(columns.lastIndexOf(", "),columns.length(),")");
        values.replace(values.lastIndexOf(", "),values.length(),")");
        sql += columns+" VALUES " + values;

        Object[] params = fieldMap.values().toArray();
        return executeUpdate(sql,params)==1;
    }

    public static <T> boolean updateEntity(Class<T> entityClass,long id,Map<String,Object> fieldMap) {
        if(CollectionUtil.isEmpty(fieldMap)) {
            LOGGER.error("can not update enttiy: fieldMap is Empey");
            return false;
        }

        String sql = " UPDATE " + getTableName(entityClass)+" SET ";
        StringBuilder columns = new StringBuilder();
        for(String fieleName:fieldMap.keySet()) {
            columns.append(fieleName).append("=?, ");
        }
        sql += columns.substring(0,columns.lastIndexOf(", "))+" WHERE id=?";
        List<Object> paramList = new ArrayList<Object>();
        paramList.addAll(fieldMap.values());
        paramList.add(id);
        Object[] params = paramList.toArray();
        return executeUpdate(sql,params) ==1;
    }

    public static <T> boolean deleteEntity(Class<T> entityClass,long id) {
        String sql = " DELETE FROM "+getTableName(entityClass) + " WHERE id = ?";
        return executeUpdate(sql,id) == 1;
    }



//    public static void closeConnection() {
//        Connection conn = CONNECTION_HOLDER.get();
//        if(conn!=null) {
//            try {
//                conn.close();
//            }catch(SQLException e) {
//                LOGGER.error("close connection failure",e);
//                throw new RuntimeException(e);
//            }finally {
//                CONNECTION_HOLDER.remove();
//            }
//        }
//    }

    private static String getTableName(Class<?> entityClass) {
        return entityClass.getSimpleName();
    }

    public static void executeSqlFile(String filePath) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String sql;
        try {
            while ((sql = reader.readLine()) != null) {
                DatabaseHelper.executeUpdate(sql);
            }
        }catch(Exception e) {
            LOGGER.error("execute sql file failure",e);
            throw new RuntimeException(e);
        }
    }


    public static void beginTransaction() {
        Connection conn = getConnection();
        if(conn != null) {
            try {
                conn.setAutoCommit(false);
            }catch (SQLException e) {
                LOGGER.error("begin transaction failure",e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.set(conn);
            }
        }
    }

    public static void commitTransaction() {
        Connection conn = getConnection();
        if(conn!=null) {
            try {
                conn.commit();
                conn.close();
            }catch (SQLException e) {
                LOGGER.error("commit transaction failure",e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.remove();
            }
        }
    }

    public static void rollbackTransaction() {
        Connection conn = getConnection();
        if(conn!=null) {
            try {
                conn.rollback();
                conn.close();
            }catch (SQLException e) {
                LOGGER.error("rollback transaction failure",e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.remove();
            }
        }
    }
}
