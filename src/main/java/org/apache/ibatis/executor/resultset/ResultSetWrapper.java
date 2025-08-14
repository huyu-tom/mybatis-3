/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Iwao AVE!
 */
public class ResultSetWrapper {

  private final ResultSet resultSet;
  private final TypeHandlerRegistry typeHandlerRegistry;

  private final List<String> columnNames;
  private final List<String> classNames = new ArrayList<>();
  private final List<JdbcType> jdbcTypes = new ArrayList<>();

  // Key为字段名称
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<>();

  // key是 resultMap的ID + "," + resultMap的前缀字段
  private final Map<String, List<String>> mappedColumnNamesMap = new HashMap<>();

  // key是 resultMap的ID + "," + resultMap的前缀字段
  private final Map<String, List<String>> unMappedColumnNamesMap = new HashMap<>();

  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.resultSet = rs;

    // 用于自动映射
    final ResultSetMetaData metaData = rs.getMetaData();
    final int columnCount = metaData.getColumnCount();

    // todo 可以优化的地方 ,而是在这里进行初始化
    columnNames = new ArrayList<>(columnCount);

    for (int i = 1; i <= columnCount; i++) {
      // label 表示别名 as xxx
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));

      // JDBC类型
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));

      // 调用 resultset.getObject()的时候,指定返回object的全类名限定符
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public List<JdbcType> getJdbcTypes() {
    return jdbcTypes;
  }

  /**
   * 通过字段来获取jdbc的类型
   *
   * @param columnName
   *
   * @return
   */
  public JdbcType getJdbcType(String columnName) {
    for (int i = 0; i < columnNames.size(); i++) {
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * Gets the type handler to use when reading the result set. Tries to get from the TypeHandlerRegistry by searching
   * for the property type. If not found it gets the column JDBC type and tries to get a handler for it.
   *
   * @param propertyType
   *          the property type
   * @param columnName
   *          the column name
   *
   * @return the type handler
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    if (columnHandlers == null) {
      columnHandlers = new HashMap<>();
      typeHandlerMap.put(columnName, columnHandlers);
    } else {
      handler = columnHandlers.get(propertyType);
    }

    if (handler == null) {
      JdbcType jdbcType = getJdbcType(columnName);

      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      if (handler == null || handler instanceof UnknownTypeHandler) {
        final int index = columnNames.indexOf(columnName);

        // java的类型
        final Class<?> javaType = resolveClass(classNames.get(index));

        if (javaType != null && jdbcType != null) {
          // Java的类型和JDBC的类型都不为空
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
        } else if (javaType != null) {
          // java的类型不为空,jdbc的类型为空
          handler = typeHandlerRegistry.getTypeHandler(javaType);
        } else if (jdbcType != null) {
          // jdbc的类型不为空,java的类型为空
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }

      if (handler == null || handler instanceof UnknownTypeHandler) {
        // 如果类型为空,就用默认的类型处理器
        handler = new ObjectTypeHandler();
      }

      columnHandlers.put(propertyType, handler);
    }
    return handler;
  }

  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  /**
   * 加载映射和未映射的字段名称
   *
   * @param resultMap
   * @param columnPrefix
   *
   * @throws SQLException
   */
  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = new ArrayList<>();
    List<String> unmappedColumnNames = new ArrayList<>();

    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);

    // 这里是已映射的字段 (如果存在前缀,就给字段加上前缀)
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);

    for (String columnName : columnNames) {
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      if (mappedColumns.contains(upperColumnName)) {
        // 已映射
        mappedColumnNames.add(upperColumnName);
      } else {
        // 未映射
        unmappedColumnNames.add(columnName);
      }
    }

    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  /**
   * 获取映射的字段名称
   *
   * @param resultMap
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (mappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

  /**
   * 获取未映射的字段名称
   *
   * @param resultMap
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    if (unMappedColumnNames == null) {
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return unMappedColumnNames;
  }

  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    final Set<String> prefixed = new HashSet<>();
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }

}
