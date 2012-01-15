/**
 * Rapid-prototyping protection schemes with IEC 61850
 *
 * Copyright (c) 2012 Steven Blair
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package scdCodeGenerator;

import java.io.File;

import org.eclipse.emf.ecore.resource.Resource;

import ch.iec._61850._2006.scl.DocumentRoot;
import ch.iec._61850._2006.scl.util.SclXMLProcessor;

public class Main {
	
	final static String PATH_TO_SOURCE = "src\\scdCodeGenerator\\";
	
	public static void main(String[] args) {
		SCDValidator validator = new SCDValidator();
		SCDCodeGenerator scdCodeGenerator = new SCDCodeGenerator();
		String filename = "scd.xml";	// edit this to match the input SCD file
		
		// import SCD file
		String scdFullFilePath = PATH_TO_SOURCE + filename;
		Resource resource = null;
		
		try {
			File scdFile = new File(scdFullFilePath);
			if (scdFile.exists()) {
				SclXMLProcessor processor = new SclXMLProcessor();
				resource = processor.load(scdFile.getAbsolutePath(), null);
			}
			else {
				validator.error("SCD file does not exist");
			}
		}
		catch (Exception e) {
			//e.printStackTrace();
			validator.error("cannot parse SCD file");
		}
		
		// get root of XML document
		DocumentRoot root = ((DocumentRoot) resource.getContents().get(0));
		
		// model validation and pre-caching
		validator.checkForDuplicateNames(root);
		validator.setPrintedType(root);
		validator.mapDataSetToControl(root);
		validator.mapExtRefToDataSet(root);
		validator.mapControlToControlBlock(root);
		validator.mapFCDAToDataType(root);
		validator.checkForCircularSDOReferences(root);
		
		// generate code
		scdCodeGenerator.generateCode(root);
	}
}
