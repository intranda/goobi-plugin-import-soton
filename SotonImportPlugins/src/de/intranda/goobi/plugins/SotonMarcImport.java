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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.XSLTransformer;
import org.marc4j.MarcException;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcXmlWriter;
import org.marc4j.converter.impl.AnselToUnicode;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;
import de.intranda.goobi.plugins.utils.ModsUtils;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.UghHelper;

@PluginImplementation
public class SotonMarcImport implements IImportPlugin, IPlugin {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(SotonMarcImport.class);

	private static final String NAME = "SOTON MARC21 Import";
	private static final String VERSION = "1.0.20111216";
	// private static final String XSLT_PATH = "jar:file:/" + ConfigMain.getParameter("pluginFolder")
	// + "import/SotonImportPlugins.jar!/resources/MARC21slim2MODS3.xsl";
	private static final String XSLT_PATH = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
	private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map.xml";

	private Prefs prefs;
	private String data = "";
	private File importFile = null;
	private String importFolder = "C:/Goobi/";
	private Map<String, String> map = new HashMap<String, String>();
	private String currentIdentifier;
	private String currentTitle;
	private String currentAuthor;
	private List<String> currentCollectionList;
    private MassImportForm form;

	public SotonMarcImport() {
		this.map.put("?monographic", "Monograph");
		this.map.put("?continuing", "Periodical");
		this.map.put("?multipart monograph", "MultiVolumeWork");
		this.map.put("?single unit", "Monograph");
		this.map.put("?integrating resource", "MultiVolumeWork");
		this.map.put("?serial", "Periodical");
		this.map.put("?cartographic", "Map");
		this.map.put("?notated music", null);
		this.map.put("?sound recording-nonmusical", null);
		this.map.put("?sound recording-musical", null);
		this.map.put("?moving image", null);
		this.map.put("?three dimensional object", null);
		this.map.put("?software, multimedia", null);
		this.map.put("?mixed material", null);
	}

	@Override
	public Fileformat convertData() {
		Fileformat ff = null;
		Document doc;
		try {
			doc = new SAXBuilder().build(new StringReader(this.data));
			if (doc != null && doc.hasRootElement()) {
				XSLTransformer transformer = new XSLTransformer(XSLT_PATH);
				// InputStream in = getClass().getResourceAsStream("/MARC21slim2MODS3.xsl");
				// XSLTransformer transformer = new XSLTransformer(in);
				// in.close();
				Document docMods = transformer.transform(doc);
				logger.debug(new XMLOutputter().outputString(docMods));

				ff = new MetsMods(this.prefs);
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
					if (eleIssuance != null && this.map.get("?" + eleIssuance.getTextTrim()) != null) {
						dsType = this.map.get("?" + eleIssuance.getTextTrim());
					}
				}
				Element eleTypeOfResource = eleMods.getChild("typeOfResource", null);
				if (eleTypeOfResource != null && this.map.get("?" + eleTypeOfResource.getTextTrim()) != null) {
					dsType = this.map.get("?" + eleTypeOfResource.getTextTrim());
				}
				logger.debug("Docstruct type: " + dsType);

				DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName(dsType));
				dd.setLogicalDocStruct(dsRoot);

				DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
				dd.setPhysicalDocStruct(dsBoundBook);

				// Collect MODS metadata
				ModsUtils.parseModsSection(MODS_MAPPING_FILE, this.prefs, dsRoot, dsBoundBook, eleMods);
				this.currentIdentifier = ModsUtils.getIdentifier(this.prefs, dsRoot);
				this.currentTitle = ModsUtils.getTitle(this.prefs, dsRoot);
				this.currentAuthor = ModsUtils.getAuthor(this.prefs, dsRoot);
				
				// Add dummy volume to anchors
				if (dsRoot.getType().getName().equals("Periodical") || dsRoot.getType().getName().equals("MultiVolumeWork")) {
					DocStruct dsVolume = null;
					if (dsRoot.getType().getName().equals("Periodical")) {
						dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("PeriodicalVolume"));
					} else if (dsRoot.getType().getName().equals("MultiVolumeWork")) {
						dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Volume"));
					}
					dsRoot.addChild(dsVolume);
					Metadata mdId = new Metadata(this.prefs.getMetadataTypeByName("CatalogIDDigital"));
					mdId.setValue(this.currentIdentifier + "_0001");
					dsVolume.addMetadata(mdId);
				}

				// Add 'pathimagefiles'
				try {
					Metadata mdForPath = new Metadata(this.prefs.getMetadataTypeByName("pathimagefiles"));
					mdForPath.setValue("./" + this.currentIdentifier);
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
					io.setImportReturnValue(ImportReturnValue.WriteError);
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
		List<Record> ret = new ArrayList<Record>();
		InputStream input = null;
		try {
			logger.debug("loaded file: " + this.importFile.getAbsolutePath());
			input = new FileInputStream(this.importFile);
			MarcReader reader = new MarcStreamReader(input);
			while (reader.hasNext()) {
				try {
					org.marc4j.marc.Record marcRecord = reader.next();
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					MarcXmlWriter writer = new MarcXmlWriter(out, "utf-8", true);
					writer.setConverter(new AnselToUnicode());
					writer.write(marcRecord);
					writer.close();
					Record record = new Record();
					ret.add(record);
					record.setData(out.toString());
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
			logger.info("Extracted " + ret.size() + " records from '" + this.importFile.getName() + "'.");
		}

		return ret;
	}

	@Override
	public List<Record> splitRecords(String records) {
		List<Record> ret = new ArrayList<Record>();

		// Split strings
		List<String> recordStrings = new ArrayList<String>();
		BufferedReader inputStream = new BufferedReader(new StringReader(records));

		StringBuilder sb = new StringBuilder();
		String l;
		try {
			while ((l = inputStream.readLine()) != null) {
				if (l.length() > 0) {
					if (l.startsWith("=LDR")) {
						if (sb.length() > 0) {
							recordStrings.add(sb.toString());
						}
						sb = new StringBuilder();
					}
					sb.append(l + "\n");
				}
			}
			if (sb.length() > 0) {
				recordStrings.add(sb.toString());
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		// Convert strings to MARCXML records and add them to Record objects
		for (String s : recordStrings) {
			String data;
			try {
				data = convertTextToMarcXml(s);
				if (data != null) {
					Record rec = new Record();
					rec.setData(data);
					ret.add(rec);
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}

		return ret;
	}

	@Override
	public List<String> splitIds(String ids) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProcessTitle() {
		if (StringUtils.isNotBlank(this.currentTitle)) {
			return createAtstsl(this.currentTitle, this.currentAuthor).toLowerCase() + "_" + this.currentIdentifier + ".xml";
		}
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
		return getDescription();
	}

	public String getId() {
		return getDescription();
	}

	@Override
	public String getDescription() {
		return NAME + " v" + VERSION;
	}

	/**
	 * 
	 * @param text
	 * @return
	 * @throws IOException
	 */
	private String convertTextToMarcXml(String text) throws IOException {
		if (StringUtils.isNotEmpty(text)) {
			Document doc = new Document();
			text = text.replace((char) 0x1E, ' ');
			BufferedReader reader = new BufferedReader(new StringReader(text));
			Element eleRoot = new Element("collection");
			doc.setRootElement(eleRoot);
			Element eleRecord = new Element("record");
			eleRoot.addContent(eleRecord);
			String str;
			while ((str = reader.readLine()) != null) {
				if (str.toUpperCase().startsWith("=LDR")) {
					// Leader
					Element eleLeader = new Element("leader");
					eleLeader.setText(str.substring(7));
					eleRecord.addContent(eleLeader);
				} else if (str.length() > 2) {
					String tag = str.substring(1, 4);
					if (tag.startsWith("00") && str.length() > 6) {
						// Control field
						str = str.substring(6);
						Element eleControlField = new Element("controlfield");
						eleControlField.setAttribute("tag", tag);
						eleControlField.addContent(str);
						eleRecord.addContent(eleControlField);
					} else if (str.length() > 6) {
						// Data field
						String ind1 = str.substring(6, 7);
						String ind2 = str.substring(7, 8);
						str = str.substring(8);
						Element eleDataField = new Element("datafield");
						eleDataField.setAttribute("tag", tag);
						eleDataField.setAttribute("ind1", !ind1.equals("\\") ? ind1 : "");
						eleDataField.setAttribute("ind2", !ind2.equals("\\") ? ind2 : "");
						Pattern p = Pattern.compile("[$]+[^$]+");
						Matcher m = p.matcher(str);
						while (m.find()) {
							String sub = str.substring(m.start(), m.end());
							Element eleSubField = new Element("subfield");
							eleSubField.setAttribute("code", sub.substring(1, 2));
							eleSubField.addContent(sub.substring(2));
							eleDataField.addContent(eleSubField);
						}
						eleRecord.addContent(eleDataField);
					}
				}
			}
			return new XMLOutputter().outputString(doc);
		}

		return null;
	}

	public static void main(String[] args) {
		SotonMarcImport converter = new SotonMarcImport();
		converter.prefs = new Prefs();
		try {
			converter.prefs.loadPrefs("resources/gdz.xml");
		} catch (PreferencesException e) {
			logger.error(e.getMessage(), e);
		}

		converter.setFile(new File("/home/robert/Arbeitsfläche/soton/00291405.mrc"));
		List<Record> records = converter.generateRecordsFromFile();

		// converter.importFile = new File("samples/marc21/multiple_records.mrk");
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
				ff.write("c:/" + converter.importFile.getName().replace(".mrc", "") + "_" + counter + ".xml");
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
	
	 private String createAtstsl(String title, String author) {
	        StringBuilder result = new StringBuilder(8);
	        if (author != null && author.trim().length() > 0) {
	            result.append(author.length() > 4 ? author.substring(0, 4) : author);
	            result.append(title.length() > 4 ? title.substring(0, 4) : title);
	        } else {
	            StringTokenizer titleWords = new StringTokenizer(title);
	            int wordNo = 1;
	            while (titleWords.hasMoreTokens() && wordNo < 5) {
	                String word = titleWords.nextToken();
	                switch (wordNo) {
	                    case 1:
	                        result.append(word.length() > 4 ? word.substring(0, 4) : word);
	                        break;
	                    case 2:
	                    case 3:
	                        result.append(word.length() > 2 ? word.substring(0, 2) : word);
	                        break;
	                    case 4:
	                        result.append(word.length() > 1 ? word.substring(0, 1) : word);
	                        break;
	                }
	                wordNo++;
	            }
	        }
	        String res = UghHelper.convertUmlaut(result.toString()).toLowerCase();
	        return res.replaceAll("[\\W]", ""); // delete umlauts etc.
	    }

    @Override
    public void setForm(MassImportForm form) {
        this.form = form;
        
    }
}
