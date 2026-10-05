package io.github.limbj.board.configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Oracle and H2 use the same explicit CHAR(1) encoding for attachment image flags. */
public final class CharBooleanTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Boolean value, JdbcType jdbcType) throws SQLException {
        statement.setString(index, value ? "1" : "0");
    }
    @Override
    public Boolean getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }
    @Override
    public Boolean getNullableResult(ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }
    @Override
    public Boolean getNullableResult(CallableStatement statement, int column) throws SQLException {
        return decode(statement.getString(column));
    }
    private static Boolean decode(String value) throws SQLException {
        if (value == null) return null;
        if ("1".equals(value.trim())) return true;
        if ("0".equals(value.trim())) return false;
        throw new SQLException("Invalid attachment image flag");
    }
}
