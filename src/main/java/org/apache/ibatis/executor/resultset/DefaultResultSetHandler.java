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

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.ibatis.annotations.AutomapConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.cursor.defaults.DefaultCursor;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * MyBatis的结果集映射 1. 鉴别器(通过结果集中的一行,来判定该行用什么resultMap),并且要最先执行, 他包一层的ResultMap(只是包装):
 *
 * <pre>
 *  <resultMap id="vehicleResult" type="Vehicle">
 *   <id property="id" column="id" />
 *   <result property="vin" column="vin"/>
 *   <result property="year" column="year"/>
 *   <result property="make" column="make"/>
 *   <result property="model" column="model"/>
 *   <result property="color" column="color"/>
 *   <discriminator javaType="int" column="vehicle_type">
 *     <case value="1" resultType="carResult">
 *       <result property="doorCount" column="door_count" />
 *     </case>
 *     <case value="2" resultType="truckResult">
 *       <result property="boxSize" column="box_size" />
 *       <result property="extendedCab" column="extended_cab" />
 *     </case>
 *     <case value="3" resultType="vanResult">
 *       <result property="powerSlidingDoor" column="power_sliding_door" />
 *     </case>
 *     <case value="4" resultType="suvResult">
 *       <result property="allWheelDrive" column="all_wheel_drive" />
 *     </case>
 *   </discriminator>
 * </resultMap>
 * </pre>
 * <p>
 * <p>
 * 2.多结果集
 *
 * <pre>
 *   1. 多结果,是不相关的,相当于直接添加到集合里面(类似于sql的union或者union all),一个结果集解析需要指定一个ResultMap (按照mybatis的逻辑是不支持的)
 *       1.  select标签可以动态表示 结果集为多个,号隔开,但是是在解析的时候,不是在执行的时候,所以ResultMap好像做不到
 *       2.  就算驱动支持,但是在mybatis封装参数的时候,就算能返回多个结果集,也是拿到之前解析好的,不能动态,(只会拿到resultMap个数的结果集(ResultSet))
 *   2. 多结果集,是相关的(都在同一个ResultMap)
 *    <select id="selectBlog" resultSets="blogs,posts" resultMap="blogResult">
 *      {call getBlogsAndPosts(#{id,jdbcType=INTEGER,mode=IN})}
 *   </select>
 *
 *   <resultMap id="blogResult" type="Blog">
 *     <id property="id" column="id" />
 *     <result property="title" column="title"/>
 *      <collection property="posts" ofType="Post" resultSet="posts" column="id" foreignColumn="blog_id">
 *         <id property="id" column="id"/>
 *         <result property="subject" column="subject"/>
 *         <result property="body" column="body"/>
 *     </collection>
 *  </resultMap>
 * </pre>
 *
 * 3.嵌套 , 一对多和一对一都是一种嵌套
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Iwao AVE!
 * @author Kazuki Shimizu
 */
public class DefaultResultSetHandler implements ResultSetHandler {

  private static final Object DEFERRED = new Object();

  // 执行器
  private final Executor executor;

  // 全局配置
  private final Configuration configuration;

  // 代表实例
  private final MappedStatement mappedStatement;

  // 代表内存分页
  private final RowBounds rowBounds;

  // 入参处理
  private final ParameterHandler parameterHandler;

  // 结果集处理
  private final ResultHandler<?> resultHandler;

  // 代表当前的sql
  private final BoundSql boundSql;

  // 类型处理器的注册器
  private final TypeHandlerRegistry typeHandlerRegistry;

  // 对象工厂
  private final ObjectFactory objectFactory;

  // 反射工厂
  private final ReflectorFactory reflectorFactory;

  // nested resultmaps
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<>();
  private final Map<String, Object> ancestorObjects = new HashMap<>();
  // 上一行值
  private Object previousRowValue;

  // multiple resultsets , 嵌套查询和多个resultset的逻辑 , 在select标签里面写入 resultset="a,b,c",
  // 将多个结果集的数据融合成一个resultMap展示出来的结果集
  private final Map<String, ResultMapping> nextResultMaps = new HashMap<>();
  private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<>();

  // Cached Automappings
  // key为resultMapId+字段前缀
  private final Map<String, List<UnMappedColumnAutoMapping>> autoMappingsCache = new HashMap<>();
  // Key为resultMapId+字段前缀
  private final Map<String, List<String>> constructorAutoMappingColumns = new HashMap<>();

  // temporary marking flag that indicate using constructor mapping (use field to reduce memory usage)
  private boolean useConstructorMappings;

  private static class PendingRelation {
    public MetaObject metaObject;
    public ResultMapping propertyMapping;
  }

  /**
   * 不知道字段的自动映射
   * <p>
   * <p>
   * 通过resultset的 metaData,从里面获取字段名称,从里面获取jdbc给我们返回的类型 然后再通过jdbc返回的java类型, => 然后再转换为我们程序所需要的类型
   */
  private static class UnMappedColumnAutoMapping {
    // sql的select的字段
    private final String column;
    // Java对象属性名称
    private final String property;
    // 类型处理 java的类型=>转化为
    private final TypeHandler<?> typeHandler;
    // 是否私有
    private final boolean primitive;

    public UnMappedColumnAutoMapping(String column, String property, TypeHandler<?> typeHandler, boolean primitive) {
      this.column = column;
      this.property = property;
      this.typeHandler = typeHandler;
      this.primitive = primitive;
    }
  }

  public DefaultResultSetHandler(Executor executor,
      // 代表这个方法
      MappedStatement mappedStatement,
      // 参数处理器
      ParameterHandler parameterHandler, ResultHandler<?> resultHandler,
      // sql(包含了sql和sql的参数(?的参数)和参数的数据)
      BoundSql boundSql,
      // 内存分页
      RowBounds rowBounds) {
    this.executor = executor;
    this.configuration = mappedStatement.getConfiguration();
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.parameterHandler = parameterHandler;
    this.boundSql = boundSql;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.reflectorFactory = configuration.getReflectorFactory();
    this.resultHandler = resultHandler;
  }

  //
  // HANDLE OUTPUT PARAMETER
  //

  @Override
  public void handleOutputParameters(CallableStatement cs) throws SQLException {
    final Object parameterObject = parameterHandler.getParameterObject();
    final MetaObject metaParam = configuration.newMetaObject(parameterObject);
    final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    for (int i = 0; i < parameterMappings.size(); i++) {
      final ParameterMapping parameterMapping = parameterMappings.get(i);
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        if (ResultSet.class.equals(parameterMapping.getJavaType())) {
          handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
        } else {
          final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
          metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
        }
      }
    }
  }

  private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam)
      throws SQLException {
    if (rs == null) {
      return;
    }
    try {
      final String resultMapId = parameterMapping.getResultMapId();
      final ResultMap resultMap = configuration.getResultMap(resultMapId);
      final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
      if (this.resultHandler == null) {
        final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
        metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
      } else {
        handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rs);
    }
  }

  //
  // HANDLE RESULT SETS
  //
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());

    // 多结果集
    final List<Object> multipleResults = new ArrayList<>();

    int resultSetCount = 0;
    ResultSetWrapper rsw = getFirstResultSet(stmt);

    // 拿到ResultMap的结果集映射
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();

    int resultMapCount = resultMaps.size();
    validateResultMapsCount(rsw, resultMapCount);

    // 多批次结果集(有多少个结果集,就要有多个ResultMap(要不然就没有作用))
    while (rsw != null && resultMapCount > resultSetCount) {
      ResultMap resultMap = resultMaps.get(resultSetCount);

      // 核心 resultSet
      handleResultSet(rsw, resultMap, multipleResults, null);

      // 拿到下一个结果集
      rsw = getNextResultSet(stmt);

      // 处理结果集后清理
      cleanUpAfterHandlingResultSet();

      // 现在遍历到的结果集的索引位置和相当于也是对应多结果集的映射的索引位置
      resultSetCount++;
    }

    // 这里也是多resultSet
    String[] resultSets = mappedStatement.getResultSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {

        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          handleResultSet(rsw, resultMap, null, parentMapping);
        }

        rsw = getNextResultSet(stmt);
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    //
    return collapseSingleResultList(multipleResults);
  }

  @Override
  public <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling cursor results").object(mappedStatement.getId());

    ResultSetWrapper rsw = getFirstResultSet(stmt);

    List<ResultMap> resultMaps = mappedStatement.getResultMaps();

    int resultMapCount = resultMaps.size();

    validateResultMapsCount(rsw, resultMapCount);

    if (resultMapCount != 1) {
      throw new ExecutorException("Cursor results cannot be mapped to multiple resultMaps");
    }

    ResultMap resultMap = resultMaps.get(0);
    return new DefaultCursor<>(this, resultMap, rsw, rowBounds);
  }

  private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
    ResultSet rs = stmt.getResultSet();
    while (rs == null) {
      // move forward to get the first resultset in case the driver
      // doesn't return the resultset as the first result (HSQLDB 2.1)
      if (stmt.getMoreResults()) {
        rs = stmt.getResultSet();
      } else if (stmt.getUpdateCount() == -1) {
        // no more results. Must be no resultset
        break;
      }
    }
    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
  }

  private ResultSetWrapper getNextResultSet(Statement stmt) {
    // Making this method tolerant of bad JDBC drivers
    try {
      if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
        // Crazy Standard JDBC way of determining if there are more results
        // DO NOT try to 'imporove' the condition even if IDE tells you to!
        // It's important that getUpdateCount() is called here.
        if (!(!stmt.getMoreResults() && stmt.getUpdateCount() == -1)) {
          ResultSet rs = stmt.getResultSet();
          if (rs == null) {
            return getNextResultSet(stmt);
          } else {
            return new ResultSetWrapper(rs, configuration);
          }
        }
      }
    } catch (Exception e) {
      // Intentionally ignored.
    }
    return null;
  }

  private void closeResultSet(ResultSet rs) {
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    }
  }

  private void cleanUpAfterHandlingResultSet() {
    nestedResultObjects.clear();
  }

  private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException(
          "A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
              + "'. 'resultType' or 'resultMap' must be specified when there is no corresponding method.");
    }
  }

  /**
   * 封装结果
   *
   * @param rsw
   * @param resultMap
   * @param multipleResults
   * @param parentMapping
   *
   * @throws SQLException
   */
  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults,
      ResultMapping parentMapping) throws SQLException {
    try {
      if (parentMapping != null) {
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      } else if (resultHandler == null) {
        // 返回一个集合
        DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
        handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
        multipleResults.add(defaultResultHandler.getResultList());
      } else {
        // 如果从外面传过来了
        handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rsw.getResultSet());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> collapseSingleResultList(List<Object> multipleResults) {
    return multipleResults.size() == 1 ? (List<Object>) multipleResults.get(0) : multipleResults;
  }

  //
  // HANDLE ROWS FOR SIMPLE RESULTMAP
  //

  /**
   * 处理resultSet的参数封装
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   *
   * @throws SQLException
   */
  public void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler,
      RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    if (resultMap.hasNestedResultMaps()) {
      // 有嵌套的类型
      ensureNoRowBounds();
      checkResultHandler();
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    } else {
      // 没有嵌套,就是普通的类型
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }

  private void ensureNoRowBounds() {
    if (configuration.isSafeRowBoundsEnabled() && rowBounds != null
        && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
      throw new ExecutorException(
          "Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
              + "Use safeRowBoundsEnabled=false setting to bypass this check.");
    }
  }

  protected void checkResultHandler() {
    if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException(
          "Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
              + "Use safeResultHandlerEnabled=false setting to bypass this check "
              + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  }

  /**
   * 存在嵌套查询 <resultMap id="blogResult" type="Blog">
   * <collection property="posts" javaType="ArrayList" column="id" ofType="Post" select="selectPostsForBlog"/>
   * </resultMap>
   *
   * @param rsw
   * @param resultMap
   * @param resultHandler
   * @param rowBounds
   * @param parentMapping
   *
   * @throws SQLException
   */
  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap,
      ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {

    // 结果上下文
    DefaultResultContext<Object> resultContext = new DefaultResultContext<>();

    // 后台返回结果集
    ResultSet resultSet = rsw.getResultSet();

    // 忽略一定的行数(物理分页)
    skipRows(resultSet, rowBounds);

    while (
    // 上下文还没有停止,并且结果集还没有达到最大
    shouldProcessMoreRows(resultContext, rowBounds) &&
    // 结果集没有关闭
        !resultSet.isClosed()
        // 结果集还有下一个结果
        && resultSet.next()) {

      // 解析判别结果图
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(resultSet, resultMap, null);

      // 获取该行的值
      Object rowValue = getRowValue(rsw, discriminatedResultMap, null);

      // 储存该行对象(放在list里面,DefaultResultHandler<Object>)
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
    }
  }

  private void storeObject(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue,
      ResultMapping parentMapping, ResultSet rs) throws SQLException {
    if (parentMapping != null) {
      // 多个结果集
      linkToParents(rs, parentMapping, rowValue);
    } else {
      // 没有嵌套
      callResultHandler(resultHandler, resultContext, rowValue);
    }
  }

  @SuppressWarnings("unchecked" /* because ResultHandler<?> is always ResultHandler<Object> */)
  private void callResultHandler(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext,
      Object rowValue) {
    resultContext.nextResultObject(rowValue);
    ((ResultHandler<Object>) resultHandler).handleResult(resultContext);
  }

  private boolean shouldProcessMoreRows(ResultContext<?> context, RowBounds rowBounds) {
    return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
  }

  private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
    if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
      if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
        // 移动到指定的行号
        rs.absolute(rowBounds.getOffset());
      }
    } else {
      for (int i = 0; i < rowBounds.getOffset(); i++) {
        if (!rs.next()) {
          break;
        }
      }
    }
  }

  //
  // GET VALUE FROM ROW FOR SIMPLE RESULT MAP
  //

  /**
   * 简单的结果集单行封装(没有嵌套)
   *
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
    final ResultLoaderMap lazyLoader = new ResultLoaderMap();

    // 先创建行的值
    Object rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);

    if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
      // 没有类型处理,并且值不为空
      final MetaObject metaObject = configuration.newMetaObject(rowValue);
      boolean foundValues = this.useConstructorMappings;

      if (shouldApplyAutomaticMappings(resultMap, false)) {
        foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
      }

      foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;

      foundValues = lazyLoader.size() > 0 || foundValues;

      rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
    }
    return rowValue;
  }

  //
  // GET VALUE FROM ROW FOR NESTED RESULT MAP
  //

  /**
   * 复杂的,嵌套的
   *
   * @param rsw
   * @param resultMap
   * @param combinedKey
   * @param columnPrefix
   * @param partialObject
   *
   * @return
   *
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw,
      // 结果集映射
      ResultMap resultMap,
      // 缓存key(嵌套属性所在对象的缓存key,用于获取嵌套属性所在的对象)
      CacheKey combinedKey,
      // 字段的前缀
      String columnPrefix,
      // 嵌套的,父结果集(该值就是嵌套属性所在的对象)
      Object partialObject) throws SQLException {
    final String resultMapId = resultMap.getId();
    Object rowValue = partialObject;

    if (rowValue != null) {
      // 说明不为空(该缓存key之前已经封装过了)
      final MetaObject metaObject = configuration.newMetaObject(rowValue);

      // key为 resultMap的id value就是值
      putAncestor(rowValue, resultMapId);

      // 执行结果嵌套(可能有嵌套也可能没有嵌套)
      applyNestedResultMappings(
          // 结果集
          rsw,
          // 拿到结果映射 resultMapping
          resultMap,
          // 用于封装数据的
          metaObject, columnPrefix, combinedKey, false);

      ancestorObjects.remove(resultMapId);
    } else {
      // (为空可能是第一次进来(对于该缓存key),rowValue是partialObject赋值,并且partialObject是通过缓存key来获取,
      // 缓存key的主要组成部分是resultMap按照一定的规则来设置的,相当于是唯一的)
      final ResultLoaderMap lazyLoader = new ResultLoaderMap();

      // 创建结果对象(通过设置的类型处理器,通过resultMap标签里面指定构造,如果是接口或者默认构造(反射构造),通过寻找一个有参构造(参数要和select的字段一致,要不然就会报错))
      rowValue = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);

      if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
        // 有值,但是没有参数的类型处理(相当于需要自己拿映射,自己搞的类型处理器的,优先级比较高)
        final MetaObject metaObject = configuration.newMetaObject(rowValue);
        boolean foundValues = this.useConstructorMappings;

        if (shouldApplyAutomaticMappings(resultMap, true)) {
          // 自动映射(根据一定的位置)
          foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
        }

        // 然后在根据写死的映射(写死分为2种,第一种没有嵌套的,另外一种是有嵌套的,这里处理的是没有嵌套的)
        foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;

        // resultMapId => 结果集 如果一个结果集里面的嵌套结果集就是现在的结果集,如果前缀是一样或者前缀是一致的,(说明select的字段名称和值都是和之前一样的
        // 所以可以复用 =>>>> 我感觉大部分都不会出现这种情况(杜绝这种情况))
        putAncestor(rowValue, resultMapId);

        // 嵌套结果集
        foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true)
            || foundValues;

        ancestorObjects.remove(resultMapId);

        foundValues = lazyLoader.size() > 0 || foundValues;
        rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
      }

      if (combinedKey != CacheKey.NULL_CACHE_KEY) {
        // 其那套结果集映射
        nestedResultObjects.put(combinedKey, rowValue);
      }
    }
    return rowValue;
  }

  private void putAncestor(Object resultObject, String resultMapId) {
    ancestorObjects.put(resultMapId, resultObject);
  }

  /**
   * 应该应用自动映射
   *
   * @param resultMap
   * @param isNested
   *
   * @return
   */
  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
    if (resultMap.getAutoMapping() != null) {
      return resultMap.getAutoMapping();
    }
    if (isNested) {
      return AutoMappingBehavior.FULL == configuration.getAutoMappingBehavior();
    } else {
      return AutoMappingBehavior.NONE != configuration.getAutoMappingBehavior();
    }
  }

  //
  // PROPERTY MAPPINGS
  //

  /**
   * 通过标签指明的属性(包含了嵌套的,但是这里会忽略,嵌套的逻辑不在这里执行)
   *
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param lazyLoader
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject,
      ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);

    boolean foundValues = false;
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();

    for (ResultMapping propertyMapping : propertyMappings) {
      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);

      // 如果有嵌套,就忽略
      if (propertyMapping.getNestedResultMapId() != null) {
        // the user added a column attribute to a nested result map, ignore it
        column = null;
      }

      if (propertyMapping.isCompositeResult()
          || column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))
          || propertyMapping.getResultSet() != null) {

        // 有嵌套查询
        Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader,
            columnPrefix);

        // issue #541 make property optional
        final String property = propertyMapping.getProperty();
        if (property == null) {
          continue;
        }
        if (value == DEFERRED) {
          foundValues = true;
          continue;
        }
        if (value != null) {
          foundValues = true;
        }
        if (value != null
            || configuration.isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive()) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          metaObject.setValue(property, value);
        }
      }
    }
    return foundValues;
  }

  private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping,
      ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    if (propertyMapping.getNestedQueryId() != null) {
      // 嵌套查询
      return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
    }

    if (propertyMapping.getResultSet() != null) {
      addPendingChildRelation(rs, metaResultObject, propertyMapping); // TODO is that OK?
      return DEFERRED;
    } else {
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      return typeHandler.getResult(rs, column);
    }
  }

  /**
   * 创建自动映射
   *
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private List<UnMappedColumnAutoMapping> createAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap,
      MetaObject metaObject, String columnPrefix) throws SQLException {
    final String mapKey = resultMap.getId() + ":" + columnPrefix;
    List<UnMappedColumnAutoMapping> autoMapping = autoMappingsCache.get(mapKey);

    if (autoMapping == null) {
      autoMapping = new ArrayList<>();

      final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);

      // Remove the entry to release the memory
      List<String> mappedInConstructorAutoMapping = constructorAutoMappingColumns.remove(mapKey);

      if (mappedInConstructorAutoMapping != null) {
        unmappedColumnNames.removeAll(mappedInConstructorAutoMapping);
      }

      for (String columnName : unmappedColumnNames) {
        String propertyName = columnName;
        if (columnPrefix != null && !columnPrefix.isEmpty()) {
          // When columnPrefix is specified,
          // ignore columns without the prefix.
          if (!columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
            continue;
          }
          propertyName = columnName.substring(columnPrefix.length());
        }
        final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
        if (property != null && metaObject.hasSetter(property)) {
          if (resultMap.getMappedProperties().contains(property)) {
            continue;
          }
          final Class<?> propertyType = metaObject.getSetterType(property);
          if (typeHandlerRegistry.hasTypeHandler(propertyType, rsw.getJdbcType(columnName))) {
            // 通过java的类型,和字段来获取类型处理器
            final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
            autoMapping
                .add(new UnMappedColumnAutoMapping(columnName, property, typeHandler, propertyType.isPrimitive()));
          } else {
            configuration.getAutoMappingUnknownColumnBehavior().doAction(mappedStatement, columnName, property,
                propertyType);
          }
        } else {
          configuration.getAutoMappingUnknownColumnBehavior().doAction(mappedStatement, columnName,
              property != null ? property : propertyName, null);
        }
      }
      autoMappingsCache.put(mapKey, autoMapping);
    }
    return autoMapping;
  }

  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject,
      String columnPrefix) throws SQLException {
    List<UnMappedColumnAutoMapping> autoMapping = createAutomaticMappings(rsw, resultMap, metaObject, columnPrefix);
    boolean foundValues = false;

    // 先封装自动映射的字段
    if (!autoMapping.isEmpty()) {

      //
      for (UnMappedColumnAutoMapping mapping : autoMapping) {
        // 从resultSet当中获取
        final Object value = mapping.typeHandler.getResult(rsw.getResultSet(), mapping.column);
        if (value != null) {
          foundValues = true;
        }

        // 调用set来进行设置
        if (value != null || configuration.isCallSettersOnNulls() && !mapping.primitive) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          metaObject.setValue(mapping.property, value);
        }
      }
    }
    return foundValues;
  }

  // MULTIPLE RESULT SETS

  private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {

    CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(),
        parentMapping.getForeignColumn());

    List<PendingRelation> parents = pendingRelations.get(parentKey);
    if (parents != null) {
      for (PendingRelation parent : parents) {
        if (parent != null && rowValue != null) {
          linkObjects(parent.metaObject, parent.propertyMapping, rowValue);
        }
      }
    }
  }

  private void addPendingChildRelation(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping)
      throws SQLException {
    CacheKey cacheKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(),
        parentMapping.getColumn());
    PendingRelation deferLoad = new PendingRelation();
    deferLoad.metaObject = metaResultObject;
    deferLoad.propertyMapping = parentMapping;
    List<PendingRelation> relations = MapUtil.computeIfAbsent(pendingRelations, cacheKey, k -> new ArrayList<>());
    // issue #255
    relations.add(deferLoad);
    ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet());
    if (previous == null) {
      nextResultMaps.put(parentMapping.getResultSet(), parentMapping);
    } else if (!previous.equals(parentMapping)) {
      throw new ExecutorException("Two different properties are mapped to the same resultSet");
    }
  }

  private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns)
      throws SQLException {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMapping);
    if (columns != null && names != null) {
      String[] columnsArray = columns.split(",");
      String[] namesArray = names.split(",");
      for (int i = 0; i < columnsArray.length; i++) {
        Object value = rs.getString(columnsArray[i]);
        if (value != null) {
          cacheKey.update(namesArray[i]);
          cacheKey.update(value);
        }
      }
    }
    return cacheKey;
  }

  //
  // INSTANTIATION & CONSTRUCTOR MAPPING
  //

  /**
   * 实例化(空参和有参(要进行映射,ResultMap->constructorResultMappings))
   *
   * @param rsw
   * @param resultMap
   * @param lazyLoader
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader,
      String columnPrefix) throws SQLException {
    // ResultMap里面有 constructor标签(id标签,arg标签),或者空参构造
    this.useConstructorMappings = false; // reset previous mapping result

    final List<Class<?>> constructorArgTypes = new ArrayList<>();
    final List<Object> constructorArgs = new ArrayList<>();

    // 创建单行的结果集
    Object resultObject = createResultObject(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);

    if (resultObject != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
      final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();

      // 嵌套查询和懒加载(只要有一个,就可以了)
      for (ResultMapping propertyMapping : propertyMappings) {
        // issue gcode #109 && issue #149
        if (propertyMapping.getNestedQueryId() != null && propertyMapping.isLazy()) {
          // 有嵌套查询和懒加载
          resultObject = configuration.getProxyFactory().createProxy(resultObject, lazyLoader, configuration,
              objectFactory, constructorArgTypes, constructorArgs);
          break;
        }
      }
    }

    this.useConstructorMappings = resultObject != null && !constructorArgTypes.isEmpty(); // set current mapping result
    return resultObject;
  }

  /**
   * 创建结果集对象
   *
   * @param rsw
   * @param resultMap
   * @param constructorArgTypes
   * @param constructorArgs
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes,
      List<Object> constructorArgs, String columnPrefix) throws SQLException {
    // 当前结果集返回的结果
    final Class<?> resultType = resultMap.getType();

    // 自己有类型处理器
    if (hasTypeHandlerForResultObject(rsw, resultType)) {
      // 如果有自己的类型处理,就用指定的类型处理器
      return createPrimitiveResultObject(rsw, resultMap, columnPrefix);
    }

    final MetaClass metaType = MetaClass.forClass(resultType, reflectorFactory);
    // 拿到构造的结果映射
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();

    if (!constructorMappings.isEmpty()) {
      // resultMap有指定构造函数的标签映射
      return createParameterizedResultObject(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs,
          columnPrefix);
    } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {
      // 通过空参(默认构造),进行构造
      return objectFactory.create(resultType);
    } else if (shouldApplyAutomaticMappings(resultMap, false)) {
      // 通过构造签名(通过不是指定的构造函数,参数的名称要和字段的名称要一致,要不然会实例错误)
      return createByConstructorSignature(rsw, resultMap, columnPrefix, resultType, constructorArgTypes,
          constructorArgs);
    }
    throw new ExecutorException("Do not know how to create an instance of " + resultType);
  }

  Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType,
      List<ResultMapping> constructorMappings, List<Class<?>> constructorArgTypes, List<Object> constructorArgs,
      String columnPrefix) {
    boolean foundValues = false;
    for (ResultMapping constructorMapping : constructorMappings) {

      // Java的类型
      final Class<?> parameterType = constructorMapping.getJavaType();
      // 字段
      final String column = constructorMapping.getColumn();

      final Object value;
      try {
        if (constructorMapping.getNestedQueryId() != null) {
          // 说明有嵌套查询
          value = getNestedQueryConstructorValue(rsw.getResultSet(), constructorMapping, columnPrefix);
        } else if (constructorMapping.getNestedResultMapId() != null) {
          // 有嵌套的resultMap
          String constructorColumnPrefix = getColumnPrefix(columnPrefix, constructorMapping);
          final ResultMap resultMap = resolveDiscriminatedResultMap(rsw.getResultSet(),
              configuration.getResultMap(constructorMapping.getNestedResultMapId()), constructorColumnPrefix);
          value = getRowValue(rsw, resultMap, constructorColumnPrefix);
        } else {
          final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
          value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
        }
      } catch (ResultMapException | SQLException e) {
        throw new ExecutorException("Could not process result for mapping: " + constructorMapping, e);
      }
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  private Object createByConstructorSignature(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix,
      Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) throws SQLException {
    return applyConstructorAutomapping(rsw, resultMap, columnPrefix, resultType, constructorArgTypes, constructorArgs,
        findConstructorForAutomapping(resultType, rsw).orElseThrow(() -> new ExecutorException(
            "No constructor found in " + resultType.getName() + " matching " + rsw.getClassNames())));
  }

  private Optional<Constructor<?>> findConstructorForAutomapping(final Class<?> resultType, ResultSetWrapper rsw) {
    Constructor<?>[] constructors = resultType.getDeclaredConstructors();
    if (constructors.length == 1) {
      return Optional.of(constructors[0]);
    }
    Optional<Constructor<?>> annotated = Arrays.stream(constructors)
        .filter(x -> x.isAnnotationPresent(AutomapConstructor.class)).reduce((x, y) -> {
          throw new ExecutorException("@AutomapConstructor should be used in only one constructor.");
        });
    if (annotated.isPresent()) {
      return annotated;
    }
    if (configuration.isArgNameBasedConstructorAutoMapping()) {
      // Finding-best-match type implementation is possible,
      // but using @AutomapConstructor seems sufficient.
      throw new ExecutorException(MessageFormat.format(
          "'argNameBasedConstructorAutoMapping' is enabled and the class ''{0}'' has multiple constructors, so @AutomapConstructor must be added to one of the constructors.",
          resultType.getName()));
    } else {
      return Arrays.stream(constructors).filter(x -> findUsableConstructorByArgTypes(x, rsw.getJdbcTypes())).findAny();
    }
  }

  private boolean findUsableConstructorByArgTypes(final Constructor<?> constructor, final List<JdbcType> jdbcTypes) {
    final Class<?>[] parameterTypes = constructor.getParameterTypes();
    if (parameterTypes.length != jdbcTypes.size()) {
      return false;
    }
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!typeHandlerRegistry.hasTypeHandler(parameterTypes[i], jdbcTypes.get(i))) {
        return false;
      }
    }
    return true;
  }

  private Object applyConstructorAutomapping(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix,
      Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor)
      throws SQLException {
    boolean foundValues = false;
    if (configuration.isArgNameBasedConstructorAutoMapping()) {
      foundValues = applyArgNameBasedConstructorAutoMapping(rsw, resultMap, columnPrefix, constructorArgTypes,
          constructorArgs, constructor, foundValues);
    } else {
      foundValues = applyColumnOrderBasedConstructorAutomapping(rsw, constructorArgTypes, constructorArgs, constructor,
          foundValues);
    }
    return foundValues || configuration.isReturnInstanceForEmptyRow()
        ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  private boolean applyColumnOrderBasedConstructorAutomapping(ResultSetWrapper rsw, List<Class<?>> constructorArgTypes,
      List<Object> constructorArgs, Constructor<?> constructor, boolean foundValues) throws SQLException {
    for (int i = 0; i < constructor.getParameterTypes().length; i++) {
      Class<?> parameterType = constructor.getParameterTypes()[i];
      String columnName = rsw.getColumnNames().get(i);
      TypeHandler<?> typeHandler = rsw.getTypeHandler(parameterType, columnName);
      Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    return foundValues;
  }

  private boolean applyArgNameBasedConstructorAutoMapping(ResultSetWrapper rsw, ResultMap resultMap,
      String columnPrefix, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor,
      boolean foundValues) throws SQLException {
    List<String> missingArgs = null;
    Parameter[] params = constructor.getParameters();
    for (Parameter param : params) {
      boolean columnNotFound = true;
      Param paramAnno = param.getAnnotation(Param.class);
      String paramName = paramAnno == null ? param.getName() : paramAnno.value();
      for (String columnName : rsw.getColumnNames()) {
        if (columnMatchesParam(columnName, paramName, columnPrefix)) {
          Class<?> paramType = param.getType();
          TypeHandler<?> typeHandler = rsw.getTypeHandler(paramType, columnName);
          Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
          constructorArgTypes.add(paramType);
          constructorArgs.add(value);
          final String mapKey = resultMap.getId() + ":" + columnPrefix;
          if (!autoMappingsCache.containsKey(mapKey)) {
            MapUtil.computeIfAbsent(constructorAutoMappingColumns, mapKey, k -> new ArrayList<>()).add(columnName);
          }
          columnNotFound = false;
          foundValues = value != null || foundValues;
        }
      }
      if (columnNotFound) {
        if (missingArgs == null) {
          missingArgs = new ArrayList<>();
        }
        missingArgs.add(paramName);
      }
    }
    if (foundValues && constructorArgs.size() < params.length) {
      throw new ExecutorException(MessageFormat.format(
          "Constructor auto-mapping of ''{1}'' failed " + "because ''{0}'' were not found in the result set; "
              + "Available columns are ''{2}'' and mapUnderscoreToCamelCase is ''{3}''.",
          missingArgs, constructor, rsw.getColumnNames(), configuration.isMapUnderscoreToCamelCase()));
    }
    return foundValues;
  }

  private boolean columnMatchesParam(String columnName, String paramName, String columnPrefix) {
    if (columnPrefix != null) {
      if (!columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
        return false;
      }
      columnName = columnName.substring(columnPrefix.length());
    }
    return paramName
        .equalsIgnoreCase(configuration.isMapUnderscoreToCamelCase() ? columnName.replace("_", "") : columnName);
  }

  private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix)
      throws SQLException {
    final Class<?> resultType = resultMap.getType();
    final String columnName;
    if (!resultMap.getResultMappings().isEmpty()) {
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      final ResultMapping mapping = resultMappingList.get(0);
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
    } else {
      columnName = rsw.getColumnNames().get(0);
    }
    final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);
    return typeHandler.getResult(rsw.getResultSet(), columnName);
  }

  //
  // NESTED QUERY
  //

  private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix)
      throws SQLException {
    final String nestedQueryId = constructorMapping.getNestedQueryId();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, constructorMapping,
        nestedQueryParameterType, columnPrefix);
    Object value = null;
    if (nestedQueryParameterObject != null) {
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT,
          nestedBoundSql);
      final Class<?> targetType = constructorMapping.getJavaType();
      final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery,
          nestedQueryParameterObject, targetType, key, nestedBoundSql);
      value = resultLoader.loadResult();
    }
    return value;
  }

  private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping,
      ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
    final String nestedQueryId = propertyMapping.getNestedQueryId();
    final String property = propertyMapping.getProperty();

    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();

    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping,
        nestedQueryParameterType, columnPrefix);
    Object value = null;

    if (nestedQueryParameterObject != null) {
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT,
          nestedBoundSql);
      final Class<?> targetType = propertyMapping.getJavaType();
      if (executor.isCached(nestedQuery, key)) {
        executor.deferLoad(nestedQuery, metaResultObject, property, key, targetType);
        value = DEFERRED;
      } else {
        final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery,
            nestedQueryParameterObject, targetType, key, nestedBoundSql);
        if (propertyMapping.isLazy()) {
          lazyLoader.addLoader(property, metaResultObject, resultLoader);
          value = DEFERRED;
        } else {
          value = resultLoader.loadResult();
        }
      }
    }
    return value;
  }

  private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType,
      String columnPrefix) throws SQLException {
    if (resultMapping.isCompositeResult()) {
      return prepareCompositeKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    }
    return prepareSimpleKeyParameter(rs, resultMapping, parameterType, columnPrefix);
  }

  private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType,
      String columnPrefix) throws SQLException {
    final TypeHandler<?> typeHandler;
    if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
      typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
    } else {
      typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
    }
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType,
      String columnPrefix) throws SQLException {
    final Object parameterObject = instantiateParameterObject(parameterType);
    final MetaObject metaObject = configuration.newMetaObject(parameterObject);
    boolean foundValues = false;
    for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
      final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty());
      final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);
      final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn(), columnPrefix));
      // issue #353 & #560 do not execute nested query if key is null
      if (propValue != null) {
        metaObject.setValue(innerResultMapping.getProperty(), propValue);
        foundValues = true;
      }
    }
    return foundValues ? parameterObject : null;
  }

  private Object instantiateParameterObject(Class<?> parameterType) {
    if (parameterType == null) {
      return new HashMap<>();
    }
    if (ParamMap.class.equals(parameterType)) {
      return new HashMap<>(); // issue #649
    } else {
      return objectFactory.create(parameterType);
    }
  }

  //
  // DISCRIMINATOR
  //

  public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix)
      throws SQLException {

    // 之前判定过的
    Set<String> pastDiscriminators = new HashSet<>();

    Discriminator discriminator = resultMap.getDiscriminator();

    // 鉴别器得到的resultMap,可能还有鉴别器(循环处理,直到结果为空)(但是有可能是会嵌套的(防止死循环))
    while (discriminator != null) {
      // 从resultSet拿到鉴别器指定的key并且得到该值
      final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);
      // 然后,通过该值,从鉴别器选择resultMap
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      if (!configuration.hasResultMap(discriminatedMapId)) {
        // 如果没有,就直接返回
        break;
      }
      // 获取到了
      resultMap = configuration.getResultMap(discriminatedMapId);
      Discriminator lastDiscriminator = discriminator;
      discriminator = resultMap.getDiscriminator();

      // 当前和之前那个是相同的,就直接返回,嵌套
      if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
        break;
      }
    }
    return resultMap;
  }

  private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix)
      throws SQLException {
    final ResultMapping resultMapping = discriminator.getResultMapping();
    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }

  //
  // HANDLE NESTED RESULT MAPS
  //

  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap,
      ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    // 有嵌套的ResultMap
    // 什么情况有 , 一对一的情况,一对多的情况
    final DefaultResultContext<Object> resultContext = new DefaultResultContext<>();

    // 拿到结果集
    ResultSet resultSet = rsw.getResultSet();

    // 物理分页的情况(在内存当中分页,RowBounds)
    skipRows(resultSet, rowBounds);

    // 上一行值
    Object rowValue = previousRowValue;

    while (shouldProcessMoreRows(resultContext, rowBounds) && !resultSet.isClosed() && resultSet.next()) {

      // 当前的resultMap可能只是包装而已,而是根据结果集指定的某一列的值来使用指定的resultMap
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(resultSet, resultMap, null);

      // rowKey,用于嵌套的时候,可查询出来,(必须保证唯一) ,CacheKey的设计可以学习, 我们常见的都是将值转换为string,然后拼接
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);

      // 拿到嵌套之前的值,大部分还是通过 1对多的情况,如果只是1对一的情况,应该用不到
      Object partialObject = nestedResultObjects.get(rowKey);

      // issue #577 && #542
      if (mappedStatement.isResultOrdered()) {
        // 结果排序
        if (partialObject == null && rowValue != null) {
          nestedResultObjects.clear();
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
        // 单个一行的处理
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
      } else {
        // 单个一行的处理
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
        if (partialObject == null) {
          storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
        }
      }
    }

    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, resultSet);
      previousRowValue = null;
    } else if (rowValue != null) {
      previousRowValue = rowValue;
    }
  }

  //
  // NESTED RESULT MAP (JOIN MAPPING)
  //

  private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject,
      String parentPrefix, CacheKey parentRowKey, boolean newObject) {
    boolean foundValues = false;

    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
      final String nestedResultMapId = resultMapping.getNestedResultMapId();

      // 该key是嵌套
      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
        // 是嵌套
        try {
          // 字段前缀
          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);

          // 拿到嵌套的 ResultMap(从全局拿到,并且还有鉴别一下(鉴别器))
          final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);

          // 当前的嵌套的resultMap之前已被封装了,如果没有前缀的话,说明jdbc的字段和java属性的字段和查询到结果集的值说明都是一样的,
          // 然后根据父方法传输过来的值,是复用(newObject为true,set(当前的值))还是新建(就继续递归封装)
          if (resultMapping.getColumnPrefix() == null) {
            // try to fill circular reference only when columnPrefix
            // is not specified for the nested result map (issue #215)
            Object ancestorObject = ancestorObjects.get(nestedResultMapId);
            if (ancestorObject != null) {
              if (newObject) {
                linkObjects(metaObject, resultMapping, ancestorObject); // issue #385
              }
              continue;
            }
          }

          // 拿到当前resultMap的缓存key
          final CacheKey rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);

          // 将当前的缓存key和父的缓存key
          final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);

          // 这里肯定是空的(第一次进来)
          Object rowValue = nestedResultObjects.get(combinedKey);

          // 是否有值
          boolean knownValue = rowValue != null;

          // 如果是集合就初始化,后续linkObjects的时候,就需要add了 (如果是一对多的话,就初始化集合)
          instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject); // mandatory

          // 值是否为空,不为空,嵌套才有意义
          if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw)) {
            // 嵌套执行
            rowValue = getRowValue(rsw, nestedResultMap, combinedKey, columnPrefix, rowValue);

            if (rowValue != null && !knownValue) {
              // 数组已在递归之前,就初始化了
              // 递归拿到的结果 (如果是一个数组,就add ,如果不是 就直接set)
              linkObjects(metaObject, resultMapping, rowValue);
              foundValues = true;
            }
          }
        } catch (SQLException e) {
          throw new ExecutorException(
              "Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
        }
      }
    }
    return foundValues;
  }

  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    if (parentPrefix != null) {
      columnPrefixBuilder.append(parentPrefix);
    }
    if (resultMapping.getColumnPrefix() != null) {
      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    }
    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
  }

  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSetWrapper rsw)
      throws SQLException {

    Set<String> notNullColumns = resultMapping.getNotNullColumns();
    if (notNullColumns != null && !notNullColumns.isEmpty()) {
      ResultSet rs = rsw.getResultSet();
      for (String column : notNullColumns) {
        rs.getObject(prependPrefix(column, columnPrefix));
        if (!rs.wasNull()) {
          return true;
        }
      }
      return false;
    }

    if (columnPrefix != null) {
      for (String columnName : rsw.getColumnNames()) {
        if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix.toUpperCase(Locale.ENGLISH))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  /**
   * 获取嵌套结果映射
   *
   * @param rs
   * @param nestedResultMapId
   * @param columnPrefix
   *
   * @return
   *
   * @throws SQLException
   */
  private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix)
      throws SQLException {
    ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
    return resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
  }

  //
  // UNIQUE RESULT KEY
  //

  private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
    final CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMap.getId());

    // 先用ID属性列表,如果没有,就用所有的列作为key
    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);

    if (resultMappings.isEmpty()) {
      // 如果还是空的话
      if (Map.class.isAssignableFrom(resultMap.getType())) {
        // 那所有的字段名称和值作为key
        createRowKeyForMap(rsw, cacheKey);
      } else {
        // 如果是自动映射,可能还有很多ResultMapping是没有记录的,通过反射来获取属性和jdbc的字段=>然后也是通过字段名称和值作为key的
        createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
      }
    } else {
      // 如果不为空
      // resultmapId:列名:值 (一般只有唯一 id标签,尽量要写ID标签)
      createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
    }

    if (cacheKey.getUpdateCount() < 2) {
      return CacheKey.NULL_CACHE_KEY;
    }
    return cacheKey;
  }

  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
      CacheKey combinedKey;
      try {
        combinedKey = rowKey.clone();
      } catch (CloneNotSupportedException e) {
        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
      }
      combinedKey.update(parentRowKey);
      return combinedKey;
    }
    return CacheKey.NULL_CACHE_KEY;
  }

  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
    if (resultMappings.isEmpty()) {
      resultMappings = resultMap.getPropertyResultMappings();
    }
    return resultMappings;
  }

  private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey,
      List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
    for (ResultMapping resultMapping : resultMappings) {
      if (resultMapping.isSimple()) {
        // jdbc的字段
        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
        // 类型处理器
        final TypeHandler<?> th = resultMapping.getTypeHandler();
        // 整个sql语句返回的字段
        List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);

        // Issue #114
        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
          final Object value = th.getResult(rsw.getResultSet(), column);
          if (value != null || configuration.isReturnInstanceForEmptyRow()) {
            cacheKey.update(column);
            cacheKey.update(value);
          }
        }
      }
    }
  }

  private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey,
      String columnPrefix) throws SQLException {
    final MetaClass metaType = MetaClass.forClass(resultMap.getType(), reflectorFactory);
    List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);

    for (String column : unmappedColumnNames) {
      String property = column;
      if (columnPrefix != null && !columnPrefix.isEmpty()) {
        // When columnPrefix is specified, ignore columns without the prefix.
        if (!column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          continue;
        }
        property = column.substring(columnPrefix.length());
      }

      if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
        String value = rsw.getResultSet().getString(column);
        if (value != null) {
          cacheKey.update(column);
          cacheKey.update(value);
        }
      }
    }
  }

  private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
    List<String> columnNames = rsw.getColumnNames();
    for (String columnName : columnNames) {
      final String value = rsw.getResultSet().getString(columnName);
      if (value != null) {
        cacheKey.update(columnName);
        cacheKey.update(value);
      }
    }
  }

  private void linkObjects(MetaObject metaObject, ResultMapping resultMapping, Object rowValue) {
    final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);
    if (collectionProperty != null) {
      // 一对多的关系
      final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
      targetMetaObject.add(rowValue);
    } else {
      // 一对一的关系
      metaObject.setValue(resultMapping.getProperty(), rowValue);
    }
  }

  /**
   * 如果嵌套是一个集合类型,这里就进行初始化或者获取
   *
   * @param resultMapping
   * @param metaObject
   *
   * @return
   */
  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
    final String propertyName = resultMapping.getProperty();

    Object propertyValue = metaObject.getValue(propertyName);

    if (propertyValue == null) {
      // 相当于还没有初始化
      Class<?> type = resultMapping.getJavaType();

      // 没有配置类型
      if (type == null) {
        // 通过set来获取类型
        type = metaObject.getSetterType(propertyName);
      }

      try {
        if (objectFactory.isCollection(type)) {
          // 一对多的情况
          propertyValue = objectFactory.create(type);
          // 然后将值设置进去
          metaObject.setValue(propertyName, propertyValue);
          return propertyValue;
        }
      } catch (Exception e) {
        throw new ExecutorException(
            "Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e,
            e);
      }
    } else if (objectFactory.isCollection(propertyValue.getClass())) {
      // 如果有值,而且还是集合,就直接返回
      return propertyValue;
    }
    return null;
  }

  private boolean hasTypeHandlerForResultObject(ResultSetWrapper rsw, Class<?> resultType) {
    // 如果sql语句返回的结果集只有1列,那么直接拿到他是啥jdbc的类型
    if (rsw.getColumnNames().size() == 1) {
      return typeHandlerRegistry.hasTypeHandler(resultType, rsw.getJdbcType(rsw.getColumnNames().get(0)));
    }
    // 他有多列,说明是一个对象,直接用返回的类型,看看是否有处理
    return typeHandlerRegistry.hasTypeHandler(resultType);
  }

}
