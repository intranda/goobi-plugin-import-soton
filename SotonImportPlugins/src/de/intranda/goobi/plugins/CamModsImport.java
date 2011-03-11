package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.production.Import.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.plugins.utils.ModsUtils;
import de.sub.goobi.Import.ImportOpac;

@PluginImplementation
public class CamModsImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(CamModsImport.class);

	private static final String ID = "cam_mods";
	private static final String NAME = "Cambridge MODS Import";
	private static final String VERSION = "1.0.20110303";

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private Map<String, String> map = new HashMap<String, String>();
	private String currentIdentifier;
	private String currentTitle;
	private String currentAuthor;

	public CamModsImport() {
		map.put("?monographic", "Monograph");
		map.put("?continuing", "Periodical");
		map.put("?multipart monograph", "MultiVolumeWork");
		map.put("?single unit", "Monograph");
		map.put("?integrating resource", "MultiVolumeWork");
		map.put("?serial", "Periodical");
		map.put("?cartographic", "Map");
		map.put("?notated music", null);
		map.put("?sound recording-nonmusical", null);
		map.put("?sound recording-musical", null);
		map.put("?moving image", null);
		map.put("?three dimensional object", null);
		map.put("?software, multimedia", null);
		map.put("?mixed material", null);
	}

	@Override
	public Fileformat convertData() {
		Fileformat ff = null;
		Document doc;
		try {
			doc = new SAXBuilder().build(new StringReader(data));
			if (doc != null && doc.hasRootElement()) {
				ff = new MetsMods(prefs);
				DigitalDocument dd = new DigitalDocument();
				ff.setDigitalDocument(dd);

				Element eleMods = doc.getRootElement();
				if (eleMods.getName().equals("modsCollection")) {
					eleMods = eleMods.getChild("mods", null);
				}

				// Determine the root docstruct type
				String dsType = "Monograph";
				if (eleMods.getChild("originInfo", null) != null) {
					Element eleIssuance = eleMods.getChild("originInfo", null).getChild("issuance", null);
					if (eleIssuance != null && map.get("?" + eleIssuance.getTextTrim()) != null) {
						dsType = map.get("?" + eleIssuance.getTextTrim());
					}
				}
				Element eleTypeOfResource = eleMods.getChild("typeOfResource", null);
				if (eleTypeOfResource != null && map.get("?" + eleTypeOfResource.getTextTrim()) != null) {
					dsType = map.get("?" + eleTypeOfResource.getTextTrim());
				}
				logger.debug("Docstruct type: " + dsType);

				DocStruct dsRoot = dd.createDocStruct(prefs.getDocStrctTypeByName(dsType));
				dd.setLogicalDocStruct(dsRoot);

				DocStruct dsBoundBook = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
				dd.setPhysicalDocStruct(dsBoundBook);

				// Collect MODS metadata
				ModsUtils.parseModsSection(prefs, dsRoot, dsBoundBook, eleMods);
				currentIdentifier = ModsUtils.getIdentifier(prefs, dsRoot);
				currentTitle = ModsUtils.getTitle(prefs, dsRoot);
				currentAuthor =  ModsUtils.getAuthor(prefs, dsRoot);

				// Add dummy volume to anchors
				if (dsRoot.getType().getName().equals("Periodical") || dsRoot.getType().getName().equals("MultiVolumeWork")) {
					DocStruct dsVolume = null;
					if (dsRoot.getType().getName().equals("Periodical")) {
						dsVolume = dd.createDocStruct(prefs.getDocStrctTypeByName("PeriodicalVolume"));
					} else if (dsRoot.getType().getName().equals("MultiVolumeWork")) {
						dsVolume = dd.createDocStruct(prefs.getDocStrctTypeByName("Volume"));
					}
					dsRoot.addChild(dsVolume);
					Metadata mdId = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
					mdId.setValue(currentIdentifier + "_0001");
					dsVolume.addMetadata(mdId);
				}
			}
		} catch (JDOMException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		} catch (TypeNotAllowedForParentException e) {
			logger.error(e.getMessage(), e);
		} catch (MetadataTypeNotAllowedException e) {
			logger.error(e.getMessage(), e);
		} catch (TypeNotAllowedAsChildException e) {
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
				r.setId(currentIdentifier);
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
		List<Record> ret = new ArrayList<Record>();

		try {
			Document doc = new SAXBuilder().build(importFile);
			if (doc != null && doc.getRootElement() != null) {
				for (Object obj : doc.getRootElement().getChildren("mods", null)) {
					Element eleMods = (Element) obj;
					Record record = new Record();
					record.setData(new XMLOutputter().outputString(eleMods));
					ret.add(record);
				}
				logger.debug(ret.size() + " records extracted.");
			} else {
				logger.error("Could not parse '" + importFile + "'.");
			}
		} catch (JDOMException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return ret;
	}

	@Override
	public List<Record> splitRecords(String records) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> splitIds(String ids) {
		// TODO Auto-generated method stub
		return null;
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
		answer.add(ImportType.Record);
		answer.add(ImportType.FILE);

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
		CamModsImport converter = new CamModsImport();
		converter.prefs = new Prefs();
		try {
			converter.prefs.loadPrefs("resources/gdz.xml");
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		}

		converter.setFile(new File("samples/mods-cam/books_mods.xml"));
		List<Record> records = converter.generateRecordsFromFile();

		// converter.importFile = new File("samples/mods-cam/bib_marc_mods.txt");
		// StringBuilder sb = new StringBuilder();
		// BufferedReader inputStream = null;
		// try {
		// inputStream = new BufferedReader(new FileReader(converter.importFile));
		// String l;
		// while ((l = inputStream.readLine()) != null) {
		// sb.append(l + "\n");
		// }
		// } catch (IOException e) {
		// logger.error(e.getMessage(), e);
		// } finally {
		// if (inputStream != null) {
		// try {
		// inputStream.close();
		// } catch (IOException e) {
		// logger.error(e.getMessage(), e);
		// }
		// }
		// }
		// List<Record> records = converter.splitRecords(sb.toString());

		int counter = 1;
		for (Record record : records) {
			logger.debug(counter + ":\n" + record.getData());
			converter.data = record.getData();
			Fileformat ff = converter.convertData();
			try {
				ff.write("c:/" + converter.importFile.getName().replace(".xml", "") + "_" + counter + ".xml");
			} catch (WriteException e) {
				e.printStackTrace();
			} catch (PreferencesException e) {
				e.printStackTrace();
			}
			counter++;
		}
	}
}
