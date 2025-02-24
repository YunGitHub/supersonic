package com.tencent.supersonic.chat.core.query;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.chat.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.chat.api.pojo.SemanticSchema;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import org.apache.calcite.sql.parser.SqlParseException;

/**
 * A semantic query executes specific type of query based on the results of semantic parsing.
 */
public interface SemanticQuery {

    String getQueryMode();

    QueryResult execute(User user) throws SqlParseException;

    void initS2Sql(SemanticSchema semanticSchema, User user);

    String explain(User user);

    SemanticParseInfo getParseInfo();

    void setParseInfo(SemanticParseInfo parseInfo);
}
