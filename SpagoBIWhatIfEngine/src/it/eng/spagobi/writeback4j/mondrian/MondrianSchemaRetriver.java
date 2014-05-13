/* SpagoBI, the Open Source Business Intelligence suite

 * Copyright (C) 2012 Engineering Ingegneria Informatica S.p.A. - SpagoBI Competency Center
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0, without the "Incompatible With Secondary Licenses" notice. 
 * If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package it.eng.spagobi.writeback4j.mondrian;

import it.eng.spagobi.utilities.engines.SpagoBIEngineException;
import it.eng.spagobi.utilities.engines.SpagoBIEngineRuntimeException;
import it.eng.spagobi.writeback4j.IMemberCoordinates;
import it.eng.spagobi.writeback4j.ISchemaRetriver;
import it.eng.spagobi.writeback4j.sql.TableEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mondrian.olap.MondrianDef;
import mondrian.olap.MondrianDef.Dimension;

import org.apache.log4j.Logger;
import org.eigenbase.xom.Parser;
import org.eigenbase.xom.XOMException;
import org.eigenbase.xom.XOMUtil;
import org.olap4j.OlapException;
import org.olap4j.metadata.Dimension.Type;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;


/**
 * @author Alberto Ghedin (alberto.ghedin@eng.it)
 *
 */
public class MondrianSchemaRetriver implements ISchemaRetriver{

	public static transient Logger logger = Logger.getLogger(MondrianSchemaRetriver.class);
	
	MondrianDef.Schema schema;
	MondrianDef.Cube editCube;
	
	
	public MondrianSchemaRetriver(MondrianDriver driver, String editCubeName) throws SpagoBIEngineException{
		String catalogUri = driver.getOlapSchema();
		File tmpFile = new File(catalogUri);
		FileInputStream fis;
		Parser xmlParser;
		
		logger.debug("IN");
		logger.debug("Loading the schema from the file "+catalogUri);
		
		try {
			fis = new FileInputStream(tmpFile);
			xmlParser = XOMUtil.createDefaultParser();
			schema = new MondrianDef.Schema(xmlParser.parse(fis));
		} catch (FileNotFoundException e) {
			logger.error("File not found Error loading the file with the schema with url "+catalogUri, e);
			throw new SpagoBIEngineException("File not found Error loading the file with the schema with url "+catalogUri, e);
		} catch (XOMException e) {
			logger.error("Error loading the file with the schema with url "+catalogUri, e);
			throw new SpagoBIEngineException("Error loading the file with the schema with url "+catalogUri, e);
		}
		logger.debug("File loaded ");
		
		logger.debug("Getting the cube for edit. The name of the cube is "+editCubeName);
		MondrianDef.Cube[] cubes = schema.cubes;

		for (int i = 0; i < cubes.length; i++) {
			MondrianDef.Cube aCube = cubes[i];
			if(aCube.name.equals(editCubeName)){
				editCube = aCube;
			}
		}
		logger.debug("Cube for writing correctly loaded");
	}
	
	
	public IMemberCoordinates getMemberCordinates(Member member){
		logger.debug("IN");

		//get the dimension for the member
		MondrianDef.CubeDimension mondrianDimension = getMondrianDimension(member.getLevel());
		
		//get the hierarchy of the level
		MondrianDef.Hierarchy  mondrianHierarchy = getMondrianHierarchy(member.getLevel(), mondrianDimension.getDimension(schema));
		
		//For each level starting from the root to the Get the map between the hierarchy of the member( root member, child, granchild, .... member) and the level
		Map<TableEntry, Member> mapTableEntryValue = getMemberColumnMap(member, mondrianHierarchy);
		
		logger.debug("OUT");
		
		return new MondrianMemberCoordinates(mondrianDimension, mondrianHierarchy, mapTableEntryValue);
		

	}
	
	
	/**
	 * Get the Dimension that includes the level  
	 * @param level
	 * @return the dimension that includes the level  
	 */
	public MondrianDef.CubeDimension getMondrianDimension(Level level){

		logger.debug("IN");
		
		String dimension = level.getDimension().getName();
		MondrianDef.CubeDimension[] dimensons = editCube.dimensions;
		for (int i = 0; i < dimensons.length; i++) {
			MondrianDef.CubeDimension aDimension = dimensons[i];
			if(aDimension.name.equals(dimension)){
				logger.debug("OUT");
				return aDimension;
			}
		}
		
		logger.error("Impossible to find the dimension for the level "+level.getUniqueName());
		throw new SpagoBIEngineRuntimeException("Impossible to find the dimension for the level "+level.getUniqueName());
	}

	/**
	 * Gets the hierarchy of the level inside the passed dimension
	 * @param level
	 * @param thisDimension
	 * @return the hierarchy of the level inside the passed dimension
	 */
	public MondrianDef.Hierarchy  getMondrianHierarchy(Level level, Dimension thisDimension){
		logger.debug("IN");
		String hierarchy = level.getHierarchy().getName();
		MondrianDef.Hierarchy[] thisHierarchies = thisDimension.hierarchies;
		for (int j = 0; j < thisHierarchies.length; j++) {
			MondrianDef.Hierarchy aHierarchy = thisHierarchies[j];
			if(aHierarchy.name==null || aHierarchy.name.equals(hierarchy)){
				logger.debug("OUT");
				return aHierarchy;
			}

		}
		logger.error("Impossible to find the hierarchy for the level "+level.getUniqueName());
		throw new SpagoBIEngineRuntimeException("Impossible to find the hierarchy for the level "+level.getUniqueName());
	}
	
	

	/**
	 * For each level starting from the root to the Get the map between the hierarchy of the member( root member, child, granchild, .... member) and the level.
	 * We need this information because we want to know table and column linked to the members
	 * @param member
	 * @param mondrianHierarchy
	 * @return the map Level-->Member of the level
	 */
	public Map<TableEntry, Member> getMemberColumnMap(Member member, MondrianDef.Hierarchy mondrianHierarchy){
		
		logger.debug("IN");
		
		Map<TableEntry, Member> mapTableEntryValue = new HashMap<TableEntry, Member>();
		int memberDepth =  member.getDepth();
		
		//get all the levels starting from the root to the one that contains the passed member
		List<MondrianDef.Level> memberValues =  getLevels(memberDepth, mondrianHierarchy);
		
		//Create a Map that links the member with the level that contains it
		Member aMember = member;
		for(int i=memberValues.size()-1; i>=0;i--){
			MondrianDef.Level aLevel = memberValues.get(i);
			mapTableEntryValue.put(new TableEntry(aLevel.column, aLevel.table) , aMember);
			aMember = aMember.getParentMember();
		}
		
		logger.debug("OUT");
		return mapTableEntryValue;
	}

	/**
	 * Gets the first n levels of a hierarchy
	 * @param levelsDepth the number of levels to get
	 * @param aHierarchy 
	 * @return the first "levelsDepth" levels of aHierarchy
	 */
	public List<MondrianDef.Level> getLevels(int levelsDepth, MondrianDef.Hierarchy aHierarchy){
		logger.debug("IN");
		List<MondrianDef.Level> levelColumns = new ArrayList<MondrianDef.Level>();
		
		MondrianDef.Level[] schemaLevels =  aHierarchy.levels;
		for (int i = 0; i < levelsDepth; i++) {
			levelColumns.add(schemaLevels[i]);
		}
		
		logger.debug("OUT");
		return levelColumns;
	}
	
	/**
	 * Gets the MondrianDef.Measure that represent the Member
	 * @param member
	 * @return if member is a Measure returns the linked MondrianDef.Measure, null otherwise
	 * @throws SpagoBIEngineException 
	 */
	public String getMeasureColumn(Member member) throws SpagoBIEngineException {
		String measure = member.getName();
		try {
			if((member.getDimension().getDimensionType().equals(Type.MEASURE))){
				for(int i=0; i<editCube.measures.length; i++){
					if(editCube.measures[i].name.equals(measure)){
						return editCube.measures[i].column;
					}
				}
			}
		} catch (OlapException e) {
			logger.error("Error loading the measure linked to the member "+member.getUniqueName(), e);
			throw new SpagoBIEngineException("Error loading the measure linked to the member "+member.getUniqueName(), e);
		}

		return null;
	}


	public String getEditCubeTableName() {
		return editCube.fact.getAlias();
	}
	
	


}