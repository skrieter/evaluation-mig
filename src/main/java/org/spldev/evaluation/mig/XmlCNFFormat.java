/* -----------------------------------------------------------------------------
 * Evaluation-MIG - Program for the evalaution of building incremetnal MIGs.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-MIG.
 * 
 * Evaluation-MIG is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-MIG is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-MIG.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-mig> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.mig;

import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.Clauses;
import org.spldev.formula.expression.io.XmlFeatureModelCNFFormat;
import org.spldev.util.Result;
import org.spldev.util.io.format.Format;

public class XmlCNFFormat implements Format<CNF> {

	public XmlCNFFormat() {
	}

	@Override
	public String getFileExtension() {
		return "xml";
	}

	@Override
	public Result<CNF> parse(CharSequence source) {
		return new XmlFeatureModelCNFFormat().parse(source).map(Clauses::convertToCNF);
	}

	@Override
	public boolean supportsParse() {
		return true;
	}

	@Override
	public XmlCNFFormat getInstance() {
		return new XmlCNFFormat();
	}

	@Override
	public String getId() {
		return "FeatureIDEXMLFormat";
	}

	@Override
	public String getName() {
		return "FeatureIDE";
	}

}
