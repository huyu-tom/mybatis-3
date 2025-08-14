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
package com.huyu.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class TestMybatis {

  /**
   * Ognl表达式 OgnlCache 用于获取数据
   *
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    String resource = "com/huyu/test/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

    SqlSession sqlSession = sqlSessionFactory.openSession();
    try {

      // sqlSession他不是线程安全的,就相当于获取一个jdbc连接,一个连接,同一时刻只能开启一个事务,所以一个sqlSession就相当于一个连接
      // 我们的mapper,是随着SqlSession的不同,而不同,他不是全局唯一的实例

      // 在和Spring整合的目标就是
      // 1. 单例Mapper(每次生成一个Mapper,就得执行一次动态代理) 2. 在Spring事务当中,共用一个SqlSession
      // 是矛盾冲突的

      // 那应该如何解决冲突
      SessionMapper mapper = sqlSession.getMapper(SessionMapper.class);
      // SessionPO sessionPO = new SessionPO().
      // setId(1665967923982356480L)
      // .setTitle("鲁迅和周树人的关系");
      // mapper.updateSessionById(sessionPO, new ArrayList<>(Arrays.asList(sessionPO)));
      System.out.println(mapper.getMap(
        Arrays.asList(738264002603438080L, 738282231749070848L, 738285321202094080L)));
    } finally {
      sqlSession.commit();
      sqlSession.close();
    }
  }
}
