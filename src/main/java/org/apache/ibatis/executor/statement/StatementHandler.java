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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * 用于是用于抽象出 jdbc的Statement的功能,
 * 1. 构建一个Statement,并且设置操作超时时间,fetchSize等等操作 prepare方法操作
 * 2. 将sql的参数封装到Statement当中去, parameterize方法
 * 3. 执行操作(batch方法,update方法,query方法(queryList和query游标))
 * <p>
 * 额外操作: 获取sql语句: getBoundSql() 获取封装sql参数的ParameterHandler 方法 getParameterHandler()
 * <p>
 * 其底下的实现类,
 * 1. 在new出实例的时候,会对sql进行动态执行(通过参数实体),从而得到一个真正的sql BoundSql
 * 2. 同时也会new出设置参数ParamterHandler和ResultSetHandler,并且进行加强
 * <p>
 * <p>
 * //对于普通simpleStatementHandler的时候,如果进行对分页加强的话,一般拦截的Query方法,对BoundSql的sql属性进行修改
 * <p>
 * <p>
 * <p>
 * <p>
 * <p>
 * //或者有2个sql //获取完善一点的话,就是对 getBoundSql进行拦截,调用该方法的时候,对BoundSql的sql属性进行修改(添加limit 函数)
 * <p>
 * <p>
 * <p>
 * 对于分页一般要执行2个sql
 *
 * @author Clinton Begin
 */
public interface StatementHandler {

  /**
   * 创建jdbc的statement,(?,还是直接拼接),并且会设置fetchSize和查询的超时时间
   *
   * @param connection
   * @param transactionTimeout
   *
   * @return
   *
   * @throws SQLException
   */
  Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException;

  /**
   * 给statement设置参数
   *
   * @param statement
   *
   * @throws SQLException
   */
  void parameterize(Statement statement) throws SQLException;

  /**
   * 批量操作
   *
   * @param statement
   *
   * @throws SQLException
   */
  void batch(Statement statement) throws SQLException;

  /**
   * 更新操作
   *
   * @param statement
   *
   * @return
   *
   * @throws SQLException
   */
  int update(Statement statement) throws SQLException;

  /**
   * 查询
   *
   * @param statement
   * @param resultHandler
   * @param <E>
   *
   * @return
   *
   * @throws SQLException
   */
  <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException;

  /**
   * 游标查询
   *
   * @param statement
   * @param <E>
   *
   * @return
   *
   * @throws SQLException
   */
  <E> Cursor<E> queryCursor(Statement statement) throws SQLException;

  /**
   * 获取slq
   *
   * @return
   */
  BoundSql getBoundSql();

  /**
   * 获取参数封装处理器
   *
   * @return
   */
  ParameterHandler getParameterHandler();

}
