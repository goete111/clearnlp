/**
 * Copyright (c) 2009/09-2012/08, Regents of the University of Colorado
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Copyright 2012/09-2013/04, University of Massachusetts Amherst
 * Copyright 2013/05-Present, IPSoft Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package com.googlecode.clearnlp.nlp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.carrotsearch.hppc.ObjectIntOpenHashMap;
import com.googlecode.clearnlp.classification.algorithm.AdaGradHinge;
import com.googlecode.clearnlp.classification.algorithm.AdaGradLR;
import com.googlecode.clearnlp.classification.model.AbstractModel;
import com.googlecode.clearnlp.classification.train.AbstractTrainSpace;
import com.googlecode.clearnlp.dependency.DEPTree;
import com.googlecode.clearnlp.io.FileExtFilter;
import com.googlecode.clearnlp.reader.AbstractColumnReader;
import com.googlecode.clearnlp.reader.AbstractReader;
import com.googlecode.clearnlp.reader.JointReader;
import com.googlecode.clearnlp.reader.LineReader;
import com.googlecode.clearnlp.reader.RawReader;
import com.googlecode.clearnlp.run.LiblinearTrain;
import com.googlecode.clearnlp.util.UTXml;

/**
 * @since 1.3.0
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
abstract public class AbstractNLP
{
	protected final Logger LOG = LoggerFactory.getLogger(this.getClass());
	
	final public String TAG_READER					= "reader";
	final public String TAG_TYPE					= "type";
	final public String TAG_READER_COLUMN			= "column";
	final public String TAG_READER_COLUMN_INDEX		= "index";
	final public String TAG_READER_COLUMN_FIELD		= "field";
	
	final public String TAG_TRAIN					= "train";
	final public String TAG_TRAIN_ALGORITHM			= "algorithm";
	final public String TAG_TRAIN_ALGORITHM_NAME	= "name";
	final public String TAG_TRAIN_THREADS			= "threads";
	
	final public String TAG_LANGUAGE				= "language";
	final public String TAG_DICTIONARY 				= "dictionary";
	final public String TAG_MODELS					= "models";
	final public String TAG_MODEL					= "model";
	final public String TAG_MODE					= "mode";
	final public String TAG_PATH					= "path";
	
//	@Option(name="-r", usage="the random seed", required=false, metaVar="<integer>")
	protected int i_rand = 5;
	
	/** Initializes arguments using args4j. */
	protected void initArgs(String[] args)
	{
		CmdLineParser cmd = new CmdLineParser(this);
		
		try
		{
			cmd.parseArgument(args);
		}
		catch (CmdLineException e)
		{
			System.err.println(e.getMessage());
			cmd.printUsage(System.err);
			System.exit(1);
		}
		catch (Exception e) {e.printStackTrace();}
	}
	
	// ============================= genetic: mode =============================
	
	protected String toString(DEPTree tree, String mode)
	{
		if      (mode.startsWith(NLPLib.MODE_POS))
			return tree.toStringPOS();
		else if (mode.equals(NLPLib.MODE_MORPH))
			return tree.toStringMorph();
		else if (mode.equals(NLPLib.MODE_DEP) || mode.equals(NLPLib.MODE_PRED) || mode.equals(NLPLib.MODE_ROLE) || mode.startsWith(NLPLib.MODE_SENSE))
			return tree.toStringDEP();
		else
			return tree.toStringSRL();
	}
	
	// ============================= getter: language =============================
	
	protected String getLanguage(Element element)
	{
		Element eLanguage = UTXml.getFirstElementByTagName(element, TAG_LANGUAGE);
		return UTXml.getTrimmedTextContent(eLanguage);
	}
	
	protected String getDictionary(Element element)
	{
		Element eDictionary = UTXml.getFirstElementByTagName(element, TAG_DICTIONARY);
		return UTXml.getTrimmedTextContent(eDictionary);
	}
	
	// ============================= getter: filenames =============================
	
	/** String[0]: input filename, String[1]: output filename. */
	protected List<String[]> getFilenames(String inputPath, String inputExt, String outputExt)
	{
		List<String[]> filenames = new ArrayList<String[]>();
		File f = new File(inputPath);
		String[] inputFiles;
		String outputFile;
		
		if (f.isDirectory())
		{
			inputFiles = f.list(new FileExtFilter(inputExt));
			Arrays.sort(inputFiles);
			
			for (String inputFile : inputFiles)
			{
				inputFile  = inputPath + File.separator + inputFile;
				outputFile = inputFile + "." + outputExt;
				filenames.add(new String[]{inputFile, outputFile});
			}
		}
		else
			filenames.add(new String[]{inputPath, inputPath+"."+outputExt});
		
		return filenames;
	}
	
	// ============================= getter: readers =============================
	
	protected AbstractReader<?> getReader(Element eReader)
	{
		String type = UTXml.getTrimmedAttribute(eReader, TAG_TYPE);
		
		if      (type.equals(AbstractReader.TYPE_RAW))
			return new RawReader();
		else if (type.equals(AbstractReader.TYPE_LINE))
			return new LineReader();
		else
			return getJointReader(eReader);
	}
	
	protected JointReader getJointReader(Element eReader)
	{
		ObjectIntOpenHashMap<String> map = getFieldMap(eReader);
		
		int iId		= map.get(AbstractColumnReader.FIELD_ID)	 - 1;
		int iForm	= map.get(AbstractColumnReader.FIELD_FORM)	 - 1;
		int iLemma	= map.get(AbstractColumnReader.FIELD_LEMMA)	 - 1;
		int iPos	= map.get(AbstractColumnReader.FIELD_POS)	 - 1;
		int iFeats	= map.get(AbstractColumnReader.FIELD_FEATS)	 - 1;
		int iHeadId	= map.get(AbstractColumnReader.FIELD_HEADID) - 1;
		int iDeprel	= map.get(AbstractColumnReader.FIELD_DEPREL) - 1;
		int iXHeads = map.get(AbstractColumnReader.FIELD_XHEADS) - 1;
		int iSHeads = map.get(AbstractColumnReader.FIELD_SHEADS) - 1;
		int iNament = map.get(AbstractColumnReader.FIELD_NAMENT) - 1;
		int iCoref  = map.get(AbstractColumnReader.FIELD_COREF)  - 1;
		
		JointReader reader = new JointReader(iId, iForm, iLemma, iPos, iFeats, iHeadId, iDeprel, iXHeads, iSHeads, iNament, iCoref);
		reader.initGoldPOSTag(map.get(AbstractColumnReader.FIELD_GPOS) - 1);
		
		return reader;
	}
	
	/** Called by {@link AbstractNLP#getCDEPReader(Element, String)}. */
	private ObjectIntOpenHashMap<String> getFieldMap(Element eReader)
	{
		NodeList list = eReader.getElementsByTagName(TAG_READER_COLUMN);
		int i, index, size = list.getLength();
		Element element;
		String field;
		
		ObjectIntOpenHashMap<String> map = new ObjectIntOpenHashMap<String>();
		
		for (i=0; i<size; i++)
		{
			element = (Element)list.item(i);
			field   = UTXml.getTrimmedAttribute(element, TAG_READER_COLUMN_FIELD);
			index   = Integer.parseInt(element.getAttribute(TAG_READER_COLUMN_INDEX));
			
			map.put(field, index);
		}
		
		return map;
	}
	
	// ============================= getModel =============================
	
	protected AbstractModel getModel(Element eTrain, AbstractTrainSpace space, int index, int boot)
	{
		NodeList list = eTrain.getElementsByTagName(TAG_TRAIN_ALGORITHM);
		int numThreads = getNumOfThreads(eTrain);
		Element  eAlgorithm;
		String   name;
		
		eAlgorithm = (Element)list.item(index);
		name       = UTXml.getTrimmedAttribute(eAlgorithm, TAG_TRAIN_ALGORITHM_NAME);
		
		if (name.equals("liblinear"))
		{
			byte solver = Byte  .parseByte  (UTXml.getTrimmedAttribute(eAlgorithm, "solver"));
			double cost = Double.parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "cost"));
			double eps  = Double.parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "eps"));
			double bias = Double.parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "bias"));

			return getLiblinearModel(space, numThreads, solver, cost, eps, bias);
		}
		else if (name.equals("adagrad"))
		{
			String[] tmp = UTXml.getTrimmedAttribute(eAlgorithm, "iter").split(",");
			
			int    iter  = Integer.parseInt   (tmp[boot]);
			double alpha = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "alpha"));
			double rho   = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "rho"));
			
			return getAdaGradModel(space, numThreads, iter, i_rand, alpha, rho);
		}
		
		else if (name.equals("adagrad-lr"))
		{
			String[] tmp = UTXml.getTrimmedAttribute(eAlgorithm, "iter").split(",");
			
			int    iter  = Integer.parseInt   (tmp[boot]);
			double alpha = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "alpha"));
			double rho   = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "rho"));
			
			return getAdaGradLRModel(space, numThreads, iter, i_rand, alpha, rho);
		}
		
		return null;
	}
	
	/** Called by {@link AbstractNLP#getModel(Element, AbstractTrainSpace, int, int)}. */
	protected AbstractModel getLiblinearModel(AbstractTrainSpace space, int numThreads, byte solver, double cost, double eps, double bias)
	{
		space.build();
		LOG.info("Liblinear:\n");
		LOG.info(String.format("- solver=%d, cost=%f, eps=%f, bias=%f\n", solver, cost, eps, bias));
		return LiblinearTrain.getModel(space, numThreads, solver, cost, eps, bias);
	}
	
	/** Called by {@link AbstractNLP#getModel(Element, AbstractTrainSpace, int, int)}. */
	protected AbstractModel getAdaGradModel(AbstractTrainSpace space, int numThreads, int iter, int rand, double alpha, double rho)
	{
		space.build();
		LOG.info("AdaGrad:\n");
		LOG.info(String.format("- iter=%d, rand=%d, alpha=%f, rho=%f\n", iter, rand, alpha, rho));

		LOG.info("Training:\n");
		AdaGradHinge ag = new AdaGradHinge(iter, alpha, rho, new Random(rand));
		
		AbstractModel model = space.getModel();
		model.setWeights(ag.getWeight(space, numThreads));
		
		return model;
	}
	
	/** Called by {@link AbstractNLP#getModel(Element, AbstractTrainSpace, int, int)}. */
	protected AbstractModel getAdaGradLRModel(AbstractTrainSpace space, int numThreads, int iter, int rand, double alpha, double rho)
	{
		space.build();
		LOG.info("AdaGrad-LR:\n");
		LOG.info(String.format("- iter=%d, rand=%d, alpha=%f, rho=%f\n", iter, rand, alpha, rho));

		LOG.info("Training:\n");
		AdaGradLR ag = new AdaGradLR(iter, alpha, rho, new Random(rand));
		
		AbstractModel model = space.getModel();
		model.setWeights(ag.getWeight(space, numThreads));
		
		return model;
	}
	
	// ============================= updateModel =============================
	
	protected void updateModel(Element eTrain, AbstractTrainSpace space, Random rand, int nUpdate, int index)
	{
		NodeList list = eTrain.getElementsByTagName(TAG_TRAIN_ALGORITHM);
		int numThreads = getNumOfThreads(eTrain);
		Element  eAlgorithm;
		String   name;
		
		eAlgorithm = (Element)list.item(index);
		name       = UTXml.getTrimmedAttribute(eAlgorithm, TAG_TRAIN_ALGORITHM_NAME);
		
		if (name.equals("adagrad"))
		{
			int    iter  = Integer.parseInt   (UTXml.getTrimmedAttribute(eAlgorithm, "iter"));
			double alpha = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "alpha"));
			double rho   = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "rho"));
			
			updateAdaGradModel(space, rand, numThreads, nUpdate, iter, alpha, rho);
		}
		else if (name.equals("adagrad-lr"))
		{
			int    iter  = Integer.parseInt   (UTXml.getTrimmedAttribute(eAlgorithm, "iter"));
			double alpha = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "alpha"));
			double rho   = Double .parseDouble(UTXml.getTrimmedAttribute(eAlgorithm, "rho"));
			
			updateAdaGradLRModel(space, rand, numThreads, nUpdate, iter, alpha, rho);
		}
	}
	
	protected void updateAdaGradModel(AbstractTrainSpace space, Random rand, int numThreads, int nUpdate, int iter, double alpha, double rho)
	{
		AbstractModel model = space.getModel();
		
		if (model.getWeights() == null)
		{
			space.build();
			model.initWeightVector();
		}
		
		LOG.info(String.format("%3d: AdaGrad, iter=%d, alpha=%f, rho=%f\n", nUpdate, iter, alpha, rho));
		AdaGradHinge ag = new AdaGradHinge(iter, alpha, rho, rand);
		ag.updateWeight(space);
	}
	
	protected void updateAdaGradLRModel(AbstractTrainSpace space, Random rand, int numThreads, int nUpdate, int iter, double alpha, double rho)
	{
		AbstractModel model = space.getModel();
		
		if (model.getWeights() == null)
		{
			space.build();
			model.initWeightVector();
		}
		
		LOG.info(String.format("%3d: AdaGrad-LR, iter=%d, alpha=%f, rho=%f\n", nUpdate, iter, alpha, rho));
		AdaGradLR ag = new AdaGradLR(iter, alpha, rho, rand);
		ag.updateWeight(space);
	}
	
	protected int getNumOfThreads(Element eTrain)
	{
		Element eThreads = UTXml.getFirstElementByTagName(eTrain, TAG_TRAIN_THREADS); 
		return Integer.parseInt(UTXml.getTrimmedTextContent(eThreads));
	}
	
	protected void printTime(String message, long st, long et)
	{
		long millis = et - st;
		long mins   = TimeUnit.MILLISECONDS.toMinutes(millis);
		long secs   = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(mins);

		LOG.debug(message+"\n");
		LOG.debug(String.format("- %d mins, %d secs", mins, secs));
	}
}