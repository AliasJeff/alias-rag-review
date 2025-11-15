package com.alias.infrastructure.typehandler;

import com.alibaba.fastjson2.JSON;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * JSONB Type Handler for MyBatis
 * Handles conversion between Java Map and PostgreSQL JSONB type
 */
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(JSON.toJSONString(parameter));
        ps.setObject(i, jsonObject);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String jsonString = rs.getString(columnName);
        return parseJson(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String jsonString = rs.getString(columnIndex);
        return parseJson(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String jsonString = cs.getString(columnIndex);
        return parseJson(jsonString);
    }

    /**
     * Parse JSON string to Map
     *
     * @param jsonString the JSON string
     * @return the parsed map or empty map if null
     */
    private Map<String, Object> parseJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty() || "{}".equals(jsonString)) {
            return new HashMap<>();
        }
        return JSON.parseObject(jsonString, Map.class);
    }
}
