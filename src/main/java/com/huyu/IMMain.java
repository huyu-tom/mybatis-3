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
package com.huyu;

import com.huyu.dynamic.CreateSql;
import com.huyu.dynamic.parse.IMXmlScriptBuilder;
import com.huyu.test.SessionPO;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;

import java.sql.Timestamp;
import java.util.*;

public class IMMain {

  public static void main(String[] args) throws InterruptedException {
    String script = "<script>"
      + "  <if test='1==1'>bbb </if> <else-if test='2==2'> aaa </else-if>   update session\n"
      + "     <bind name='bindVar' value=\"sqlAppend + 'xxx'\"></bind>              "
      + "      <if test=\"PO.title!=null and PO.title.trim()!=''\">\n"
      + "        title = ${PO.title},\n" + "      </if>\n\n" + "                       "
      + "      <else>" + "        title = '你好'" + "      </else>" + "      ${bindVar}    "
      + "      <if test=\"PO.updateTime!=null\">\n" + "        update_time = ${PO.updateTime},\n"
      + "      </if>\n" + "      <if test=\"PO.delIs!=null\">\n" + "        del_is = ${PO.delIs}\n"
      + "      </if>\n" + "    where\n" + "    id = ${PO.id}\n"
      + "    <foreach item=\"item\" index=\"index\" collection=\"list\"\n"
      + "             open=\"AND ID in (\" separator=\",\" close=\")\">\n" + "      ${item.id}\n"
      + "    </foreach>\n" + "    <foreach item=\"item\" index=\"index\" collection=\"list\"\n"
      + "             open=\"AND ID in (\" separator=\",\" close=\")\">\n" + "      ${item.id}\n"
      + "    </foreach>\n" + "\n" + "</script>";

    if (script.startsWith("<script>")) {
      XPathParser parser = new XPathParser(script, false, new Properties(),
        new XMLMapperEntityResolver());
      XNode xNode = parser.evalNode("/script");
      IMXmlScriptBuilder scriptBuilder = new IMXmlScriptBuilder(xNode);
      CreateSql createSql = scriptBuilder.parseScriptNode();

      // 动态sql的入参,用于模拟 mybatis的接口中参数(最终mybatis也会封装成一个Map)
      List<SessionPO> sessionPOS = new ArrayList<>();
      for (int i = 0; i < 10000; i++) {
        sessionPOS.add(new SessionPO().setId((long) i));
      }
      Map<String, Object> paramMap = new HashMap<>();
      paramMap.put("PO", new SessionPO().setId(1L).setTitle(null).setDelIs(0)
        .setUpdateTime(new Timestamp(System.currentTimeMillis())));
      paramMap.put("list", sessionPOS);
      paramMap.put("sqlAppend", "你叉");

      // 统计执行时间
      long l = System.currentTimeMillis();
      String sql = createSql.createSql(paramMap);
      System.out.println("动态sql执行消耗毫秒时间为: " + (System.currentTimeMillis() - l));
      System.out.println("sql is " + sql);
    }
  }

}
