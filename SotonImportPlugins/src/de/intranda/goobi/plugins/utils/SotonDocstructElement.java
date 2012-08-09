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

package de.intranda.goobi.plugins.utils;

import org.goobi.production.Import.DocstructElement;
import org.goobi.production.properties.ImportProperty;
import org.goobi.production.properties.Type;

public class SotonDocstructElement extends DocstructElement {

	private ImportProperty partProperty = new ImportProperty();
	private ImportProperty volumeProperty = new ImportProperty();
	private ImportProperty yearProperty = new ImportProperty();

	public SotonDocstructElement(String docStruct, int order) {
		super(docStruct, order);
		populatePropertyList();
	}

	private void populatePropertyList() {
		volumeProperty.setName("Volume");
		volumeProperty.setType(Type.TEXT);
		volumeProperty.setRequired(true);

		partProperty.setName("Part");
		partProperty.setType(Type.TEXT);
		partProperty.setRequired(false);

		yearProperty.setName("Year");
		yearProperty.setType(Type.TEXT);
		yearProperty.setRequired(false);
	}


	public ImportProperty getVolumeProperty() {
		return volumeProperty;
	}

	public ImportProperty getPartProperty() {
		return partProperty;
	}
	
	public ImportProperty getYearProperty() {
		return yearProperty;
	}
}
