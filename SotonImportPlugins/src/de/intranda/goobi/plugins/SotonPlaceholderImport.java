package de.intranda.goobi.plugins;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.Import.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.plugins.utils.ModsUtils;
import de.sub.goobi.Import.ImportOpac;

@PluginImplementation
public class SotonPlaceholderImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(SotonPlaceholderImport.class);

	private static final String ID = "soton_placeholder";
	private static final String NAME = "SOTON Placeholder Import";
	private static final String VERSION = "1.0.20110307";

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private String currentIdentifier;
	private String currentTitle;
	private String currentAuthor;

	public SotonPlaceholderImport() {
	}

	@Override
	public Fileformat convertData() {
		Fileformat ff = null;
		try {
			ff = new MetsMods(prefs);
			DigitalDocument dd = new DigitalDocument();
			ff.setDigitalDocument(dd);

			// Determine the root docstruct type
			String dsType = "Monograph";
			logger.debug("Docstruct type: " + dsType);

			DocStruct dsRoot = dd.createDocStruct(prefs.getDocStrctTypeByName(dsType));
			dd.setLogicalDocStruct(dsRoot);

			DocStruct dsBoundBook = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
			dd.setPhysicalDocStruct(dsBoundBook);

			currentIdentifier = ModsUtils.getIdentifier(prefs, dsRoot);
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
	public HashMap<String, ImportReturnValue> generateFiles(List<Record> records) {
		HashMap<String, ImportReturnValue> ret = new HashMap<String, ImportReturnValue>();

		for (Record r : records) {
			data = r.getData();
			Fileformat ff = convertData();
			if (ff != null) {
				try {
					MetsMods mm = new MetsMods(prefs);
					mm.setDigitalDocument(ff.getDigitalDocument());
					String fileName = getImportFolder() + getProcessTitle();
					logger.debug("Writing '" + fileName + "' into hotfolder...");
					mm.write(fileName);
					ret.put(getProcessTitle(), ImportReturnValue.ExportFinished);
				} catch (PreferencesException e) {
					logger.error(e.getMessage(), e);
					ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
				} catch (WriteException e) {
					logger.error(e.getMessage(), e);
					ret.put(getProcessTitle(), ImportReturnValue.WriteError);
				}
			} else {
				ret.put(getProcessTitle(), ImportReturnValue.InvalidData);
			}
		}

		return ret;
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
		if (StringUtils.isNotBlank(currentTitle)) {
			return new ImportOpac().createAtstsl(currentTitle, currentAuthor).toLowerCase() + "_" + currentIdentifier + ".xml";
		}
		return currentIdentifier + ".xml";
	}

	@Override
	public void setData(Record r) {
		this.data = r.getData();
	}

	@Override
	public String getImportFolder() {
		return importFolder;
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
		return NAME + " v" + VERSION;
	}

	@Override
	public String getId() {
		return ID;
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
}
