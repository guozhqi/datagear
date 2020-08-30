/*
 * Copyright (c) 2018 datagear.tech. All Rights Reserved.
 */

/**
 * 
 */
package org.datagear.analysis.support;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.datagear.analysis.DataSetException;
import org.datagear.analysis.DataSetProperty;
import org.datagear.analysis.DataSetResult;
import org.datagear.analysis.ResolvableDataSet;
import org.datagear.analysis.ResolvedDataSetResult;
import org.datagear.util.IOUtil;

/**
 * 抽象CSV数据集。
 * 
 * @author datagear@163.com
 *
 */
public abstract class AbstractCsvDataSet extends AbstractFmkTemplateDataSet implements ResolvableDataSet
{
	/** 作为名称行的行号 */
	private int nameRow = -1;

	public AbstractCsvDataSet()
	{
		super();
	}

	@SuppressWarnings("unchecked")
	public AbstractCsvDataSet(String id, String name)
	{
		super(id, name, Collections.EMPTY_LIST);
	}

	public AbstractCsvDataSet(String id, String name, List<DataSetProperty> properties)
	{
		super(id, name, properties);
	}

	/**
	 * 是否有名称行。
	 * 
	 * @return
	 */
	public boolean hasNameRow()
	{
		return (this.nameRow > 0);
	}

	/**
	 * 获取作为名称行的行号。
	 * 
	 * @return
	 */
	public int getNameRow()
	{
		return nameRow;
	}

	/**
	 * 设置作为名称行的行号。
	 * 
	 * @param nameRow 行号，小于{@code 1}则表示无名称行。
	 */
	public void setNameRow(int nameRow)
	{
		this.nameRow = nameRow;
	}

	@Override
	public DataSetResult getResult(Map<String, ?> paramValues) throws DataSetException
	{
		List<DataSetProperty> properties = getProperties();

		if (properties == null || properties.isEmpty())
			throw new DataSetException("[getProperties()] must not be empty");

		ResolvedDataSetResult result = resolveResult(paramValues, properties);

		return result.getResult();
	}

	@Override
	public ResolvedDataSetResult resolve(Map<String, ?> paramValues) throws DataSetException
	{
		return resolveResult(paramValues, null);
	}

	/**
	 * 
	 * @param paramValues
	 * @param properties  允许为{@code null}，此时会自动解析
	 * @return
	 * @throws DataSetException
	 */
	protected ResolvedDataSetResult resolveResult(Map<String, ?> paramValues, List<DataSetProperty> properties)
			throws DataSetException
	{
		Reader reader = null;
		try
		{
			reader = getCsvReader(paramValues);

			ResolvedDataSetResult result = resolveResult(reader, properties);

			return result;
		}
		catch(DataSetException e)
		{
			throw e;
		}
		catch(Throwable t)
		{
			throw new DataSetSourceParseException(t);
		}
		finally
		{
			IOUtil.close(reader);
		}
	}

	/**
	 * 获取CSV输入流。
	 * <p>
	 * 实现方法应该返回实例级不变的输入流。
	 * </p>
	 * 
	 * @param paramValues
	 * @return
	 * @throws Throwable
	 */
	protected abstract Reader getCsvReader(Map<String, ?> paramValues) throws Throwable;

	/**
	 * 
	 * @param csvReader
	 * @param properties 允许为{@code null}，此时会自动解析
	 * @return
	 * @throws DataSetException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected ResolvedDataSetResult resolveResult(Reader csvReader, List<DataSetProperty> properties)
			throws DataSetException
	{
		boolean resolveProperties = (properties == null || properties.isEmpty());

		CSVParser csvParser = buildCSVParser(csvReader);

		List<String> propertyNames = null;
		List<List<Object>> data = new ArrayList<>();

		int rowIdx = 0;
		int dataRowIdx = 0;

		for (CSVRecord csvRecord : csvParser)
		{
			if (isNameRow(rowIdx))
			{
				if (resolveProperties)
					propertyNames = resolveDataSetPropertyNames(csvRecord, false);
			}
			else
			{
				if (resolveProperties && dataRowIdx == 0 && propertyNames == null)
					propertyNames = resolveDataSetPropertyNames(csvRecord, true);

				if (resolveProperties)
					data.add(resolveCSVRecordValues(csvRecord, null));
				else
					data.add(resolveCSVRecordValues(csvRecord, properties));

				dataRowIdx++;
			}

			rowIdx++;
		}

		if (resolveProperties)
			return resolveResult(propertyNames, ((List) data));
		else
		{
			DataSetResult result = new DataSetResult(listRowsToMapRows(data, properties));
			return new ResolvedDataSetResult(result, properties);
		}
	}

	/**
	 * 由原始的字符串CSV数据解析{@linkplain ResolvedDataSetResult}。
	 * 
	 * @param propertyNames 允许为{@code null}
	 * @param data          允许为{@code null}
	 * @return
	 */
	protected ResolvedDataSetResult resolveResult(List<String> propertyNames, List<List<String>> data)
	{
		List<DataSetProperty> properties = new ArrayList<DataSetProperty>(
				(propertyNames == null ? 0 : propertyNames.size()));

		if (propertyNames != null)
		{
			for (String name : propertyNames)
				properties.add(new DataSetProperty(name, DataSetProperty.DataType.STRING));
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> resultData = Collections.EMPTY_LIST;

		// 根据数据格式，修订可能的数值类型：只有某一列的所有字符串都是数值格式，才认为是数值类型
		if (data != null)
		{
			int plen = properties.size();

			// 指定索引的字符串是否都是数值内容
			Map<Integer, Boolean> asNumberMap = new HashMap<>();

			for (List<String> ele : data)
			{
				for (int i = 0; i < Math.min(plen, ele.size()); i++)
				{
					Boolean asNumber = asNumberMap.get(i);

					if (Boolean.FALSE.equals(asNumber))
						continue;

					String val = ele.get(i);
					asNumberMap.put(i, isNumberString(val));
				}
			}

			for (Map.Entry<Integer, Boolean> entry : asNumberMap.entrySet())
			{
				if (Boolean.TRUE.equals(entry.getValue()))
					properties.get(entry.getKey()).setType(DataSetProperty.DataType.NUMBER);
			}

			List<List<Object>> newData = new ArrayList<List<Object>>(data.size());

			for (List<String> ele : data)
			{
				int size = Math.min(plen, ele.size());

				List<Object> newEle = new ArrayList<Object>(size);

				for (int i = 0; i < size; i++)
				{
					String val = ele.get(i);

					if (Boolean.TRUE.equals(asNumberMap.get(i)))
					{
						Number num = parseNumberString(val);
						newEle.add(num);
					}
					else
						newEle.add(val);
				}

				newData.add(newEle);
			}

			resultData = listRowsToMapRows(newData, properties);
		}

		DataSetResult result = new DataSetResult(resultData);

		return new ResolvedDataSetResult(result, properties);
	}

	/**
	 * 指定的CSV值是否可被当做数值类型。
	 * 
	 * @param value
	 * @return
	 */
	protected boolean isNumberString(String value)
	{
		if (value == null || value.isEmpty())
			return false;

		try
		{
			parseNumberString(value);

			return true;
		}
		catch(Throwable t)
		{
			return false;
		}
	}

	/**
	 * 解析数值字符串，{@linkplain #isNumberString(String)}应为{@code true}。
	 * 
	 * @param s
	 * @return
	 */
	protected Number parseNumberString(String s)
	{
		return Double.parseDouble(s);
	}

	/**
	 * 
	 * @param csvRecord
	 * @param forceIndex 是否强制使用索引号，将返回：{@code ["1"、"2"、....]}
	 * @return
	 * @throws DataSetException
	 */
	protected List<String> resolveDataSetPropertyNames(CSVRecord csvRecord, boolean forceIndex)
			throws DataSetException
	{
		int size = csvRecord.size();
		List<String> list = new ArrayList<>(size);

		for (int i = 0; i < size; i++)
		{
			if (forceIndex)
				list.add(Integer.toString(i + 1));
			else
				list.add(csvRecord.get(i));
		}

		return list;
	}

	/**
	 * 解析数据列表。
	 * <p>
	 * 如果{@code properties}为{@code null}或者对应元素为{@code null}，则返回列表对应元素将是{@linkplain String}类型。
	 * </p>
	 * 
	 * @param csvRecord
	 * @param properties 允许为{@code null}、元素为{@code null}
	 * @return
	 */
	protected List<Object> resolveCSVRecordValues(CSVRecord csvRecord, List<DataSetProperty> properties)
	{
		int size = csvRecord.size();
		List<Object> list = new ArrayList<>(size);

		int propertySize = (properties == null ? 0 : properties.size());

		for (int i = 0; i < size; i++)
		{
			DataSetProperty property = (i < propertySize ? properties.get(i) : null);

			String rawValue = csvRecord.get(i);

			if (property == null)
				list.add(rawValue);
			else
			{
				Object value = convertToPropertyDataType(rawValue, property);
				list.add(value);
			}
		}

		return list;
	}

	/**
	 * 是否名称行
	 * 
	 * @param rowIndex 行索引（以{@code 0}计数）
	 * @return
	 */
	protected boolean isNameRow(int rowIndex)
	{
		return ((rowIndex + 1) == this.nameRow);
	}

	/**
	 * 构建{@linkplain CSVParser}。
	 * 
	 * @param reader
	 * @return
	 * @throws DataSetSourceParseException
	 */
	protected CSVParser buildCSVParser(Reader reader) throws DataSetSourceParseException
	{
		try
		{
			return CSVFormat.DEFAULT.withIgnoreSurroundingSpaces().parse(reader);
		}
		catch(Throwable t)
		{
			throw new DataSetSourceParseException(t);
		}
	}
}
