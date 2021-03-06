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

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

import org.kohsuke.args4j.Option;
import org.w3c.dom.Element;

import com.carrotsearch.hppc.ObjectIntOpenHashMap;
import com.googlecode.clearnlp.classification.feature.JointFtrXml;
import com.googlecode.clearnlp.classification.model.StringModel;
import com.googlecode.clearnlp.classification.train.StringTrainSpace;
import com.googlecode.clearnlp.component.AbstractStatisticalComponent;
import com.googlecode.clearnlp.component.dep.AbstractDEPParser;
import com.googlecode.clearnlp.component.dep.DefaultDEPParser;
import com.googlecode.clearnlp.component.dep.EnglishDEPParser;
import com.googlecode.clearnlp.component.pos.AbstractPOSTagger;
import com.googlecode.clearnlp.component.pos.CPOSTaggerSB;
import com.googlecode.clearnlp.component.pos.DefaultPOSTagger;
import com.googlecode.clearnlp.component.pos.EnglishPOSTagger;
import com.googlecode.clearnlp.component.srl.AbstractSRLabeler;
import com.googlecode.clearnlp.component.srl.CPredIdentifier;
import com.googlecode.clearnlp.component.srl.CRolesetClassifier;
import com.googlecode.clearnlp.component.srl.CSenseClassifier;
import com.googlecode.clearnlp.component.srl.DefaultSRLabeler;
import com.googlecode.clearnlp.component.srl.EnglishSRLabeler;
import com.googlecode.clearnlp.dependency.DEPTree;
import com.googlecode.clearnlp.engine.EngineProcess;
import com.googlecode.clearnlp.propbank.frameset.AbstractFrames;
import com.googlecode.clearnlp.propbank.frameset.MultiFrames;
import com.googlecode.clearnlp.reader.AbstractReader;
import com.googlecode.clearnlp.reader.JointReader;
import com.googlecode.clearnlp.util.UTFile;
import com.googlecode.clearnlp.util.UTInput;
import com.googlecode.clearnlp.util.UTXml;
import com.googlecode.clearnlp.util.map.Prob1DMap;

/**
 * @since 1.3.0
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
public class NLPTrain extends AbstractNLP
{
	protected final String DELIM_FILES	= ":";
	@Option(name="-c", usage="configuration file (required)", required=true, metaVar="<filename>")
	protected String s_configFile;
	@Option(name="-f", usage="feature template files delimited by '"+DELIM_FILES+"' (required)", required=true, metaVar="<filename>")
	protected String s_featureFiles;
	@Option(name="-i", usage="input directory containing training files (required)", required=true, metaVar="<directory>")
	protected String s_trainDir;
	@Option(name="-m", usage="model file (output; required)", required=true, metaVar="<filename>")
	protected String s_modelFile;
	@Option(name="-n", usage="bootstrapping level (default: 2)", required=false, metaVar="<integer>")
	protected int n_boot = 0;
	@Option(name="-z", usage="mode (pos|morph|dep-sb|pred|role|srl)", required=true, metaVar="<string>")
	protected String s_mode;
	@Option(name="-margin", usage="margin between the 1st and 2nd predictions (default: 0.5)", required=false, metaVar="<double>")
	protected double d_margin = 0.5;
	@Option(name="-beam", usage="the size of beam (default: 1)", required=false, metaVar="<double>")
	protected int n_beams = 1;
	@Option(name="-frames", usage="directory containing frameset files", required=false, metaVar="<directory>")
	protected String s_framesDir;
	
	public NLPTrain() {}
	
	public NLPTrain(String[] args)
	{
		initArgs(args);
		
		try
		{
			train(s_configFile, s_featureFiles.split(DELIM_FILES), s_trainDir, s_modelFile, s_mode);
		}
		catch (Exception e) {e.printStackTrace();}
	}
	
	public void train(String configFile, String[] featureFiles, String trainDir, String modelFile, String mode) throws Exception
	{
		Element     eConfig = UTXml.getDocumentElement(new FileInputStream(configFile));
		JointFtrXml[]  xmls = getFeatureTemplates(featureFiles);
		String[] trainFiles = UTFile.getSortedFileListBySize(trainDir, ".*", true);
		JointReader  reader = getJointReader(UTXml.getFirstElementByTagName(eConfig, TAG_READER));

		AbstractStatisticalComponent component = getComponent(eConfig, reader, xmls, trainFiles, -1, mode);
		component.saveModels(new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(modelFile))));
	}
	
	//	====================================== GETTERS/SETTERS ======================================

	protected AbstractStatisticalComponent getComponent(Element eConfig, JointReader reader, JointFtrXml[] xmls, String[] trainFiles, int devId, String mode)
	{
		String language = getLanguage(eConfig);
		
		if      (mode.equals(NLPLib.MODE_POS))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, getPOSTaggerForCollect(reader, xmls, trainFiles, devId, language), mode, devId);
		else if (mode.equals(NLPLib.MODE_DEP))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, null, mode, devId);
		else if (mode.equals(NLPLib.MODE_PRED))
			return getTrainedComponent(eConfig, xmls, trainFiles, null, null, mode, 0, devId);
		else if (mode.equals(NLPLib.MODE_ROLE))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, new CRolesetClassifier(xmls), mode, devId);
		else if (mode.equals(NLPLib.MODE_SRL))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, getSRLabelerForCollect(xmls, language), mode, devId);
		else if (mode.startsWith(NLPLib.MODE_SENSE))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, new CSenseClassifier(xmls, mode.substring(mode.lastIndexOf("_")+1)), mode, devId);
		else if (mode.equals(NLPLib.MODE_POS_SB))
			return getTrainedComponent(eConfig, reader, xmls, trainFiles, new CPOSTaggerSB(xmls, getLowerSimplifiedForms(reader, xmls[0], trainFiles, devId)), mode, devId);
		
		throw new IllegalArgumentException("The requested mode '"+mode+"' is not supported.");
	}
	
	protected AbstractPOSTagger getPOSTaggerForCollect(JointReader reader, JointFtrXml[] xmls, String[] trainFiles, int devId, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return new EnglishPOSTagger(xmls, getLowerSimplifiedForms(reader, xmls[0], trainFiles, devId));
		else
			return new DefaultPOSTagger(xmls, getLowerSimplifiedForms(reader, xmls[0], trainFiles, devId));
	}
	
	protected AbstractSRLabeler getSRLabelerForCollect(JointFtrXml[] xmls, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return new EnglishSRLabeler(xmls, getFrames());
		else
			return new DefaultSRLabeler(xmls, getFrames());
	}
	
	protected AbstractFrames getFrames()
	{
		return (s_framesDir != null) ? new MultiFrames(s_framesDir) : null;
	}
	
	/** @return a component for developing. */
	protected AbstractStatisticalComponent getComponent(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, String mode, String language)
	{
		if      (mode.equals(NLPLib.MODE_POS))
			return getPOSTaggerForDevelop(xmls, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_DEP))
			return getDEPParserForDevelop(xmls, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_PRED))
			return new CPredIdentifier(xmls, models, lexica);
		else if (mode.equals(NLPLib.MODE_ROLE))
			return new CRolesetClassifier(xmls, models, lexica);
		else if (mode.equals(NLPLib.MODE_SRL))
			return getSRLabelerForDevelop(xmls, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_POS_SB))
			return new CPOSTaggerSB(xmls, models, lexica, d_margin, n_beams);
		else if (mode.startsWith(NLPLib.MODE_SENSE))
			return new CSenseClassifier(xmls, models, lexica, mode.substring(mode.lastIndexOf("_")+1));
		
		throw new IllegalArgumentException("The requested mode '"+mode+"' is not supported.");
	}
	
	protected AbstractPOSTagger getPOSTaggerForDevelop(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return new EnglishPOSTagger(xmls, models, lexica);
		else
			return new DefaultPOSTagger(xmls, models, lexica);
	}
	
	protected AbstractDEPParser getDEPParserForDevelop(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return new EnglishDEPParser(xmls, models, lexica, d_margin, n_beams);
		else
			return new DefaultDEPParser(xmls, models, lexica, d_margin, n_beams);
	}
	
	protected AbstractSRLabeler getSRLabelerForDevelop(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return new EnglishSRLabeler(xmls, models, lexica);
		else
			return new DefaultSRLabeler(xmls, models, lexica);
	}
	
	protected AbstractStatisticalComponent getTrainedComponent(Element eConfig, JointReader reader, JointFtrXml[] xmls, String[] trainFiles, AbstractStatisticalComponent component, String mode, int devId) 
	{
		Object[] lexica = (component != null) ? getLexica(component, reader, xmls, trainFiles, devId) : null;
		AbstractStatisticalComponent processor = null;
		StringModel[] models = null;
		int boot;
		
		for (boot=0; boot<=n_boot; boot++)
		{
			processor = getTrainedComponent(eConfig, xmls, trainFiles, models, lexica, mode, boot, devId);
			models = processor.getModels();
		}
		
		return processor;
	}
	
	protected JointFtrXml[] getFeatureTemplates(String[] featureFiles) throws Exception
	{
		int i, size = featureFiles.length;
		JointFtrXml[] xmls = new JointFtrXml[size];
		
		for (i=0; i<size; i++)
			xmls[i] = new JointFtrXml(new FileInputStream(featureFiles[i]));
		
		return xmls;
	}
	
	protected Object[] getLexica(AbstractStatisticalComponent component, JointReader reader, JointFtrXml[] xmls, String[] trainFiles, int devId)
	{
		int i, size = trainFiles.length;
		DEPTree tree;
		
		LOG.info("Collecting lexica:\n");
		
		for (i=0; i<size; i++)
		{
			if (devId == i)	continue;
			reader.open(UTInput.createBufferedFileReader(trainFiles[i]));
			
			while ((tree = reader.next()) != null)
				component.process(tree);
			
			reader.close();
			LOG.debug(".");
		}	LOG.debug("\n");
		
		return component.getLexica();
	}
	
	//	====================================== PART-OF-SPEECH TAGGING ======================================
	
	/** Called by {@link NLPTrain#trainPOSTagger(Element, JointFtrXml[], String[], JointReader)}. */
	protected Set<String> getLowerSimplifiedForms(JointReader reader, JointFtrXml xml, String[] trainFiles, int devId)
	{
		Set<String> set = new HashSet<String>();
		int i, j, len, size = trainFiles.length;
		Prob1DMap map = new Prob1DMap();
		DEPTree tree;
		
		LOG.info("Collecting word-forms:\n");
		
		for (i=0; i<size; i++)
		{
			if (devId == i)	continue;
			
			reader.open(UTInput.createBufferedFileReader(trainFiles[i]));
			set.clear();
			
			while ((tree = reader.next()) != null)
			{
				EngineProcess.normalizeForms(tree);
				len = tree.size();
				
				for (j=1; j<len; j++)
					set.add(tree.get(j).lowerSimplifiedForm);
			}
			
			reader.close();
			map.addAll(set);
			LOG.debug(".");
		}	LOG.debug("\n");
		
		return map.toSet(xml.getDocumentFrequencyCutoff());
	}
	
	//	====================================== TRAINING ======================================
	
	/** Called by {@link NLPTrain#getTrainedJointPD(String, String[], String, String)}. */
	protected AbstractStatisticalComponent getTrainedComponent(Element eConfig, JointFtrXml[] xmls, String[] trainFiles, StringModel[] models, Object[] lexica, String mode, int boot, int devId)
	{
		StringTrainSpace[] spaces = getStringTrainSpaces(eConfig, xmls, trainFiles, models, lexica, mode, boot, devId);
		Element eTrain = UTXml.getFirstElementByTagName(eConfig, mode);
		String language = getLanguage(eConfig);
		
		int i, mSize = spaces.length;
		models = new StringModel[mSize];
		
		for (i=0; i<mSize; i++)
		{
			if (mode.equals(NLPLib.MODE_ROLE) || mode.startsWith(NLPLib.MODE_SENSE))
				models[i] = (StringModel)getModel(eTrain, spaces[i], 0, boot);
			else
				models[i] = (StringModel)getModel(eTrain, spaces[i], i, boot);

			spaces[i].clear();
		}
		
		return getComponent(xmls, models, lexica, mode, language);
	}
	
	protected StringTrainSpace[] getStringTrainSpaces(Element eConfig, JointFtrXml[] xmls, String[] trainFiles, StringModel[] models, Object[] lexica, String mode, int boot, int devId)
	{
		Element eTrain = UTXml.getFirstElementByTagName(eConfig, mode);
		int i, j, mSize = 1, size = trainFiles.length;
		int numThreads = getNumOfThreads(eTrain);
		String language = getLanguage(eConfig);
		
		List<StringTrainSpace[]> lSpaces = new ArrayList<StringTrainSpace[]>();
		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		StringTrainSpace[] spaces;
		
		LOG.info("Collecting training instances:\n");
		
		for (i=0; i<size; i++)
		{
			if (devId != i)
			{
				lSpaces.add(spaces = getStringTrainSpaces(xmls, lexica, mode, boot));
				executor.execute(new TrainTask(eConfig, trainFiles[i], getComponent(xmls, spaces, models, lexica, mode, language)));
			}
		}
		
		executor.shutdown();
		
		try
		{
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		}
		catch (InterruptedException e) {e.printStackTrace();}
		
		LOG.debug("\n");
		
		mSize = lSpaces.get(0).length;
		spaces = new StringTrainSpace[mSize];
		StringTrainSpace sp;

		for (i=0; i<mSize; i++)
		{
			spaces[i] = lSpaces.get(0)[i];
			
			if ((size = lSpaces.size()) > 1)
			{
				LOG.info("Merging training instances:\n");
				
				for (j=1; j<size; j++)
				{
					spaces[i].appendSpace(sp = lSpaces.get(j)[i]);
					sp.clear();
					LOG.debug(".");
				}	LOG.debug("\n");
			}
		}
		
		return spaces;
	}
	
	protected AbstractStatisticalComponent getComponent(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, String mode, String language)
	{
		if      (mode.equals(NLPLib.MODE_POS))
			return getPOSTaggerForTrain(xmls, spaces, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_DEP))
			return getDEPParserForTrain(xmls, spaces, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_SRL))
			return getSRLabelerForTrain(xmls, spaces, models, lexica, language);
		else if (mode.equals(NLPLib.MODE_PRED))
			return new CPredIdentifier(xmls, spaces, lexica);	
		else if (mode.equals(NLPLib.MODE_ROLE))
			return new CRolesetClassifier(xmls, spaces, lexica);
		else if (mode.startsWith(NLPLib.MODE_SENSE))
			return new CSenseClassifier(xmls, spaces, lexica, mode.substring(mode.lastIndexOf("_")+1));
		else if (mode.equals(NLPLib.MODE_POS_SB))
			return (models == null) ? new CPOSTaggerSB(xmls, spaces, lexica, d_margin, n_beams) : new CPOSTaggerSB(xmls, spaces, models, lexica, d_margin, n_beams);
		
		throw new IllegalArgumentException("The requested mode '"+mode+"' is not supported.");
	}
	
	protected AbstractPOSTagger getPOSTaggerForTrain(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return (models == null) ? new EnglishPOSTagger(xmls, spaces, lexica) : new EnglishPOSTagger(xmls, spaces, models, lexica);
		else
			return (models == null) ? new DefaultPOSTagger(xmls, spaces, lexica) : new DefaultPOSTagger(xmls, spaces, models, lexica);
	}
	
	protected AbstractDEPParser getDEPParserForTrain(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return (models == null) ? new EnglishDEPParser(xmls, spaces, lexica, d_margin, n_beams) : new EnglishDEPParser(xmls, spaces, models, lexica, d_margin, n_beams);
		else
			return (models == null) ? new DefaultDEPParser(xmls, spaces, lexica, d_margin, n_beams) : new DefaultDEPParser(xmls, spaces, models, lexica, d_margin, n_beams);
	}
	
	protected AbstractSRLabeler getSRLabelerForTrain(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, String language)
	{
		if (language.equals(AbstractReader.LANG_EN))
			return (models == null) ? new EnglishSRLabeler(xmls, spaces, lexica) : new EnglishSRLabeler(xmls, spaces, models, lexica);
		else
			return (models == null) ? new DefaultSRLabeler(xmls, spaces, lexica) : new DefaultSRLabeler(xmls, spaces, models, lexica);
	}
	
	@SuppressWarnings("unchecked")
	/** Called by {@link COMTrain#getStringTrainSpaces(Element, JointFtrXml[], String[], StringModel[], Object[], String, int)}. */
	protected StringTrainSpace[] getStringTrainSpaces(JointFtrXml[] xmls, Object[] lexica, String mode, int boot)
	{
		if      (mode.equals(NLPLib.MODE_ROLE) || mode.startsWith(NLPLib.MODE_SENSE))
			return getStringTrainSpaces(xmls[0], ((ObjectIntOpenHashMap<String>)lexica[1]).size());
		else if (boot > 0 && mode.equals(NLPLib.MODE_DEP))
			return getStringTrainSpaces(xmls, 1);
		else if (mode.equals(NLPLib.MODE_SRL))
			return getStringTrainSpaces(xmls[0], 2);
		else
			return getStringTrainSpaces(xmls);
	}
	
	/** Called by {@link NLPTrain#getStringTrainSpaces(JointFtrXml[], Object[], String)}. */
	private StringTrainSpace[] getStringTrainSpaces(JointFtrXml[] xmls)
	{
		return getStringTrainSpaces(xmls, 0);
	}
	
	private StringTrainSpace[] getStringTrainSpaces(JointFtrXml[] xmls, int cIndex)
	{
		int i, size = xmls.length;
		StringTrainSpace[] spaces = new StringTrainSpace[size];
		
		for (i=0; i<size; i++)
			spaces[i] = new StringTrainSpace(false, xmls[i].getLabelCutoff(cIndex), xmls[i].getFeatureCutoff(cIndex));
		
		return spaces;
	}
	
	/** Called by {@link NLPTrain#getStringTrainSpaces(JointFtrXml[], Object[], String)}. */
	private StringTrainSpace[] getStringTrainSpaces(JointFtrXml xml, int size)
	{
		StringTrainSpace[] spaces = new StringTrainSpace[size];
		int i;
		
		for (i=0; i<size; i++)
			spaces[i] = new StringTrainSpace(false, xml.getLabelCutoff(0), xml.getFeatureCutoff(0));
		
		return spaces;
	}
	
	/** Called by {@link NLPTrain#getStringTrainSpaces(Element, JointFtrXml[], String[], StringModel[], Object[], String, int)}. */
	private class TrainTask implements Runnable
	{
		AbstractStatisticalComponent j_component;
		JointReader j_reader;
		
		public TrainTask(Element eConfig, String trainFile, AbstractStatisticalComponent component)
		{
			j_reader = getJointReader(UTXml.getFirstElementByTagName(eConfig, TAG_READER));
			j_reader.open(UTInput.createBufferedFileReader(trainFile));
			j_component = component;
		}
		
		public void run()
		{
			DEPTree tree;
			
			while ((tree = j_reader.next()) != null)
				j_component.process(tree);
			
			j_reader.close();
			LOG.debug(".");
		}
	}
	
	static public void main(String[] args)
	{
		new NLPTrain(args);
	}
}
