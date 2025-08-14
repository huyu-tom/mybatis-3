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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 会解析 (#{})(可能存在),(${},动态标签)(必须存在)
 *
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  // 全局配置
  private final Configuration configuration;

  // 动态SqlNode节点(用于执行)
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 1. 为什么要先运行动态sql,而不是解析 #{},${} 答案: foreach的情况就很明显,有临时变量 item , item不是同一个变量,如果将开始就解析的话,都是一样的或者没有这个变量解析失败 2.
   * 运行动态sql的时候,会解析 ${} , 但是没有解析 #{},(特殊for循环里面,就解析了#{},在for循环外的解析可能就不会解析) ,这是为什么? 可以一起解析??
   *
   * @param parameterObject
   *
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {

    // 先运行动态sql,并且解析 ${},在运行当中, 同时也会解析 #{}, 在for循环的时候
    DynamicContext context = new DynamicContext(configuration, parameterObject);

    // 然后进行执行动态sql
    rootSqlNode.apply(context);

    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();

    // 解析 #{}, 对于for循环的item的变量名,随着循环他的值是变化的,虽然他的key是不变的,在执行动态sql的时候
    // 如果存在for循环中具有 #{}的情况, 变量名会变成全局唯一,生成的规则 可见 ForSqlNode 类的 apply方法
    // #{} 按照前后顺序生成 List<ParamMapping>,其实在动态sql执行的时候,也可以add,并不一定要在这个地方

    // 为什么要在这里, 因为在普通节点的时候,只解析了 ${}, 没有解析 #{},所以如果只处理 For循环的节点,他不是按照顺序处理的
    // 其实,处理普通节点的时候,就可以同时解析 ${} 和 #{} ,但是可能存在写法复杂的问题( #{${}} 在一个node里面同时解析)
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());

    // 然后获取sql,动态sql生成和解析了 ${},就变成了一个完整的sql,如果没有${},就不需要设置参数

    // 现在是一个完全静态的sql了,只相当于赋值
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);

    // 额外参数就是全局的上下文
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
