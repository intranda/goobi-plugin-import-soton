package de.intranda.goobi.plugins.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdom.Element;

import ugh.dl.DocStruct;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;

public class ModsUtils {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(ModsUtils.class);

	/**
	 * 
	 * @param dsLogical
	 * @param dsPhysical
	 * @param eleMods
	 * @throws MetadataTypeNotAllowedException
	 */
	public static void parseModsSection(Prefs prefs, DocStruct dsLogical, DocStruct dsPhysical, Element eleMods) {
		for (Object objMeta : eleMods.getChildren()) {
			try {
				Element eleMeta = (Element) objMeta;
				// logger.debug("md: " + eleMeta.getName());
				if (eleMeta.getName().equals("titleInfo")) {
					String localTitle = "";
					if (eleMeta.getChild("nonSort", null) != null) {
						localTitle += eleMeta.getChild("nonSort", null).getText();
					}
					if (eleMeta.getChild("title", null) != null) {
						localTitle += eleMeta.getChild("title", null).getText();
					}

					if (eleMeta.getAttribute("type") != null) {
						if (eleMeta.getAttributeValue("type").equals("alternative")) {
							Metadata mdTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocParallel"));
							dsLogical.addMetadata(mdTitle);
							mdTitle.setValue(localTitle);
						}
					} else {
						// Main title
						// currentTitle = localTitle;
						Metadata mdTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocMain"));
						dsLogical.addMetadata(mdTitle);
						mdTitle.setValue(localTitle);
						if (eleMeta.getChild("subTitle", null) != null) {
							// Main subtitle
							Metadata mdSubTitle = new Metadata(prefs.getMetadataTypeByName("TitleDocSub1"));
							dsLogical.addMetadata(mdSubTitle);
							mdSubTitle.setValue(eleMeta.getChild("subTitle", null).getTextTrim());
						}
					}

				} else if (eleMeta.getName().equals("name")) {
					if (eleMeta.getAttributeValue("type").equals("personal")) {
						String name = "";
						String lastName = "";
						String firstName = "";
						for (Object obj : eleMeta.getChildren("namePart", null)) {
							Element eleNamePart = (Element) obj;
							if (eleNamePart.getAttribute("type") != null) {
								if (eleNamePart.getAttributeValue("type").equals("family")) {
									lastName = eleMeta.getChild("namePart", null).getAttributeValue("type");
								} else if (eleNamePart.getAttributeValue("type").equals("given")) {
									firstName = eleMeta.getChild("namePart", null).getAttributeValue("type");
								} else if (eleNamePart.getAttributeValue("type").equals("date")) {
									// TODO currently not supported by the ruleset
								}
							} else {
								name += eleMeta.getChild("namePart", null).getText();
							}
						}
						if (name.contains(",")) {
							String[] nameSplit = name.split("[,]");
							if (nameSplit.length > 0 && StringUtils.isEmpty(lastName)) {
								lastName = nameSplit[0].trim();
							}
							if (nameSplit.length > 1 && StringUtils.isEmpty(firstName)) {
								firstName = nameSplit[1].trim();
							}
						} else {
							lastName = name;
						}

						Person person = new Person(prefs.getMetadataTypeByName("Author"));
						dsLogical.addPerson(person);
						person.setFirstname(firstName);
						person.setLastname(lastName);
						person.setRole("Author");
					} else if (eleMeta.getAttributeValue("type").equals("corporate")) {
						// TODO currently not supported by the ruleset
					}
				} else if (eleMeta.getName().equals("originInfo")) {
					for (Object obj : eleMeta.getChildren()) {
						Element ele = (Element) obj;
						if (ele.getName().equals("place")) {
							Element elePlaceTerm = ele.getChild("placeTerm", null);
							if (elePlaceTerm != null && elePlaceTerm.getAttribute("type") != null) {
								if (elePlaceTerm.getAttributeValue("type").equals("text")) {
									Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PlaceOfPublication"));
									dsLogical.addMetadata(metadata);
									metadata.setValue(elePlaceTerm.getTextTrim());
								} else if (elePlaceTerm.getAttributeValue("type").equals("code")) {
									// TODO currently not supported by the ruleset
								}
							}
						} else if (ele.getName().equals("publisher")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PublisherName"));
							dsLogical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
						} else if (ele.getName().equals("dateIssued")) {
							if (ele.getAttribute("point") != null) {
								if (ele.getAttributeValue("point").equals("start")) {
									Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PublicationStart"));
									dsLogical.addMetadata(metadata);
									metadata.setValue(ele.getTextTrim());
								} else if (ele.getAttributeValue("point").equals("end")) {
									Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PublicationEnd"));
									dsLogical.addMetadata(metadata);
									metadata.setValue(ele.getTextTrim());
								}
							} else {
								Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PublicationYear"));
								dsLogical.addMetadata(metadata);
								metadata.setValue(ele.getTextTrim());
							}
						} else if (ele.getName().equals("dateCreated")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("PublicationYear"));
							dsLogical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
						}
					}
				} else if (eleMeta.getName().equals("language")) {
					Element eleLanguageTerm = eleMeta.getChild("languageTerm", null);
					if (eleLanguageTerm != null && eleLanguageTerm.getAttribute("authority") != null
							&& eleLanguageTerm.getAttributeValue("authority").equals("iso639-2b")) {
						String language = eleMeta.getChildTextTrim("languageTerm", null);
						Metadata metadata = new Metadata(prefs.getMetadataTypeByName("DocLanguage"));
						dsLogical.addMetadata(metadata);
						metadata.setValue(language);
					}
				} else if (eleMeta.getName().equals("physicalDescription")) {
					for (Object obj : eleMeta.getChildren()) {
						Element ele = (Element) obj;
						if (ele.getName().equals("extent")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("SizeSourcePrint"));
							dsLogical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
						}
					}
				} else if (eleMeta.getName().equals("recordInfo")) {
					for (Object obj : eleMeta.getChildren()) {
						Element ele = (Element) obj;
						if (ele.getName().equals("recordIdentifier")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("CatalogIDDigital"));
							dsLogical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
							// currentIdentifier = metadata.getValue();
						}
					}
				} else if (eleMeta.getName().equals("location")) {
					for (Object obj : eleMeta.getChildren()) {
						Element ele = (Element) obj;
						if (ele.getName().equals("physicalLocation")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("physicallocation"));
							dsLogical.addMetadata(metadata);
							dsPhysical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
						} else if (ele.getName().equals("shelfLocation")) {
							Metadata metadata = new Metadata(prefs.getMetadataTypeByName("shelfmarksource"));
							dsLogical.addMetadata(metadata);
							dsPhysical.addMetadata(metadata);
							metadata.setValue(ele.getTextTrim());
						}
					}
				}
			} catch (MetadataTypeNotAllowedException e) {
				logger.warn(e.getMessage());
			}
		}
	}

	/**
	 * Returns the document's identifier, or a timestamp if the record has none
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getIdentifier(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypeId = prefs.getMetadataTypeByName("CatalogIDDigital");
		if (ds.getAllMetadataByType(mdTypeId) != null && !ds.getAllMetadataByType(mdTypeId).isEmpty()) {
			Metadata mdId = ds.getAllMetadataByType(mdTypeId).get(0);
			ret = mdId.getValue();
		} else {
			Metadata mdId = new Metadata(mdTypeId);
			ds.addMetadata(mdId);
			mdId.setValue(String.valueOf(System.currentTimeMillis()));
			ret = mdId.getValue();
		}

		return ret;
	}

	/**
	 * Returns the document's title.
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getTitle(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypeTitle = prefs.getMetadataTypeByName("TitleDocMain");
		if (ds.getAllMetadataByType(mdTypeTitle) != null && !ds.getAllMetadataByType(mdTypeTitle).isEmpty()) {
			Metadata mdTitle = ds.getAllMetadataByType(mdTypeTitle).get(0);
			ret = mdTitle.getValue();
		}

		return ret;
	}

	/**
	 * Returns the document's author.
	 * 
	 * @param prefs
	 * @param ds
	 * @return
	 * @throws MetadataTypeNotAllowedException
	 * @throws DocStructHasNoTypeException
	 */
	public static String getAuthor(Prefs prefs, DocStruct ds) throws MetadataTypeNotAllowedException, DocStructHasNoTypeException {
		String ret = null;

		MetadataType mdTypePerson = prefs.getMetadataTypeByName("Author");
		if (ds.getAllPersonsByType(mdTypePerson) != null && !ds.getAllPersonsByType(mdTypePerson).isEmpty()) {
			Person personAuthor = ds.getAllPersonsByType(mdTypePerson).get(0);
			ret = personAuthor.getLastname();
			if (StringUtils.isNotEmpty(personAuthor.getFirstname())) {
				ret += ", " + personAuthor.getFirstname();
			}
		}

		return ret;
	}
}
