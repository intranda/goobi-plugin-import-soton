package de.intranda.goobi.plugins;
import java.io.File;
import java.util.HashMap;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.log4j.Logger;
import org.goobi.production.Import.Record;
import org.goobi.production.enums.ImportReturnValue;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@PluginImplementation
public class SimpleImport implements IImportPlugin, IPlugin {

	private static final Logger logger = Logger.getLogger(SimpleImport.class);

	private static final String ID = "simple_import_plugin";
	private static final String NAME = "Simple Import Plugin";
	private static final String VERSION = "1.0.20110228";
	
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setPrefs(Prefs prefs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setData(Record r) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Fileformat convertData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getImportFolder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProcessTitle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Record> splitRecords(String records) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HashMap<String, ImportReturnValue> generateFiles(List<Record> records) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setImportFolder(String folder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<Record> generateRecordsFromFile() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFile(File importFile) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<String> splitIds(String ids) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ImportType> getImportTypes() {
		// TODO Auto-generated method stub
		return null;
	}


}
