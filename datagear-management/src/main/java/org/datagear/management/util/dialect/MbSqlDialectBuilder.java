/*
 * Copyright 2018 datagear.tech
 *
 * Licensed under the LGPLv3 license:
 * http://www.gnu.org/licenses/lgpl-3.0.html
 */

package org.datagear.management.util.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.datagear.connection.support.DerbyURLSensor;
import org.datagear.connection.support.MySqlURLSensor;
import org.datagear.connection.support.OracleURLSensor;
import org.datagear.connection.support.PostgresqlURLSensor;
import org.datagear.util.JdbcUtil;
import org.datagear.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@linkplain MbSqlDialect}构建器。
 * <p>
 * 此类根据给定的数据库连接，构建对应的{@linkplain MbSqlDialect}。
 * </p>
 * 
 * @author datagear@163.com
 *
 */
public class MbSqlDialectBuilder
{
	private static final Logger LOGGER = LoggerFactory.getLogger(MbSqlDialectBuilder.class);

	/**
	 * 默认标识符引用符。
	 */
	public static final String DEFAULT_IDENTIFIER_QUOTE = " ";

	public MbSqlDialectBuilder()
	{
	}

	/**
	 * 构建{@linkplain MbSqlDialect}。
	 * 
	 * @param dataSource
	 * @return
	 * @throws SQLException
	 */
	public MbSqlDialect build(DataSource dataSource) throws SQLException
	{
		Connection cn = null;

		try
		{
			cn = dataSource.getConnection();
			return build(cn);
		}
		finally
		{
			JdbcUtil.closeConnection(cn);
		}
	}

	/**
	 * 构建{@linkplain MbSqlDialect}。
	 * <p>
	 * 注意：此方法不会关闭{@code cn}。
	 * </p>
	 * 
	 * @param cn
	 * @return
	 */
	public MbSqlDialect build(Connection cn) throws SQLException
	{
		String url = JdbcUtil.getURLIfSupports(cn);
		return build(cn, url);
	}

	/**
	 * 
	 * @param cn
	 * @param url
	 *            允许为{@code null}
	 * @return
	 * @throws SQLException
	 */
	protected MbSqlDialect build(Connection cn, String url) throws SQLException
	{
		if (StringUtil.isEmpty(url))
			return buildDefaultMbSqlDialect(cn);

		if (DerbyURLSensor.INSTANCE.supports(url))
			return buildDerbyMbSqlDialect(cn);

		if (MySqlURLSensor.INSTANCE.supports(url))
			return buildMysqlMbSqlDialect(cn);

		if (OracleURLSensor.INSTANCE.supports(url))
			return buildOracleMbSqlDialect(cn);

		if (PostgresqlURLSensor.INSTANCE.supports(url))
			return buildPostgresqlMbSqlDialect(cn);

		return buildDefaultMbSqlDialect(cn);
	}

	protected DerbyMbSqlDialect buildDerbyMbSqlDialect(Connection cn) throws SQLException
	{
		return new DerbyMbSqlDialect(getIdentifierQuote(cn));
	}

	protected MysqlMbSqlDialect buildMysqlMbSqlDialect(Connection cn) throws SQLException
	{
		return new MysqlMbSqlDialect(getIdentifierQuote(cn));
	}

	protected OracleMbSqlDialect buildOracleMbSqlDialect(Connection cn) throws SQLException
	{
		return new OracleMbSqlDialect(getIdentifierQuote(cn));
	}

	protected PostgresqlMbSqlDialect buildPostgresqlMbSqlDialect(Connection cn) throws SQLException
	{
		return new PostgresqlMbSqlDialect(getIdentifierQuote(cn));
	}

	protected DefaultMbSqlDialect buildDefaultMbSqlDialect(Connection cn) throws SQLException
	{
		return new DefaultMbSqlDialect(getIdentifierQuote(cn));
	}

	/**
	 * 获取数据库标识符引用符。
	 * <p>
	 * 如果出现异常，将返回{@linkplain #DEFAULT_IDENTIFIER_QUOTE}。
	 * </p>
	 * 
	 * @param cn
	 * @return
	 */
	protected String getIdentifierQuote(Connection cn)
	{
		String identifierQuote = null;

		try
		{
			identifierQuote = cn.getMetaData().getIdentifierQuoteString();
		}
		catch (SQLException e)
		{
			identifierQuote = DEFAULT_IDENTIFIER_QUOTE;

			LOGGER.error("Default identifier quote \"" + identifierQuote + "\" is used for error", e);
		}

		return identifierQuote;
	}
}
