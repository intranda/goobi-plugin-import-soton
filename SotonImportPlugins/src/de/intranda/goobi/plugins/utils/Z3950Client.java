
package de.intranda.goobi.plugins.utils;

//public static String RECSYN_SUTRS = "sutrs";
//public static String RECSYN_HTML = "html";
//public static String RECSYN_XML = "xml";
//public static String RECSYN_UKMARC = "ukmarc";
//public static String RECSYN_USMARC = "usmarc";
//public static String RECSYN_MARC21 = "marc21";
//public static String RECSYN_NORMARC = "normarc";

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.k_int.IR.InvalidQueryException;
import com.k_int.IR.SearchException;
import com.k_int.gen.AsnUseful.EXTERNAL_type;
import com.k_int.gen.Z39_50_APDU_1995.DefaultDiagFormat_type;
import com.k_int.gen.Z39_50_APDU_1995.NamePlusRecord_type;
import com.k_int.gen.Z39_50_APDU_1995.PresentResponse_type;
import com.k_int.gen.Z39_50_APDU_1995.Records_type;
import com.k_int.gen.Z39_50_APDU_1995.SearchResponse_type;
import com.k_int.gen.Z39_50_APDU_1995.record_inline13_type;
import com.k_int.z3950.client.ZClient;

/**
 * JZkit Z39.50 client object.
 */
public class Z3950Client {

	/** Logger for this class. */
	private static final Logger logger = Logger.getLogger(Z3950Client.class);

	private ZClient client;
	private String currentResultSetName = null;
	private int rs = 0;

	/**
	 * Constructor.
	 * 
	 * @param url Server URL.
	 * @param port Server port.
	 * @param db Server database name.
	 * @param auth Authentication string.
	 */
	public Z3950Client(String url, String port, String db, String auth) {
		client = new ZClient();
		client.cmdAuth(auth);
		client.clearAllDatabases();
		client.addDatatabse(db);
		client.connect(url, port);

	}

	/**
	 * Disconnects the ZClient.
	 */
	public void disconnect() {
		if (client != null) {
			client.disconnect();
		}
	}

	/**
	 * 
	 * @param query BIB-1 query.
	 * @param format Allowed format name as defined in <code>Z3950Constants</code>.
	 * @return The result dataset string.
	 */
	public String query(String query, String format) {
		String ret = null;

		client.setRecordSyntax(format);
		SearchResponse_type resp = null;
		currentResultSetName = "RS" + (rs++);

		try {
			resp = client.sendSearch(new com.k_int.IR.QueryModels.PrefixString(query), null, currentResultSetName, null);
		} catch (SearchException se) {
			logger.error(se.toString());
			if (se.additional != null) {
				resp = (SearchResponse_type) (se.additional);
			}
		} catch (InvalidQueryException iqe) {
			logger.error(iqe.getMessage(), iqe);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		if (resp != null) {
			logger.debug("\n  Search Response");
			if (resp.referenceId != null) {
				logger.debug("  Reference ID : " + new String(resp.referenceId));
			}
			logger.debug("  Search Result : " + resp.searchStatus);
			logger.debug("  Result Count : " + resp.resultCount);
			logger.debug("  Num Records Returned : " + resp.numberOfRecordsReturned);
			logger.debug("  Next RS position : " + resp.nextResultSetPosition);
			if (null != resp.additionalSearchInfo) {
				logger.debug("  search response contains " + resp.additionalSearchInfo.size() + " additionalSearchInfo entries");
			}

			if (null != resp.otherInfo) {
				logger.debug("  search response contains " + resp.otherInfo.size() + " otherInfo entries");
			}

			if ((resp.records != null) && (resp.numberOfRecordsReturned.intValue() > 0)) {
				logger.debug("  Search has piggyback records");
				client.displayRecords(resp.records);
			}

			ret = present();
		}

		return ret;
	}

	/**
	 * Fetches and returns the result set with the given name (name aquired by the first query).
	 * 
	 * @return The result dataset string.
	 */
	private String present() {
		String ret = null;
		try {
			PresentResponse_type resp = client.sendPresent(1, 1, "F", currentResultSetName);

			logger.debug("\n  Present Response");

			if (resp.referenceId != null) {
				logger.debug("  Reference ID : " + new String(resp.referenceId));
			}

			logger.debug("  Number of records : " + resp.numberOfRecordsReturned);
			logger.debug("  Next RS Position : " + resp.nextResultSetPosition);
			logger.debug("  Present Status : " + resp.presentStatus);

			Records_type r = resp.records;
			ret = resultToString(r);

			logger.debug(ret);

		} catch (Exception e) {
			logger.error("Exception processing show command ", e);
		}

		return ret;
	}

	/**
	 * 
	 * @param r The Records_type to convert to string.
	 * @return The result dataset string.
	 */
	@SuppressWarnings("unchecked")
	private String resultToString(Records_type r) {
		String ret = "";
		if (r != null) {
			switch (r.which) {
			case Records_type.responserecords_CID:
				Vector v = (Vector) (r.o);
				int numRecords = v.size();
				logger.debug("Response contains " + numRecords + " Response Records");
				for (Enumeration recs = v.elements(); recs.hasMoreElements();) {
					NamePlusRecord_type npr = (NamePlusRecord_type) (recs.nextElement());

					if (null != npr) {
						logger.debug("[" + npr.name + "] ");

						switch (npr.record.which) {
						case record_inline13_type.retrievalrecord_CID:
							// RetrievalRecord is an external
							EXTERNAL_type et = (EXTERNAL_type) npr.record.o;
							// logger.debug("  Direct Reference="+et.direct_reference+"] ");
							// dumpOID(et.direct_reference);
							// Just rely on a toString method for now
							if (et.direct_reference.length == 6) {
								switch (et.direct_reference[(et.direct_reference.length) - 1]) {
								case 1: // Unimarc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 3: // CCF
									break;
								case 10: // US Marc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 11: // UK Marc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 12: // Normarc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 13: // Librismarc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 14: // Danmarc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 15: // Finmarc
									ret = new com.k_int.IR.Syntaxes.marc.iso2709((byte[]) et.encoding.o).toString();
									break;
								case 100: // Explain
									// Write display code....
									break;
								case 101: // SUTRS
									ret = (String) et.encoding.o;
									break;
								case 102: // Opac
									// Write display code....
									break;
								case 105: // GRS1
									// displayGRS((java.util.Vector) et.encoding.o);
									break;
								default:
									logger.debug("Unknown.... ");
									ret = et.encoding.o.toString();
									break;
								}
							} else if ((et.direct_reference.length == 7) && (et.direct_reference[5] == 109)) {
								switch (et.direct_reference[6]) {
								case 3: // HTML
									String htmlRec = null;
									if (et.encoding.o instanceof byte[]) {
										htmlRec = new String((byte[]) et.encoding.o);
									} else {
										htmlRec = et.encoding.o.toString();
									}
									ret = htmlRec.toString();
									break;
								case 9: // SGML
									ret = et.encoding.o.toString();
									break;
								case 10: // XML
									ret = new String((byte[]) (et.encoding.o));
									break;
								default:
									ret = et.encoding.o.toString();
									break;
								}
							} else {
								logger.debug("Unknown direct reference OID: " + et.direct_reference);
							}
							break;
						case record_inline13_type.surrogatediagnostic_CID:
							logger.debug("SurrogateDiagnostic");
							break;
						case record_inline13_type.startingfragment_CID:
							logger.debug("StartingFragment");
							break;
						case record_inline13_type.intermediatefragment_CID:
							logger.debug("IntermediateFragment");
							break;
						case record_inline13_type.finalfragment_CID:
							logger.debug("FinalFragment");
							break;
						default:
							logger.debug("Unknown Record type for NamePlusRecord");
							break;
						}
					} else {
						logger.debug("Error... record ptr is null");
					}
				}
				break;
			case Records_type.nonsurrogatediagnostic_CID:
				DefaultDiagFormat_type diag = (DefaultDiagFormat_type) r.o;
				logger.debug("    Non surrogate diagnostics : " + diag.condition);
				if (diag.addinfo != null) {
					// addinfo is VisibleString in v2, InternationalString in V3
					logger.debug("Additional Info: " + diag.addinfo.o.toString());
				}
				break;
			case Records_type.multiplenonsurdiagnostics_CID:
				logger.debug("    Multiple non surrogate diagnostics");
				break;
			default:
				logger.debug("    Unknown choice for records response : " + r.which);
				break;
			}
		}

		return ret;
	}

	/**
	 * Test main method.
	 * 
	 * @param args Command line args.
	 */
	public static void main(String[] args) {
		// Z3950Client z = new Z3950Client("z3950.obvsg.at", "9991", "ACC01", "idpass ,Z39-LBO , ,ZLBO39");
		// z.query("@attrset bib-1 @attr 1=52 AC00653235", "usmarc");

		Z3950Client z = new Z3950Client("cpdes.sub.uni-hamburg.de", "2100", "hans", "idpass ,opac , ,MCCOYO");
		z.query("@attrset bib-1 @attr 1=12 w2986", "xml");

		// Z3950Client z = new Z3950Client("lib-dev-a.iss.soton.ac.uk", "2200", "SIRSI", "anon");
		// z.query("@attrset bib-1 @attr 1=12 \"W63375\"", "usmarc");

		z.disconnect();
	}
}
