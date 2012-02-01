/*
 * SpagoBI, the Open Source Business Intelligence suite
 * � 2005-2015 Engineering Group
 *
 * This file is part of SpagoBI. SpagoBI is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 2.1 of the License, or any later version. 
 * SpagoBI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with SpagoBI. If not, see: http://www.gnu.org/licenses/.
 * The complete text of SpagoBI license is included in the COPYING.LESSER file. 
 */
package it.eng.spagobi.engines.worksheet.services.export;

import it.eng.qbe.query.WhereField;
import it.eng.qbe.serializer.SerializationManager;
import it.eng.spago.base.SourceBean;
import it.eng.spago.configuration.ConfigSingleton;
import it.eng.spagobi.commons.QbeEngineStaticVariables;
import it.eng.spagobi.commons.utilities.StringUtilities;
import it.eng.spagobi.engines.qbe.crosstable.CrossTab;
import it.eng.spagobi.engines.qbe.crosstable.exporter.CrosstabXLSExporterFromJavaObject;
import it.eng.spagobi.engines.qbe.crosstable.exporter.CrosstabXLSXExporterFromJavaObject;
import it.eng.spagobi.engines.worksheet.WorksheetEngineInstance;
import it.eng.spagobi.engines.worksheet.bo.Filter;
import it.eng.spagobi.engines.worksheet.bo.FiltersInfo;
import it.eng.spagobi.engines.worksheet.bo.Sheet;
import it.eng.spagobi.engines.worksheet.bo.SheetContent;
import it.eng.spagobi.engines.worksheet.bo.WorkSheetDefinition;
import it.eng.spagobi.engines.worksheet.exceptions.WrongConfigurationForFiltersOnDomainValuesException;
import it.eng.spagobi.engines.worksheet.exporter.WorkSheetPDFExporter;
import it.eng.spagobi.engines.worksheet.exporter.WorkSheetXLSExporter;
import it.eng.spagobi.engines.worksheet.exporter.WorkSheetXLSXExporter;
import it.eng.spagobi.engines.worksheet.serializer.json.WorkSheetSerializationUtils;
import it.eng.spagobi.engines.worksheet.serializer.json.decorator.FiltersInfoJSONDecorator;
import it.eng.spagobi.engines.worksheet.services.runtime.ExecuteWorksheetQueryAction;
import it.eng.spagobi.engines.worksheet.template.WorksheetTemplate;
import it.eng.spagobi.engines.worksheet.utils.crosstab.CrosstabQueryCreator;
import it.eng.spagobi.engines.worksheet.widgets.CrosstabDefinition;
import it.eng.spagobi.engines.worksheet.widgets.TableDefinition;
import it.eng.spagobi.tools.dataset.bo.IDataSet;
import it.eng.spagobi.tools.dataset.common.behaviour.FilteringBehaviour;
import it.eng.spagobi.tools.dataset.common.datastore.DataStore;
import it.eng.spagobi.tools.dataset.common.datastore.IDataStore;
import it.eng.spagobi.tools.dataset.common.datawriter.JSONDataWriter;
import it.eng.spagobi.tools.dataset.persist.IDataSetTableDescriptor;
import it.eng.spagobi.tools.datasource.bo.IDataSource;
import it.eng.spagobi.utilities.StringUtils;
import it.eng.spagobi.utilities.assertion.Assert;
import it.eng.spagobi.utilities.engines.EngineConstants;
import it.eng.spagobi.utilities.engines.EngineMessageBundle;
import it.eng.spagobi.utilities.engines.SpagoBIEngineException;
import it.eng.spagobi.utilities.engines.SpagoBIEngineRuntimeException;
import it.eng.spagobi.utilities.engines.SpagoBIEngineServiceExceptionHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class ExportWorksheetAction extends ExecuteWorksheetQueryAction {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7193307157829114510L;
	// INPUT PARAMETERS
	public static final String MIME_TYPE = "MIME_TYPE";
	public static final String RESPONSE_TYPE = "RESPONSE_TYPE";
	public static final String WORKSHEETS = "WORKSHEETS";
	public static final String METADATA = "METADATA";
	public static final String PARAMETERS = "PARAMETERS";
	public static final String SHEETS_NUM = "SHEETS_NUM";
	
	public static final String EXPORTED_SHEETS = "EXPORTED_SHEETS";
	public static final String CONTENT = "CONTENT";
	public static final String CONTENT_PARS = "PARS";
	public static final String SPLITTING_FILTER= "splittingFilter";
	public static String WORKSHEETS_ADDITIONAL_DATA = "WORKSHEETS_ADDITIONAL_DATA";
	public static String FIELDS_OPTIONS = "fieldsOptions";
	
	public static final String MASSIVE_SPLITTING_FILTER= "SPLITTING_FILTER";
	
	// misc
	public static final String RESPONSE_TYPE_INLINE = "RESPONSE_TYPE_INLINE";
	public static final String RESPONSE_TYPE_ATTACHMENT = "RESPONSE_TYPE_ATTACHMENT";

	public static String SVG = "svg";
	public static String OUTPUT_FORMAT = "type";

	public static String OUTPUT_FORMAT_PNG = "image/png";
	public static String OUTPUT_FORMAT_JPEG = "image/jpeg";
	public static String OUTPUT_FORMAT_PDF = "application/pdf";
	public static String OUTPUT_FORMAT_SVG = "image/svg+xml";
	
	private static int DEFAULT_COLUMN_WIDTH = 30;
	private static int CHART_START_COLUMN = 0;
	private static int CHART_END_COLUMN = 4;
	private static int CHART_HEIGHT_IN_ROWS = 25;
	
	private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

	protected DecimalFormat numberFormat;
	protected String userDateFormat;
	// used in massive export case
	protected boolean splittingFilter = true;

	/** Logger component. */
	public static transient Logger logger = Logger.getLogger(ExportWorksheetAction.class);


	public void service(SourceBean request, SourceBean response) {				

		logger.debug("IN");
		String responseType = null;
		boolean writeBackResponseInline = false;
		String mimeType = null;
		JSONObject worksheetJSON = null;
		File exportFile = null;
		JSONArray metadataPropertiesJSON = null;
		JSONArray parametersJSON = null;
		

		try {
			setSpagoBIRequestContainer( request );
			setSpagoBIResponseContainer( response );

			Locale locale = (Locale)getEngineInstance().getEnv().get(EngineConstants.ENV_LOCALE);	
			numberFormat =  (DecimalFormat) NumberFormat.getInstance(locale);
			numberFormat.applyPattern("##,##0.00");
			userDateFormat = (String)getEngineInstance().getEnv().get(EngineConstants.ENV_USER_DATE_FORMAT);	


			mimeType = getAttributeAsString( MIME_TYPE );
			logger.debug(MIME_TYPE + ": " + mimeType);		
			responseType = getAttributeAsString( RESPONSE_TYPE );
			logger.debug(RESPONSE_TYPE + ": " + responseType);
			
			String splittingFilterS = getAttributeAsString(MASSIVE_SPLITTING_FILTER);
			if (splittingFilterS != null) {
				splittingFilter = Boolean.valueOf(splittingFilterS);
			}
			logger.debug(MASSIVE_SPLITTING_FILTER + ": " + splittingFilter);

			worksheetJSON = getAttributeAsJSONObject( WORKSHEETS );	
			if ( worksheetJSON == null ) {
				logger.debug("worksheet JSON conversion: massive export case");
				WorksheetEngineInstance engineInstance = getEngineInstance();
				WorkSheetDefinition workSheetDefinition = (WorkSheetDefinition) engineInstance.getAnalysisState();
				FiltersInfoJSONDecorator decorator = new FiltersInfoJSONDecorator(workSheetDefinition, engineInstance.getDataSet());
				JSONObject prevWorksheetJSON = workSheetDefinition.getConf(decorator);
				worksheetJSON = convertToExportJSON(engineInstance, prevWorksheetJSON);
			} else {
				logger.debug("worksheet retrieved from request: simple export case");				
			}

			logger.debug(WORKSHEETS + ": " + worksheetJSON);

			
			if( requestContainsAttribute(METADATA) ) {
				metadataPropertiesJSON = getAttributeAsJSONArray( METADATA );	
				logger.debug(METADATA + ": " + metadataPropertiesJSON);
			}
			
			if( requestContainsAttribute(PARAMETERS) ) {
				parametersJSON = getAttributeAsJSONArray( PARAMETERS );	
				logger.debug(PARAMETERS + ": " + parametersJSON);
			}
			
			writeBackResponseInline = RESPONSE_TYPE_INLINE.equalsIgnoreCase(responseType);

			if( "application/vnd.ms-excel".equalsIgnoreCase( mimeType ) ) {

				exportFile = File.createTempFile("worksheet", ".xls");
				FileOutputStream stream = new FileOutputStream(exportFile);
				try {
					exportToXLS(worksheetJSON, metadataPropertiesJSON, parametersJSON, stream);
				} finally {
					if (stream != null) {
						stream.close();
					}
				}

			} else if( "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equalsIgnoreCase( mimeType ) ) {

				exportFile = File.createTempFile("worksheet", ".xlsx");
				FileOutputStream stream = new FileOutputStream(exportFile);
				try {
					exportToXLSX(worksheetJSON, metadataPropertiesJSON, parametersJSON, stream);
				} finally {
					if (stream != null) {
						stream.close();
					}
				}

			} else if ( "application/pdf".equalsIgnoreCase( mimeType ) ) {

				exportFile = File.createTempFile("worksheet", ".pdf");
				FileOutputStream stream = new FileOutputStream(exportFile);
				try {
					exportToPDF(worksheetJSON, stream);
				} finally {
					if (stream != null) {
						stream.close();
					}
				}

			} else {
				throw new SpagoBIEngineException("Cannot export worksheet in " + mimeType + " format, only application/vnd.ms-excel ans application/pdf are supported");
			}

			try {				
				writeBackToClient(exportFile, null, writeBackResponseInline, exportFile.getName(), mimeType);
			} catch (IOException ioe) {
				throw new SpagoBIEngineException("Impossible to write back the responce to the client", ioe);
			}

		} catch (Throwable t) {
			throw SpagoBIEngineServiceExceptionHandler.getInstance().getWrappedException(getActionName(), getEngineInstance(), t);
		} finally {
			logger.debug("OUT");
		}	
	}

	public void exportToPDF(JSONObject worksheetJSON, OutputStream outputStream) throws Exception {

		WorkSheetPDFExporter exporter = new WorkSheetPDFExporter();
		exporter.open(outputStream);
		exporter.setNumberFormat(numberFormat);
		exporter.setUserDateFormat(userDateFormat);

		int sheetsNumber = worksheetJSON.getInt(SHEETS_NUM);
		JSONArray exportedSheets = worksheetJSON.getJSONArray(EXPORTED_SHEETS);
		for (int i = 0; i < sheetsNumber; i++) {
			JSONObject sheetJ = exportedSheets.getJSONObject(i);
			if (isTableContent(sheetJ)) {
				IDataStore dataStore = getTableDataStore(sheetJ, null);
				exporter.addSheet(sheetJ, dataStore);
			} else {
				exporter.addSheet(sheetJ);
			}
		}

		exporter.close();
		outputStream.flush();
	}

	public void exportToXLS(JSONObject worksheetJSON, JSONArray metadataPropertiesJSON, JSONArray parametersJSON, OutputStream stream) throws Exception {
		
		HSSFWorkbook wb = new HSSFWorkbook();
		int sheetsNumber = worksheetJSON.getInt(SHEETS_NUM);
		WorkSheetXLSExporter exporter = new WorkSheetXLSExporter();
		CreationHelper createHelper = wb.getCreationHelper();

		if (metadataPropertiesJSON != null) {
			exportMetadataToXLS(wb, exporter, createHelper, metadataPropertiesJSON, parametersJSON);
		}
		
		JSONArray exportedSheets = worksheetJSON.getJSONArray(EXPORTED_SHEETS);

		JSONArray fieldOptions = WorkSheetSerializationUtils.getFieldOptions(worksheetJSON);
		
		for (int i = 0; i < sheetsNumber; i++) {

			JSONObject sheetJ = exportedSheets.getJSONObject(i);
			JSONObject optionalFilters = sheetJ.optJSONObject(QbeEngineStaticVariables.FILTERS);
			String sheetName = sheetJ.getString(SHEET);
			List<WhereField> splittingWF = getSplittingFieldValues(optionalFilters, sheetName);
			WhereField splittingWhereField = null;
			if(splittingWF==null || splittingWF.size()==0){
				exportSheetToXLS(wb, sheetJ, fieldOptions, exporter, createHelper, splittingWhereField);
			}else{
				for(int y=0; y< splittingWF.size(); y++){
					splittingWhereField = splittingWF.get(y);
					exportSheetToXLS( wb, sheetJ, fieldOptions, exporter, createHelper, splittingWhereField);
				}
			}
		}

		wb.write(stream);
		stream.flush();
	}
	
	public void exportMetadataToXLS(HSSFWorkbook wb, WorkSheetXLSExporter exporter, CreationHelper createHelper
			, JSONArray metadataPropertiesJSON, JSONArray parametersJSON) throws Exception{
		
		int FIRST_ROW = 0;
		int FIRST_COLUMN = 0;
		int rowCount = 0;
		
		JSONArray technicalMetadataProperty;
		JSONArray shortBusinessMetadataProperty;
		JSONArray longBusinessMetadataProperty;
		
		HSSFSheet sheet = wb.createSheet(EngineMessageBundle.getMessage("worksheet.export.metadata.title", this.getLocale()));
		
		sheet.setColumnWidth(FIRST_COLUMN, 256*25);
		sheet.setColumnWidth(FIRST_COLUMN + 1, 256*90);
		
		
		CellStyle headerCellStyle = exporter.buildMetadataTitleCellStyle(sheet);
		CellStyle metaNameCellStyle =  exporter.buildMetadataNameCellStyle(sheet);
		CellStyle metaValueCellStyle =  exporter.buildMetadataValueCellStyle(sheet);
		
		Row row;
		Cell nameCell;
		Cell valueCell;
		Cell headerCell;
		String text;
		
		technicalMetadataProperty = new JSONArray();
		shortBusinessMetadataProperty = new JSONArray();
		longBusinessMetadataProperty = new JSONArray();
		
		for (int i = 0; i < metadataPropertiesJSON.length(); i++) {
			JSONObject metadataProperty = metadataPropertiesJSON.getJSONObject(i);		
			String  metadataPropertyType = metadataProperty.getString("meta_type");
			if("SHORT_TEXT".equalsIgnoreCase(metadataPropertyType)) {
				shortBusinessMetadataProperty.put(metadataProperty);
				continue;
			} else if("LONG_TEXT".equalsIgnoreCase(metadataPropertyType)) {
				longBusinessMetadataProperty.put(metadataProperty);
				continue;
			} else {
				technicalMetadataProperty.put(metadataProperty);
			}
			
		}
		
		if (technicalMetadataProperty.length() > 0) {
			
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.technicalMetadata", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(HSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			
			rowCount++;
			
			for (int i = 0; i < technicalMetadataProperty.length(); i++) {
				JSONObject metadataProperty = technicalMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
				rowCount++;
			}
			
			rowCount = rowCount + 2;
			
		}
		
		if (shortBusinessMetadataProperty.length() + longBusinessMetadataProperty.length() > 0) {
		
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.businessMetadata", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(HSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			rowCount++;
			
			
			for(int i = 0; i < shortBusinessMetadataProperty.length(); i++, rowCount++) {
				
				JSONObject metadataProperty = shortBusinessMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
			}
			
			for(int i = 0; i < longBusinessMetadataProperty.length(); i++, rowCount++) {
				
				JSONObject metadataProperty = longBusinessMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
			}
			
			rowCount = rowCount + 2;
		
		}
		
		if (parametersJSON.length() > 0) {
		
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.analyticalDrivers", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(HSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			
			rowCount++;
			
			Drawing drawing = sheet.createDrawingPatriarch();
			
			for (int i = 0; i < parametersJSON.length(); i++) {
				JSONObject parameterJSON = parametersJSON.getJSONObject(i);
				String name = parameterJSON.getString("name");
				String value = parameterJSON.getString("value");
				String description = parameterJSON.optString("description");
				
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(name));
				nameCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				
				if (StringUtilities.isNotEmpty(description)) {
					 
					valueCell.setCellValue(createHelper.createRichTextString(description));
					
				    ClientAnchor anchor = createHelper.createClientAnchor();
				    anchor.setCol1(valueCell.getColumnIndex());
				    anchor.setCol2(valueCell.getColumnIndex()+1);
				    anchor.setRow1(row.getRowNum());
				    anchor.setRow2(row.getRowNum()+3);
	
				    Comment comment = drawing.createCellComment(anchor);
				    RichTextString str = createHelper.createRichTextString(value);
				    comment.setString(str);
				    comment.setAuthor("SpagoBI");
	
					valueCell.setCellComment(comment);
				} else {
					valueCell.setCellValue(createHelper.createRichTextString(value));
				}
				valueCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
				rowCount++;
			}
			
		}
		
	}

	public void exportSheetToXLS(HSSFWorkbook wb,JSONObject sheetJ, JSONArray fieldOptions, WorkSheetXLSExporter exporter,CreationHelper createHelper, WhereField splittingWhereField) throws Exception{

		//The number of row of the sheet
		int sheetRow;

		String sheetName = sheetJ.getString(SHEET);

		sheetRow=0;

		if(splittingWhereField!=null){
			sheetName = sheetName+" ("+splittingWhereField.getRightOperand().values[0]+")";
		}

		HSSFSheet sheet = wb.createSheet(sheetName);
		sheet.setDefaultColumnWidth(DEFAULT_COLUMN_WIDTH);

		HSSFPatriarch patriarch = sheet.createDrawingPatriarch();

		//		sheet.createRow(sheetRow);
		//		sheetRow++;

		if(sheetJ.has(WorkSheetXLSExporter.HEADER)){
			JSONObject header = sheetJ.getJSONObject(WorkSheetXLSExporter.HEADER);
			if(header!=null){
				sheetRow = exporter.setHeader(sheet, header, createHelper, wb, patriarch, sheetRow);
			}
			sheet.createRow(sheetRow);
			sheetRow++;
		}
		
		JSONObject optionalFiltersJSON = sheetJ.optJSONObject(QbeEngineStaticVariables.FILTERS);
		List<WhereField> optionalFilters = getOptionalFilters(optionalFiltersJSON);
		if (splittingWhereField != null) {
			optionalFilters.add(splittingWhereField);
		}
    	Map<String, List<String>> filters = getSheetFiltersInfo(sheetJ.getString(SHEET), optionalFilters);
		if (filters != null && !filters.isEmpty()) {
			sheetRow = fillFiltersInfo(filters, wb, sheet, splittingWhereField, createHelper, sheetRow, 0);
			sheet.createRow(sheetRow);
			sheetRow++;
		}

		if(sheetJ.has(WorkSheetXLSExporter.CONTENT)){
			sheetRow = fillSheetContent(wb, sheet, sheetJ, fieldOptions,splittingWhereField, createHelper, exporter, patriarch, sheetRow);
		}			

		sheet.createRow(sheetRow);
		sheetRow++;

		if(sheetJ.has(WorkSheetXLSExporter.FOOTER)){
			JSONObject footer = sheetJ.getJSONObject(WorkSheetXLSExporter.FOOTER);
			if(footer!=null){
				exporter.setFooter(sheet, footer, createHelper, wb, patriarch, sheetRow);
			}
		}	
	}

	private int fillFiltersInfo(Map<String, List<String>> filters, HSSFWorkbook wb, HSSFSheet sheet,
			WhereField splittingWhereField, CreationHelper createHelper,
			int beginRowHeaderData, int beginColumnHeaderData) {

		int sheetRow = beginRowHeaderData;
		
		if (filters != null && !filters.isEmpty()) {
			
			WorkSheetXLSExporter exporter = new WorkSheetXLSExporter();
			
			CellStyle titleCellStyle = exporter.buildFiltersTitleCellStyle(sheet);
			CellStyle contentCellStyle = exporter.buildFiltersValuesCellStyle(sheet);
			
			// table title
			HSSFRow row = sheet.createRow(sheetRow); // row for filters title
			Cell cell = row.createCell(beginColumnHeaderData);
			cell.setCellType(HSSFCell.CELL_TYPE_STRING);
			String text = EngineMessageBundle.getMessage("worksheet.export.filters.info.title", this.getLocale());
			cell.setCellValue(createHelper.createRichTextString(text));
			cell.setCellStyle(titleCellStyle);
			
			sheetRow++;
	
			// table content
			Iterator<String> it = filters.keySet().iterator();
			while (it.hasNext()) {
				// Filter name and values
				String aKey = it.next();
				List<String> values = filters.get(aKey);
				String[] array = values.toArray(new String[]{});
				String allFilterValues = StringUtils.join(array, FiltersInfoJSONDecorator.VALUES_SEPARATOR);
				Row aRow = sheet.createRow(sheetRow);
				Cell aCell = aRow.createCell(beginColumnHeaderData);
				aCell.setCellType(HSSFCell.CELL_TYPE_STRING);
				aCell.setCellValue(aKey + " : " + allFilterValues);
				aCell.setCellStyle(contentCellStyle);
				sheetRow++;
			}
			
		}

		return sheetRow;
	}

	private Map<String, List<String>> getSheetFiltersInfo(String sheetName, List<WhereField> additionalFilters) {
		WorksheetEngineInstance worksheetEngineInstance = getEngineInstance();
		WorkSheetDefinition workSheetDefinition = (WorkSheetDefinition) worksheetEngineInstance.getAnalysisState();
		Map<String, List<String>> toReturn = null;
		FiltersInfo infos = new FiltersInfo(workSheetDefinition, worksheetEngineInstance.getDataSet());
		infos.setAdditionalFilters(additionalFilters);
		try {
			toReturn = infos.getFiltersInfoAsMap(sheetName);
		} catch (WrongConfigurationForFiltersOnDomainValuesException e) {
			throw new SpagoBIEngineRuntimeException("Wrong filters configuration", e);
		}
		return toReturn;
	}
	
	public int fillSheetContent(HSSFWorkbook wb, HSSFSheet sheet, JSONObject sheetJ, JSONArray fieldOptions, WhereField splittingWhereField,
			CreationHelper createHelper, WorkSheetXLSExporter exporter, HSSFPatriarch patriarch, int sheetRow) throws Exception {

		JSONObject content = sheetJ.getJSONObject(WorkSheetXLSExporter.CONTENT);
		String sheetType = content.getString(WorkSheetXLSExporter.SHEET_TYPE);


		if (sheetType != null && !sheetType.equals("")) {

			Locale locale = (Locale)getEngineInstance().getEnv().get(EngineConstants.ENV_LOCALE);	
			if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.CHART)) {
				List<File> images = WorkSheetXLSExporter.getImage(content);
				
				if(images!=null){

					for (int j=0; j< images.size(); j++){
						for (int i = 0; i < CHART_HEIGHT_IN_ROWS; i++) {
							sheet.createRow(sheetRow + i);
						}
						exporter.setImageIntoWorkSheet(wb, patriarch, images.get(j), CHART_START_COLUMN,
								CHART_END_COLUMN, sheetRow, CHART_HEIGHT_IN_ROWS,
								HSSFWorkbook.PICTURE_TYPE_JPEG);
						sheetRow = sheetRow + CHART_HEIGHT_IN_ROWS;
					}

				}
				
			} else if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.CROSSTAB)) {
				JSONArray calculateFieldsJSON = null;
				String crosstabDefinition = content
						.optString("CROSSTABDEFINITION");
				if (crosstabDefinition.equals("")) {
					crosstabDefinition = content
							.getString("crosstabDefinition");
				}

				String sheetName = sheetJ.getString(SHEET);
				JSONObject filters = sheetJ
						.optJSONObject(QbeEngineStaticVariables.FILTERS);
				JSONObject crosstabDefinitionJSON = new JSONObject(
						crosstabDefinition);

				String calculateFields = content.optString("CF");
				if (calculateFields != null && !calculateFields.equals("")) {
					calculateFieldsJSON = new JSONArray(calculateFields);
				}

				CrossTab cs = getCrosstab(crosstabDefinitionJSON, fieldOptions,
						filters, sheetName, splittingWhereField,
						calculateFieldsJSON);

				String calculatedFieldsDecimalsString = (String) ConfigSingleton
						.getInstance()
						.getAttribute(
								"QBE.QBE-CROSSTAB-CALCULATEDFIELDS-DECIMAL.value");
				int calculatedFieldsDecimals = 0;
				if (calculatedFieldsDecimalsString != null) {
					calculatedFieldsDecimals = Integer
							.valueOf(calculatedFieldsDecimalsString);
				}
				CrosstabXLSExporterFromJavaObject expCr = new CrosstabXLSExporterFromJavaObject(
						calculatedFieldsDecimals);

				sheetRow = expCr.fillAlreadyCreatedSheet(sheet, cs,
						crosstabDefinitionJSON, createHelper, sheetRow, locale);
				
			} else if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.TABLE)) {

				IDataStore dataStore = getTableDataStore(sheetJ, fieldOptions);
				long recCount = dataStore.getRecordsCount();
				recCount = (new Long(recCount)).intValue() + 5;
				int startRow = sheetRow;
				for(int i=0; i<recCount; i++){
					sheet.createRow(sheetRow);
					sheetRow++;
				}
				exporter.designTableInWorksheet(sheet, wb, createHelper, dataStore,startRow, locale);			
			}
		}
		return sheetRow;
	}

	public void exportToXLSX(JSONObject worksheetJSON , JSONArray metadataPropertiesJSON,JSONArray parametersJSON,OutputStream stream) throws Exception {

		XSSFWorkbook wb = new XSSFWorkbook();
		int sheetsNumber = worksheetJSON.getInt(SHEETS_NUM);
		WorkSheetXLSXExporter exporter = new WorkSheetXLSXExporter();
		CreationHelper createHelper = wb.getCreationHelper();

		if (metadataPropertiesJSON != null) {
			exportMetadataToXLSX(wb, exporter, createHelper, metadataPropertiesJSON, parametersJSON );
		}
		
		JSONArray exportedSheets = worksheetJSON.getJSONArray(EXPORTED_SHEETS);

		JSONArray fieldOptions = WorkSheetSerializationUtils.getFieldOptions(worksheetJSON);

		for (int i = 0; i < sheetsNumber; i++) {

			JSONObject sheetJ = exportedSheets.getJSONObject(i);
			JSONObject optionalFilters = sheetJ.optJSONObject(QbeEngineStaticVariables.FILTERS);
			String sheetName = sheetJ.getString(SHEET);
			List<WhereField> splittingWF = getSplittingFieldValues(optionalFilters, sheetName);
			WhereField splittingWhereField = null;

			if(splittingWF==null || splittingWF.size()==0){
				exportSheetToXLSX(wb, sheetJ,fieldOptions, exporter, createHelper, splittingWhereField);
			}else{
				for(int y=0; y< splittingWF.size(); y++){
					splittingWhereField = splittingWF.get(y);
					exportSheetToXLSX( wb, sheetJ,fieldOptions, exporter, createHelper, splittingWhereField);
				}
			}
		}

		wb.write(stream);
		stream.flush();

	}
	
	public void exportMetadataToXLSX(XSSFWorkbook wb, WorkSheetXLSXExporter exporter, CreationHelper createHelper
			, JSONArray metadataPropertiesJSON, JSONArray parametersJSON) throws Exception{
		
		int FIRST_ROW = 0;
		int FIRST_COLUMN = 0;
		int rowCount = 0;
		
		JSONArray technicalMetadataProperty;
		JSONArray shortBusinessMetadataProperty;
		JSONArray longBusinessMetadataProperty;
		
		XSSFSheet sheet = wb.createSheet(EngineMessageBundle.getMessage("worksheet.export.metadata.title", this.getLocale()));
		
		sheet.setColumnWidth(FIRST_COLUMN, 256*25);
		sheet.setColumnWidth(FIRST_COLUMN + 1, 256*90);
		
		
		CellStyle headerCellStyle = exporter.buildMetadataTitleCellStyle(sheet);
		CellStyle metaNameCellStyle =  exporter.buildMetadataNameCellStyle(sheet);
		CellStyle metaValueCellStyle =  exporter.buildMetadataValueCellStyle(sheet);
		
		Row row;
		Cell nameCell;
		Cell valueCell;
		Cell headerCell;
		String text;
		
		technicalMetadataProperty = new JSONArray();
		shortBusinessMetadataProperty = new JSONArray();
		longBusinessMetadataProperty = new JSONArray();
		
		for (int i = 0; i < metadataPropertiesJSON.length(); i++) {
			JSONObject metadataProperty = metadataPropertiesJSON.getJSONObject(i);		
			String  metadataPropertyType = metadataProperty.getString("meta_type");
			if("SHORT_TEXT".equalsIgnoreCase(metadataPropertyType)) {
				shortBusinessMetadataProperty.put(metadataProperty);
				continue;
			} else if("LONG_TEXT".equalsIgnoreCase(metadataPropertyType)) {
				longBusinessMetadataProperty.put(metadataProperty);
				continue;
			} else {
				technicalMetadataProperty.put(metadataProperty);
			}
			
		}
		
		if (technicalMetadataProperty.length() > 0) {
			
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.technicalMetadata", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(XSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			
			rowCount++;
			
			for (int i = 0; i < technicalMetadataProperty.length(); i++) {
				JSONObject metadataProperty = technicalMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
				rowCount++;
			}
			
			rowCount = rowCount + 2;
			
		}
		
		if (shortBusinessMetadataProperty.length() + longBusinessMetadataProperty.length() > 0) {
		
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.businessMetadata", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(XSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			rowCount++;
			
			
			for(int i = 0; i < shortBusinessMetadataProperty.length(); i++, rowCount++) {
				
				JSONObject metadataProperty = shortBusinessMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
			}
			
			for(int i = 0; i < longBusinessMetadataProperty.length(); i++, rowCount++) {
				
				JSONObject metadataProperty = longBusinessMetadataProperty.getJSONObject(i);	
				
				String  metadataPropertyName = metadataProperty.getString("meta_name");
				String  metadataPropertyValue = metadataProperty.getString("meta_content");
				
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(metadataPropertyName));
				nameCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				valueCell.setCellValue(createHelper.createRichTextString(metadataPropertyValue));
				valueCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
			}
			
			rowCount = rowCount + 2;
		
		}
		
		if (parametersJSON.length() > 0) {
		
			row = sheet.createRow((FIRST_ROW) + rowCount);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			headerCell = row.createCell(FIRST_COLUMN + 1);
			text = EngineMessageBundle.getMessage("worksheet.export.metadata.analyticalDrivers", this.getLocale());
			headerCell.setCellValue(createHelper.createRichTextString(text));
			headerCell.setCellType(XSSFCell.CELL_TYPE_STRING);
			headerCell.setCellStyle(headerCellStyle);
			
			rowCount++;
			
			Drawing drawing = sheet.createDrawingPatriarch();
			
			for (int i = 0; i < parametersJSON.length(); i++) {
				JSONObject parameterJSON = parametersJSON.getJSONObject(i);
				String name = parameterJSON.getString("name");
				String value = parameterJSON.getString("value");
				String description = parameterJSON.optString("description");
				
				row = sheet.createRow((FIRST_ROW) + rowCount);
				
				nameCell = row.createCell(FIRST_COLUMN);
				nameCell.setCellValue(createHelper.createRichTextString(name));
				nameCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				nameCell.setCellStyle(metaNameCellStyle);
				
				valueCell = row.createCell(FIRST_COLUMN + 1);
				
				if (StringUtilities.isNotEmpty(description)) {
					 
					valueCell.setCellValue(createHelper.createRichTextString(description));
					
				    ClientAnchor anchor = createHelper.createClientAnchor();
				    anchor.setCol1(valueCell.getColumnIndex());
				    anchor.setCol2(valueCell.getColumnIndex()+1);
				    anchor.setRow1(row.getRowNum());
				    anchor.setRow2(row.getRowNum()+3);
	
				    Comment comment = drawing.createCellComment(anchor);
				    RichTextString str = createHelper.createRichTextString(value);
				    comment.setString(str);
				    comment.setAuthor("SpagoBI");
	
					valueCell.setCellComment(comment);
				} else {
					valueCell.setCellValue(createHelper.createRichTextString(value));
				}
				valueCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				valueCell.setCellStyle(metaValueCellStyle);
				rowCount++;
			}
			
		}
		
	}
	
	public void exportSheetToXLSX(XSSFWorkbook wb,JSONObject sheetJ, JSONArray fieldOptions, WorkSheetXLSXExporter exporter,CreationHelper createHelper, WhereField splittingWhereField) throws Exception{

		//The number of row of the sheet
		int sheetRow;

		String sheetName = sheetJ.getString(SHEET);

		sheetRow=0;

		if(splittingWhereField!=null){
			sheetName = sheetName+" ("+splittingWhereField.getRightOperand().values[0]+")";
		}

		XSSFSheet sheet = wb.createSheet(sheetName);
		sheet.setDefaultColumnWidth(DEFAULT_COLUMN_WIDTH);

		XSSFDrawing patriarch = sheet.createDrawingPatriarch();

		//		sheet.createRow(sheetRow);
		//		sheetRow++;

		if(sheetJ.has(WorkSheetXLSExporter.HEADER)){
			JSONObject header = sheetJ.getJSONObject(WorkSheetXLSExporter.HEADER);
			if(header!=null){
				sheetRow = exporter.setHeader(sheet, header, createHelper, wb, patriarch, sheetRow);
			}
			sheet.createRow(sheetRow);
			sheetRow++;
		}
		
		JSONObject optionalFiltersJSON = sheetJ.optJSONObject(QbeEngineStaticVariables.FILTERS);
		List<WhereField> optionalFilters = getOptionalFilters(optionalFiltersJSON);
		if (splittingWhereField != null) {
			optionalFilters.add(splittingWhereField);
		}
    	Map<String, List<String>> filters = getSheetFiltersInfo(sheetJ.getString(SHEET), optionalFilters);
		if (filters != null && !filters.isEmpty()) {
			sheetRow = fillFiltersInfo(filters, wb, sheet, splittingWhereField, createHelper, sheetRow, 0);
			sheet.createRow(sheetRow);
			sheetRow++;
		}

		if(sheetJ.has(WorkSheetXLSExporter.CONTENT)){
			sheetRow = fillSheetContent(wb, sheet, sheetJ, fieldOptions,splittingWhereField, createHelper, exporter, patriarch, sheetRow);
		}			

		sheet.createRow(sheetRow);
		sheetRow++;

		if(sheetJ.has(WorkSheetXLSExporter.FOOTER)){
			JSONObject footer = sheetJ.getJSONObject(WorkSheetXLSExporter.FOOTER);
			if(footer!=null){
				exporter.setFooter(sheet, footer, createHelper, wb, patriarch, sheetRow);
			}
		}	
	}
	
	private int fillFiltersInfo(Map<String, List<String>> filters, XSSFWorkbook wb, XSSFSheet sheet,
			WhereField splittingWhereField, CreationHelper createHelper,
			int beginRowHeaderData, int beginColumnHeaderData) {

		int sheetRow = beginRowHeaderData;
		
		if (filters != null && !filters.isEmpty()) {
			
			WorkSheetXLSExporter exporter = new WorkSheetXLSExporter();
			
			CellStyle titleCellStyle = exporter.buildFiltersTitleCellStyle(sheet);
			CellStyle contentCellStyle = exporter.buildFiltersValuesCellStyle(sheet);
			
			// table title
			XSSFRow row = sheet.createRow(sheetRow); // row for filters title
			Cell cell = row.createCell(beginColumnHeaderData);
			cell.setCellType(XSSFCell.CELL_TYPE_STRING);
			String text = EngineMessageBundle.getMessage("worksheet.export.filters.info.title", this.getLocale());
			cell.setCellValue(createHelper.createRichTextString(text));
			cell.setCellStyle(titleCellStyle);
			
			sheetRow++;
	
			// table content
			Iterator<String> it = filters.keySet().iterator();
			while (it.hasNext()) {
				// Filter name and values
				String aKey = it.next();
				List<String> values = filters.get(aKey);
				String[] array = values.toArray(new String[]{});
				String allFilterValues = StringUtils.join(array, FiltersInfoJSONDecorator.VALUES_SEPARATOR);
				Row aRow = sheet.createRow(sheetRow);
				Cell aCell = aRow.createCell(beginColumnHeaderData);
				aCell.setCellType(XSSFCell.CELL_TYPE_STRING);
				aCell.setCellValue(aKey + " : " + allFilterValues);
				aCell.setCellStyle(contentCellStyle);
				sheetRow++;
			}
			
		}

		return sheetRow;
	}

	public int fillSheetContent(XSSFWorkbook wb, XSSFSheet sheet, JSONObject sheetJ , JSONArray fieldOptions, WhereField splittingWhereField,
			CreationHelper createHelper, WorkSheetXLSXExporter exporter, XSSFDrawing patriarch, int sheetRow) throws Exception {

		JSONObject content = sheetJ.getJSONObject(WorkSheetXLSXExporter.CONTENT);
		String sheetType = content.getString(WorkSheetXLSXExporter.SHEET_TYPE);


		if (sheetType != null && !sheetType.equals("")) {

			Locale locale = (Locale)getEngineInstance().getEnv().get(EngineConstants.ENV_LOCALE);	
			if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.CHART)) {

				List<File> images = WorkSheetXLSExporter.getImage(content);
				
				if(images!=null){
					for (int j=0; j< images.size(); j++){
						for (int i = 0; i < CHART_HEIGHT_IN_ROWS; i++) {
							sheet.createRow(sheetRow + i);
						}
						exporter.setImageIntoWorkSheet(wb, patriarch, images.get(j), CHART_START_COLUMN,
								CHART_END_COLUMN, sheetRow, CHART_HEIGHT_IN_ROWS,
								HSSFWorkbook.PICTURE_TYPE_JPEG);
						sheetRow = sheetRow + CHART_HEIGHT_IN_ROWS;
					}
				}
				
			} else if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.CROSSTAB)) {
				JSONArray calculateFieldsJSON = null;
				String crosstabDefinition = content
						.getString("CROSSTABDEFINITION");
				String crosstab = content
						.getString(WorkSheetXLSExporter.CROSSTAB);
				String sheetName = sheetJ.getString(SHEET);
				JSONObject filters = sheetJ
						.optJSONObject(QbeEngineStaticVariables.FILTERS);
				JSONObject crosstabDefinitionJSON = new JSONObject(
						crosstabDefinition);
				JSONObject crosstabJSON = new JSONObject(crosstab);

				String calculateFields = content.optString("CF");
				if (calculateFields != null) {
					calculateFieldsJSON = new JSONArray(calculateFields);
				}

				String calculatedFieldsDecimalsString = (String) ConfigSingleton
						.getInstance()
						.getAttribute(
								"QBE.QBE-CROSSTAB-CALCULATEDFIELDS-DECIMAL.value");
				int calculatedFieldsDecimals = 0;
				if (calculatedFieldsDecimalsString != null) {
					calculatedFieldsDecimals = Integer
							.valueOf(calculatedFieldsDecimalsString);
				}

				CrossTab cs = getCrosstab(crosstabDefinitionJSON, fieldOptions,
						filters, sheetName, splittingWhereField,
						calculateFieldsJSON);
				CrosstabXLSXExporterFromJavaObject expCr = new CrosstabXLSXExporterFromJavaObject(
						calculatedFieldsDecimals);
				sheetRow = expCr.fillAlreadyCreatedSheet(sheet, cs,
						crosstabJSON, createHelper, sheetRow, locale);
			} else if (sheetType.equalsIgnoreCase(WorkSheetXLSExporter.TABLE)) {

				IDataStore dataStore = getTableDataStore(sheetJ,  fieldOptions);
				long recCount = dataStore.getRecordsCount();
				recCount = (new Long(recCount)).intValue() + 5;
				int startRow = sheetRow;
				for(int i=0; i<recCount; i++){
					sheet.createRow(sheetRow);
					sheetRow++;
				}
				exporter.designTableInWorksheet(sheet, wb, createHelper, dataStore,startRow, locale);			
			}
		}
		return sheetRow;
	}



	/**
	 * Execute the query active in the engine instance and return
	 * the data store
	 * @return the data store after the execution of the active query
	 */
	private IDataStore getTableDataStore(JSONObject sheetJ, JSONArray fieldOptions) throws Exception {
		JSONObject sheetContentPars = null;
		JSONArray jsonVisibleSelectFields = null;
		String sheetName = sheetJ.getString(SHEET);
		JSONObject sheetContent = sheetJ.optJSONObject(CONTENT);
		sheetContentPars = sheetContent.optJSONObject(CONTENT_PARS);
		// get the visible columns
		if (sheetContentPars != null) {
			jsonVisibleSelectFields = sheetContentPars
			.optJSONArray(QbeEngineStaticVariables.OPTIONAL_VISIBLE_COLUMNS);
		}
		JSONObject filters = sheetJ.optJSONObject(QbeEngineStaticVariables.FILTERS);
		IDataStore dataStore = executeQuery(jsonVisibleSelectFields, filters, sheetName, fieldOptions);
		return dataStore;
	}

	/**
	 * Return true if the content of a sheet is a table
	 * @param sheetJSON a sheet
	 * @return true if the content of a sheet is a table
	 */
	public boolean isTableContent(JSONObject sheetJSON){
		try{
			JSONObject content = sheetJSON.getJSONObject(WorkSheetPDFExporter.CONTENT);
			String sheetType = content.getString(WorkSheetPDFExporter.SHEET_TYPE);
			return (WorkSheetPDFExporter.TABLE.equalsIgnoreCase(sheetType));	
		}catch (JSONException e){
			return false;
		}
	}


	public CrossTab getCrosstab(JSONObject crosstabDefinitionJSON, JSONArray fieldOptions, JSONObject optionalFilters,String sheetName, WhereField splittingWhereField, JSONArray calculateFieldsJSON) throws Exception{

		// retrieve engine instance
		WorksheetEngineInstance engineInstance = getEngineInstance();
		Assert.assertNotNull(engineInstance, "It's not possible to execute " + this.getActionName() + " service before having properly created an instance of EngineInstance class");

		// persist dataset into temporary table	
		IDataSetTableDescriptor descriptor = this.persistDataSet();

		IDataSet dataset = engineInstance.getDataSet();
		// build SQL query against temporary table
		List<WhereField> whereFields = new ArrayList<WhereField>();
		if (!dataset.hasBehaviour(FilteringBehaviour.ID)) {
			/* 
			 * If the dataset had the FilteringBehaviour, data was already filtered on domain values by the FilteringBehaviour itself.
			 * If the dataset hadn't the FilteringBehaviour, we must pust filters on domain values on query to temporary table 
			 */
			Map<String, List<String>> globalFilters = getGlobalFiltersOnDomainValues();
			LogMF.debug(logger, "Global filters on domain values detected: {0}", globalFilters);
			List<WhereField> temp = transformIntoWhereClauses(globalFilters);
			whereFields.addAll(temp);
		}

		/* 
		 * We must consider sheet filters anyway because temporary table contains data for all sheets,
		 * but different sheets could have different filters defined on them
		 */
		Map<String, List<String>> sheetFilters = getSheetFiltersOnDomainValues(sheetName);
		LogMF.debug(logger, "Sheet filters on domain values detected: {0}", sheetFilters);
		List<WhereField> temp = transformIntoWhereClauses(sheetFilters);
		whereFields.addAll(temp);

		temp = getOptionalFilters(optionalFilters);

		whereFields.addAll(temp);

		//ADD THE WHERE FIELD TO SPLIT THE CROSSTAB INTO DIFFERENT SHEET
		if(splittingWhereField!=null){
			whereFields.add(splittingWhereField);
		}


		// deserialize crosstab definition
		CrosstabDefinition crosstabDefinition = (CrosstabDefinition) SerializationManager.deserialize(crosstabDefinitionJSON, "application/json", CrosstabDefinition.class);
		crosstabDefinition.setCellLimit(0);//FOR THE EXPORT WE REMOVE THE CELL LIMIT

		String worksheetQuery = this.buildSqlStatement(crosstabDefinition, descriptor, whereFields, engineInstance.getDataSource());
		// execute SQL query against temporary table
		logger.debug("Executing query on temporary table : " + worksheetQuery);
		IDataStore dataStore = this.executeWorksheetQuery(worksheetQuery, null, null);
		LogMF.debug(logger, "Query on temporary table executed successfully; datastore obtained: {0}", dataStore);
		Assert.assertNotNull(dataStore, "Datastore obatined is null!!");
		/* since the datastore, at this point, is a JDBC datastore, 
		 * it does not contain information about measures/attributes, fields' name and alias...
		 * therefore we adjust its metadata
		 */
		this.adjustMetadata((DataStore) dataStore, dataset, descriptor);
		LogMF.debug(logger, "Adjusted metadata: {0}", dataStore.getMetaData());
		logger.debug("Decoding dataset ...");
		this.applyOptions(dataStore);
		dataStore = dataset.decode(dataStore);
		LogMF.debug(logger, "Dataset decoded: {0}", dataStore);

		CrossTab crossTab = new CrossTab(dataStore, crosstabDefinition, fieldOptions, calculateFieldsJSON);

		return crossTab;
	}

	/**
	 * Build the sql statement to query the temporary table 
	 * @param crosstabDefinition definition of the crosstab
	 * @param descriptor the temporary table descriptor
	 * @param dataSource the datasource
	 * @param tableName the temporary table name
	 * @return the sql statement to query the temporary table 
	 */
	protected String buildSqlStatement(CrosstabDefinition crosstabDefinition,
			IDataSetTableDescriptor descriptor, List<WhereField> filters, IDataSource dataSource) {
		return CrosstabQueryCreator.getCrosstabQuery(crosstabDefinition, descriptor, filters, dataSource);
	}

	public List<WhereField> getSplittingFieldValues(JSONObject optionalFilters, String sheetName) throws JSONException{


		String[] fields = new String[0];
		List<WhereField> splittingWhereFields = new ArrayList<WhereField>();

		try {
			if(optionalFilters!=null){
				fields = JSONObject.getNames(optionalFilters);
			}

		} catch (Exception e) {
			logger.error("Error loading the splitting filters from the string "+optionalFilters);
		}


		for (String field : fields) {
			// check splitting filter is true (always in simple case, depends ine xport case)
			if(optionalFilters.getString(field).equals(SPLITTING_FILTER) && splittingFilter == true){
				IDataStore ds = getUserSheetFilterValues(sheetName, field);
				JSONDataWriter dataSetWriter = new JSONDataWriter();
				JSONObject gridDataFeed = (JSONObject) dataSetWriter.write(ds);
				JSONArray rows = gridDataFeed.getJSONArray(JSONDataWriter.ROOT);

				for(int j=0; j<rows.length(); j++){
					JSONArray ja = new JSONArray();
					ja.put(((JSONObject)(rows.get(j))).get("column_1"));
					JSONObject jo = new JSONObject();
					jo.put(field, ja);
					splittingWhereFields.addAll(transformIntoWhereClauses(jo));
				}

			}
		}
		return splittingWhereFields;
		
	}

	public JSONObject convertToExportJSON(WorksheetEngineInstance engineInstance, JSONObject worksheetJSON) throws JSONException{
		logger.debug("IN");

		WorksheetTemplate template = engineInstance.getTemplate();
		WorkSheetDefinition definition = template.getWorkSheetDefinition();	
		List<Sheet> sheets = definition.getSheets();
		Integer sheetsNum = sheets.size();

		JSONArray arraySheets = worksheetJSON.getJSONArray("sheets");

		JSONObject tobuild = new JSONObject();
		JSONArray exportedSheetsJSON = new JSONArray();

		// *** SHEETS
		for (int i = 0; i < arraySheets.length(); i++) {
			JSONObject sheetObj = (JSONObject)arraySheets.get(i);
			JSONObject sheetObjContent = sheetObj.getJSONObject("content");

			String sheetName = sheetObj.getString("name");

			Sheet sheet = sheets.get(i);
			List<Filter> filters = sheet.getFilters();

			JSONObject sheetToInsert = new JSONObject();
			JSONObject contentJSON = null;

			// get the content
			String sheetType = null;
			SheetContent content= sheet.getContent();
			if (content instanceof TableDefinition) {
				contentJSON = new JSONObject();
				sheetType = WorkSheetXLSExporter.TABLE;
				// PARS is present only with table. TODO why?
				contentJSON.put("PARS", sheetObjContent );
			} else if(content instanceof CrosstabDefinition) {
				sheetType = WorkSheetXLSExporter.CROSSTAB;
				contentJSON = sheetObjContent;

			} else{
				// others not handled
			}

			contentJSON.put("SHEET_TYPE", sheetType);

			sheetToInsert.put("sheetName", sheetName);
			sheetToInsert.put(WorkSheetXLSExporter.CONTENT, contentJSON);
			sheetToInsert.put(WorkSheetXLSExporter.HEADER, sheet.getHeader());
			sheetToInsert.put(WorkSheetXLSExporter.FOOTER, sheet.getFooter());

			// build filters!
			JSONArray filtersJSON = new JSONArray();

			if(filters.size()>1)
			{			
				for (Iterator iterator = filters.iterator(); iterator.hasNext();) {
					Filter filter = (Filter) iterator.next();
					JSONObject filterJSON = new JSONObject();
					// if filter is splittingValue do not put values but splittingValue!
					if(splittingFilter==true && filter.isSplittingFilter()){
						filterJSON.put(filter.getEntityId(), "splittingFilter");
					}
					else{
						filterJSON.put(filter.getEntityId(), filter.getValues());				
					}
					filtersJSON.put(filterJSON);
				}
				sheetToInsert.put("FILTERS", filtersJSON);
			}
			else if(filters.size() == 1){				// SINGLE FILTER CASE
				JSONObject filterJSON = new JSONObject();
				Filter filter = filters.get(0);
				if(splittingFilter==true && filter.isSplittingFilter()){
					filterJSON.put(filter.getEntityId(), "splittingFilter");
				}
				else{
					filterJSON.put(filter.getEntityId(), filter.getValues());				
				}
				sheetToInsert.put("FILTERS", filterJSON);
			}

			exportedSheetsJSON.put(sheetToInsert);
		}
		// *** end SHEETs

		tobuild.put("SHEETS_NUM", sheetsNum.toString());

		tobuild.put(ExportWorksheetAction.EXPORTED_SHEETS, exportedSheetsJSON);

		// Additional information
		//WorksheetFieldsOptions fieldOptions = definition.getFieldsOptions();
		JSONArray fielsdOption = worksheetJSON.getJSONArray(ExportWorksheetAction.FIELDS_OPTIONS);
		JSONObject additionalData = new JSONObject();
		additionalData.put(ExportWorksheetAction.FIELDS_OPTIONS, fielsdOption);
		tobuild.put(ExportWorksheetAction.WORKSHEETS_ADDITIONAL_DATA, additionalData);

		//System.out.println(tobuild);
		logger.debug(tobuild);
		logger.debug("OUT");
		return tobuild;

	}
	
	  public static byte[] decodeToByteArray(String data) {
	        // Initialise output ByteArray for decoded data
	        List<Byte> output = new ArrayList<Byte>();
	       
	        // Create data and output buffers
	        int[] dataBuffer = new int[4];
	        byte[] outputBuffer = new byte[3];

	        // While there are data bytes left to be processed
	        for (int i=0; i < data.length(); i = i+4) {
	                // Populate data buffer with position of Base64 characters for
	                // next 4 bytes from encoded data
	                for (int j = 0; j < 4 && i + j < data.length(); j++) {
	                        dataBuffer[j] = BASE64_CHARS.indexOf(data.charAt(i + j));
	                }
	       
	        // Decode data buffer back into bytes
	                outputBuffer[0] = (byte)((dataBuffer[0] << 2) + ((dataBuffer[1] & 0x30) >> 4));
	                outputBuffer[1] = (byte)(((dataBuffer[1] & 0x0f) << 4) + ((dataBuffer[2] & 0x3c) >> 2));                
	                outputBuffer[2] = (byte)(((dataBuffer[2] & 0x03) << 6) + dataBuffer[3]);
	               
	                // Add all non-padded bytes in output buffer to decoded data
	                for (int k = 0; k < outputBuffer.length; k++) {
	                        if (dataBuffer[k+1] == 64) break;
	                        output.add(outputBuffer[k]);
	                }
	        }
	        byte[] toReturn = new byte[output.size()];
	        for(int i=0; i<output.size(); i++){
	        	toReturn[i]=output.get(i);
	        }
	       
	        // Return decoded data
	        return toReturn;
	}	
}
