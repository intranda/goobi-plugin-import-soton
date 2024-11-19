/**
 * This file is part of CamImportPlugins/SotonImportPlugins.
 * 
 * Copyright (C) 2012 intranda GmbH
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
 * @author Robert Sehr
 */

package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;
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

import de.intranda.goobi.plugins.utils.ModsUtils;
import de.intranda.goobi.plugins.utils.SotonDocstructElement;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.forms.MassImportForm;
import io.goobi.workflow.api.connection.HttpUtils;
import net.xeoh.plugins.base.annotations.PluginImplementation;
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

import javax.faces.model.SelectItem;

@PluginImplementation
public class SotonMarcMultiVolumeImport implements IImportPlugin, IPlugin {

    private static final Logger logger = Logger.getLogger(SotonMarcMultiVolumeImport.class);

    private static final String NAME = "Soton Multivolume Import";
    //	private static final String ID = "sotonMultiVolumeImport";
    private static final String XSLT = ConfigurationHelper.getInstance().getXsltFolder() + "MARC21slim2MODS3.xsl";
    private static final String MODS_MAPPING_FILE = ConfigurationHelper.getInstance().getXsltFolder() + "mods_map_multi.xml";

    private static final String TYPE_PROPERTY_NAME = "Publication type";

    private static final String DOCSTRUCT_TYPE_SERIAL = "Series";
    private static final String DOCSTRUCT_TYPE_PERIODICAL = "Periodical";
    private static final String DOCSTRUCT_TYPE_MULTIVOLUME = "Multipart monograph";

    // private static final Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");
    private List<String> currentCollectionList;
    private List<ImportProperty> properties = new ArrayList<>();
    private List<SotonDocstructElement> currentDocStructs = new ArrayList<>();
    private SotonDocstructElement docstruct;
    // private HashMap<String, String> structType = new HashMap<String, String>();;

    private File importFile = null;

    private Prefs prefs;
    private String data = "";
    // private File importFile = null;
    private String importFolder = "C:/Goobi/";
    private String currentIdentifier;
    private MassImportForm form;

    public SotonMarcMultiVolumeImport() {

        {
            ImportProperty ip = new ImportProperty();
            ip.setName(TYPE_PROPERTY_NAME);
            ip.setType(Type.LIST);
            List<String> values = new ArrayList<>();
            values.add(DOCSTRUCT_TYPE_SERIAL);
            values.add(DOCSTRUCT_TYPE_PERIODICAL);
            values.add(DOCSTRUCT_TYPE_MULTIVOLUME);
            ip.setPossibleValues(values.stream()
                    .map(v -> new SelectItem(v, v))
                    .toList());
            ip.setRequired(true);
            this.properties.add(ip);
        }

    }

    public String getId() {
        return getDescription();
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return NAME;
    }

    public String getDescription() {
        return NAME;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<>();

        if (records.size() > 0) {
            if (form != null) {
                form.addProcessToProgressBar();
            }
            Record r = records.get(0);
            this.data = r.getData();
            this.currentCollectionList = r.getCollections();
            for (SotonDocstructElement dse : currentDocStructs) {
                docstruct = dse;
                ImportObject io = new ImportObject();
                Fileformat ff = null;
                ff = convertData();
                io.setProcessTitle(getProcessTitle());
                if (ff != null) {
                    r.setId(this.currentIdentifier);
                    try {
                        MetsMods mm = new MetsMods(this.prefs);
                        mm.setDigitalDocument(ff.getDigitalDocument());
                        String fileName = getImportFolder() + getProcessTitle() + ".xml";
                        logger.debug("Writing '" + fileName + "' into given folder...");
                        mm.write(fileName);
                        io.setMetsFilename(fileName);
                        io.setImportReturnValue(ImportReturnValue.ExportFinished);
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.ExportFinished);
                    } catch (PreferencesException e) {
                        logger.error(e.getMessage(), e);
                        io.setErrorMessage(e.getMessage());
                        io.setImportReturnValue(ImportReturnValue.InvalidData);
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.InvalidData);
                    } catch (WriteException e) {
                        logger.error(e.getMessage(), e);
                        io.setImportReturnValue(ImportReturnValue.WriteError);
                        io.setErrorMessage(e.getMessage());
                        // ret.put(getProcessTitle(),
                        // ImportReturnValue.WriteError);
                    }
                } else {
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                    // ret.put(getProcessTitle(),
                    // ImportReturnValue.InvalidData);
                }
                answer.add(io);
            }
        }
        return answer;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        List<Record> ret = new ArrayList<>();
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
        List<Record> ret = new ArrayList<>();

        // Split strings
        List<String> recordStrings = new ArrayList<>();
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
        List<String> answer = new ArrayList<>();
        answer.add(ids);
        return answer;
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
        List<ImportType> answer = new ArrayList<>();
        answer.add(ImportType.Record);
        answer.add(ImportType.ID);
        answer.add(ImportType.FILE);
        return answer;
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
                        eleDataField.setAttribute("ind1", !"\\".equals(ind1) ? ind1 : "");
                        eleDataField.setAttribute("ind2", !"\\".equals(ind2) ? ind2 : "");
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

    @Override
    public List<ImportProperty> getProperties() {
        return this.properties;
    }

    @Override
    public List<String> getAllFilenames() {
        List<String> answer = new ArrayList<>();
        return answer;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        List<Record> records = new ArrayList<>();
        return records;
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
    }

    @Override
    public String addDocstruct() {

        int order = 1;
        if (!currentDocStructs.isEmpty()) {
            order = currentDocStructs.get(currentDocStructs.size() - 1).getOrder() + 1;
        }
        SotonDocstructElement dse = new SotonDocstructElement("Monograph", order);
        currentDocStructs.add(dse);

        return "";
    }

    @Override
    public String deleteDocstruct() {
        if (currentDocStructs.contains(docstruct)) {
            currentDocStructs.remove(docstruct);
        }
        return "";
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        if (currentDocStructs.size() == 0) {
            SotonDocstructElement dse = new SotonDocstructElement("Monograph", 1);
            currentDocStructs.add(dse);
        }
        return currentDocStructs;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        List<String> dsl = new ArrayList<>();
        dsl.add("Archive");
        dsl.add("Artwork");
        dsl.add("Audio");
        dsl.add("Born digital");
        dsl.add("Bound manuscript");
        dsl.add("Monograph");
        dsl.add("Multiple copy");
        dsl.add("Multiple volume");
        dsl.add("Multiple volume multiple copy");
        dsl.add("Poster image");
        dsl.add("Still images");
        dsl.add("Transcript");
        dsl.add("Video");
        return dsl;
    }

    @Override
    public Fileformat convertData() {
        Fileformat ff = null;
        Document doc;
        try {
            String marc = data;
            // String marc = fetchRecord("http://pdf.library.soton.ac.uk/example_output.html");
            if (!data.startsWith("<?xml")) {
                marc = fetchRecord("http://lms.soton.ac.uk/cgi-bin/goobi_marc.cgi?itemid=" + this.data);
                currentIdentifier = data;
                if (StringUtils.isEmpty(marc) || marc.toLowerCase().contains("barcode not found")) {
                    return null;
                }
                marc = extractMarcFromHtml(marc);
                marc = convertToMarcXml(marc);
            }
            logger.trace(marc);
            doc = new SAXBuilder().build(new StringReader(marc));
            if (doc != null && doc.hasRootElement()) {
                XSLTransformer transformer = new XSLTransformer(XSLT);
                Document docMods = transformer.transform(doc);
                logger.trace(new XMLOutputter().outputString(docMods));

                ff = new MetsMods(this.prefs);
                DigitalDocument dd = new DigitalDocument();
                ff.setDigitalDocument(dd);

                Element eleMods = docMods.getRootElement();
                if ("modsCollection".equals(eleMods.getName())) {
                    eleMods = eleMods.getChild("mods", null);
                }

                // Determine the docstruct type, default is MultiVolumeWork

                DocStruct dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName("MultiVolumeWork"));
                DocStruct dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Volume"));
                for (ImportProperty ip : properties) {
                    if (TYPE_PROPERTY_NAME.equals(ip.getName())) {
                        if (DOCSTRUCT_TYPE_SERIAL.equals(ip.getValue())) {
                            dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Series"));
                            dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("SerialVolume"));
                        } else if (DOCSTRUCT_TYPE_PERIODICAL.equals(ip.getValue())) {
                            dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Periodical"));
                            dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("PeriodicalVolume"));
                        } else {
                            dsRoot = dd.createDocStruct(this.prefs.getDocStrctTypeByName("MultiVolumeWork"));
                            dsVolume = dd.createDocStruct(this.prefs.getDocStrctTypeByName("Volume"));
                        }

                    }
                }

                dd.setLogicalDocStruct(dsRoot);

                DocStruct dsBoundBook = dd.createDocStruct(this.prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(dsBoundBook);

                // Collect MODS metadata
                ModsUtils.parseModsSectionForMultivolumes(MODS_MAPPING_FILE, this.prefs, dsRoot, dsVolume, dsBoundBook, eleMods);
                // use filename as identifier
                if (importFile != null) {
                    this.currentIdentifier = importFile.getName().replace(".mrc", "").replace(".xml", "");
                    // use ID as identifier
                } else if (data.startsWith("<")) {
                    this.currentIdentifier = this.data;
                }
                // use control field 001 as identifier
                else if (dsRoot.getAllMetadataByType(this.prefs.getMetadataTypeByName("CatalogIDDigital")) != null
                        && dsRoot.getAllMetadataByType(this.prefs.getMetadataTypeByName("CatalogIDDigital")).size() > 0) {
                    currentIdentifier = dsRoot.getAllMetadataByType(this.prefs.getMetadataTypeByName("CatalogIDDigital")).get(0).getValue();
                } else {
                    currentIdentifier = String.valueOf(System.currentTimeMillis());
                }
                // Add volume to anchors

                dsRoot.addChild(dsVolume);
                Metadata mdId = new Metadata(this.prefs.getMetadataTypeByName("CatalogIDDigital"));
                mdId.setValue(this.currentIdentifier + String.valueOf(docstruct.getOrder()));
                dsVolume.addMetadata(mdId);

                // Add order number to child element
                Metadata currentNo = new Metadata(this.prefs.getMetadataTypeByName("CurrentNo"));
                currentNo.setValue(String.valueOf(docstruct.getOrder()));
                dsVolume.addMetadata(currentNo);
                Metadata currentNoSorting = new Metadata(this.prefs.getMetadataTypeByName("CurrentNoSorting"));
                currentNoSorting.setValue(String.valueOf(docstruct.getOrder()));
                dsVolume.addMetadata(currentNoSorting);

                // Add volume to child element
                Metadata volumeNumber = new Metadata(this.prefs.getMetadataTypeByName("volumeNumber"));
                volumeNumber.setValue(docstruct.getVolumeProperty().getValue());
                dsVolume.addMetadata(volumeNumber);

                // Add Part number to child element
                if (docstruct.getPartProperty().getValue() != null && docstruct.getPartProperty().getValue().length() > 0) {
                    currentNo.setValue(String.valueOf(docstruct.getPartProperty().getValue()));
                }

                // Add Date/Year to child element
                if (docstruct.getYearProperty().getValue() != null && docstruct.getYearProperty().getValue().length() > 0) {
                    Metadata publicationYear = new Metadata(this.prefs.getMetadataTypeByName("PublicationYear"));
                    publicationYear.setValue(docstruct.getYearProperty().getValue());
                    dsVolume.addMetadata(publicationYear);
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
                        Metadata mdCollectionVolume = new Metadata(mdTypeCollection);
                        mdCollectionVolume.setValue(collection);
                        dsVolume.addMetadata(mdCollectionVolume);
                    }
                }
            }
        } catch (JDOMException | IOException | PreferencesException | TypeNotAllowedForParentException | MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
            logger.error(e.getMessage(), e);
        } catch (TypeNotAllowedAsChildException e) {
            logger.warn(e.getMessage(), e);
        }

        return ff;
    }

    private String convertToMarcXml(String marc) {
        String ret = null;
        InputStream input = null;
        try {
            input = new ByteArrayInputStream(marc.getBytes("iso8859-1"));
            MarcReader reader = new MarcStreamReader(input);
            while (reader.hasNext()) {
                try {
                    org.marc4j.marc.Record marcRecord = reader.next();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    MarcXmlWriter writer = new MarcXmlWriter(out, "utf-8", true);
                    writer.setConverter(new AnselToUnicode());
                    writer.write(marcRecord);
                    writer.close();
                    ret = out.toString();
                    out.close();
                } catch (MarcException e) {
                    logger.warn(e.getMessage());
                }
            }
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
        String ret = "";

        if (StringUtils.isNotEmpty(url)) {

            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpGet method = new HttpGet(url);
            try {
                ret = client.execute(method, HttpUtils.stringResponseHandler);
            } catch (IOException e) {
                logger.error(e);
            } finally {
                method.releaseConnection();

                if (client != null) {
                    try {
                        client.close();
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
            }

        }

        return ret;
    }

    @Override
    public String getProcessTitle() {

        String strId = String.valueOf(docstruct.getOrder());
        if (docstruct.getOrder() < 10) {
            strId = "000" + strId;
        } else if (docstruct.getOrder() < 100) {
            strId = "00" + strId;
        } else if (docstruct.getOrder() < 1000) {
            strId = "0" + strId;
        }

        return this.currentIdentifier + "_" + strId;
    }

    @Override
    public void setForm(MassImportForm form) {
        this.form = form;
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
}
