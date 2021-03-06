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
package com.googlecode.clearnlp.component.dep;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.carrotsearch.hppc.IntOpenHashSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.googlecode.clearnlp.classification.algorithm.AbstractAlgorithm;
import com.googlecode.clearnlp.classification.feature.FtrToken;
import com.googlecode.clearnlp.classification.feature.JointFtrXml;
import com.googlecode.clearnlp.classification.model.StringModel;
import com.googlecode.clearnlp.classification.prediction.StringPrediction;
import com.googlecode.clearnlp.classification.train.StringTrainSpace;
import com.googlecode.clearnlp.classification.vector.StringFeatureVector;
import com.googlecode.clearnlp.component.AbstractStatisticalComponentSB;
import com.googlecode.clearnlp.dependency.DEPHead;
import com.googlecode.clearnlp.dependency.DEPLabel;
import com.googlecode.clearnlp.dependency.DEPLib;
import com.googlecode.clearnlp.dependency.DEPLibEn;
import com.googlecode.clearnlp.dependency.DEPNode;
import com.googlecode.clearnlp.dependency.DEPState;
import com.googlecode.clearnlp.dependency.DEPTree;
import com.googlecode.clearnlp.nlp.NLPLib;
import com.googlecode.clearnlp.util.UTInput;
import com.googlecode.clearnlp.util.UTOutput;
import com.googlecode.clearnlp.util.pair.ObjectDoublePair;
import com.googlecode.clearnlp.util.pair.Pair;
import com.googlecode.clearnlp.util.pair.StringIntPair;
import com.googlecode.clearnlp.util.triple.Triple;

/**
 * Dependency parser using selectional branching.
 * @since 1.3.2
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
abstract public class AbstractDEPParser extends AbstractStatisticalComponentSB
{
	protected final String ENTRY_CONFIGURATION = NLPLib.MODE_DEP+"_sb" + NLPLib.ENTRY_CONFIGURATION;
	protected final String ENTRY_FEATURE	   = NLPLib.MODE_DEP+"_sb" + NLPLib.ENTRY_FEATURE;
	protected final String ENTRY_LEXICA		   = NLPLib.MODE_DEP+"_sb" + NLPLib.ENTRY_LEXICA;
	protected final String ENTRY_MODEL		   = NLPLib.MODE_DEP+"_sb" + NLPLib.ENTRY_MODEL;
	protected final String ENTRY_WEIGHTS	   = NLPLib.MODE_DEP+"_sb" + NLPLib.ENTRY_WEIGHTS;
	
	protected final String LB_LEFT		= "L";
	protected final String LB_RIGHT		= "R";
	protected final String LB_NO		= "N";
	protected final String LB_SHIFT		= "S";
	protected final String LB_REDUCE	= "R";
	protected final String LB_PASS		= "P";
	
	protected IntOpenHashSet	s_reduce;
	protected StringIntPair[]	g_heads;
	protected int				i_lambda, i_beta;

	List<ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>> l_branches;
	protected Map<String,Pair<DEPLabel,DEPLabel>> m_labels;
	protected List<List<DEPHead>> l_2ndDep;
	protected double[] n_2ndPos;
	protected int n_trans;
	
//	====================================== CONSTRUCTORS ======================================
	
	/** Constructs a dependency parsing for training. */
	public AbstractDEPParser(JointFtrXml[] xmls, StringTrainSpace[] spaces, Object[] lexica, double margin, int beams)
	{
		super(xmls, spaces, lexica, margin, beams);
	}
	
	/** Constructs a dependency parsing for developing. */
	public AbstractDEPParser(JointFtrXml[] xmls, StringModel[] models, Object[] lexica, double margin, int beams)
	{
		super(xmls, models, lexica, margin, beams);
	}
	
	/** Constructs a dependency parser for bootsrapping. */
	public AbstractDEPParser(JointFtrXml[] xmls, StringTrainSpace[] spaces, StringModel[] models, Object[] lexica, double margin, int beams)
	{
		super(xmls, spaces, models, lexica, margin, beams);
	}
	
	/** Constructs a dependency parser for decoding. */
	public AbstractDEPParser(ZipInputStream in)
	{
		super(in);
	}
	
	@Override
	protected void initLexia(Object[] lexica) {}
	
//	====================================== ABSTRACT METHODS ======================================
	
	abstract protected void    rerankPredictions(List<StringPrediction> ps);
	abstract protected void    resetPost(DEPNode lambda, DEPNode beta, DEPLabel label);
	abstract protected boolean isNotHead(DEPNode node);
	abstract protected boolean resetPre(DEPNode lambda, DEPNode beta);
	abstract protected void    postParse();
	
//	====================================== LOAD/SAVE MODELS ======================================
	
	@Override
	public void loadModels(ZipInputStream zin)
	{
		f_xmls   = new JointFtrXml[1];
		s_models = null;
		ZipEntry zEntry;
		String   entry;
				
		try
		{
			while ((zEntry = zin.getNextEntry()) != null)
			{
				entry = zEntry.getName();
				
				if      (entry.equals(ENTRY_CONFIGURATION))
					loadConfiguration(zin);
				else if (entry.startsWith(ENTRY_FEATURE))
					loadFeatureTemplates(zin, Integer.parseInt(entry.substring(ENTRY_FEATURE.length())));
				else if (entry.startsWith(ENTRY_MODEL))
					loadStatisticalModels(zin, Integer.parseInt(entry.substring(ENTRY_MODEL.length())));
				else if (entry.startsWith(ENTRY_WEIGHTS))
					loadWeightVector(zin, Integer.parseInt(entry.substring(ENTRY_WEIGHTS.length())));
			}		
		}
		catch (Exception e) {e.printStackTrace();}
	}
	
	protected void loadConfiguration(ZipInputStream zin) throws Exception
	{
		BufferedReader fin = UTInput.createBufferedReader(zin);
		LOG.info("Loading configuration.\n");
		
		int i, mSize = Integer.parseInt(fin.readLine());
		n_beams  = Integer.parseInt(fin.readLine());
		d_margin = Double.parseDouble(fin.readLine());
		
		s_models = new StringModel[mSize];
		
		for (i=0; i<mSize; i++)
			s_models[i] = new StringModel();
	}

	@Override
	public void saveModels(ZipOutputStream zout)
	{
		try
		{
			saveConfiguration    (zout, ENTRY_CONFIGURATION);
			saveFeatureTemplates (zout, ENTRY_FEATURE);
			saveStatisticalModels(zout, ENTRY_MODEL);
			saveWeightVector     (zout, ENTRY_WEIGHTS);
			zout.close();
		}
		catch (Exception e) {e.printStackTrace();}
	}
	
	protected void saveConfiguration(ZipOutputStream zout, String entryName) throws Exception
	{
		zout.putNextEntry(new ZipEntry(entryName));
		PrintStream fout = UTOutput.createPrintBufferedStream(zout);
		LOG.info("Saving configuration.\n");
		
		fout.println(s_models.length);
		fout.println(n_beams);
		fout.println(d_margin);
		
		fout.flush();
		zout.closeEntry();
	}
	
//	====================================== GETTERS AND SETTERS ======================================
	
	@Override
	public Object[] getLexica() {return null;}
	
	@Override
	public void countAccuracy(int[] counts)
	{
		int i, las = 0, uas = 0, ls = 0;
		StringIntPair p;
		DEPNode node;
		
		for (i=1; i<t_size; i++)
		{
			node = d_tree.get(i);
			
			p = g_heads[i];
			
			if (node.isDependentOf(d_tree.get(p.i)))
			{
				uas++;
				if (node.isLabel(p.s)) las++;
			}
			
			if (node.isLabel(p.s)) ls++;
		}
		
		counts[0] += t_size - 1;
		counts[1] += las;
		counts[2] += uas;
		counts[3] += ls;
	}
	
//	================================ PROCESS ================================
	
	@Override
	public void process(DEPTree tree)
	{
		init(tree);
		processAux();
	}
	
	/**
	 * {@link AbstractDEPParser#process(DEPTree)} must be called before this method.
	 * @param uniqueOnly if {@code true}, include only unique trees.
	 * @return a list of pairs containing parsed trees and their scores, sorted by scores in descending order.
	 */
	@SuppressWarnings("unchecked")
	public List<ObjectDoublePair<DEPTree>> getParsedTrees(boolean uniqueOnly)
	{
		ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>> p;
		Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> t;
		List<ObjectDoublePair<DEPTree>> trees = Lists.newArrayList();
		Set<String> set = Sets.newHashSet();
		int i, size = l_branches.size();
		String s;
		
		Collections.sort(l_branches);
		DEPTree tree;
		
		for (i=0; i<size; i++)
		{
			p = l_branches.get(i);
			t = (Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>)p.o;
			tree = d_tree.clone();
			tree.resetHeads(t.o1);
			
			d_tree = tree;
			postProcess();
			postParse();
			s = tree.toStringDEP();
			
			if (!uniqueOnly || !set.contains(s))
			{
				set.add(s);
				trees.add(new ObjectDoublePair<DEPTree>(tree, p.d));
			}
		}
		
		return trees;
	}
	
	/** Called by {@link AbstractDEPParser#process(DEPTree)}. */
	protected void init(DEPTree tree)
	{
	 	d_tree = tree;
	 	t_size = tree.size();
	 	
	 	m_labels = new HashMap<String,Pair<DEPLabel,DEPLabel>>();
	 	l_2ndDep = new ArrayList<List<DEPHead>>();
	 	n_2ndPos = new double[t_size];
	 	
	 	int i; for (i=0; i<t_size; i++)
	 		l_2ndDep.add(new ArrayList<DEPHead>());
	 	
	 	if (i_flag != FLAG_DECODE)
	 	{
	 		g_heads = tree.getHeads();
	 		tree.clearHeads();	
	 	}
	 	
	 	initAux(false);
	}
	
	protected void initAux(boolean clear)
	{
		i_lambda = 0;
	 	i_beta   = 1;
	 	d_score  = 0;
	 	n_trans  = 0;
	 	b_first  = true;
	 	s_reduce = new IntOpenHashSet();
	 	
	 	if (clear)
	 	{
		 	int i; for (i=0; i<t_size; i++)
		 		l_2ndDep.get(i).clear();
		 	
		 	Arrays.fill(n_2ndPos, 0);
		 	d_tree.clearHeads();	 		
	 	}
	}
	
	/** Called by {@link AbstractDEPParser#process(DEPTree)}. */
	protected void processAux()
	{
		List<Pair<String,StringFeatureVector>> insts = parse();
		
		if (i_flag == FLAG_TRAIN || i_flag == FLAG_BOOTSTRAP)
		{
			for (Pair<String,StringFeatureVector> inst : insts)
				s_spaces[0].addInstance(inst.o1, inst.o2);				
		}
		else if (i_flag == FLAG_DECODE && resetPOSTags())
		{
			initAux(true);
			processAux();
		}
	}
	
	/** Called by {@link AbstractDEPParser#processAux()}. */
	protected List<Pair<String,StringFeatureVector>> parse()
	{
		List<Pair<String,StringFeatureVector>> insts = (i_flag == FLAG_TRAIN) ? parseMain().o2 : parseBranches();

		if (i_flag == FLAG_DEVELOP || i_flag == FLAG_DECODE)
		{
			postProcess();
			postParse();
		}

		return insts;
	}
	
	protected Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> parseMain()
	{
		List<Pair<String,StringFeatureVector>> insts = new ArrayList<Pair<String,StringFeatureVector>>();
		List<DEPState> states = new ArrayList<DEPState>();
		DEPNode lambda, beta;
		DEPLabel label;
		
		while (i_beta < t_size)
		{
			if (i_lambda < 0)
			{
				noShift();
				continue;
			}
			
			lambda = d_tree.get(i_lambda);
			beta   = d_tree.get(i_beta);
			
			if (resetPre(lambda, beta))
				continue;
		
			label = getLabel(insts, states);
			parseAux(lambda, beta, label);
			
			resetPost(lambda, beta, label);
		}
		
		if (states.size() > n_beams - 1)
		{
			Collections.sort(states);
			states = states.subList(0, n_beams - 1);
		}
		
		return new Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>(d_tree.getHeads(), insts, states);
	}
	
	protected void parseAux(DEPNode lambda, DEPNode beta, DEPLabel label)
	{
		d_score += label.score;
		n_trans++;
		
		if (label.isArc(LB_LEFT))
		{
			if (i_lambda == DEPLib.ROOT_ID)
				noShift();
			else if (beta.isDescendentOf(lambda))
				noPass();
			else if (label.isList(LB_REDUCE))
				leftReduce(lambda, beta, label.deprel);
			else
				leftPass(lambda, beta, label.deprel);
		}
		else if (label.isArc(LB_RIGHT))
		{
			if (lambda.isDescendentOf(beta))
				noPass();
			else if (label.isList(LB_SHIFT))
				rightShift(lambda, beta, label.deprel);
			else
				rightPass(lambda, beta, label.deprel);
		}
		else
		{
			if (label.isList(LB_SHIFT))
				noShift();
			else if (label.isList(LB_REDUCE) && lambda.hasHead())
				noReduce();
			else
				noPass();
		}
	}
	
	/** Called by {@link AbstractDEPParser#parse()}. */
	protected DEPLabel getLabel(List<Pair<String,StringFeatureVector>> insts, List<DEPState> states)
	{
		StringFeatureVector vector = getFeatureVector(f_xmls[0]);
		DEPLabel label = null;
		
		if (i_flag == FLAG_TRAIN)
		{
			label = getGoldLabel();
			insts.add(new Pair<String,StringFeatureVector>(label.toString(), vector));
		}
		else if (i_flag == FLAG_DECODE || i_flag == FLAG_DEVELOP)
		{
			label = getAutoLabel(vector, states);
		}
		else if (i_flag == FLAG_BOOTSTRAP)
		{
			label = getAutoLabel(vector, states);
			insts.add(new Pair<String,StringFeatureVector>(getGoldLabel().toString(), vector));
		}
		
		return label;
	}
	
	/** Called by {@link AbstractDEPParser#getLabel()}. */
	protected DEPLabel getGoldLabel()
	{
		DEPLabel label = getGoldLabelArc();
		
		if (label.isArc(LB_LEFT))
			label.list = isGoldReduce(true) ? LB_REDUCE : LB_PASS;
		else if (label.isArc(LB_RIGHT))
			label.list = isGoldShift() ? LB_SHIFT : LB_PASS;
		else
		{
			if      (isGoldShift())			label.list = LB_SHIFT;
			else if (isGoldReduce(false))	label.list = LB_REDUCE;
			else							label.list = LB_PASS;
		}
		
		return label;
	}
	
	/** Called by {@link AbstractDEPParser#getGoldLabel()}. */
	private DEPLabel getGoldLabelArc()
	{
		StringIntPair head = g_heads[i_lambda];
		
		if (head.i == i_beta)
			return new DEPLabel(LB_LEFT, head.s);
		
		head = g_heads[i_beta];
		
		if (head.i == i_lambda)
			return new DEPLabel(LB_RIGHT, head.s);
		
		return new DEPLabel(LB_NO, "");
	}
	
	/** Called by {@link AbstractDEPParser#getGoldLabel()}. */
	private boolean isGoldShift()
	{
		if (g_heads[i_beta].i < i_lambda)
			return false;
		
		int i;
		
		for (i=i_lambda-1; i>0; i--)
		{
			if (s_reduce.contains(i))
				continue;
			
			if (g_heads[i].i == i_beta)
				return false;
		}
		
		return true;
	}
	
	/** Called by {@link AbstractDEPParser#getGoldLabel()}. */
	private boolean isGoldReduce(boolean hasHead)
	{
		if (!hasHead && !d_tree.get(i_lambda).hasHead())
			return false;
		
		int i, size = d_tree.size();
		
		for (i=i_beta+1; i<size; i++)
		{
			if (g_heads[i].i == i_lambda)
				return false;
		}
		
		return true;
	}
	
	/** Called by {@link AbstractDEPParser#getLabel()}. */
	private DEPLabel getAutoLabel(StringFeatureVector vector, List<DEPState> states)
	{
		String key = vector.toString();
		Pair<DEPLabel,DEPLabel> val = m_labels.get(key);
		DEPLabel fst, snd;
		List<DEPHead> p;
		
		if (val != null)
		{
			fst = val.o1;
			snd = val.o2;
		}
		else
		{
			List<StringPrediction> ps = getPredictions(vector);

			fst = new DEPLabel(ps.get(0).label, ps.get(0).score);
			snd = new DEPLabel(ps.get(1).label, ps.get(1).score);
			
			m_labels.put(key, new Pair<DEPLabel,DEPLabel>(fst, snd));
		}
		
		if (fst.score - snd.score < d_margin)
		{
			if (fst.isArc(LB_NO))
			{
				if (snd.isArc(LB_LEFT))
				{
					p = l_2ndDep.get(i_lambda);
					p.add(new DEPHead(i_beta, snd.deprel, snd.score));
				}
				else if (snd.isArc(LB_RIGHT))
				{
					p = l_2ndDep.get(i_beta);
					p.add(new DEPHead(i_lambda, snd.deprel, snd.score));
				}
			}
			
			if (b_first)
				states.add(new DEPState(i_lambda, i_beta, n_trans, d_score, snd, d_tree.getHeads(), s_reduce.clone()));
		}
		
		return fst;
	}
	
	private List<StringPrediction> getPredictions(StringFeatureVector vector)
	{
		List<StringPrediction> ps = s_models[0].predictAll(vector);
		AbstractAlgorithm.normalize(ps);
		rerankPredictions(ps);
		
		return ps;
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void leftReduce(DEPNode lambda, DEPNode beta, String deprel)
	{
		leftArc(lambda, beta, deprel);
		reduce();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void leftPass(DEPNode lambda, DEPNode beta, String deprel)
	{
		leftArc(lambda, beta, deprel);
		pass();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void rightShift(DEPNode lambda, DEPNode beta, String deprel)
	{
		rightArc(lambda, beta, deprel);
		shift();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void rightPass(DEPNode lambda, DEPNode beta, String deprel)
	{
		rightArc(lambda, beta, deprel);
		pass();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void noShift()
	{
		shift();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void noReduce()
	{
		reduce();
	}
	
	/** Called by {@link AbstractDEPParser#depParseAux()}. */
	protected void noPass()
	{
		pass();
	}
	
	private void leftArc(DEPNode lambda, DEPNode beta, String deprel)
	{
		lambda.setHead(beta, deprel);
	}
	
	private void rightArc(DEPNode lambda, DEPNode beta, String deprel)
	{
		beta.setHead(lambda, deprel);
	}
	
	private void shift()
	{
		i_lambda = i_beta++;
	}
	
	private void reduce()
	{
		s_reduce.add(i_lambda);
		passAux();
	}
	
	private void pass()
	{
		passAux();
	}
	
	protected void passAux()
	{
		int i;
		
		for (i=i_lambda-1; i>=0; i--)
		{
			if (!s_reduce.contains(i))
			{
				i_lambda = i;
				return;
			}
		}
		
		i_lambda = i;
	}
	
	protected void postProcess()
	{
		Triple<DEPNode,String,Double> max = new Triple<DEPNode,String,Double>(null, null, -1d);
		DEPNode root = d_tree.get(DEPLib.ROOT_ID);
		List<DEPHead> list;
		DEPNode node, head;
		int i;
		
		for (i=1; i<t_size; i++)
		{
			node = d_tree.get(i);
			
			if (!node.hasHead())
			{
				if (!(list = l_2ndDep.get(node.id)).isEmpty())
				{
					for (DEPHead p : list)
					{
						head = d_tree.get(p.headId);
						
						if (!isNotHead(head) && !head.isDescendentOf(node))
						{
							node.setHead(head, p.deprel);
							break;
						}
					}
				}
				
				if (!node.hasHead())
				{
					max.set(root, DEPLibEn.DEP_ROOT, -1d);
					
					postProcessAux(node, -1, max);
					postProcessAux(node, +1, max);
					
					node.setHead(max.o1, max.o2);
				}
			}
		}
	}
	
	protected void postProcessAux(DEPNode node, int dir, Triple<DEPNode,String,Double> max)
	{
		List<StringPrediction> ps;
		DEPLabel label;
		DEPNode  head;
		int i;
		
		if (dir < 0)	i_beta   = node.id;
		else			i_lambda = node.id;
		
		for (i=node.id+dir; 0<=i && i<t_size; i+=dir)
		{
			head = d_tree.get(i);			
			if (head.isDescendentOf(node))	continue;
			
			if (dir < 0)	i_lambda = i;
			else			i_beta   = i;
			
			ps = getPredictions(getFeatureVector(f_xmls[0]));
			
			for (StringPrediction p : ps)
			{
				if (p.score <= max.o3)
					break;
				
				label = new DEPLabel(p.label);
				
				if ((dir < 0 && label.isArc(LB_RIGHT)) || (dir > 0 && label.isArc(LB_LEFT)))
				{
					max.set(head, label.deprel, p.score);
					break;
				}
			}
		}
	}

//	================================ FEATURE EXTRACTION ================================

	@Override
	protected String getField(FtrToken token)
	{
		DEPNode node = getNode(token);
		if (node == null)	return null;
		Matcher m;
		
		if (token.isField(JointFtrXml.F_FORM))
		{
			return node.form;
		}
		else if (token.isField(JointFtrXml.F_LEMMA))
		{
			return node.lemma;
		}
		else if (token.isField(JointFtrXml.F_POS))
		{
			return node.pos;
		}
		else if (token.isField(JointFtrXml.F_DEPREL))
		{
			return node.getLabel();
		}
		else if (token.isField(JointFtrXml.F_DISTANCE))
		{
			int dist = i_beta - i_lambda;
			return (dist > 6) ? "6" : Integer.toString(dist);
		}
		else if (token.isField(JointFtrXml.F_LEFT_VALENCY))
		{
			return Integer.toString(d_tree.getLeftValency(node.id));
		}
		else if (token.isField(JointFtrXml.F_RIGHT_VALENCY))
		{
			return Integer.toString(d_tree.getRightValency(node.id));
		}
		else if ((m = JointFtrXml.P_BOOLEAN.matcher(token.field)).find())
		{
			int field = Integer.parseInt(m.group(1));
			
			switch (field)
			{
			case  0: return (i_lambda == 1) ? token.field : null;
			case  1: return (i_beta == t_size-1) ? token.field : null;
			case  2: return (i_lambda+1 == i_beta) ? token.field : null;
			default: throw new IllegalArgumentException("Unsupported feature: "+field);
			}
		}
		else if ((m = JointFtrXml.P_FEAT.matcher(token.field)).find())
		{
			return node.getFeat(m.group(1));
		}
		
		return null;
	}
	
	@Override
	protected String[] getFields(FtrToken token)
	{
		return null;
	}
	
//	================================ NODE GETTER ================================
	
	protected DEPNode getNode(FtrToken token)
	{
		DEPNode node = null;
		
		switch (token.source)
		{
		case JointFtrXml.S_STACK : node = getNodeStack(token);	break;
		case JointFtrXml.S_LAMBDA: node = getNodeLambda(token);	break;
		case JointFtrXml.S_BETA  : node = getNodeBeta(token);	break;
		}
		
		if (node == null)	return null;
		
		if (token.relation != null)
		{
			     if (token.isRelation(JointFtrXml.R_H))		node = node.getHead();
			else if (token.isRelation(JointFtrXml.R_H2))	node = node.getGrandHead();
			else if (token.isRelation(JointFtrXml.R_LMD))	node = d_tree.getLeftMostDependent  (node.id);
			else if (token.isRelation(JointFtrXml.R_RMD))	node = d_tree.getRightMostDependent (node.id);
			else if (token.isRelation(JointFtrXml.R_LMD2))	node = d_tree.getLeftMostDependent  (node.id, 1);
			else if (token.isRelation(JointFtrXml.R_RMD2))	node = d_tree.getRightMostDependent (node.id, 1);
			else if (token.isRelation(JointFtrXml.R_LNS))	node = d_tree.getLeftNearestSibling (node.id);
			else if (token.isRelation(JointFtrXml.R_RNS))	node = d_tree.getRightNearestSibling(node.id);
		}
		
		return node;
	}
	
	/** Called by {@link AbstractDEPParser#getNode(FtrToken)}. */
	private DEPNode getNodeStack(FtrToken token)
	{
		if (token.offset == 0)
			return d_tree.get(i_lambda);
		
		int offset = Math.abs(token.offset), i;
		int dir    = (token.offset < 0) ? -1 : 1;
					
		for (i=i_lambda+dir; 0<i && i<i_beta; i+=dir)
		{
			if (!s_reduce.contains(i) && --offset == 0)
				return d_tree.get(i);
		}
		
		return null;
	}

	/** Called by {@link AbstractDEPParser#getNode(FtrToken)}. */
	private DEPNode getNodeLambda(FtrToken token)
	{
		if (token.offset == 0)
			return d_tree.get(i_lambda);
		
		int cIndex = i_lambda + token.offset;
		
		if (0 < cIndex && cIndex < i_beta)
			return d_tree.get(cIndex);
		
		return null;
	}
	
	/** Called by {@link AbstractDEPParser#getNode(FtrToken)}. */
	private DEPNode getNodeBeta(FtrToken token)
	{
		if (token.offset == 0)
			return d_tree.get(i_beta);
		
		int cIndex = i_beta + token.offset;
		
		if (i_lambda < cIndex && cIndex < t_size)
			return d_tree.get(cIndex);
		
		return null;
	}
	
//	================================ SELECTIONAL BRANCHING ================================
	
	@SuppressWarnings("unchecked")
	public List<Pair<String,StringFeatureVector>> parseBranches()
	{
		Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> t0 = parseMain();
		double s0 = d_score / n_trans; 

		l_branches = Lists.newArrayList();
		l_branches.add(new ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>(t0, s0));
		
		if ((i_flag == FLAG_DECODE || i_flag == FLAG_DEVELOP) && t0.o3.isEmpty()) return null;
		Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> tm;
		b_first = false;
		
		branch(l_branches, t0.o3);
		
		if (i_flag == FLAG_DECODE || i_flag == FLAG_DEVELOP)
		{
//			for (ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>> pp : list)
//			{
//				Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> tt = (Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>)pp.o;
//				d_tree.resetHeads(tt.o1);
//				System.out.println(pp.d+"\n"+d_tree.toStringDEP()+"\n");
//			}
			
			tm = (Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>)getMax(l_branches).o;
			d_tree.resetHeads(tm.o1);
			return null;
		}
		else
		{
			List<Pair<String,StringFeatureVector>> insts = new ArrayList<Pair<String,StringFeatureVector>>(t0.o2);
			setGoldScores(l_branches);
			
			tm = (Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>)getMax(l_branches).o;
			insts.addAll(tm.o2);
			
			return insts;
		}
	}
	
	private void branch(List<ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>> list, List<DEPState> states)
	{
		Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>> t1;
		double s1;
		
		for (DEPState state : states)
		{
			resetState(state);
			t1 = parseMain();
			s1 = d_score / n_trans;
			list.add(new ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>(t1, s1));
		}
	}
	
	private void resetState(DEPState state)
	{
		i_lambda = state.iLambda;
		i_beta   = state.iBeta;
		n_trans  = state.trans;
		d_score  = state.score;
		s_reduce = state.reduce;
		d_tree.resetHeads(state.heads);
		parseAux(d_tree.get(i_lambda), d_tree.get(i_beta), state.label);
	}
	
	private ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>> getMax(List<ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>> dList) 
	{
		ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>> max = dList.get(0), t;
		int i, size = dList.size();
		
		for (i=1; i<size; i++)
		{
			t = dList.get(i);
			if (max.d < t.d) max = t;
		}
		
		return max;
	}
	
	@SuppressWarnings("unchecked")
	private void setGoldScores(List<ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>>> list)
	{
		StringIntPair gHead, sHead;
		StringIntPair[] sHeads;
		int i, c;
		
		for (ObjectDoublePair<Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>> p : list)
		{
			sHeads = ((Triple<StringIntPair[],List<Pair<String,StringFeatureVector>>,List<DEPState>>)p.o).o1;
			
			for (i=1,c=0; i<t_size; i++)
			{
				gHead = g_heads[i];
				sHead =  sHeads[i];
				
				if (gHead.i == sHead.i && gHead.s.equals(sHead.s))
					c++;
			}
			
			p.d = c;
		}
	}
	
//	================================ RESET POS TAGS ================================
	
	private boolean resetPOSTags()
	{
		boolean reset = false;
		DEPNode node;
		int i;
		
		for (i=1; i<t_size; i++)
		{
			if (n_2ndPos[i] > 0)
			{
				reset = true;
				node = d_tree.get(i);
				node.pos = node.removeFeat(DEPLib.FEAT_POS2);
			}
		}
		
		return reset;
	}
}
