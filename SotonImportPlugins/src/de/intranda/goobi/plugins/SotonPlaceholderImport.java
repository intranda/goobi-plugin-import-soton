/**
 * This file is part of CamImportPlugins/SotonImportPlugins.
 * 
 * Copyright (C) 2011 intranda GmbH
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Andrey Kozhushkov
 */
package de.intranda.goobi.plugins;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import de.sub.goobi.forms.MassImportForm;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
public class SotonPlaceholderImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(SotonPlaceholderImport.class);

	private static final String NAME = "SOTON Placeholder Import";
	private static final String VERSION = "1.0.20110616";

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private String currentIdentifier;
	private List<String> currentCollectionList;
	private MassImportForm form;
	
	public SotonPlaceholderImport() {
	}

	@Override
	public Fileformat convertData() {
		Fileformat ff = null;
		try {
			ff = new MetsMods(this.prefs);
			DigitalDocument dd = new DigitalDocument();
			ff.setDigitalDocument(dd);

			// Determine the root docstruct type
			String dsType = "Monograph";
			logger.debug("Docstruct type: " + dsType);

			DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
			dd.setLogicalDocStruct(dsRoot);

			DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
			dd.setPhysicalDocStruct(dsBoundBook);

			MetadataType mdTypeId = this.prefs.getMetadataTypeByName("CatalogIDDigital");
			Metadata mdId = new Metadata(mdTypeId);
			dsRoot.addMetadata(mdId);
			if (StringUtils.isNotEmpty(this.data)) {
				mdId.setValue(this.data);
				this.currentIdentifier = this.data;
			} else {
				// Add a timestamp as identifer if the record still has none
				mdId.setValue(String.valueOf(System.currentTimeMillis()));
				this.currentIdentifier = mdId.getValue();
			}
			
			// Add 'pathimagefiles'
			try {
				Metadata mdForPath = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
				mdForPath.setValue("./" + mdId.getValue());
				dsBoundBook.addMetadata(mdForPath);
			} catch (MetadataTypeNotAllowedException e1) {
				logger.error("MetadataTypeNotAllowedException while reading images", e1);
			} catch (DocStructHasNoTypeException e1) {
				logger.error("DocStructHasNoTypeException while reading images", e1);
			}
			
			// Add collection names attached to the current record
			if (this.currentCollectionList != null) {
				MetadataType mdTypeCollection = this.prefs.getMetadataTypeByName("singleDigCollection");
				for (String collection : this.currentCollectionList) {
					Metadata mdCollection = new Metadata(mdTypeCollection);
					mdCollection.setValue(collection);
					dsRoot.addMetadata(mdCollection);
				}
			}
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		} catch (TypeNotAllowedForParentException e) {
			logger.error(e.getMessage(), e);
		} catch (MetadataTypeNotAllowedException e) {
			logger.error(e.getMessage(), e);
		}

		return ff;
	}

	@Override
	public List<ImportObject> generateFiles(List<Record> records) {
		List<ImportObject> answer = new ArrayList<ImportObject>();

		for (Record r : records) {
		    if (form != null) {
                form.addProcessToProgressBar();
            }
			this.data = r.getData();
			this.currentCollectionList = r.getCollections();
			Fileformat ff = convertData();
			ImportObject io = new ImportObject();
			io.setProcessTitle(getProcessTitle().substring(0, getProcessTitle().length() -4));
			if (ff != null) {
				r.setId(this.currentIdentifier);
				try {
					MetsMods mm = new MetsMods(this.prefs);
					mm.setDigitalDocument(ff.getDigitalDocument());
					String fileName = getImportFolder() + getProcessTitle();
					logger.debug("Writing '" + fileName + "' into hotfolder...");
					mm.write(fileName);
					io.setMetsFilename(fileName);
					io.setImportReturnValue(ImportReturnValue.ExportFinished);
				} catch (PreferencesException e) {
					logger.error(e.getMessage(), e);
					io.setImportReturnValue(ImportReturnValue.InvalidData);
				} catch (WriteException e) {
					logger.error(e.getMessage(), e);
					io.setImportReturnValue(ImportReturnValue.InvalidData);
				}
			} else {
				io.setImportReturnValue(ImportReturnValue.InvalidData);
			}
			answer.add(io);
		}

		return answer;
	}

	@Override
	public List<Record> generateRecordsFromFile() {
		return new ArrayList<Record>();
	}

	@Override
	public List<Record> splitRecords(String records) {
		return new ArrayList<Record>();
	}

	@Override
	public List<String> splitIds(String ids) {
		List<String> ret = new ArrayList<String>();

		String[] idsSplit = ids.trim().split("[\n]");
		for (String id : idsSplit) {
			if (StringUtils.isNotBlank(id)) {
				ret.add(id.trim());
			}
		}

		return ret;
	}

	@Override
	public String getProcessTitle() {
		return this.currentIdentifier + ".xml";
	}

	@Override
	public void setData(Record r) {
		this.data = r.getData();
	}

	@Override
	public String getImportFolder() {
		return this.importFolder;
	}

	@Override
	public void setImportFolder(String folder) {
		this.importFolder = folder;
	}

	@Override
	public void setFile(File importFile) {
		this.importFile = importFile;
	}

	@Override
	public void setPrefs(Prefs prefs) {
		this.prefs = prefs;

	}

	@Override
	public List<ImportType> getImportTypes() {
		List<ImportType> answer = new ArrayList<ImportType>();
		answer.add(ImportType.ID);

		return answer;
	}

	@Override
	public PluginType getType() {
		return PluginType.Import;
	}

	@Override
	public String getTitle() {
		return getDescription();
	}

	public String getId() {
		return getDescription();
	}
	
	@Override
	public String getDescription() {
		return NAME + " v" + VERSION;
	}

	public static void main(String[] args) {
		SotonPlaceholderImport converter = new SotonPlaceholderImport();
		converter.prefs = new Prefs();
		try {
			converter.prefs.loadPrefs("resources/gdz.xml");
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		}

		List<Record> records = new ArrayList<Record>();
		List<String> ids = converter.splitIds("a1 a2  a3");
		for (String id : ids) {
			Record r = new Record();
			r.setData(id);
			r.setId(id);
			records.add(r);
		}

		int counter = 1;
		for (Record record : records) {
			logger.debug(counter + ":\n" + record.getData());
			converter.data = record.getData();
			Fileformat ff = converter.convertData();
			try {
				ff.write("c:/" + converter.currentIdentifier + ".xml");
			} catch (WriteException e) {
				e.printStackTrace();
			} catch (PreferencesException e) {
				e.printStackTrace();
			}
			counter++;
		}
	}
	
	@Override
	public List<Record> generateRecordsFromFilenames(List<String> filenames) {
		return new ArrayList<Record>();
	}

	@Override
	public List<ImportProperty> getProperties() {
		return new ArrayList<ImportProperty>();
	}

	@Override
	public List<String> getAllFilenames() {
		return new ArrayList<String>();
	}

	@Override
	public void deleteFiles(List<String> selectedFilenames) {		
	}

	@Override
	public List<DocstructElement> getCurrentDocStructs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteDocstruct() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addDocstruct() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getPossibleDocstructs() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DocstructElement getDocstruct() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDocstruct(DocstructElement dse) {
		// TODO Auto-generated method stub
		
	}
	

    @Override
    public void setForm(MassImportForm form) {
        this.form = form;
    }
}
