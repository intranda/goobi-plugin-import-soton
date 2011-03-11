package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
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
import org.jdom.transform.XSLTransformer;
import org.marc4j.MarcException;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcXmlWriter;
import org.marc4j.converter.impl.AnselToUnicode;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.plugins.utils.ModsUtils;
import de.sub.goobi.Import.ImportOpac;
import de.sub.goobi.config.ConfigMain;

@PluginImplementation
public class SotonCatalogueImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(SotonCatalogueImport.class);

	private static final String ID = "soton_catalogue";
	private static final String NAME = "SOTON Catalogue Import";
	private static final String VERSION = "1.0.20110311";
	private static final String XSLT_PATH = ConfigMain.getParameter("xsltFolder") + "MARC21slim2MODS3.xsl";

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private Map<String, String> map = new HashMap<String, String>();
	private String currentIdentifier;
	private String currentTitle;
	private String currentAuthor;

	public SotonCatalogueImport() {
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
			// String marc = fetchRecord("http://pdf.library.soton.ac.uk/example_output.html");
			String marc = fetchRecord("http://lms.soton.ac.uk/cgi-bin/goobi_marc.cgi?itemid=" + data);
			if (marc.toLowerCase().contains("barcode not found")) {
				return null;
			}
			marc = extractMarcFromHtml(marc);
			data = convertToMarcXml(marc);
			logger.debug(data);
			doc = new SAXBuilder().build(new StringReader(data));
			if (doc != null && doc.hasRootElement()) {
				XSLTransformer transformer = new XSLTransformer(XSLT_PATH);
				Document docMods = transformer.transform(doc);
				logger.debug(new XMLOutputter().outputString(docMods));

				ff = new MetsMods(prefs);
				DigitalDocument dd = new DigitalDocument();
				ff.setDigitalDocument(dd);

				Element eleMods = docMods.getRootElement();
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
				currentAuthor = ModsUtils.getAuthor(prefs, dsRoot);
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

		String[] idsSplit = ids.trim().split("[ ]");
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

	private String convertToMarcXml(String marc) {
		String ret = null;
		InputStream input = null;
		try {
			input = new ByteArrayInputStream(marc.getBytes("iso8859-1"));
			MarcReader reader = new MarcStreamReader(input);
			while (reader.hasNext()) {
				try {
					org.marc4j.marc.Record marcRecord = (org.marc4j.marc.Record) reader.next();
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					MarcXmlWriter writer = new MarcXmlWriter(out, "utf-8", true);
					writer.setConverter(new AnselToUnicode());
					writer.write(marcRecord);
					writer.close();
					ret = out.toString();
					out.close();
				} catch (MarcException e) {
					logger.error(e.getMessage());
				}
			}
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}

		return ret;
	}

	private String extractMarcFromHtml(String html) throws JDOMException, IOException {
		BufferedReader inputStream = new BufferedReader(new StringReader(html));
		String l;
		try {
			int line = 0;
			while ((l = inputStream.readLine()) != null) {
				if (line == 2) {
					l = l.substring(1, l.length());
					l += "\n";

					// BufferedReader inputStream2 = new BufferedReader(new StringReader(l));
					// StringBuffer buffer = new StringBuffer();
					// int ch;
					// while ((ch = inputStream2.read()) != -1) {
					// if (ch == 13) {
					// buffer.append("[\\r]");
					// } else if (ch == 10) {
					// buffer.append("[\\n]\"");
					// buffer.insert(0, "\"");
					// logger.debug(buffer.toString());
					// buffer.setLength(0);
					// } else if ((ch < 32) || (ch > 127)) {
					// buffer.append("[0x");
					// buffer.append(Integer.toHexString(ch));
					// buffer.append("]");
					// } else {
					// buffer.append((char) ch);
					// }
					// }

					return l;
				}
				line++;
			}
			inputStream.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}

	private String fetchRecord(String url) {
		String ret = null;

		if (StringUtils.isNotEmpty(url)) {
			HttpClient client = new HttpClient();
			GetMethod method = null;
			try {
				method = new GetMethod(url);
				client.executeMethod(method);
				ret = method.getResponseBodyAsString();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			} finally {
				if (method != null) {
					method.releaseConnection();
				}
			}
		}

		return ret;
	}

	public static void main(String[] args) {
		SotonCatalogueImport converter = new SotonCatalogueImport();
		converter.prefs = new Prefs();
		try {
			converter.prefs.loadPrefs("resources/gdz.xml");
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		}

		List<Record> records = new ArrayList<Record>();
		List<String> ids = converter.splitIds("00000000 00044167 00040558 00043679 00083328 00110330");
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
			if (ff != null) {
				try {
					ff.write("c:/" + record.getId() + ".xml");
				} catch (WriteException e) {
					e.printStackTrace();
				} catch (PreferencesException e) {
					e.printStackTrace();
				}
			}
			counter++;
		}
	}
}
